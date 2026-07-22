package org.congcong.algomentor.cache.registry;

import java.util.Objects;

/** 共享缓存 namespace 与 schema version 组成的稳定集群契约。 */
public record SharedCacheIdentity(String namespace, int schemaVersion) {

  public SharedCacheIdentity {
    Objects.requireNonNull(namespace, "namespace must not be null");
    if (schemaVersion < 1) {
      throw new IllegalArgumentException("schemaVersion must be at least one");
    }
  }
}
