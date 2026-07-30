package org.congcong.algomentor.agent.runtime.governance;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import org.congcong.algomentor.agent.core.runtime.api.AgentInvocationContext;
import org.congcong.algomentor.agent.core.runtime.api.AgentInvocationMode;
import org.congcong.algomentor.agent.core.runtime.definition.AgentKey;
import org.congcong.algomentor.agent.core.runtime.model.AgentRuntimeMetadataKeys;
import org.congcong.algomentor.agent.core.runtime.model.PreparedAgentRun;
import org.congcong.algomentor.ai.governance.accounting.AiLlmCallKind;
import org.congcong.algomentor.ai.governance.execution.AiRunGovernanceLease;
import org.congcong.algomentor.ai.governance.execution.AiRunGovernanceMode;
import org.congcong.algomentor.ai.governance.execution.AiRunGovernanceRequest;
import org.congcong.algomentor.ai.governance.execution.AiRunGovernanceService;

/** 将 Runtime 的受信调用身份转换为不可被业务输入覆盖的治理租约。 */
public final class AgentRuntimeGovernanceService {

  private final AiRunGovernanceService governanceService;
  private final AgentGovernanceScenarioCatalog scenarioCatalog;

  public AgentRuntimeGovernanceService(AiRunGovernanceService governanceService) {
    this(governanceService, new AgentGovernanceScenarioCatalog());
  }

  public AgentRuntimeGovernanceService(
      AiRunGovernanceService governanceService,
      AgentGovernanceScenarioCatalog scenarioCatalog
  ) {
    this.governanceService = Objects.requireNonNull(governanceService, "AI governance service must not be null");
    this.scenarioCatalog = Objects.requireNonNull(scenarioCatalog, "Agent governance scenario catalog must not be null");
  }

  public AgentRuntimeGovernanceLease begin(
      AgentKey<?> agentKey,
      AgentInvocationContext invocation,
      PreparedAgentRun preparedRun
  ) {
    AgentKey<?> key = Objects.requireNonNull(agentKey, "Agent key must not be null");
    AgentInvocationContext context = Objects.requireNonNull(invocation, "Agent invocation context must not be null");
    PreparedAgentRun run = Objects.requireNonNull(preparedRun, "Prepared agent run must not be null");
    validateIdentity(key, context, run);
    AgentGovernanceScenario scenario = scenarioCatalog.resolve(key);
    AiRunGovernanceLease lease = governanceService.begin(new AiRunGovernanceRequest(
        mode(context.mode()),
        run.runUuid(),
        context.userId(),
        scenario.purpose(),
        scenario.runSource(),
        context.idempotencyKey(),
        context.requestSize(),
        context.streaming(),
        AiLlmCallKind.AGENT_STEP,
        "ALL",
        metadata(key, context, run)));
    return new AgentRuntimeGovernanceLease(scenario, lease);
  }

  private static void validateIdentity(
      AgentKey<?> key,
      AgentInvocationContext context,
      PreparedAgentRun run
  ) {
    if (!key.value().equals(run.agentKey())) {
      throw new IllegalArgumentException("Prepared agent run key does not match the invocation key");
    }
    if (run.mode() != context.mode()) {
      throw new IllegalArgumentException("Prepared agent run mode does not match the invocation mode");
    }
    boolean invocationHasParent = context.parentRunId() != null && context.parentStepIndex() != null;
    boolean preparedHasParent = run.parentRunId() != null && run.parentStepIndex() != null;
    switch (context.mode()) {
      case USER_ENTRY -> {
        if (invocationHasParent || preparedHasParent) {
          throw new IllegalArgumentException("User-entry agent runs must not have a parent run");
        }
      }
      case CHILD -> {
        if (!invocationHasParent || !preparedHasParent) {
          throw new IllegalArgumentException("Child agent runs require a parent run and step");
        }
      }
      case BACKGROUND -> {
        if (invocationHasParent || preparedHasParent) {
          throw new IllegalArgumentException("Background agent runs must not have a parent run");
        }
      }
    }
  }

  private static AiRunGovernanceMode mode(AgentInvocationMode mode) {
    return switch (mode) {
      case USER_ENTRY -> AiRunGovernanceMode.USER_ENTRY;
      case CHILD -> AiRunGovernanceMode.CHILD;
      case BACKGROUND -> AiRunGovernanceMode.BACKGROUND;
    };
  }

  private static Map<String, Object> metadata(
      AgentKey<?> key,
      AgentInvocationContext context,
      PreparedAgentRun run
  ) {
    Map<String, Object> metadata = new LinkedHashMap<>(run.metadata());
    metadata.put(AgentRuntimeMetadataKeys.TASK_ID, run.taskId());
    metadata.put(AgentRuntimeMetadataKeys.TURN_ID, run.turnId());
    metadata.put(AgentRuntimeMetadataKeys.RUN_DB_ID, run.runId());
    metadata.put(AgentRuntimeMetadataKeys.AGENT_RUN_ID, run.runUuid());
    metadata.put(AgentRuntimeMetadataKeys.AGENT_KEY, key.value());
    metadata.put(AgentRuntimeMetadataKeys.INVOCATION_MODE, context.mode().databaseValue());
    if (run.parentRunId() != null) {
      metadata.put(AgentRuntimeMetadataKeys.PARENT_RUN_ID, run.parentRunId());
      metadata.put(AgentRuntimeMetadataKeys.PARENT_STEP_INDEX, run.parentStepIndex());
    }
    if (run.retryOfRunId() != null) {
      metadata.put(AgentRuntimeMetadataKeys.RETRY_OF_RUN_ID, run.retryOfRunId());
    }
    return Map.copyOf(metadata);
  }
}
