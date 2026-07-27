package org.congcong.algomentor.llm.core.model;

import java.time.Instant;
import java.util.Set;
import org.congcong.algomentor.llm.core.provider.LlmCapability;
import org.congcong.algomentor.llm.core.provider.LlmProviderClient;
import org.congcong.algomentor.llm.core.provider.LlmProviderType;

/** 单次业务执行已解析且不可变的动态 provider 调用目标。 */
public record LlmInvocationTarget(
    LlmProviderType providerType,
    long providerInstanceId,
    long configuredModelId,
    LlmModelId upstreamModelId,
    Instant providerUpdatedAt,
    Set<LlmCapability> supportedCapabilities,
    LlmProviderClient client
) {

  public LlmInvocationTarget {
    if (providerType == null) {
      throw new IllegalArgumentException("providerType must not be null");
    }
    if (providerInstanceId < 1 || configuredModelId < 1) {
      throw new IllegalArgumentException("providerInstanceId and configuredModelId must be positive");
    }
    if (upstreamModelId == null || providerUpdatedAt == null || client == null) {
      throw new IllegalArgumentException("invocation target model, provider version, and client are required");
    }
    supportedCapabilities = supportedCapabilities == null ? Set.of() : Set.copyOf(supportedCapabilities);
  }
}
