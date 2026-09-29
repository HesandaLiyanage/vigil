package dev.vigil.oracle;

import dev.vigil.access.AccessEntry;
import dev.vigil.config.TargetRegistry;
import dev.vigil.model.EngineType;
import dev.vigil.model.ManagedTarget;
import dev.vigil.spi.AccessInspector;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Set;
import java.util.TreeSet;
import org.springframework.stereotype.Component;

/**
 * Invariant: Inspects Oracle user privileges and roles for non-system accounts from DBA views.
 */
@Component
public class OracleAccessInspector implements AccessInspector {
  private final TargetRegistry registry;

  public OracleAccessInspector(TargetRegistry registry) {
    this.registry = registry;
  }

  @Override
  public EngineType engine() {
    return EngineType.ORACLE;
  }

  @Override
  public Set<AccessEntry> snapshot(ManagedTarget t) throws Exception {
    Set<AccessEntry> out = new TreeSet<>();
    try (Connection c = registry.jdbc(t.id()); Statement st = c.createStatement()) {
      // 1. Roles granted to non-system users
      try (ResultSet rs = st.executeQuery("""
          SELECT grantee, granted_role
          FROM dba_role_privs
          WHERE grantee IN (SELECT username FROM dba_users WHERE oracle_maintained = 'N')
          """)) {
        while (rs.next()) {
          out.add(new AccessEntry(rs.getString("grantee"), "ROLE", rs.getString("granted_role")));
        }
      }

      // 2. System privileges granted to non-system users
      try (ResultSet rs = st.executeQuery("""
          SELECT grantee, privilege
          FROM dba_sys_privs
          WHERE grantee IN (SELECT username FROM dba_users WHERE oracle_maintained = 'N')
          """)) {
        while (rs.next()) {
          out.add(new AccessEntry(rs.getString("grantee"), "SYSTEM", rs.getString("privilege")));
        }
      }
    }
    return out;
  }
}
