package org.congcong.algomentor.ai.governance.provider.runtime;

import java.time.Instant;
import org.congcong.algomentor.llm.core.provider.LlmProviderClient;

/** 某个 provider instance 当前配置版本对应的共享 SDK Client。 */
public record ProviderClientHandle(
    long providerInstanceId,
    Instant providerUpdatedAt,
    LlmProviderClient client
) {

  public ProviderClientHandle {
    if (providerInstanceId < 1 || providerUpdatedAt == null || client == null) {
      throw new IllegalArgumentException("provider client handle requires id, updatedAt, and client");
    }
  }
}
