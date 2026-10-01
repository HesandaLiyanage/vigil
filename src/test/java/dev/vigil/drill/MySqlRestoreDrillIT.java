package dev.vigil.drill;

import dev.vigil.config.TargetRegistry;
import dev.vigil.config.VigilProperties;
import dev.vigil.model.DrillResult;
import dev.vigil.model.EngineType;
import dev.vigil.model.ManagedTarget;
import dev.vigil.model.Role;
import dev.vigil.mysql.MySqlRestoreDriller;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/**
 * Invariant: Proves automated end-to-end MySQL restore drill execution and corruption detection in Testcontainers.
 */
@Testcontainers
class MySqlRestoreDrillIT {
  @Container
  static MySQLContainer<?> source = new MySQLContainer<>("mysql:8.4").withPassword("srcpw");

  @Test
  void cleanBackupPassesAndTruncatedBackupFails() throws Exception {
    try (Connection c = DriverManager.getConnection(source.getJdbcUrl(), "root", "srcpw");
         Statement st = c.createStatement()) {
      st.execute("CREATE DATABASE vigil_demo");
      st.execute("CREATE TABLE vigil_demo.t (id INT PRIMARY KEY, v VARCHAR(20))");
      st.execute("INSERT INTO vigil_demo.t VALUES (1,'a'),(2,'b'),(3,'c')");
      st.execute("CREATE DATABASE vigil_meta");
      st.execute("CREATE TABLE vigil_meta.canary (id BIGINT AUTO_INCREMENT PRIMARY KEY, token VARCHAR(64) NOT NULL)");
    }
    String containerName = source.getContainerInfo().getName().substring(1);
    ManagedTarget target = new ManagedTarget(
        "it-mysql",
        EngineType.MYSQL,
        Role.PRIMARY,
        null,
        source.getJdbcUrl(),
        "root",
        "srcpw",
        null,
        containerName,
        List.of("vigil_demo", "vigil_meta"),
        List.of("vigil_demo.t"));

    VigilProperties props = new VigilProperties(
        List.of(target),
        null,
        null,
        null,
        new VigilProperties.Drill("0 0 */6 * * *", Path.of("./target/test-drills"), 0.02, "vigil_meta.canary"),
        null);

    TargetRegistry registry = new TargetRegistry(props);
    JdbcVerifier verifier = new JdbcVerifier();
    CanaryWriter canary = new CanaryWriter();
    MySqlRestoreDriller driller = new MySqlRestoreDriller(registry, verifier, canary, props);

    DrillResult cleanResult = driller.run(target, DrillOptions.clean());
    assertEquals(DrillResult.Status.PASSED, cleanResult.status(), () -> "Checks: " + cleanResult.checks() + " error=" + cleanResult.error());

    DrillResult truncateResult = driller.run(target, new DrillOptions(CorruptionMode.TRUNCATE));
    assertNotEquals(DrillResult.Status.PASSED, truncateResult.status());
  }
}
