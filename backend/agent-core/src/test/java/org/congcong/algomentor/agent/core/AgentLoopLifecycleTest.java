package org.congcong.algomentor.agent.core;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.SubmissionPublisher;
import org.congcong.algomentor.agent.core.permission.AgentToolPermissionAuthorization;
import org.congcong.algomentor.agent.core.permission.AgentToolPermissionBehavior;
import org.congcong.algomentor.agent.core.permission.AgentToolPermissionCoordinator;
import org.congcong.algomentor.agent.core.permission.AgentToolPermissionDecision;
import org.congcong.algomentor.agent.core.permission.AgentToolPermissionDecisionPlan;
import org.congcong.algomentor.agent.core.permission.AgentToolPermissionDecisionType;
import org.congcong.algomentor.agent.core.permission.AgentToolPermissionGuard;
import org.congcong.algomentor.agent.core.permission.AgentToolPermissionHook;
import org.congcong.algomentor.agent.core.permission.AgentToolPermissionHookChain;
import org.congcong.algomentor.agent.core.permission.AgentToolPermissionCheck;
import org.congcong.algomentor.agent.core.permission.AgentToolPermissionRequest;
import org.congcong.algomentor.agent.core.runtime.model.AgentRuntimeMetadataKeys;
import org.congcong.algomentor.llm.core.request.LlmMessage;
import org.congcong.algomentor.llm.core.tool.LlmToolCall;
import org.congcong.algomentor.llm.core.tool.LlmToolSpec;
import org.junit.jupiter.api.Test;

class AgentLoopLifecycleTest {

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  @Test
  void lifecyclePublishesPermissionEventsAndNotifiesObservers() {
    SubmissionPublisher<AgentStreamEvent> publisher = new SubmissionPublisher<>();
    CollectingSubscriber subscriber = new CollectingSubscriber();
    publisher.subscribe(subscriber);
    List<String> observed = new ArrayList<>();
    AgentLoopObserver observer = new AgentLoopObserver() {
      @Override
      public void onToolPermissionRequest(
          AgentLoopContext context,
          AgentToolPermissionRequest request,
          AgentToolPermissionDecisionPlan plan
      ) {
        observed.add("request:" + request.permissionRequestId() + ":" + plan.policySource());
      }

      @Override
      public void onToolPermissionDecision(
          AgentLoopContext context,
          AgentToolPermissionRequest request,
          AgentToolPermissionDecision decision,
          AgentToolPermissionDecisionPlan plan
      ) {
        observed.add("decision:" + decision.decision());
      }

      @Override
      public void onToolPermissionTimeout(
          AgentLoopContext context,
          AgentToolPermissionRequest request,
          String reason,
          Instant expiredAt,
          AgentToolPermissionDecisionPlan plan
      ) {
        observed.add("timeout:" + reason);
      }
    };
    AgentLoopLifecycle lifecycle = new AgentLoopLifecycle(publisher, List.of(observer), List.of());
    AgentLoopContext context = context();
    AgentToolPermissionRequest request = request();
    AgentToolPermissionDecisionPlan plan = askPlan();
    AgentToolPermissionDecision decision = new AgentToolPermissionDecision(
        "perm-1",
        AgentToolPermissionDecisionType.ALLOW,
        "user_confirmed",
        7L,
        Instant.parse("2026-06-26T00:00:10Z"));

    lifecycle.toolPermissionRequested(context, request, plan);
    lifecycle.toolPermissionDecided(context, request, decision, plan);
    lifecycle.toolPermissionTimedOut(context, request, "timeout", request.expiresAt(), plan);
    subscriber.awaitEventCount(3);
    publisher.close();

    assertThat(subscriber.events())
        .extracting(AgentStreamEvent::name)
        .containsExactly(
            AgentStreamEventNames.TOOL_PERMISSION_REQUEST,
            AgentStreamEventNames.TOOL_PERMISSION_DECISION,
            AgentStreamEventNames.TOOL_PERMISSION_TIMEOUT);
    assertThat(observed).containsExactly(
        "request:perm-1:test-policy",
        "decision:ALLOW",
        "timeout:timeout");
  }

  @Test
  void beforeToolExecutionCallsPermissionGuardThroughLifecycle() {
    AgentToolPermissionCoordinator coordinator = new AgentToolPermissionCoordinator() {
      @Override
      public AgentToolPermissionAuthorization authorize(
          org.congcong.algomentor.agent.core.permission.AgentToolPermissionCheck check,
          AgentToolPermissionDecisionPlan plan,
          AgentCancellationToken cancellationToken,
          EventPublisher eventPublisher
      ) {
        eventPublisher.toolPermissionRequested(request(), plan);
        return new AgentToolPermissionAuthorization.Allowed(plan);
      }

      @Override
      public org.congcong.algomentor.agent.core.permission.AgentToolPermissionDecisionResult decide(
          String permissionRequestId,
          AgentToolPermissionDecisionType decision,
          String reason,
          long userId
      ) {
        throw new UnsupportedOperationException();
      }
    };
    SubmissionPublisher<AgentStreamEvent> publisher = new SubmissionPublisher<>();
    CollectingSubscriber subscriber = new CollectingSubscriber();
    publisher.subscribe(subscriber);
    AgentLoopLifecycle lifecycle = new AgentLoopLifecycle(
        publisher,
        List.of(),
        List.of(),
        new AgentToolPermissionGuard(
            new AgentToolPermissionHookChain(List.of(new AgentToolPermissionHook() {
              @Override
              public int order() {
                return 10;
              }

              @Override
              public AgentToolPermissionDecisionPlan evaluate(AgentToolPermissionCheck check) {
                return AgentToolPermissionDecisionPlan.ask(
                    "提交代码 Review",
                    "模型请求执行正式 Review",
                    Map.of("effect", "save_review"),
                    "test-policy");
              }
            })),
            coordinator));

    AgentToolPermissionAuthorization authorization = lifecycle.beforeToolExecution(
        context(),
        1,
        new LlmToolCall("call-1", "submit_practice_code_review", OBJECT_MAPPER.createObjectNode()),
        tool());
    subscriber.awaitEventCount(1);
    publisher.close();

    assertThat(authorization).isInstanceOf(AgentToolPermissionAuthorization.Allowed.class);
    assertThat(subscriber.events())
        .extracting(AgentStreamEvent::name)
        .containsExactly(AgentStreamEventNames.TOOL_PERMISSION_REQUEST);
  }

  private static AgentLoopContext context() {
    AgentRequest request = new AgentRequest(
        "run-1",
        "request-1",
        List.of(LlmMessage.user("review my code")),
        Map.of(AgentRuntimeMetadataKeys.USER_ID, 7L));
    return new AgentLoopContext("run-1", request, 4, request.metadata());
  }

  private static AgentToolPermissionRequest request() {
    return new AgentToolPermissionRequest(
        "perm-1",
        "run-1",
        1,
        "call-1",
        "submit_practice_code_review",
        "提交代码 Review",
        "模型请求执行正式 Review",
        Map.of("effect", "save_review"),
        Instant.parse("2026-06-26T00:00:00Z"),
        Instant.parse("2026-06-26T00:01:00Z"));
  }

  private static AgentToolPermissionDecisionPlan askPlan() {
    return AgentToolPermissionDecisionPlan.ask(
        "提交代码 Review",
        "模型请求执行正式 Review",
        Map.of("effect", "save_review"),
        "test-policy");
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

  private static final class CollectingSubscriber implements java.util.concurrent.Flow.Subscriber<AgentStreamEvent> {

    private final List<AgentStreamEvent> events = java.util.Collections.synchronizedList(new ArrayList<>());
    private java.util.concurrent.Flow.Subscription subscription;

    @Override
    public void onSubscribe(java.util.concurrent.Flow.Subscription subscription) {
      this.subscription = subscription;
      subscription.request(Long.MAX_VALUE);
    }

    @Override
    public void onNext(AgentStreamEvent item) {
      events.add(item);
    }

    @Override
    public void onError(Throwable throwable) {
    }

    @Override
    public void onComplete() {
    }

    List<AgentStreamEvent> events() {
      if (subscription != null) {
        subscription.request(Long.MAX_VALUE);
      }
      return List.copyOf(events);
    }

    private void awaitEventCount(int expectedCount) {
      long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(1);
      while (System.nanoTime() < deadline && events.size() < expectedCount) {
        try {
          Thread.sleep(5);
        } catch (InterruptedException ex) {
          Thread.currentThread().interrupt();
          return;
        }
      }
    }
  }
}
