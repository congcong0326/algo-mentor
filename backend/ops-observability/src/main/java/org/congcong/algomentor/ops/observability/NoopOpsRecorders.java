package org.congcong.algomentor.ops.observability;

public final class NoopOpsRecorders {

  private static final SseOpsRecorder SSE = new NoopSseOpsRecorder();
  private static final AgentOpsRecorder AGENT = new NoopAgentOpsRecorder();
  private static final LearningOpsRecorder LEARNING = new NoopLearningOpsRecorder();

  private NoopOpsRecorders() {
  }

  public static SseOpsRecorder sse() {
    return SSE;
  }

  public static AgentOpsRecorder agent() {
    return AGENT;
  }

  public static LearningOpsRecorder learning() {
    return LEARNING;
  }

  private static final class NoopSseOpsRecorder implements SseOpsRecorder {

    @Override
    public void opened(SseStreamType streamType) {
    }

    @Override
    public void completed(SseStreamType streamType) {
    }

    @Override
    public void failed(SseStreamType streamType, SseFailureType failureType) {
    }

    @Override
    public void timeout(SseStreamType streamType) {
    }

    @Override
    public void clientDisconnected(SseStreamType streamType) {
    }

  }

  private static final class NoopAgentOpsRecorder implements AgentOpsRecorder {

    @Override
    public void runStarted(AgentOpsSource source) {
    }

    @Override
    public void runCompleted(AgentOpsSource source) {
    }

    @Override
    public void runFailed(AgentOpsSource source) {
    }

    @Override
    public void toolPermissionDecision(String decision) {
    }

    @Override
    public void toolExecution(String toolName, OpsStatus status) {
    }

  }

  private static final class NoopLearningOpsRecorder implements LearningOpsRecorder {

    @Override
    public void learningPlanDraft(OpsStatus status) {
    }

    @Override
    public void practiceMessageStream(OpsStatus status) {
    }

    @Override
    public void practiceCodeReview(OpsStatus status) {
    }

  }

}
