package org.congcong.algomentor.ai.governance.routing;

import org.congcong.algomentor.ai.governance.model.AiBusinessScenario;

/** 模型路由策略与受信 metadata 的稳定公共契约。 */
public final class AiModelRoutingContract {

  public static final String POLICY_TYPE_PREFIX = "ai.model-route.";
  public static final String POLICY_TYPE_SUFFIX = ".v1";

  private AiModelRoutingContract() {
  }

  public static String policyTypeCode(AiBusinessScenario scenario) {
    if (scenario == null) {
      throw new IllegalArgumentException("AI business scenario must not be null");
    }
    return POLICY_TYPE_PREFIX + scenario.code() + POLICY_TYPE_SUFFIX;
  }
}
