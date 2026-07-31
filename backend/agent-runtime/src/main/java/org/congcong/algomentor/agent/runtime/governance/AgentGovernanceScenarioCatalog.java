package org.congcong.algomentor.agent.runtime.governance;

import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import org.congcong.algomentor.agent.core.runtime.definition.AgentKey;
import org.congcong.algomentor.ai.governance.model.AiBusinessScenario;
import org.congcong.algomentor.ai.governance.model.AiPurpose;
import org.congcong.algomentor.ai.governance.model.AiRunSource;

/** Agent stable key 到 AI 场景、审计来源和治理 purpose 的唯一代码目录。 */
public final class AgentGovernanceScenarioCatalog {

  private static final Map<AiBusinessScenario, AgentGovernanceScenario> SCENARIOS = createScenarios();

  public AgentGovernanceScenario resolve(AgentKey<?> agentKey) {
    return resolve(Objects.requireNonNull(agentKey, "Agent key must not be null").value());
  }

  public AgentGovernanceScenario resolve(String agentKeyValue) {
    AiBusinessScenario scenario = AiBusinessScenario.fromCode(agentKeyValue);
    AgentGovernanceScenario resolved = SCENARIOS.get(scenario);
    if (resolved == null) {
      throw new IllegalStateException("No agent governance scenario mapping for: " + scenario.code());
    }
    return resolved;
  }

  private static Map<AiBusinessScenario, AgentGovernanceScenario> createScenarios() {
    Map<AiBusinessScenario, AgentGovernanceScenario> scenarios = new EnumMap<>(AiBusinessScenario.class);
    register(scenarios, AiBusinessScenario.MENTOR_CONVERSATION, AiRunSource.LEARNING_CHAT, AiPurpose.LEARNING_CHAT);
    register(scenarios, AiBusinessScenario.TOPIC_EXPLANATION, AiRunSource.PROBLEM_DETAIL, AiPurpose.PROBLEM_EXPLANATION);
    register(scenarios, AiBusinessScenario.PRACTICE_CHAT, AiRunSource.PRACTICE_CHAT, AiPurpose.LEARNING_CHAT);
    register(scenarios, AiBusinessScenario.LEARNING_PLAN_DRAFT, AiRunSource.LEARNING_PLAN_DRAFT, AiPurpose.LEARNING_PLAN);
    register(scenarios, AiBusinessScenario.LEARNING_PLAN_REVISION, AiRunSource.LEARNING_PLAN_DRAFT_REVISION, AiPurpose.LEARNING_PLAN);
    register(scenarios, AiBusinessScenario.LEARNING_PLAN_EXTENSION, AiRunSource.LEARNING_PLAN_EXTENSION_PROPOSAL, AiPurpose.LEARNING_PLAN);
    register(scenarios, AiBusinessScenario.PRACTICE_CODE_REVIEW, AiRunSource.PRACTICE_CODE_REVIEW, AiPurpose.LEARNING_CHAT);
    register(scenarios, AiBusinessScenario.LEARNER_DECLARED_PROFILE_UPDATE,
        AiRunSource.LEARNER_MEMORY_DECLARED_UPDATE, AiPurpose.LEARNING_CHAT);
    register(scenarios, AiBusinessScenario.CODE_REVIEW_PROFILE_UPDATE,
        AiRunSource.LEARNER_PROFILE_CODE_REVIEW_BATCH, AiPurpose.LEARNING_CHAT);
    if (scenarios.size() != AiBusinessScenario.values().length) {
      throw new IllegalStateException("Every AI business scenario must have an agent governance mapping");
    }
    return Map.copyOf(scenarios);
  }

  private static void register(
      Map<AiBusinessScenario, AgentGovernanceScenario> scenarios,
      AiBusinessScenario scenario,
      AiRunSource source,
      AiPurpose purpose
  ) {
    scenarios.put(scenario, new AgentGovernanceScenario(scenario, source, purpose));
  }
}
