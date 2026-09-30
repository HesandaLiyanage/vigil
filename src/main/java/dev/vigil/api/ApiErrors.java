package dev.vigil.api;

import java.util.NoSuchElementException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Invariant: Maps core domain exceptions to standard HTTP error status codes across all REST controllers.
 */
@RestControllerAdvice
public class ApiErrors {
  @ExceptionHandler(NoSuchElementException.class)
  public ResponseEntity<String> notFound(NoSuchElementException e) {
    return ResponseEntity.status(404).body(e.getMessage());
  }

  @ExceptionHandler(IllegalStateException.class)
  public ResponseEntity<String> conflict(IllegalStateException e) {
    return ResponseEntity.status(409).body(e.getMessage());
  }

  @ExceptionHandler(UnsupportedOperationException.class)
  public ResponseEntity<String> unsupported(UnsupportedOperationException e) {
    return ResponseEntity.status(400).body(e.getMessage());
  }
}
