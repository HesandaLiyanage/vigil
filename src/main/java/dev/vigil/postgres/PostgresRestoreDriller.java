package dev.vigil.postgres;

import dev.vigil.config.TargetRegistry;
import dev.vigil.config.VigilProperties;
import dev.vigil.drill.CanaryWriter;
import dev.vigil.drill.ChecksumStrategy;
import dev.vigil.drill.JdbcVerifier;
import dev.vigil.drill.LogicalRestoreDriller;
import dev.vigil.drill.RestoreFailedException;
import dev.vigil.model.EngineType;
import dev.vigil.model.ManagedTarget;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.concurrent.TimeUnit;
import org.springframework.stereotype.Component;
import org.testcontainers.containers.JdbcDatabaseContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;

/**
 * Invariant: Executes PostgreSQL restore verification drills using pg_dump and ephemeral PostgreSQL Testcontainers.
 */
@Component
public class PostgresRestoreDriller extends LogicalRestoreDriller {
  public PostgresRestoreDriller(TargetRegistry r, JdbcVerifier v, CanaryWriter c, VigilProperties p) {
    super(r, v, c, p);
  }

  @Override
  public EngineType engine() {
    return EngineType.POSTGRES;
  }

  @Override
  protected Path createBackup(ManagedTarget t, Path workDir) throws Exception {
    Path out = workDir.resolve(t.id() + "-" + System.currentTimeMillis() + ".dump");
    Path err = workDir.resolve(t.id() + "-dump.err");
    Process p = new ProcessBuilder(
        "docker", "exec", t.dumpContainer(),
        "pg_dump", "-U", "vigil_admin", "-Fc", t.database())
        .redirectOutput(out.toFile())
        .redirectError(err.toFile())
        .start();
    if (!p.waitFor(10, TimeUnit.MINUTES) || p.exitValue() != 0) {
      throw new IllegalStateException("pg_dump failed: " + (Files.exists(err) ? Files.readString(err) : ""));
    }
    return out;
  }

  @Override
  protected JdbcDatabaseContainer<?> newRestoreContainer() {
    return new PostgreSQLContainer<>(DockerImageName.parse("postgres:16"));
  }

  @Override
  protected void loadBackup(JdbcDatabaseContainer<?> c, Path artifact) throws Exception {
    c.copyFileToContainer(MountableFile.forHostPath(artifact), "/tmp/backup.dump");
    var r = c.execInContainer("pg_restore", "-U", c.getUsername(), "-d", c.getDatabaseName(),
        "--no-owner", "--exit-on-error", "/tmp/backup.dump");
    if (r.getExitCode() != 0) {
      throw new RestoreFailedException(r.getStderr());
    }
  }

  @Override
  protected ChecksumStrategy checksumStrategy() {
    return (conn, table) -> {
      try (Statement st = conn.createStatement();
           ResultSet rs = st.executeQuery(
               "SELECT md5(string_agg(t::text, '|' ORDER BY t::text)) FROM " + table + " t")) {
        rs.next();
        return rs.getString(1);
      }
    };
  }
}
