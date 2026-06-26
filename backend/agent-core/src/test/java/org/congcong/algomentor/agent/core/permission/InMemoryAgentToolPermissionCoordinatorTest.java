package org.congcong.algomentor.agent.core.permission;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.congcong.algomentor.agent.core.AgentCancellationToken;
import org.congcong.algomentor.agent.core.AgentExecutionContext;
import org.congcong.algomentor.agent.core.AgentLoopContext;
import org.congcong.algomentor.agent.core.AgentRequest;
import org.congcong.algomentor.agent.core.AgentTool;
import org.congcong.algomentor.agent.core.runtime.model.AgentRuntimeMetadataKeys;
import org.congcong.algomentor.agent.core.runtime.model.AgentToolResultJsonKeys;
import org.congcong.algomentor.agent.core.runtime.model.AgentToolResultTypes;
import org.congcong.algomentor.llm.core.request.LlmMessage;
import org.congcong.algomentor.llm.core.tool.LlmToolCall;
import org.congcong.algomentor.llm.core.tool.LlmToolSpec;
import org.junit.jupiter.api.Test;

class InMemoryAgentToolPermissionCoordinatorTest {

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
  private static final Instant NOW = Instant.parse("2026-06-26T00:00:00Z");

  @Test
  void allowPlanReturnsAllowedAuthorization() {
    InMemoryAgentToolPermissionCoordinator coordinator = coordinator();

    AgentToolPermissionAuthorization authorization = coordinator.authorize(
        check(7L),
        AgentToolPermissionDecisionPlan.allow("default"),
        new AgentCancellationToken());

    assertThat(authorization).isInstanceOf(AgentToolPermissionAuthorization.Allowed.class);
    assertThat(coordinator.pendingRequestCount()).isZero();
  }

  @Test
  void denyPlanReturnsSyntheticDeniedResult() {
    RecordingMetrics metrics = new RecordingMetrics();
    InMemoryAgentToolPermissionCoordinator coordinator = coordinator(metrics);

    AgentToolPermissionAuthorization authorization = coordinator.authorize(
        check(7L),
        AgentToolPermissionDecisionPlan.deny("policy_denied", "tool-policy"),
        new AgentCancellationToken());

    AgentToolPermissionAuthorization.SyntheticResult synthetic =
        (AgentToolPermissionAuthorization.SyntheticResult) authorization;
    assertThat(synthetic.result().get(AgentToolResultJsonKeys.TYPE).asText())
        .isEqualTo(AgentToolResultTypes.TOOL_PERMISSION_DENIED);
    assertThat(synthetic.result().get(AgentToolResultJsonKeys.REASON).asText())
        .isEqualTo("policy_denied");
    assertThat(coordinator.pendingRequestCount()).isZero();
    assertThat(metrics.records)
        .contains("latency:submit_practice_code_review:deny:PT0S");
  }

  @Test
  void askPlanPublishesRequestAndAllowsAfterOwnerDecision() throws Exception {
    RecordingMetrics metrics = new RecordingMetrics();
    InMemoryAgentToolPermissionCoordinator coordinator = coordinator(metrics);
    RecordingEventPublisher events = new RecordingEventPublisher();
    CompletableFuture<AgentToolPermissionAuthorization> authorization = CompletableFuture.supplyAsync(() ->
        coordinator.authorize(check(7L), askPlan(), new AgentCancellationToken(), events));

    awaitRequest(events);
    AgentToolPermissionDecisionResult result = coordinator.decide(
        "perm-1",
        AgentToolPermissionDecisionType.ALLOW,
        "user_confirmed",
        7L);

    AgentToolPermissionAuthorization resolved = authorization.get(1, TimeUnit.SECONDS);
    assertThat(resolved).isInstanceOf(AgentToolPermissionAuthorization.Allowed.class);
    assertThat(result.accepted()).isTrue();
    assertThat(result.request().permissionRequestId()).isEqualTo("perm-1");
    assertThat(events.records).containsExactly("request:perm-1", "decision:ALLOW:user_confirmed");
    assertThat(coordinator.pendingRequestCount()).isZero();
    assertThat(metrics.records)
        .contains(
            "request:submit_practice_code_review:test-policy",
            "decision:submit_practice_code_review:ALLOW",
            "latency:submit_practice_code_review:allow:PT0S",
            "high:submit_practice_code_review:test-policy");
  }

  @Test
  void askPlanReturnsDeniedSyntheticResultAfterOwnerRejects() throws Exception {
    RecordingMetrics metrics = new RecordingMetrics();
    InMemoryAgentToolPermissionCoordinator coordinator = coordinator(metrics);
    RecordingEventPublisher events = new RecordingEventPublisher();
    CompletableFuture<AgentToolPermissionAuthorization> authorization = CompletableFuture.supplyAsync(() ->
        coordinator.authorize(check(7L), askPlan(), new AgentCancellationToken(), events));

    awaitRequest(events);
    coordinator.decide("perm-1", AgentToolPermissionDecisionType.DENY, "user_rejected", 7L);

    AgentToolPermissionAuthorization.SyntheticResult synthetic =
        (AgentToolPermissionAuthorization.SyntheticResult) authorization.get(1, TimeUnit.SECONDS);
    assertThat(synthetic.result().get(AgentToolResultJsonKeys.TYPE).asText())
        .isEqualTo(AgentToolResultTypes.TOOL_PERMISSION_DENIED);
    assertThat(synthetic.result().get(AgentToolResultJsonKeys.PERMISSION_REQUEST_ID).asText()).isEqualTo("perm-1");
    assertThat(synthetic.result().get(AgentToolResultJsonKeys.REASON).asText()).isEqualTo("user_rejected");
    assertThat(events.records).containsExactly("request:perm-1", "decision:DENY:user_rejected");
    assertThat(coordinator.pendingRequestCount()).isZero();
    assertThat(metrics.records)
        .contains(
            "request:submit_practice_code_review:test-policy",
            "decision:submit_practice_code_review:DENY",
            "latency:submit_practice_code_review:deny:PT0S")
        .doesNotContain("high:submit_practice_code_review:test-policy");
  }

  @Test
  void nonOwnerDecisionFailsAndOwnerCanStillDecide() throws Exception {
    InMemoryAgentToolPermissionCoordinator coordinator = coordinator();
    RecordingEventPublisher events = new RecordingEventPublisher();
    CompletableFuture<AgentToolPermissionAuthorization> authorization = CompletableFuture.supplyAsync(() ->
        coordinator.authorize(check(7L), askPlan(), new AgentCancellationToken(), events));

    awaitRequest(events);
    assertThatThrownBy(() -> coordinator.decide(
        "perm-1",
        AgentToolPermissionDecisionType.ALLOW,
        "user_confirmed",
        8L))
        .isInstanceOf(AgentToolPermissionException.class)
        .extracting(error -> ((AgentToolPermissionException) error).code())
        .isEqualTo(AgentToolPermissionException.Code.FORBIDDEN);

    coordinator.decide("perm-1", AgentToolPermissionDecisionType.ALLOW, "user_confirmed", 7L);
    assertThat(authorization.get(1, TimeUnit.SECONDS))
        .isInstanceOf(AgentToolPermissionAuthorization.Allowed.class);
  }

  @Test
  void duplicateDecisionFailsWithConflict() throws Exception {
    InMemoryAgentToolPermissionCoordinator coordinator = coordinator();
    RecordingEventPublisher events = new RecordingEventPublisher();
    CompletableFuture<AgentToolPermissionAuthorization> authorization = CompletableFuture.supplyAsync(() ->
        coordinator.authorize(check(7L), askPlan(), new AgentCancellationToken(), events));

    awaitRequest(events);
    coordinator.decide("perm-1", AgentToolPermissionDecisionType.ALLOW, "user_confirmed", 7L);
    authorization.get(1, TimeUnit.SECONDS);

    assertThatThrownBy(() -> coordinator.decide(
        "perm-1",
        AgentToolPermissionDecisionType.DENY,
        "user_rejected",
        7L))
        .isInstanceOf(AgentToolPermissionException.class)
        .extracting(error -> ((AgentToolPermissionException) error).code())
        .isEqualTo(AgentToolPermissionException.Code.ALREADY_DECIDED);
  }

  @Test
  void unknownRequestFailsWithNotFound() {
    InMemoryAgentToolPermissionCoordinator coordinator = coordinator();

    assertThatThrownBy(() -> coordinator.decide(
        "missing",
        AgentToolPermissionDecisionType.ALLOW,
        "user_confirmed",
        7L))
        .isInstanceOf(AgentToolPermissionException.class)
        .extracting(error -> ((AgentToolPermissionException) error).code())
        .isEqualTo(AgentToolPermissionException.Code.NOT_FOUND);
  }

  @Test
  void timeoutReturnsRetryableSyntheticResultAndCleansPendingRequest() throws Exception {
    MutableClock clock = new MutableClock(NOW);
    RecordingMetrics metrics = new RecordingMetrics();
    InMemoryAgentToolPermissionCoordinator coordinator =
        coordinator(clock, Duration.ofMillis(10), () -> "perm-timeout", metrics);
    RecordingEventPublisher events = new RecordingEventPublisher();

    CompletableFuture<AgentToolPermissionAuthorization> authorization = CompletableFuture.supplyAsync(() ->
        coordinator.authorize(check(7L), askPlan(), new AgentCancellationToken(), events));

    awaitRequest(events);
    clock.advance(Duration.ofMillis(20));

    AgentToolPermissionAuthorization.SyntheticResult synthetic =
        (AgentToolPermissionAuthorization.SyntheticResult) authorization.get(1, TimeUnit.SECONDS);
    assertThat(synthetic.result().get(AgentToolResultJsonKeys.TYPE).asText())
        .isEqualTo(AgentToolResultTypes.TOOL_PERMISSION_TIMEOUT);
    assertThat(synthetic.result().get(AgentToolResultJsonKeys.RETRYABLE).asBoolean()).isTrue();
    assertThat(events.records).containsExactly("request:perm-timeout", "timeout:timeout");
    assertThat(coordinator.pendingRequestCount()).isZero();
    assertThat(metrics.records)
        .contains(
            "request:submit_practice_code_review:test-policy",
            "timeout:submit_practice_code_review",
            "latency:submit_practice_code_review:timeout:PT0.02S");

    assertThatThrownBy(() -> coordinator.decide(
        "perm-timeout",
        AgentToolPermissionDecisionType.ALLOW,
        "user_confirmed",
        7L))
        .isInstanceOf(AgentToolPermissionException.class)
        .extracting(error -> ((AgentToolPermissionException) error).code())
        .isEqualTo(AgentToolPermissionException.Code.EXPIRED);
    assertThat(events.records).doesNotContain("decision:DENY:timeout");
  }

  @Test
  void cancellationReturnsSyntheticResultAndDoesNotLeavePendingRequest() throws Exception {
    RecordingMetrics metrics = new RecordingMetrics();
    InMemoryAgentToolPermissionCoordinator coordinator = coordinator(() -> "perm-cancel", metrics);
    RecordingEventPublisher events = new RecordingEventPublisher();
    AgentCancellationToken cancellationToken = new AgentCancellationToken();

    CompletableFuture<AgentToolPermissionAuthorization> authorization = CompletableFuture.supplyAsync(() ->
        coordinator.authorize(check(7L), askPlan(), cancellationToken, events));

    awaitRequest(events);
    cancellationToken.cancel();

    AgentToolPermissionAuthorization.SyntheticResult synthetic =
        (AgentToolPermissionAuthorization.SyntheticResult) authorization.get(1, TimeUnit.SECONDS);
    assertThat(synthetic.result().get(AgentToolResultJsonKeys.REASON).asText())
        .isEqualTo(AgentToolPermissionResultFactory.REASON_RUN_CANCELLED);
    assertThat(coordinator.pendingRequestCount()).isZero();
    assertThat(metrics.records)
        .contains("latency:submit_practice_code_review:cancelled:PT0S");
  }

  @Test
  void askWithoutTrustedUserIdFailsClosedWithoutPendingRequest() {
    InMemoryAgentToolPermissionCoordinator coordinator = coordinator();

    AgentToolPermissionAuthorization.SyntheticResult synthetic =
        (AgentToolPermissionAuthorization.SyntheticResult) coordinator.authorize(
            checkWithoutUserId(),
            askPlan(),
            new AgentCancellationToken());

    assertThat(synthetic.result().get(AgentToolResultJsonKeys.TYPE).asText())
        .isEqualTo(AgentToolResultTypes.TOOL_PERMISSION_DENIED);
    assertThat(synthetic.result().get(AgentToolResultJsonKeys.REASON).asText())
        .isEqualTo("missing_trusted_user_id");
    assertThat(coordinator.pendingRequestCount()).isZero();
  }

  private static InMemoryAgentToolPermissionCoordinator coordinator() {
    return coordinator(() -> "perm-1");
  }

  private static InMemoryAgentToolPermissionCoordinator coordinator(AgentToolPermissionMetrics metrics) {
    return coordinator(() -> "perm-1", metrics);
  }

  private static InMemoryAgentToolPermissionCoordinator coordinator(java.util.function.Supplier<String> requestId) {
    return coordinator(requestId, NoopAgentToolPermissionMetrics.INSTANCE);
  }

  private static InMemoryAgentToolPermissionCoordinator coordinator(
      java.util.function.Supplier<String> requestId,
      AgentToolPermissionMetrics metrics
  ) {
    return coordinator(Clock.fixed(NOW, ZoneOffset.UTC), Duration.ofSeconds(5), requestId, metrics);
  }

  private static InMemoryAgentToolPermissionCoordinator coordinator(
      Clock clock,
      Duration timeout,
      java.util.function.Supplier<String> requestId
  ) {
    return coordinator(clock, timeout, requestId, NoopAgentToolPermissionMetrics.INSTANCE);
  }

  private static InMemoryAgentToolPermissionCoordinator coordinator(
      Clock clock,
      Duration timeout,
      java.util.function.Supplier<String> requestId,
      AgentToolPermissionMetrics metrics
  ) {
    return new InMemoryAgentToolPermissionCoordinator(
        new AgentToolPermissionResultFactory(OBJECT_MAPPER),
        timeout,
        clock,
        requestId,
        metrics);
  }

  private static AgentToolPermissionDecisionPlan askPlan() {
    return AgentToolPermissionDecisionPlan.ask(
        "提交代码 Review",
        "模型请求执行正式 Review",
        Map.of("effect", "save_review"),
        "test-policy");
  }

  private static AgentToolPermissionCheck check(long userId) {
    AgentRequest request = new AgentRequest(
        "run-1",
        "request-1",
        List.of(LlmMessage.user("review my code")),
        Map.of(AgentRuntimeMetadataKeys.USER_ID, userId));
    AgentLoopContext context = new AgentLoopContext("run-1", request, 4, request.metadata());
    LlmToolCall toolCall = new LlmToolCall(
        "call-1",
        "submit_practice_code_review",
        OBJECT_MAPPER.createObjectNode().put("intent", "review"));
    return new AgentToolPermissionCheck(context, 1, toolCall, tool(), request.metadata());
  }

  private static AgentToolPermissionCheck checkWithoutUserId() {
    AgentRequest request = new AgentRequest(
        "run-1",
        "request-1",
        List.of(LlmMessage.user("review my code")),
        Map.of());
    AgentLoopContext context = new AgentLoopContext("run-1", request, 4, request.metadata());
    LlmToolCall toolCall = new LlmToolCall(
        "call-1",
        "submit_practice_code_review",
        OBJECT_MAPPER.createObjectNode().put("intent", "review"));
    return new AgentToolPermissionCheck(context, 1, toolCall, tool(), request.metadata());
  }

  private static AgentTool tool() {
    return new AgentTool() {
      @Override
      public LlmToolSpec spec() {
        return new LlmToolSpec(
            "submit_practice_code_review",
            "Submit code review",
            OBJECT_MAPPER.createObjectNode().put("type", "object"),
            true);
      }

      @Override
      public JsonNode execute(JsonNode arguments, AgentExecutionContext context) {
        return OBJECT_MAPPER.createObjectNode().put("ok", true);
      }
    };
  }

  private static void awaitRequest(RecordingEventPublisher events) throws Exception {
    long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(1);
    while (System.nanoTime() < deadline) {
      if (events.request.get() != null) {
        return;
      }
      Thread.sleep(5);
    }
    throw new AssertionError("permission request was not published");
  }

  private static final class RecordingEventPublisher implements AgentToolPermissionCoordinator.EventPublisher {

    private final AtomicReference<AgentToolPermissionRequest> request = new AtomicReference<>();
    private final List<String> records = java.util.Collections.synchronizedList(new ArrayList<>());

    @Override
    public void toolPermissionRequested(
        AgentToolPermissionRequest request,
        AgentToolPermissionDecisionPlan plan
    ) {
      this.request.set(request);
      records.add("request:" + request.permissionRequestId());
    }

    @Override
    public void toolPermissionDecided(
        AgentToolPermissionRequest request,
        AgentToolPermissionDecision decision,
        AgentToolPermissionDecisionPlan plan
    ) {
      records.add("decision:" + decision.decision() + ":" + decision.reason());
    }

    @Override
    public void toolPermissionTimedOut(
        AgentToolPermissionRequest request,
        String reason,
        Instant expiredAt,
        AgentToolPermissionDecisionPlan plan
    ) {
      records.add("timeout:" + reason);
    }
  }

  private static final class RecordingMetrics implements AgentToolPermissionMetrics {

    private final List<String> records = java.util.Collections.synchronizedList(new ArrayList<>());

    @Override
    public void recordHookDecision(
        String toolName,
        AgentToolPermissionBehavior behavior,
        String policySource
    ) {
    }

    @Override
    public void recordPermissionRequest(
        String toolName,
        String policySource
    ) {
      records.add("request:" + toolName + ":" + policySource);
    }

    @Override
    public void recordUserDecision(
        String toolName,
        AgentToolPermissionDecisionType decision
    ) {
      records.add("decision:" + toolName + ":" + decision);
    }

    @Override
    public void recordTimeout(String toolName) {
      records.add("timeout:" + toolName);
    }

    @Override
    public void recordLatency(
        String toolName,
        String outcome,
        Duration latency
    ) {
      records.add("latency:" + toolName + ":" + outcome + ":" + latency);
    }

    @Override
    public void recordHighPermissionExecution(
        String toolName,
        String policySource
    ) {
      records.add("high:" + toolName + ":" + policySource);
    }
  }

  private static final class MutableClock extends Clock {

    private Instant instant;

    private MutableClock(Instant instant) {
      this.instant = instant;
    }

    void advance(Duration duration) {
      instant = instant.plus(duration);
    }

    @Override
    public ZoneOffset getZone() {
      return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(java.time.ZoneId zone) {
      return this;
    }

    @Override
    public Instant instant() {
      return instant;
    }
  }
}
