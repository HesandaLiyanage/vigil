package dev.vigil.postgres;

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
 * Invariant: Inspects PostgreSQL cluster role flags and relation privileges using aclexplode.
 */
@Component
public class PostgresAccessInspector implements AccessInspector {
  private final TargetRegistry registry;

  public PostgresAccessInspector(TargetRegistry registry) {
    this.registry = registry;
  }

  @Override
  public EngineType engine() {
    return EngineType.POSTGRES;
  }

  @Override
  public Set<AccessEntry> snapshot(ManagedTarget t) throws Exception {
    Set<AccessEntry> out = new TreeSet<>();
    try (Connection c = registry.jdbc(t.id()); Statement st = c.createStatement()) {
      // 1. Role flags
      try (ResultSet rs = st.executeQuery(
          "SELECT rolname, rolsuper, rolcreaterole, rolcreatedb FROM pg_roles WHERE rolname !~ '^pg_'")) {
        while (rs.next()) {
          String role = rs.getString(1);
          if (rs.getBoolean("rolsuper")) out.add(new AccessEntry(role, "CLUSTER", "SUPERUSER"));
          if (rs.getBoolean("rolcreaterole")) out.add(new AccessEntry(role, "CLUSTER", "CREATEROLE"));
          if (rs.getBoolean("rolcreatedb")) out.add(new AccessEntry(role, "CLUSTER", "CREATEDB"));
        }
      }

      // 2. Table and view grants via aclexplode
      try (ResultSet rs = st.executeQuery("""
          SELECT r.rolname, n.nspname || '.' || c.relname AS relation, a.privilege_type
          FROM pg_class c
          JOIN pg_namespace n ON n.oid = c.relnamespace
          CROSS JOIN LATERAL aclexplode(c.relacl) a
          JOIN pg_roles r ON r.oid = a.grantee
          WHERE c.relkind IN ('r','v','m','p')
            AND n.nspname NOT IN ('pg_catalog','information_schema','pg_toast')
          """)) {
        while (rs.next()) {
          out.add(new AccessEntry(rs.getString(1), rs.getString(2), rs.getString(3)));
        }
      }
    }
    return out;
  }
}
