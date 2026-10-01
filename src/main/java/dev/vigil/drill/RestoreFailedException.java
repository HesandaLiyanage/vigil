package dev.vigil.drill;

/**
 * Invariant: Thrown when container restoration process fails or exits with a non-zero code.
 */
public class RestoreFailedException extends RuntimeException {
  public RestoreFailedException(String msg) {
    super(msg);
  }
}
