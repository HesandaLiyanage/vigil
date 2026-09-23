package dev.vigil.mysql;

import dev.vigil.collect.PerTargetRunner;
import dev.vigil.config.TargetRegistry;
import dev.vigil.config.VigilProperties;
import dev.vigil.model.EngineType;
import dev.vigil.model.ManagedTarget;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Invariant: Periodically writes millisecond-precision UTC heartbeats to primary MySQL databases having replicas.
 */
@Component
public class HeartbeatWriter {
  private static final Logger log = LoggerFactory.getLogger(HeartbeatWriter.class);
  private final VigilProperties props;
  private final TargetRegistry registry;
  private final PerTargetRunner runner = new PerTargetRunner("heartbeat");

  public HeartbeatWriter(VigilProperties props, TargetRegistry registry) {
    this.props = props;
    this.registry = registry;
  }

  @PostConstruct
  void start() {
    Set<String> primariesWithReplicas = props.targets().stream()
        .map(ManagedTarget::replicaOf)
        .filter(Objects::nonNull)
        .collect(Collectors.toSet());
    runner.schedule(
        props.heartbeatInterval(),
        () -> registry.all().stream()
            .filter(t -> t.engine() == EngineType.MYSQL && primariesWithReplicas.contains(t.id()))
            .toList(),
        this::beat);
  }

  private void beat(ManagedTarget t) throws SQLException {
    try (Connection c = registry.jdbc(t.id()); Statement st = c.createStatement()) {
      st.executeUpdate("INSERT INTO vigil_meta.heartbeat (id, ts) VALUES (1, NOW(6)) "
          + "ON DUPLICATE KEY UPDATE ts = NOW(6)");
    }
  }

  @PreDestroy
  void stop() {
    runner.close();
  }
}
