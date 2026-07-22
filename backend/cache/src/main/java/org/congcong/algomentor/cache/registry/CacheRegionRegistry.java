package org.congcong.algomentor.cache.registry;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.congcong.algomentor.cache.spec.CacheRegionName;

public final class CacheRegionRegistry {

  private final Map<CacheRegionName, CacheRegionDefinition> definitions = new ConcurrentHashMap<>();
  private final Map<SharedCacheIdentity, CacheRegionName> sharedNames = new ConcurrentHashMap<>();

  public synchronized void register(CacheRegionDefinition definition) {
    Objects.requireNonNull(definition, "definition must not be null");
    CacheRegionDefinition existing = definitions.get(definition.name());
    if (existing != null && !existing.equals(definition)) {
      throw new IllegalStateException(
          "Cache region '" + definition.name().value() + "' was already registered as " + existing);
    }
    if (definition.sharedIdentity() != null) {
      CacheRegionName existingName = sharedNames.get(definition.sharedIdentity());
      if (existingName != null && !existingName.equals(definition.name())) {
        throw new IllegalStateException(
            "Shared cache namespace '" + definition.sharedIdentity().namespace()
                + "' schema version " + definition.sharedIdentity().schemaVersion()
                + " was already registered by cache '" + existingName.value() + "'");
      }
      sharedNames.putIfAbsent(definition.sharedIdentity(), definition.name());
    }
    definitions.putIfAbsent(definition.name(), definition);
  }

  public Optional<CacheRegionDefinition> find(CacheRegionName name) {
    return Optional.ofNullable(definitions.get(Objects.requireNonNull(name, "name must not be null")));
  }
}
