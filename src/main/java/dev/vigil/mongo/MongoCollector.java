package dev.vigil.mongo;

import com.mongodb.client.MongoDatabase;
import dev.vigil.config.TargetRegistry;
import dev.vigil.model.EngineType;
import dev.vigil.model.HealthSample;
import dev.vigil.model.ManagedTarget;
import dev.vigil.spi.Collector;
import java.util.LinkedHashMap;
import java.util.Map;
import org.bson.Document;
import org.springframework.stereotype.Component;

/**
 * Invariant: Collects MongoDB connection usage, uptime, opcounters, and cluster storage size via administrative commands.
 */
@Component
public class MongoCollector implements Collector {
  private final TargetRegistry registry;

  public MongoCollector(TargetRegistry registry) {
    this.registry = registry;
  }

  @Override
  public EngineType engine() {
    return EngineType.MONGODB;
  }

  @Override
  public HealthSample collect(ManagedTarget t) {
    MongoDatabase admin = registry.mongo(t.id()).getDatabase("admin");
    Document ss = admin.runCommand(new Document("serverStatus", 1));
    Document conns = ss.get("connections", Document.class);
    Map<String, Double> m = new LinkedHashMap<>();
    if (conns != null) {
      if (conns.get("current") instanceof Number n) {
        m.put("connections_active", n.doubleValue());
      }
      if (conns.get("available") instanceof Number n) {
        m.put("connections_available", n.doubleValue());
      }
    }
    if (ss.get("uptime") instanceof Number n) {
      m.put("uptime_seconds", n.doubleValue());
    }

    Document op = ss.get("opcounters", Document.class);
    if (op != null) {
      if (op.get("query") instanceof Number n) {
        m.put("opcounters_query", n.doubleValue());
      }
      if (op.get("insert") instanceof Number n) {
        m.put("opcounters_insert", n.doubleValue());
      }
      if (op.get("update") instanceof Number n) {
        m.put("opcounters_update", n.doubleValue());
      }
      if (op.get("delete") instanceof Number n) {
        m.put("opcounters_delete", n.doubleValue());
      }
    }

    Document dbs = admin.runCommand(new Document("listDatabases", 1));
    if (dbs != null && dbs.get("totalSize") instanceof Number n) {
      m.put("storage_bytes", n.doubleValue());
    }
    return new HealthSample(m);
  }
}
