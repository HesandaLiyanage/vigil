package dev.vigil.mysql;

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
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.concurrent.TimeUnit;
import org.springframework.stereotype.Component;
import org.testcontainers.containers.JdbcDatabaseContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;

/**
 * Invariant: Executes MySQL restore verification drills using mysqldump and ephemeral MySQL Testcontainers.
 */
@Component
public class MySqlRestoreDriller extends LogicalRestoreDriller {
  private static final String PW = "restorepw";

  public MySqlRestoreDriller(TargetRegistry r, JdbcVerifier v, CanaryWriter c, VigilProperties p) {
    super(r, v, c, p);
  }

  @Override
  public EngineType engine() {
    return EngineType.MYSQL;
  }

  @Override
  protected Path createBackup(ManagedTarget t, Path workDir) throws Exception {
    requireSafeIdentifiers(t.schemas());
    Path out = workDir.resolve(t.id() + "-" + System.currentTimeMillis() + ".sql");
    Path err = workDir.resolve(t.id() + "-dump.err");
    // --set-gtid-purged=OFF is REQUIRED: the throwaway container has no GTID state, and without
    // this flag the restore fails on SET @@GLOBAL.GTID_PURGED.
    String cmd = "mysqldump -uroot -p\"$MYSQL_ROOT_PASSWORD\" --single-transaction --routines --triggers "
        + "--set-gtid-purged=OFF --databases " + String.join(" ", t.schemas());
    Process p = new ProcessBuilder("docker", "exec", t.dumpContainer(), "sh", "-c", cmd)
        .redirectOutput(out.toFile())
        .redirectError(err.toFile())
        .start();
    if (!p.waitFor(10, TimeUnit.MINUTES) || p.exitValue() != 0) {
      throw new IllegalStateException("mysqldump failed: " + (Files.exists(err) ? Files.readString(err) : ""));
    }
    return out;
  }

  @Override
  protected JdbcDatabaseContainer<?> newRestoreContainer() {
    return new MySQLContainer<>(DockerImageName.parse("mysql:8.4")).withPassword(PW);
  }

  @Override
  protected Connection connectRestored(JdbcDatabaseContainer<?> c) throws SQLException {
    return DriverManager.getConnection(c.getJdbcUrl(), "root", PW);
  }

  @Override
  protected void loadBackup(JdbcDatabaseContainer<?> c, Path artifact) throws Exception {
    c.copyFileToContainer(MountableFile.forHostPath(artifact), "/tmp/backup.sql");
    var r = c.execInContainer("sh", "-c", "mysql -uroot -p" + PW + " < /tmp/backup.sql");
    if (r.getExitCode() != 0) {
      throw new RestoreFailedException(r.getStderr());
    }
  }

  @Override
  protected ChecksumStrategy checksumStrategy() {
    return (conn, table) -> {
      try (Statement st = conn.createStatement(); ResultSet rs = st.executeQuery("CHECKSUM TABLE " + table)) {
        rs.next();
        return rs.getString(2);
      }
    };
  }
}
