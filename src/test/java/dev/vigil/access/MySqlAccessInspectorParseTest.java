package dev.vigil.access;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.vigil.mysql.MySqlAccessInspector;
import java.util.Set;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;

class MySqlAccessInspectorParseTest {

  @Test
  void parsesStandardGrants() {
    Set<AccessEntry> entries = new TreeSet<>();
    MySqlAccessInspector.parse("app@%", "GRANT SELECT, INSERT ON `vigil_demo`.* TO `app`@`%`", entries);
    assertEquals(2, entries.size());
    assertTrue(entries.contains(new AccessEntry("app@%", "vigil_demo.*", "SELECT")));
    assertTrue(entries.contains(new AccessEntry("app@%", "vigil_demo.*", "INSERT")));
  }

  @Test
  void parsesColumnPrivilegeList() {
    Set<AccessEntry> entries = new TreeSet<>();
    MySqlAccessInspector.parse("app@%", "GRANT SELECT (id, name), INSERT ON `vigil_demo`.`users` TO `app`@`%`", entries);
    assertEquals(2, entries.size());
    assertTrue(entries.contains(new AccessEntry("app@%", "vigil_demo.users", "SELECT (ID, NAME)")));
    assertTrue(entries.contains(new AccessEntry("app@%", "vigil_demo.users", "INSERT")));
  }

  @Test
  void parsesWithGrantOption() {
    Set<AccessEntry> entries = new TreeSet<>();
    MySqlAccessInspector.parse("admin@%", "GRANT ALL PRIVILEGES ON *.* TO `admin`@`%` WITH GRANT OPTION", entries);
    assertEquals(2, entries.size());
    assertTrue(entries.contains(new AccessEntry("admin@%", "*.*", "ALL PRIVILEGES")));
    assertTrue(entries.contains(new AccessEntry("admin@%", "*.*", "GRANT OPTION")));
  }

  @Test
  void ignoresGrantUsage() {
    Set<AccessEntry> entries = new TreeSet<>();
    MySqlAccessInspector.parse("app@%", "GRANT USAGE ON *.* TO `app`@`%`", entries);
    assertTrue(entries.isEmpty());
  }

  @Test
  void parsesRoleGrants() {
    Set<AccessEntry> entries = new TreeSet<>();
    MySqlAccessInspector.parse("analyst@%", "GRANT `reader`, `exporter` TO `analyst`@`%`", entries);
    assertEquals(2, entries.size());
    assertTrue(entries.contains(new AccessEntry("analyst@%", "ROLE", "READER")));
    assertTrue(entries.contains(new AccessEntry("analyst@%", "ROLE", "EXPORTER")));
  }
}
