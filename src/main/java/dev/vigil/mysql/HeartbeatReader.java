package dev.vigil.mysql;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import org.springframework.stereotype.Component;

/**
 * Invariant: Measures true replication lag by comparing replica current time with heartbeat row written by primary in UTC.
 */
@Component
public class HeartbeatReader {
  /** Lag = replica clock now minus the timestamp the primary wrote. Both sessions run in UTC. */
  public double readLagMillis(Connection replica) throws SQLException {
    try (Statement st = replica.createStatement();
         ResultSet rs = st.executeQuery(
             "SELECT TIMESTAMPDIFF(MICROSECOND, ts, NOW(6)) / 1000.0 FROM vigil_meta.heartbeat WHERE id = 1")) {
      return rs.next() ? rs.getDouble(1) : Double.NaN;
    }
  }
}
