package org.congcong.algomentor.ai.governance.provider.runtime;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.congcong.algomentor.ai.governance.provider.model.AiProviderInstance;
import org.congcong.algomentor.llm.core.provider.LlmProviderAdapter;
import org.congcong.algomentor.llm.core.provider.LlmProviderInstanceSpec;
import org.congcong.algomentor.llm.core.provider.LlmProviderType;

/**
 * 仅缓存 SDK Client，不缓存 provider 配置记录、模型或模型路由。
 *
 * <p>同一 provider instance 的旧 handle 可以被进行中的执行继续持有；registry 只保留最新版本。</p>
 */
public class ProviderClientRegistry {

  public static final String CLIENT_CREATIONS_TOTAL = "ai_provider_client_creations_total";

  private final ConcurrentMap<Long, ProviderClientHandle> currentHandles = new ConcurrentHashMap<>();
  private final Object[] locks = new Object[64];
  private final MeterRegistry meterRegistry;

  public ProviderClientRegistry() {
    this(null);
  }

  public ProviderClientRegistry(MeterRegistry meterRegistry) {
    this.meterRegistry = meterRegistry;
    for (int index = 0; index < locks.length; index++) {
      locks[index] = new Object();
    }
  }

  public ProviderClientHandle acquire(AiProviderInstance instance, LlmProviderAdapter adapter) {
    Objects.requireNonNull(instance, "provider instance must not be null");
    Objects.requireNonNull(adapter, "provider adapter must not be null");
    if (instance.id() == null || instance.updatedAt() == null) {
      throw new IllegalArgumentException("persisted provider instance id and updatedAt are required");
    }
    LlmProviderType providerType = LlmProviderType.of(instance.providerType());
    if (!providerType.equals(adapter.providerType())) {
      throw new IllegalArgumentException("provider adapter type does not match provider instance type");
    }
    long instanceId = instance.id();
    ProviderClientHandle existing = currentHandles.get(instanceId);
    if (isCurrent(existing, instance)) {
      return existing;
    }
    synchronized (lockFor(instanceId)) {
      ProviderClientHandle refreshed = currentHandles.get(instanceId);
      if (isCurrent(refreshed, instance)) {
        return refreshed;
      }
      LlmProviderInstanceSpec spec = new LlmProviderInstanceSpec(
          instanceId, providerType, instance.config(), instance.updatedAt());
      try {
        adapter.validateConfig(spec.config());
        ProviderClientHandle created = new ProviderClientHandle(
            instanceId, instance.updatedAt(), adapter.createClient(spec));
        currentHandles.put(instanceId, created);
        recordCreation(providerType, "success");
        return created;
      } catch (RuntimeException exception) {
        recordCreation(providerType, "failure");
        throw exception;
      }
    }
  }

  private boolean isCurrent(ProviderClientHandle handle, AiProviderInstance instance) {
    return handle != null && handle.providerUpdatedAt().equals(instance.updatedAt());
  }

  private Object lockFor(long instanceId) {
    return locks[Math.floorMod(Long.hashCode(instanceId), locks.length)];
  }

  private void recordCreation(LlmProviderType providerType, String result) {
    if (meterRegistry != null) {
      Counter.builder(CLIENT_CREATIONS_TOTAL)
          .tag("provider_type", providerType.value())
          .tag("result", result)
          .register(meterRegistry)
          .increment();
    }
  }
}
