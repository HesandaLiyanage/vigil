package dev.vigil.drill;

import dev.vigil.model.CheckResult;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * Invariant: Verifies structural table presence, tolerance-bounded row counts, canary presence, and table checksums.
 */
@Component
public class JdbcVerifier {

  public Map<String, Long> rowCounts(Connection c, List<String> schemas) throws SQLException {
    String q = c.getMetaData().getIdentifierQuoteString();
    String in = schemas.stream().map(s -> "?").collect(Collectors.joining(","));
    List<String> tables = new ArrayList<>();
    try (PreparedStatement ps = c.prepareStatement(
        "SELECT table_schema, table_name FROM information_schema.tables "
            + "WHERE table_type = 'BASE TABLE' AND table_schema IN (" + in + ")")) {
      for (int i = 0; i < schemas.size(); i++) {
        ps.setString(i + 1, schemas.get(i));
      }
      try (ResultSet rs = ps.executeQuery()) {
        while (rs.next()) {
          tables.add(rs.getString(1) + "." + rs.getString(2));
        }
      }
    }
    Map<String, Long> out = new TreeMap<>();
    for (String qualified : tables) {
      String[] p = qualified.split("\\.", 2);
      try (Statement st = c.createStatement();
           ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM " + q + p[0] + q + "." + q + p[1] + q)) {
        rs.next();
        out.put(qualified, rs.getLong(1));
      }
    }
    return out;
  }

  public Map<String, String> checksums(Connection c, ChecksumStrategy cs, List<String> tables) throws SQLException {
    Map<String, String> out = new LinkedHashMap<>();
    for (String t : tables) {
      out.put(t, cs.checksum(c, t));
    }
    return out;
  }

  public List<CheckResult> verify(Connection restored, VerificationPlan plan) {
    List<CheckResult> out = new ArrayList<>();
    Map<String, Long> after;
    try {
      after = rowCounts(restored, plan.schemas());
    } catch (SQLException e) {
      return List.of(new CheckResult("restored_db_readable", false, e.getMessage()));
    }

    // 1. every source table exists after restore
    List<String> missing = plan.rowCountsBefore().keySet().stream().filter(k -> !after.containsKey(k)).toList();
    out.add(new CheckResult("tables_present", missing.isEmpty(),
        missing.isEmpty() ? plan.rowCountsBefore().size() + " tables" : "missing: " + missing));

    // 2. row counts within tolerance (the live source keeps changing, so exact equality is wrong)
    List<String> off = new ArrayList<>();
    plan.rowCountsBefore().forEach((tbl, b) -> {
      Long a = after.get(tbl);
      if (a == null) return;
      double drift = Math.abs(a - b) / (double) Math.max(1, b);
      if (drift > plan.tolerance()) {
        off.add(tbl + " source=" + b + " restored=" + a);
      }
    });
    out.add(new CheckResult("row_counts", off.isEmpty(),
        off.isEmpty() ? "within " + (plan.tolerance() * 100) + "%" : String.join("; ", off)));

    // 3. canary: backup must contain the token written just before the dump
    try (PreparedStatement ps = restored.prepareStatement(
        "SELECT COUNT(*) FROM " + plan.canaryTable() + " WHERE token = ?")) {
      ps.setString(1, plan.canaryToken());
      try (ResultSet rs = ps.executeQuery()) {
        rs.next();
        out.add(new CheckResult("canary_token", rs.getLong(1) == 1, "found=" + rs.getLong(1)));
      }
    } catch (SQLException e) {
      out.add(new CheckResult("canary_token", false, e.getMessage()));
    }

    // 4. checksums of tables that do not change during the drill
    plan.checksumsBefore().forEach((tbl, expected) -> {
      try {
        String actual = plan.checksum().checksum(restored, tbl);
        out.add(new CheckResult("checksum:" + tbl, expected.equals(actual), "expected=" + expected + " actual=" + actual));
      } catch (SQLException e) {
        out.add(new CheckResult("checksum:" + tbl, false, e.getMessage()));
      }
    });
    return out;
  }
}
