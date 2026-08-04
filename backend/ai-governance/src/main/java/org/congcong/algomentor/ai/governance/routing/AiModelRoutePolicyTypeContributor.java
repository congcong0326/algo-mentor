package org.congcong.algomentor.ai.governance.routing;

import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.congcong.algomentor.ai.governance.model.AiBusinessScenario;
import org.congcong.algomentor.ai.governance.provider.service.AiProviderManagementService;
import org.congcong.algomentor.policy.type.GenericPolicyType;
import org.congcong.algomentor.policy.type.GenericPolicyTypeContributor;
import org.congcong.algomentor.policy.type.GenericPolicyTypeExposure;

/** 为每个代码注册的 AI 场景贡献一个内部模型路由策略类型。 */
public final class AiModelRoutePolicyTypeContributor implements GenericPolicyTypeContributor {

  private final Map<AiBusinessScenario, GenericPolicyType<AiModelRoutePolicyContent>> types;

  public AiModelRoutePolicyTypeContributor(AiProviderManagementService providerManagementService) {
    Objects.requireNonNull(providerManagementService, "providerManagementService must not be null");
    Map<AiBusinessScenario, GenericPolicyType<AiModelRoutePolicyContent>> values =
        new EnumMap<>(AiBusinessScenario.class);
    for (AiBusinessScenario scenario : AiBusinessScenario.values()) {
      values.put(scenario, GenericPolicyType.of(
          AiModelRoutingContract.policyTypeCode(scenario),
          AiModelRoutePolicyContent.class,
          (json, content) -> providerManagementService.validateModelRoute(
              content.modelId(), content.reasoningEffort()),
          GenericPolicyTypeExposure.INTERNAL_ONLY));
    }
    this.types = Collections.unmodifiableMap(values);
  }

  @Override
  public Collection<GenericPolicyType<?>> policyTypes() {
    return List.copyOf(types.values());
  }

  public GenericPolicyType<AiModelRoutePolicyContent> require(AiBusinessScenario scenario) {
    GenericPolicyType<AiModelRoutePolicyContent> type = types.get(scenario);
    if (type == null) {
      throw new IllegalArgumentException("AI business scenario is not registered for model routing");
    }
    return type;
  }
}
