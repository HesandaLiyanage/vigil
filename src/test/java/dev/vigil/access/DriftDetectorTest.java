package dev.vigil.access;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;

class DriftDetectorTest {
  private final DriftDetector d = new DriftDetector();

  @Test
  void flagsUnexpectedHighRiskGrant() {
    var policy = new AccessPolicy.TargetPolicy(
        List.of(),
        Map.of("app@%", List.of(new AccessPolicy.ScopeGrant("db.*", List.of("SELECT")))));
    Set<AccessEntry> actual = new TreeSet<>(Set.of(
        new AccessEntry("app@%", "db.*", "SELECT"),
        new AccessEntry("app@%", "*.*", "SUPER")));
    var findings = d.diff(actual, policy);
    assertEquals(1, findings.size());
    assertEquals(DriftFinding.Severity.HIGH, findings.get(0).severity());
    assertEquals(DriftFinding.Type.UNEXPECTED, findings.get(0).type());
    assertEquals("*.*", findings.get(0).entry().scope());
    assertEquals("SUPER", findings.get(0).entry().privilege());
  }

  @Test
  void flagsMissingGrantAsMediumSeverity() {
    var policy = new AccessPolicy.TargetPolicy(
        List.of(),
        Map.of("app@%", List.of(new AccessPolicy.ScopeGrant("db.*", List.of("SELECT", "INSERT")))));
    Set<AccessEntry> actual = new TreeSet<>(Set.of(
        new AccessEntry("app@%", "db.*", "SELECT")));
    var findings = d.diff(actual, policy);
    assertEquals(1, findings.size());
    assertEquals(DriftFinding.Severity.MEDIUM, findings.get(0).severity());
    assertEquals(DriftFinding.Type.MISSING, findings.get(0).type());
    assertEquals("INSERT", findings.get(0).entry().privilege());
  }

  @Test
  void ignoresConfiguredPrincipals() {
    var policy = new AccessPolicy.TargetPolicy(
        List.of("root@localhost"),
        Map.of("app@%", List.of(new AccessPolicy.ScopeGrant("db.*", List.of("SELECT")))));
    Set<AccessEntry> actual = new TreeSet<>(Set.of(
        new AccessEntry("root@localhost", "*.*", "ALL PRIVILEGES"),
        new AccessEntry("app@%", "db.*", "SELECT")));
    var findings = d.diff(actual, policy);
    assertTrue(findings.isEmpty());
  }

  @Test
  void rootAndGlobalScopeAreHighSeverity() {
    var policy = new AccessPolicy.TargetPolicy(
        List.of(),
        Map.of("app@%", List.of()));
    Set<AccessEntry> actual = new TreeSet<>(Set.of(
        new AccessEntry("app@%", "*.*", "SELECT"),
        new AccessEntry("app@%", "db.*", "SUPERUSER"),
        new AccessEntry("app@%", "db.*", "DROP"),
        new AccessEntry("app@%", "db.*", "SELECT")));
    var findings = d.diff(actual, policy);
    assertEquals(4, findings.size());

    assertTrue(findings.stream().anyMatch(f -> f.entry().scope().equals("*.*") && f.severity() == DriftFinding.Severity.HIGH));
    assertTrue(findings.stream().anyMatch(f -> f.entry().privilege().equals("SUPERUSER") && f.severity() == DriftFinding.Severity.HIGH));
    assertTrue(findings.stream().anyMatch(f -> f.entry().privilege().equals("DROP") && f.severity() == DriftFinding.Severity.MEDIUM));
    assertTrue(findings.stream().anyMatch(f -> f.entry().privilege().equals("SELECT") && f.severity() == DriftFinding.Severity.LOW));
  }
}
