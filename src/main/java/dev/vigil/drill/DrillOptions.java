package dev.vigil.drill;

/**
 * Invariant: Encapsulates drill execution options, defaulting to clean uncorrupted verification.
 */
public record DrillOptions(CorruptionMode corruption) {
  public static DrillOptions clean() {
    return new DrillOptions(CorruptionMode.NONE);
  }
}
