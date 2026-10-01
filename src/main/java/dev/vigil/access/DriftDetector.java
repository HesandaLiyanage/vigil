package dev.vigil.access;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import org.springframework.stereotype.Component;

/**
 * Invariant: Reconciles observed database access grants against declarative policy to discover unexpected or missing privileges.
 */
@Component
public class DriftDetector {
  private static final Set<String> HIGH_RISK = Set.of(
      "ALL PRIVILEGES", "ALL", "SUPER", "GRANT OPTION", "FILE", "SHUTDOWN", "CREATE USER",
      "SUPERUSER", "CREATEROLE", "DBA", "SYSDBA", "GRANT ANY PRIVILEGE", "ROOT");
  private static final Set<String> MEDIUM = Set.of("DROP", "ALTER", "CREATE", "TRUNCATE", "DELETE", "REFERENCES");

  public List<DriftFinding> diff(Set<AccessEntry> actual, AccessPolicy.TargetPolicy policy) {
    Set<AccessEntry> filtered = new TreeSet<>();
    for (AccessEntry e : actual) {
      if (!policy.ignored().contains(e.principal())) {
        filtered.add(e);
      }
    }
    Set<AccessEntry> expected = policy.expected();

    List<DriftFinding> out = new ArrayList<>();
    for (AccessEntry e : filtered) {
      if (!expected.contains(e)) {
        out.add(new DriftFinding(DriftFinding.Type.UNEXPECTED, severity(e), e));
      }
    }
    for (AccessEntry e : expected) {
      if (!filtered.contains(e)) {
        out.add(new DriftFinding(DriftFinding.Type.MISSING, DriftFinding.Severity.MEDIUM, e));
      }
    }
    return out;
  }

  static DriftFinding.Severity severity(AccessEntry e) {
    if (HIGH_RISK.contains(e.privilege()) || e.scope().equals("*.*")) {
      return DriftFinding.Severity.HIGH;
    }
    if (MEDIUM.contains(e.privilege())) {
      return DriftFinding.Severity.MEDIUM;
    }
    return DriftFinding.Severity.LOW;
  }
}
