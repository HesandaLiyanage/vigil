package dev.vigil.model;

import java.util.List;

/**
 * Invariant: Represents an immutable configuration descriptor for a monitored database target.
 */
public record ManagedTarget(
    String id,
    EngineType engine,
    Role role,
    String replicaOf,
    String url,
    String username,
    String password,
    String database,
    String dumpContainer,
    List<String> schemas,
    List<String> stableTables) {

  public ManagedTarget {
    role = role == null ? Role.STANDALONE : role;
    schemas = schemas == null ? List.of() : List.copyOf(schemas);
    stableTables = stableTables == null ? List.of() : List.copyOf(stableTables);
  }
}
