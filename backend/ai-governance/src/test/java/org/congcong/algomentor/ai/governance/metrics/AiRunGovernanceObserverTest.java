package org.congcong.algomentor.ai.governance.metrics;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.congcong.algomentor.agent.core.AgentErrorCode;
import org.congcong.algomentor.agent.core.AgentException;
import org.congcong.algomentor.agent.core.AgentExecutionOptions;
import org.congcong.algomentor.agent.core.AgentLoopContext;
import org.congcong.algomentor.agent.core.AgentRequest;
import org.congcong.algomentor.agent.core.AgentRunResult;
import org.congcong.algomentor.agent.core.runlock.AgentRunLockToken;
import org.congcong.algomentor.ai.governance.admission.AiRunAdmission;
import org.congcong.algomentor.ai.governance.admission.AiRunLifecycleService;
import org.congcong.algomentor.ai.governance.model.AiGovernanceErrorCode;
import org.congcong.algomentor.ai.governance.model.AiGovernanceMetadataKeys;
import org.congcong.algomentor.ai.governance.model.AiPurpose;
import org.congcong.algomentor.ai.governance.model.AiRunSource;
import org.congcong.algomentor.ai.governance.model.AiRunStatus;
import org.congcong.algomentor.ai.governance.model.AiUsage;
import org.congcong.algomentor.ai.governance.policy.AiPurposePolicy;
import org.congcong.algomentor.llm.core.model.LlmModelId;
import org.congcong.algomentor.llm.core.provider.LlmProviderId;
import org.congcong.algomentor.llm.core.request.LlmMessage;
import org.congcong.algomentor.llm.core.response.LlmFinishReason;
import org.congcong.algomentor.llm.core.response.LlmUsage;
import org.congcong.algomentor.llm.core.stream.LlmStreamEvent;
import org.junit.jupiter.api.Test;

class AiRunGovernanceObserverTest {

  @Test
  void updatesRunningCompletedUsageAndReleasesLockFromAgentLifecycle() {
    RecordingLifecycleService lifecycle = new RecordingLifecycleService();
    AiRunGovernanceObserver observer = new AiRunGovernanceObserver(lifecycle);
    AiRunAdmission admission = admittedRun();
    AgentLoopContext context = contextWithAdmission(admission);

    observer.onRunStart(context);
    observer.onLlmEvent(context, 1, new LlmStreamEvent.MessageStart(
        LlmProviderId.of("openai"),
        LlmModelId.of("gpt-4.1-mini")));
    observer.onLlmEvent(context, 1, new LlmStreamEvent.Usage(new LlmUsage(10, 20, 0, 0, 30)));
    observer.onRunEnd(context, new AgentRunResult(1, LlmFinishReason.STOP, Map.of()));

    assertThat(lifecycle.events).containsExactly("running", "completed");
    assertThat(lifecycle.lastUsage.totalTokens()).isEqualTo(30);
    assertThat(lifecycle.released).isTrue();
  }

  @Test
  void mapsAgentCancellationToCancelledStatus() {
    RecordingLifecycleService lifecycle = new RecordingLifecycleService();
    AiRunGovernanceObserver observer = new AiRunGovernanceObserver(lifecycle);
    AgentLoopContext context = contextWithAdmission(admittedRun());

    observer.onError(context, new AgentException(AgentErrorCode.CANCELLED, "cancelled"));

    assertThat(lifecycle.events).containsExactly("cancelled");
    assertThat(lifecycle.released).isTrue();
  }

  static AiRunAdmission admittedRun() {
    AiPurposePolicy policy = new AiPurposePolicy(
        true, 50, 1, 32768, 2048, 8, true, true, false, false,
        null, null, "test-policy");
    return new AiRunAdmission(
        1L,
        "run-1",
        7L,
        AiPurpose.LEARNING_CHAT,
        AiRunSource.LEARNING_CHAT,
        AiRunStatus.ADMITTED,
        "ALL",
        new AgentRunLockToken("user:7:ai:all", "node-1", "token-1", null),
        policy,
        Map.of(AiGovernanceMetadataKeys.RUN_ID, "run-1"),
        Instant.now());
  }

  static AgentLoopContext contextWithAdmission(AiRunAdmission admission) {
    return new AgentLoopContext(
        "run-1",
        new AgentRequest(
            "run-1",
            "request-1",
            List.of(LlmMessage.user("hello")),
            Map.of(),
            AgentExecutionOptions.defaults()),
        8,
        Map.of(AiGovernanceMetadataKeys.ADMISSION, admission));
  }

  static class RecordingLifecycleService extends AiRunLifecycleService {

    final List<String> events = new ArrayList<>();
    AiUsage lastUsage = AiUsage.zero();
    boolean released;

    RecordingLifecycleService() {
      super(null, null, null, null);
    }

    @Override
    public void markRunning(AiRunAdmission admission, String provider, String model) {
      events.add("running");
    }

    @Override
    public void markCompleted(AiRunAdmission admission, AiUsage usage, String provider, String model) {
      events.add("completed");
      lastUsage = usage;
      released = true;
    }

    @Override
    public void markFailed(
        AiRunAdmission admission,
        AiGovernanceErrorCode errorCode,
        AiUsage usage,
        String provider,
        String model) {
      events.add("failed");
      lastUsage = usage;
      released = true;
    }

    @Override
    public void markCancelled(AiRunAdmission admission, AiUsage usage, String provider, String model) {
      events.add("cancelled");
      lastUsage = usage;
      released = true;
    }
  }
}
