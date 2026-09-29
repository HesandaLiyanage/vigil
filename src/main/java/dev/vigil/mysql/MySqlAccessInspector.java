package dev.vigil.mysql;

import dev.vigil.access.AccessEntry;
import dev.vigil.config.TargetRegistry;
import dev.vigil.model.EngineType;
import dev.vigil.model.ManagedTarget;
import dev.vigil.spi.AccessInspector;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * Invariant: Inspects MySQL user grants and normalizes SHOW GRANTS statements into structured AccessEntry records.
 */
@Component
public class MySqlAccessInspector implements AccessInspector {
  private static final Pattern GRANT_ON = Pattern.compile("^GRANT (.+) ON (.+) TO (.+?)( WITH GRANT OPTION)?$");
  private static final Pattern GRANT_ROLE = Pattern.compile("^GRANT (.+) TO (.+?)( WITH ADMIN OPTION)?$");
  private static final Pattern TOP_LEVEL_COMMA = Pattern.compile(",\\s*(?![^()]*\\))"); // not inside (col, col)

  private final TargetRegistry registry;

  public MySqlAccessInspector(TargetRegistry registry) {
    this.registry = registry;
  }

  @Override
  public EngineType engine() {
    return EngineType.MYSQL;
  }

  @Override
  public Set<AccessEntry> snapshot(ManagedTarget t) throws Exception {
    Set<AccessEntry> out = new TreeSet<>();
    try (Connection c = registry.jdbc(t.id()); Statement st = c.createStatement()) {
      List<String[]> accounts = new ArrayList<>();
      try (ResultSet rs = st.executeQuery("SELECT user, host FROM mysql.user")) {
        while (rs.next()) {
          accounts.add(new String[]{rs.getString(1), rs.getString(2)});
        }
      }
      for (String[] a : accounts) {
        String principal = a[0] + "@" + a[1];
        String sql = "SHOW GRANTS FOR '" + a[0].replace("'", "''") + "'@'" + a[1].replace("'", "''") + "'";
        try (ResultSet rs = st.executeQuery(sql)) {
          while (rs.next()) {
            parse(principal, rs.getString(1), out);
          }
        }
      }
    }
    return out;
  }

  public static void parse(String principal, String line, Set<AccessEntry> out) {
    Matcher m = GRANT_ON.matcher(line);
    if (m.matches()) {
      String scope = m.group(2).replace("`", "");
      for (String priv : TOP_LEVEL_COMMA.split(m.group(1))) {
        if (!priv.trim().equalsIgnoreCase("USAGE")) {
          out.add(new AccessEntry(principal, scope, priv));
        }
      }
      if (m.group(4) != null) {
        out.add(new AccessEntry(principal, scope, "GRANT OPTION"));
      }
      return;
    }
    Matcher r = GRANT_ROLE.matcher(line);
    if (r.matches()) {
      for (String role : r.group(1).split(",")) {
        out.add(new AccessEntry(principal, "ROLE", role.replace("`", "").trim()));
      }
    }
  }
}
