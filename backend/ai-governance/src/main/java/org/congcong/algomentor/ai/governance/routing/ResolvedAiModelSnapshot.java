package org.congcong.algomentor.ai.governance.routing;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.congcong.algomentor.ai.governance.model.AiBusinessScenario;
import org.congcong.algomentor.ai.governance.model.AiGovernanceMetadataKeys;
import org.congcong.algomentor.llm.core.model.LlmInvocationTarget;
import org.congcong.algomentor.llm.core.model.LlmModelId;
import org.congcong.algomentor.llm.core.provider.LlmProviderClient;
import org.congcong.algomentor.llm.core.provider.LlmProviderType;
import org.congcong.algomentor.policy.model.PolicyMatchSource;

/** 一次业务执行固定持有的模型、provider 版本与 SDK Client 快照。 */
public record ResolvedAiModelSnapshot(
    AiBusinessScenario scenario,
    long routePolicyId,
    long routePolicyVersion,
    PolicyMatchSource matchSource,
    Long matchedSubjectId,
    long aiModelId,
    String upstreamModelId,
    long providerInstanceId,
    String providerType,
    Instant providerUpdatedAt,
    LlmProviderClient client,
    java.util.Set<org.congcong.algomentor.llm.core.provider.LlmCapability> supportedCapabilities
) {

  public ResolvedAiModelSnapshot {
    if (scenario == null || routePolicyId < 1 || routePolicyVersion < 1 || matchSource == null
        || aiModelId < 1 || upstreamModelId == null || upstreamModelId.isBlank()
        || providerInstanceId < 1 || providerType == null || providerType.isBlank()
        || providerUpdatedAt == null || client == null) {
      throw new IllegalArgumentException("Resolved AI model snapshot is incomplete");
    }
    supportedCapabilities = supportedCapabilities == null ? java.util.Set.of() : java.util.Set.copyOf(supportedCapabilities);
  }

  public LlmInvocationTarget invocationTarget() {
    return new LlmInvocationTarget(
        LlmProviderType.of(providerType),
        providerInstanceId,
        aiModelId,
        LlmModelId.of(upstreamModelId),
        providerUpdatedAt,
        supportedCapabilities,
        client);
  }

  /** 仅返回允许进入 trace、SSE 与调用台账的低敏 metadata。 */
  public Map<String, Object> trustedMetadata() {
    Map<String, Object> metadata = new LinkedHashMap<>();
    metadata.put(AiGovernanceMetadataKeys.SCENARIO_CODE, scenario.code());
    metadata.put(AiGovernanceMetadataKeys.MODEL_ROUTE_POLICY_ID, routePolicyId);
    metadata.put(AiGovernanceMetadataKeys.MODEL_ROUTE_POLICY_VERSION, routePolicyVersion);
    metadata.put(AiGovernanceMetadataKeys.MODEL_ROUTE_MATCH_SOURCE, matchSource.name());
    if (matchedSubjectId != null) {
      metadata.put(AiGovernanceMetadataKeys.MODEL_ROUTE_MATCHED_SUBJECT_ID, matchedSubjectId);
    }
    metadata.put(AiGovernanceMetadataKeys.CONFIGURED_MODEL_ID, aiModelId);
    metadata.put(AiGovernanceMetadataKeys.PROVIDER_INSTANCE_ID, providerInstanceId);
    metadata.put(AiGovernanceMetadataKeys.PROVIDER_TYPE, providerType);
    metadata.put(AiGovernanceMetadataKeys.UPSTREAM_MODEL_ID, upstreamModelId);
    metadata.put(AiGovernanceMetadataKeys.PROVIDER_CONFIG_REVISION, providerUpdatedAt.toString());
    return Map.copyOf(metadata);
  }
}
