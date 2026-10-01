package dev.vigil.drill;

import java.sql.Connection;
import java.sql.SQLException;

/**
 * Invariant: Defines strategy for calculating deterministic table content checksums across restored engines.
 */
public interface ChecksumStrategy {
  String checksum(Connection c, String qualifiedTable) throws SQLException;
}
