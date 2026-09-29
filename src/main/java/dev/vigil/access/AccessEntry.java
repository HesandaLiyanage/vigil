package dev.vigil.access;

import java.util.Comparator;
import java.util.Locale;

/**
 * One normalized permission: who, on what, which privilege. Natural ordering makes diffs stable.
 * Invariant: Immutable normalized access representation with consistent uppercase privileges and unquoted scopes.
 */
public record AccessEntry(String principal, String scope, String privilege) implements Comparable<AccessEntry> {
  public AccessEntry {
    privilege = privilege == null ? "" : privilege.trim().toUpperCase(Locale.ROOT);
    scope = scope == null ? "" : scope.replace("`", "").trim();
    principal = principal == null ? "" : principal.trim();
  }

  @Override
  public int compareTo(AccessEntry o) {
    return Comparator.comparing(AccessEntry::principal)
        .thenComparing(AccessEntry::scope)
        .thenComparing(AccessEntry::privilege)
        .compare(this, o);
  }
}
