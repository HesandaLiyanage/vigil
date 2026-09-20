package dev.vigil.config;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import dev.vigil.model.ManagedTarget;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.stereotype.Component;

/**
 * Invariant: Maintains thread-safe target metadata and lazy connection pools so unavailable targets do not block startup.
 */
@Component
public class TargetRegistry implements DisposableBean {
  private final Map<String, ManagedTarget> targets = new LinkedHashMap<>();
  private final Map<String, HikariDataSource> pools = new ConcurrentHashMap<>();
  private final Map<String, MongoClient> mongoClients = new ConcurrentHashMap<>();

  public TargetRegistry(VigilProperties props) {
    if (props.targets() != null) {
      props.targets().forEach(t -> targets.put(t.id(), t));
    }
  }

  public Collection<ManagedTarget> all() {
    return targets.values();
  }

  public ManagedTarget get(String id) {
    ManagedTarget t = targets.get(id);
    if (t == null) {
      throw new NoSuchElementException("unknown target: " + id);
    }
    return t;
  }

  /** Small lazy pool per target. initializationFailTimeout=-1 so a down DB never blocks startup. */
  public Connection jdbc(String id) throws SQLException {
    ManagedTarget t = get(id);
    return pools.computeIfAbsent(id, k -> {
      HikariConfig cfg = new HikariConfig();
      cfg.setPoolName("vigil-" + id);
      cfg.setJdbcUrl(t.url());
      cfg.setUsername(t.username());
      cfg.setPassword(t.password());
      cfg.setMaximumPoolSize(4);
      cfg.setMinimumIdle(0);
      cfg.setConnectionTimeout(3_000);
      cfg.setInitializationFailTimeout(-1);
      return new HikariDataSource(cfg);
    }).getConnection();
  }

  public MongoClient mongo(String id) {
    return mongoClients.computeIfAbsent(id, k -> MongoClients.create(get(id).url()));
  }

  @Override
  public void destroy() {
    pools.values().forEach(HikariDataSource::close);
    mongoClients.values().forEach(MongoClient::close);
  }
}
