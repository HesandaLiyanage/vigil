package dev.vigil.drill;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * Invariant: Writes high-entropy canary tokens to metadata tables immediately prior to backup creation.
 */
@Component
public class CanaryWriter {
  private static final Pattern SAFE = Pattern.compile("[A-Za-z0-9_.]+");

  public void write(Connection c, String canaryTable, String token) throws SQLException {
    if (!SAFE.matcher(canaryTable).matches()) {
      throw new IllegalArgumentException("bad table name: " + canaryTable);
    }
    try (PreparedStatement ps = c.prepareStatement("INSERT INTO " + canaryTable + " (token) VALUES (?)")) {
      ps.setString(1, token);
      ps.executeUpdate();
    }
  }
}
