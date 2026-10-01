package dev.vigil.access;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Invariant: Parses and structures declarative access control policies loaded from YAML.
 */
public record AccessPolicy(Map<String, TargetPolicy> targets) {
  public record TargetPolicy(List<String> ignorePrincipals, Map<String, List<ScopeGrant>> principals) {
    public Set<AccessEntry> expected() {
      Set<AccessEntry> out = new TreeSet<>();
      if (principals != null) {
        principals.forEach((p, grants) -> {
          if (grants != null) {
            grants.forEach(g -> {
              if (g.privileges() != null) {
                g.privileges().forEach(priv -> out.add(new AccessEntry(p, g.scope(), priv)));
              }
            });
          }
        });
      }
      return out;
    }

    public Set<String> ignored() {
      return ignorePrincipals == null ? Set.of() : Set.copyOf(ignorePrincipals);
    }
  }

  public record ScopeGrant(String scope, List<String> privileges) {}

  public static AccessPolicy load(Path file) throws IOException {
    ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
    return mapper.readValue(file.toFile(), AccessPolicy.class);
  }
}
