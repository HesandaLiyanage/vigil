package dev.vigil.drill;

/**
 * Invariant: Specifies supported fault injection modes for backup corruption testing.
 */
public enum CorruptionMode {
  NONE,
  TRUNCATE,
  BITROT
}
