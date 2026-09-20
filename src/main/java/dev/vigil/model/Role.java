package dev.vigil.model;

/**
 * Invariant: Identifies the replication topology role of a managed target.
 */
public enum Role {
  PRIMARY,
  REPLICA,
  STANDALONE
}
