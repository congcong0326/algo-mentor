package org.congcong.algomentor.cache.registry;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.congcong.algomentor.cache.spec.CacheRegionName;

public final class CacheRegionRegistry {

  private final Map<CacheRegionName, CacheRegionDefinition> definitions = new ConcurrentHashMap<>();

  public void register(CacheRegionDefinition definition) {
    Objects.requireNonNull(definition, "definition must not be null");
    CacheRegionDefinition existing = definitions.putIfAbsent(definition.name(), definition);
    if (existing != null && !existing.equals(definition)) {
      throw new IllegalStateException(
          "Cache region '" + definition.name().value() + "' was already registered as " + existing);
    }
  }

  public Optional<CacheRegionDefinition> find(CacheRegionName name) {
    return Optional.ofNullable(definitions.get(Objects.requireNonNull(name, "name must not be null")));
  }
}
