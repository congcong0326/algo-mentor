package org.congcong.algomentor.ai.governance.policy;

import static org.assertj.core.api.Assertions.assertThat;

import org.congcong.algomentor.ai.governance.model.AiPurpose;
import org.junit.jupiter.api.Test;

class AiPurposePolicyResolverTest {

  @Test
  void defaultPoliciesShareDailyLimitAndAllowExpectedCapabilities() {
    AiPurposePolicyResolver resolver = new AiPurposePolicyResolver(new AiGovernanceProperties());

    AiPurposePolicy learningPlan = resolver.resolve(AiPurpose.LEARNING_PLAN);
    AiPurposePolicy explanation = resolver.resolve(AiPurpose.PROBLEM_EXPLANATION);
    AiPurposePolicy chat = resolver.resolve(AiPurpose.LEARNING_CHAT);

    assertThat(learningPlan.dailyRequestLimit()).isEqualTo(50);
    assertThat(learningPlan.maxConcurrentRunsPerUser()).isEqualTo(1);
    assertThat(learningPlan.toolsAllowed()).isTrue();
    assertThat(learningPlan.structuredOutputRequired()).isTrue();
    assertThat(explanation.streamingAllowed()).isTrue();
    assertThat(chat.systemPolicyVersion()).isEqualTo("learning-chat-p0");
  }

  @Test
  void overridesPurposePolicyFromConfigurationProperties() {
    AiGovernanceProperties properties = new AiGovernanceProperties();
    properties.getPurposes().get(AiPurpose.LEARNING_CHAT).setEnabled(false);
    properties.getPurposes().get(AiPurpose.LEARNING_CHAT).setMaxRequestBytes(2048);

    AiPurposePolicy policy = new AiPurposePolicyResolver(properties).resolve(AiPurpose.LEARNING_CHAT);

    assertThat(policy.enabled()).isFalse();
    assertThat(policy.maxRequestBytes()).isEqualTo(2048);
  }
}
