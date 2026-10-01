package dev.vigil.access;

/**
 * Invariant: Represents discrepancy between inspected runtime grants and declarative policy.
 */
public record DriftFinding(Type type, Severity severity, AccessEntry entry) {
  public enum Type { UNEXPECTED, MISSING }
  public enum Severity { HIGH, MEDIUM, LOW }
}
