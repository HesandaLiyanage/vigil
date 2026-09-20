package dev.vigil.model;

import java.time.Instant;

/**
 * Invariant: Records a single observed query execution sample with latency and timestamp.
 */
public record QueryObservation(String sql, long latencyMicros, long count, Instant at) {}
