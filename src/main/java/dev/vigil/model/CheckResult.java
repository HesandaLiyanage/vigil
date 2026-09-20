package dev.vigil.model;

/**
 * Invariant: Represents an individual pass/fail verification assertion from a restore drill.
 */
public record CheckResult(String name, boolean passed, String detail) {}
