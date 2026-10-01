package dev.vigil.drill;

import dev.vigil.collect.RestoreDrillEvent;
import dev.vigil.config.TargetRegistry;
import dev.vigil.config.VigilProperties;
import dev.vigil.model.CheckResult;
import dev.vigil.model.DrillResult;
import dev.vigil.model.ManagedTarget;
import dev.vigil.spi.RestoreDriller;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.JdbcDatabaseContainer;

/**
 * Invariant: Template method orchestrating safe restore drills into throwaway containers and enforcing verification invariants.
 */
public abstract class LogicalRestoreDriller implements RestoreDriller {
  private static final Logger log = LoggerFactory.getLogger(LogicalRestoreDriller.class);

  protected final TargetRegistry registry;
  protected final JdbcVerifier verifier;
  protected final CanaryWriter canary;
  protected final VigilProperties props;

  protected LogicalRestoreDriller(TargetRegistry r, JdbcVerifier v, CanaryWriter c, VigilProperties p) {
    this.registry = r;
    this.verifier = v;
    this.canary = c;
    this.props = p;
  }

  /** Produce a backup file in workDir. Default impls shell out to docker exec; production would fetch the real artifact. */
  protected abstract Path createBackup(ManagedTarget t, Path workDir) throws Exception;
  protected abstract JdbcDatabaseContainer<?> newRestoreContainer();
  protected abstract void loadBackup(JdbcDatabaseContainer<?> c, Path artifact) throws Exception;
  protected abstract ChecksumStrategy checksumStrategy();

  @Override
  public DrillResult run(ManagedTarget t, DrillOptions opts) {
    String id = UUID.randomUUID().toString();
    Instant started = Instant.now();
    Path workDir = props.drill().workDir();
    RestoreDrillEvent ev = new RestoreDrillEvent();
    ev.target = t.id();
    ev.engine = t.engine().name();
    ev.begin();

    long backupMs = 0, startMs = 0, restoreMs = 0, verifyMs = 0, bytes = 0;
    String sha = null, error = null;
    List<CheckResult> checks = List.of();
    DrillResult.Status status = DrillResult.Status.ERROR;
    Path artifact = null;

    try {
      Files.createDirectories(workDir);
      String token = UUID.randomUUID().toString();
      Map<String, Long> before;
      Map<String, String> sums;
      try (Connection src = registry.jdbc(t.id())) {
        canary.write(src, props.drill().canaryTable(), token);
        before = verifier.rowCounts(src, t.schemas());
        sums = verifier.checksums(src, checksumStrategy(), t.stableTables());
      }

      long t0 = System.nanoTime();
      artifact = createBackup(t, workDir);
      backupMs = ms(t0);
      bytes = Files.size(artifact);
      sha = Sha256.of(artifact);                    // hash BEFORE fault injection
      FaultInjector.apply(artifact, opts.corruption());

      try (JdbcDatabaseContainer<?> c = newRestoreContainer()) {
        t0 = System.nanoTime();
        c.start();
        startMs = ms(t0);

        t0 = System.nanoTime();
        try {
          loadBackup(c, artifact);
        } finally {
          restoreMs = ms(t0);
        }

        t0 = System.nanoTime();
        try (Connection restored = connectRestored(c)) {
          checks = verifier.verify(restored, new VerificationPlan(
              t.schemas(), before, sums, token, props.drill().canaryTable(),
              props.drill().rowCountTolerance(), checksumStrategy()));
        }
        verifyMs = ms(t0);
      }
      status = checks.stream().allMatch(CheckResult::passed)
          ? DrillResult.Status.PASSED : DrillResult.Status.FAILED_VERIFICATION;
    } catch (RestoreFailedException e) {
      status = DrillResult.Status.FAILED_RESTORE;
      error = e.getMessage();
    } catch (Exception e) {
      status = DrillResult.Status.ERROR;
      error = e.toString();
      log.warn("drill error for {}", t.id(), e);
    } finally {
      if (artifact != null) {
        try {
          Files.deleteIfExists(artifact);
        } catch (IOException ignored) {}
      }
      ev.status = status.name();
      ev.backupBytes = bytes;
      ev.commit();
    }
    return new DrillResult(id, t.id(), started, status, backupMs, bytes, startMs, restoreMs, verifyMs,
        startMs + restoreMs + verifyMs, sha, checks, error);
  }

  protected Connection connectRestored(JdbcDatabaseContainer<?> c) throws SQLException {
    return DriverManager.getConnection(c.getJdbcUrl(), c.getUsername(), c.getPassword());
  }

  private static long ms(long t0) {
    return (System.nanoTime() - t0) / 1_000_000;
  }

  protected static void requireSafeIdentifiers(List<String> names) {
    for (String n : names) {
      if (!n.matches("[A-Za-z0-9_]+")) {
        throw new IllegalArgumentException("unsafe name: " + n);
      }
    }
  }
}
