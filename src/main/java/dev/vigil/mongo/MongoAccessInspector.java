package dev.vigil.mongo;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoDatabase;
import dev.vigil.access.AccessEntry;
import dev.vigil.config.TargetRegistry;
import dev.vigil.model.EngineType;
import dev.vigil.model.ManagedTarget;
import dev.vigil.spi.AccessInspector;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import org.bson.Document;
import org.springframework.stereotype.Component;

/**
 * Invariant: Inspects MongoDB user definitions and roles across all databases using admin usersInfo command.
 */
@Component
public class MongoAccessInspector implements AccessInspector {
  private final TargetRegistry registry;

  public MongoAccessInspector(TargetRegistry registry) {
    this.registry = registry;
  }

  @Override
  public EngineType engine() {
    return EngineType.MONGODB;
  }

  @Override
  public Set<AccessEntry> snapshot(ManagedTarget t) throws Exception {
    Set<AccessEntry> out = new TreeSet<>();
    MongoClient client = registry.mongo(t.id());
    MongoDatabase admin = client.getDatabase("admin");
    Document cmd = new Document("usersInfo", new Document("forAllDBs", true)).append("showPrivileges", false);
    Document result = admin.runCommand(cmd);
    List<Document> users = result.getList("users", Document.class);
    if (users != null) {
      for (Document u : users) {
        String user = u.getString("user");
        String db = u.getString("db");
        String principal = user + "@" + db;
        List<Document> roles = u.getList("roles", Document.class);
        if (roles != null) {
          for (Document r : roles) {
            String roleDb = r.getString("db");
            String roleName = r.getString("role");
            out.add(new AccessEntry(principal, roleDb, roleName));
          }
        }
      }
    }
    return out;
  }
}
