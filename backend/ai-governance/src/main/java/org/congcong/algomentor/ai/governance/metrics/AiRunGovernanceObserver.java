package org.congcong.algomentor.ai.governance.metrics;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.congcong.algomentor.agent.core.AgentErrorCode;
import org.congcong.algomentor.agent.core.AgentException;
import org.congcong.algomentor.agent.core.AgentLoopContext;
import org.congcong.algomentor.agent.core.AgentLoopObserver;
import org.congcong.algomentor.agent.core.AgentRunResult;
import org.congcong.algomentor.ai.governance.admission.AiRunAdmission;
import org.congcong.algomentor.ai.governance.admission.AiRunLifecycleService;
import org.congcong.algomentor.ai.governance.model.AiGovernanceErrorCode;
import org.congcong.algomentor.ai.governance.model.AiGovernanceMetadataKeys;
import org.congcong.algomentor.ai.governance.model.AiUsage;
import org.congcong.algomentor.llm.core.stream.LlmStreamEvent;

public class AiRunGovernanceObserver implements AgentLoopObserver {

  private final AiRunLifecycleService lifecycleService;
  private final Map<String, RunBuffer> buffers = new ConcurrentHashMap<>();

  public AiRunGovernanceObserver(AiRunLifecycleService lifecycleService) {
    this.lifecycleService = lifecycleService;
  }

  @Override
  public void onRunStart(AgentLoopContext context) {
    AiRunAdmission admission = admission(context);
    if (admission == null) {
      return;
    }
    buffers.put(context.runId(), new RunBuffer());
    lifecycleService.markRunning(admission, null, null);
  }

  @Override
  public void onLlmEvent(AgentLoopContext context, int stepIndex, LlmStreamEvent event) {
    if (admission(context) == null) {
      return;
    }
    RunBuffer buffer = buffers.computeIfAbsent(context.runId(), ignored -> new RunBuffer());
    if (event instanceof LlmStreamEvent.MessageStart start) {
      buffer.provider = start.provider() == null ? null : start.provider().value();
      buffer.model = start.model() == null ? null : start.model().value();
    }
    if (event instanceof LlmStreamEvent.Usage usage) {
      buffer.usage = buffer.usage.plus(new AiUsage(
          usage.usage().inputTokens(),
          usage.usage().outputTokens(),
          usage.usage().cachedTokens(),
          usage.usage().reasoningTokens(),
          usage.usage().totalTokens()));
    }
  }

  @Override
  public void onRunEnd(AgentLoopContext context, AgentRunResult result) {
    AiRunAdmission admission = admission(context);
    if (admission == null) {
      return;
    }
    RunBuffer buffer = buffers.remove(context.runId());
    lifecycleService.markCompleted(
        admission,
        usage(buffer),
        provider(buffer),
        model(buffer));
  }

  @Override
  public void onError(AgentLoopContext context, AgentException error) {
    AiRunAdmission admission = admission(context);
    if (admission == null) {
      return;
    }
    RunBuffer buffer = buffers.remove(context.runId());
    if (error.code() == AgentErrorCode.CANCELLED) {
      lifecycleService.markCancelled(admission, usage(buffer), provider(buffer), model(buffer));
      return;
    }
    AiGovernanceErrorCode code = error.code() == AgentErrorCode.STRUCTURED_OUTPUT_INVALID
        ? AiGovernanceErrorCode.AI_STRUCTURED_OUTPUT_INVALID
        : AiGovernanceErrorCode.AI_UNKNOWN;
    lifecycleService.markFailed(admission, code, usage(buffer), provider(buffer), model(buffer));
  }

  private AiRunAdmission admission(AgentLoopContext context) {
    Object value = context.metadata().get(AiGovernanceMetadataKeys.ADMISSION);
    return value instanceof AiRunAdmission admission ? admission : null;
  }

  private static AiUsage usage(RunBuffer buffer) {
    return buffer == null ? AiUsage.zero() : buffer.usage;
  }

  private static String provider(RunBuffer buffer) {
    return buffer == null ? null : buffer.provider;
  }

  private static String model(RunBuffer buffer) {
    return buffer == null ? null : buffer.model;
  }

  private static final class RunBuffer {
    private AiUsage usage = AiUsage.zero();
    private String provider;
    private String model;
  }
}
