package org.congcong.algomentor.agent.runtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.congcong.algomentor.agent.core.AgentCancellationToken;
import org.congcong.algomentor.agent.core.AgentErrorCode;
import org.congcong.algomentor.agent.core.AgentExecutionOptions;
import org.congcong.algomentor.agent.core.AgentException;
import org.congcong.algomentor.agent.core.AgentLlmRequestFactory;
import org.congcong.algomentor.agent.core.AgentLoopEngine;
import org.congcong.algomentor.agent.core.AgentOutput;
import org.congcong.algomentor.agent.core.AgentRunResult;
import org.congcong.algomentor.agent.core.AgentStreamEvent;
import org.congcong.algomentor.agent.core.AgentToolRegistry;
import org.congcong.algomentor.agent.core.DefaultAgentModelSelectorResolver;
import org.congcong.algomentor.agent.core.compaction.RunMessageCompactor;
import org.congcong.algomentor.agent.core.compaction.ToolResultCompactionPolicy;
import org.congcong.algomentor.agent.core.compaction.ToolResultCompactor;
import org.congcong.algomentor.agent.core.execution.AgentExecutor;
import org.congcong.algomentor.agent.core.execution.AgentExecutionGroup;
import org.congcong.algomentor.agent.core.permission.AgentToolPermissionGuard;
import org.congcong.algomentor.agent.core.permission.AgentToolPermissionHookChain;
import org.congcong.algomentor.agent.core.permission.AgentToolPermissionResultFactory;
import org.congcong.algomentor.agent.core.permission.InMemoryAgentToolPermissionCoordinator;
import org.congcong.algomentor.agent.core.runtime.api.AgentInvocation;
import org.congcong.algomentor.agent.core.runtime.api.AgentInvocationContext;
import org.congcong.algomentor.agent.core.runtime.api.AgentInvocationMode;
import org.congcong.algomentor.agent.core.runtime.definition.AgentDefinition;
import org.congcong.algomentor.agent.core.runtime.definition.AgentKey;
import org.congcong.algomentor.agent.core.runtime.definition.AgentLoopPolicy;
import org.congcong.algomentor.agent.core.runtime.definition.AgentOutputContract;
import org.congcong.algomentor.agent.core.runtime.definition.AgentPreparedRequest;
import org.congcong.algomentor.agent.core.runtime.definition.AgentRunResource;
import org.congcong.algomentor.agent.core.runtime.model.AgentRunPreparationRequest;
import org.congcong.algomentor.agent.core.runtime.model.AgentRuntimeMetadataKeys;
import org.congcong.algomentor.agent.core.runtime.model.PreparedAgentRun;
import org.congcong.algomentor.agent.core.runtime.repository.AgentConversationRepository;
import org.congcong.algomentor.agent.core.toolresult.InMemoryToolResultStore;
import org.congcong.algomentor.agent.runtime.definition.AgentDefinitionRegistry;
import org.congcong.algomentor.agent.runtime.governance.AgentRuntimeGovernanceService;
import org.congcong.algomentor.ai.governance.admission.AiRunAdmissionService;
import org.congcong.algomentor.ai.governance.admission.AiRunLifecycleService;
import org.congcong.algomentor.ai.governance.model.AiBusinessScenario;
import org.congcong.algomentor.ai.governance.model.AiGovernanceErrorCode;
import org.congcong.algomentor.ai.governance.model.AiPurpose;
import org.congcong.algomentor.ai.governance.model.AiRunContext;
import org.congcong.algomentor.ai.governance.model.AiRunStatus;
import org.congcong.algomentor.ai.governance.model.AiUsage;
import org.congcong.algomentor.ai.governance.execution.AiRunGovernanceService;
import org.congcong.algomentor.ai.governance.policy.AiGovernanceProperties;
import org.congcong.algomentor.ai.governance.policy.AiPurposePolicy;
import org.congcong.algomentor.ai.governance.policy.AiPurposePolicyResolver;
import org.congcong.algomentor.ai.governance.policy.runtime.AiRuntimePolicyService;
import org.congcong.algomentor.ai.governance.policy.runtime.EffectiveAiRuntimePolicy;
import org.congcong.algomentor.ai.governance.repository.mybatis.PostgresAiRunAdmissionRepository;
import org.congcong.algomentor.ai.governance.routing.AiModelRouteResolver;
import org.congcong.algomentor.ai.governance.routing.AiRunInvocationTargetStore;
import org.congcong.algomentor.ai.governance.routing.ResolvedAiModelSnapshot;
import org.congcong.algomentor.ai.governance.runlock.AiRunLockService;
import org.congcong.algomentor.ai.governance.usage.AiDailyUsageStore;
import org.congcong.algomentor.agent.core.runlock.AgentRunLockToken;
import org.congcong.algomentor.llm.core.gateway.LlmGateway;
import org.congcong.algomentor.llm.core.model.LlmModelId;
import org.congcong.algomentor.llm.core.model.LlmModelSelector;
import org.congcong.algomentor.llm.core.provider.LlmCapability;
import org.congcong.algomentor.llm.core.provider.LlmProviderClient;
import org.congcong.algomentor.llm.core.provider.LlmProviderId;
import org.congcong.algomentor.llm.core.request.LlmCompletionRequest;
import org.congcong.algomentor.llm.core.request.LlmMessage;
import org.congcong.algomentor.llm.core.response.LlmCompletionResult;
import org.congcong.algomentor.llm.core.response.LlmFinishReason;
import org.congcong.algomentor.llm.core.response.LlmUsage;
import org.congcong.algomentor.llm.core.stream.LlmStreamEvent;
import org.congcong.algomentor.llm.core.tool.LlmToolChoice;
import org.congcong.algomentor.policy.model.PolicyMatchSource;
import org.junit.jupiter.api.Test;

class DefaultAgentRuntimeTest {

  private static final AgentKey<String> KEY = new AgentKey<>(
      AiBusinessScenario.PRACTICE_CHAT.code(), String.class);
  private static final AgentKey<String> PRACTICE_REVIEW_KEY = new AgentKey<>(
      AiBusinessScenario.PRACTICE_CODE_REVIEW.code(), String.class);
  private static final AgentKey<String> CODE_REVIEW_PROFILE_KEY = new AgentKey<>(
      AiBusinessScenario.CODE_REVIEW_PROFILE_UPDATE.code(), String.class);

  @Test
  void executesAndStreamsTheSameTextWithOneStepNoToolsAndAuditedUserRun() {
    Fixture fixture = new Fixture();
    fixture.gateway.responses.add(response("Explain binary search."));
    fixture.gateway.responses.add(response("Explain binary search."));
    DefaultAgentRuntime runtime = fixture.runtime(false, false);

    AgentRunResult result = runtime.execute(invocation("binary search", false));
    CollectingSubscriber subscriber = new CollectingSubscriber(false);
    runtime.stream(invocation("binary search", true)).subscribe(subscriber);

    assertThat(result.output().text()).isEqualTo("Explain binary search.");
    assertThat(subscriber.content()).isEqualTo("Explain binary search.");
    assertThat(fixture.gateway.requests).hasSize(2).allSatisfy(request -> {
      assertThat(request.tools()).isEmpty();
      assertThat(request.toolChoice()).isEqualTo(LlmToolChoice.none());
    });
    assertThat(fixture.executor.executeCalls).isEqualTo(2);
    assertThat(fixture.conversations.requests).hasSize(2).allSatisfy(request -> {
      assertThat(request.userId()).isEqualTo(7L);
      assertThat(request.agentKey()).isEqualTo(KEY.value());
      assertThat(request.mode()).isEqualTo(AgentInvocationMode.USER_ENTRY);
      assertThat(request.maxSteps()).isEqualTo(1);
    });
    assertThat(fixture.governance.usage.consumeCalls).isEqualTo(2);
    assertThat(fixture.governance.locks.acquireCalls).isEqualTo(2);
    assertThat(fixture.governance.locks.releaseCalls).isEqualTo(2);
    assertThat(fixture.governance.repository.statuses)
        .containsExactly(AiRunStatus.RUNNING, AiRunStatus.COMPLETED, AiRunStatus.RUNNING, AiRunStatus.COMPLETED);
  }

  @Test
  void rejectsUserEntryInvocationFromAnAgentExecutorThread() {
    Fixture fixture = new Fixture();
    DefaultAgentRuntime runtime = fixture.runtime(true, false);

    assertThatThrownBy(() -> runtime.execute(invocation("binary search", false)))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("Agent USER_ENTRY invocation must not start inside an Agent executor thread");

    assertThat(fixture.executor.executeCalls).isZero();
    assertThat(fixture.governance.repository.statuses).isEmpty();
  }

  @Test
  void exposesActualProviderModelAndUsageInSuccessfulRuntimeResultMetadata() {
    Fixture fixture = new Fixture();
    LlmUsage usage = new LlmUsage(13, 7, 2, 3, 20);
    fixture.gateway.responses.add(List.of(
        new LlmStreamEvent.MessageStart(LlmProviderId.of("provider-from-message-start"), LlmModelId.of("model-from-message-start")),
        new LlmStreamEvent.ContentDelta("structured result"),
        new LlmStreamEvent.Usage(usage),
        new LlmStreamEvent.MessageEnd(LlmFinishReason.STOP, Map.of())));
    DefaultAgentRuntime runtime = fixture.runtime(false, false);

    AgentRunResult result = runtime.execute(invocation("binary search", false));

    assertThat(result.metadata())
        .containsEntry(AgentRuntimeMetadataKeys.RUNTIME_PROVIDER, "provider-from-message-start")
        .containsEntry(AgentRuntimeMetadataKeys.RUNTIME_MODEL, "model-from-message-start")
        .containsEntry(AgentRuntimeMetadataKeys.RUNTIME_USAGE, usage);
  }

  @Test
  void runsPracticeReviewChildInlineOnAnOrdinaryNamedAgentWorkerThread() throws Exception {
    Fixture fixture = new Fixture();
    fixture.gateway.responses.add(response("review result"));
    DefaultAgentRuntime runtime = fixture.runtime(true, false, new TextDefinition(false, PRACTICE_REVIEW_KEY));
    AtomicReference<AgentRunResult> result = new AtomicReference<>();
    AtomicReference<Throwable> failure = new AtomicReference<>();
    Thread worker = new Thread(() -> {
      try {
        result.set(runtime.execute(childInvocation()));
      } catch (Throwable exception) {
        failure.set(exception);
      }
    }, "ordinary-http-worker");

    worker.start();
    worker.join();

    assertThat(failure.get()).isNull();
    assertThat(result.get().output().text()).isEqualTo("review result");
    assertThat(result.get().metadata())
        .containsEntry(AgentRuntimeMetadataKeys.RUN_DB_ID, 1L)
        .containsEntry(AgentRuntimeMetadataKeys.AGENT_RUN_ID, "run-1")
        .containsEntry(AgentRuntimeMetadataKeys.PARENT_RUN_ID, 41L)
        .containsEntry(AgentRuntimeMetadataKeys.PARENT_STEP_INDEX, 2);
    assertThat(fixture.executor.executeCalls).isZero();
    assertThat(fixture.conversations.requests).singleElement().satisfies(request -> {
      assertThat(request.taskId()).isNull();
      assertThat(request.userId()).isEqualTo(7L);
      assertThat(request.agentKey()).isEqualTo(PRACTICE_REVIEW_KEY.value());
      assertThat(request.mode()).isEqualTo(AgentInvocationMode.CHILD);
      assertThat(request.parentRunId()).isEqualTo(41L);
      assertThat(request.parentStepIndex()).isEqualTo(2);
    });
    assertThat(fixture.governance.usage.consumeCalls).isZero();
    assertThat(fixture.governance.locks.acquireCalls).isZero();
    assertThat(fixture.governance.locks.releaseCalls).isZero();
    assertThat(fixture.governance.routes.lastScenario).isEqualTo(AiBusinessScenario.PRACTICE_CODE_REVIEW);
    assertThat(fixture.governance.repository.statuses).isEmpty();
  }

  @Test
  void rejectsPracticeReviewChildFromAnExternalThread() {
    Fixture fixture = new Fixture();
    DefaultAgentRuntime runtime = fixture.runtime(false, false, new TextDefinition(false, PRACTICE_REVIEW_KEY));

    assertThatThrownBy(() -> runtime.execute(childInvocation()))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("Agent CHILD invocation must execute inside an Agent executor thread");

    assertThat(fixture.executor.executeCalls).isZero();
    assertThat(fixture.governance.usage.consumeCalls).isZero();
    assertThat(fixture.governance.locks.acquireCalls).isZero();
    assertThat(fixture.governance.locks.releaseCalls).isZero();
  }

  @Test
  void rejectsBackgroundInvocationFromAnAgentExecutorThread() {
    Fixture fixture = new Fixture();
    DefaultAgentRuntime runtime = fixture.runtime(true, false,
        new TextDefinition(false, CODE_REVIEW_PROFILE_KEY));

    assertThatThrownBy(() -> runtime.execute(backgroundInvocation()))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("Agent BACKGROUND invocation must not start inside an Agent executor thread");

    assertThat(fixture.conversations.requests).isEmpty();
    assertThat(fixture.governance.usage.consumeCalls).isZero();
    assertThat(fixture.governance.locks.acquireCalls).isZero();
    assertThat(fixture.governance.locks.releaseCalls).isZero();
    assertThat(fixture.governance.routes.lastScenario).isNull();
  }

  @Test
  void propagatesDefinitionRetrySourceWhenPreparingChildRun() {
    Fixture fixture = new Fixture();
    fixture.gateway.responses.add(response("retry result"));
    DefaultAgentRuntime runtime = fixture.runtime(true, false,
        new TextDefinition(false, PRACTICE_REVIEW_KEY, 801L));

    runtime.execute(childInvocation());

    assertThat(fixture.conversations.requests).singleElement().satisfies(request -> {
      assertThat(request.mode()).isEqualTo(AgentInvocationMode.CHILD);
      assertThat(request.parentRunId()).isEqualTo(41L);
      assertThat(request.parentStepIndex()).isEqualTo(2);
      assertThat(request.retryOfRunId()).isEqualTo(801L);
    });
  }

  @Test
  void settlesGovernanceExactlyOnceWhenExecutorRejects() {
    Fixture fixture = new Fixture();
    DefaultAgentRuntime runtime = fixture.runtime(false, true);

    assertThatThrownBy(() -> runtime.execute(invocation("binary search", false)))
        .isInstanceOf(AgentException.class)
        .extracting(error -> ((AgentException) error).code())
        .isEqualTo(AgentErrorCode.AGENT_EXECUTOR_OVERLOADED);

    assertThat(fixture.governance.usage.consumeCalls).isEqualTo(1);
    assertThat(fixture.governance.locks.acquireCalls).isEqualTo(1);
    assertThat(fixture.governance.locks.releaseCalls).isEqualTo(1);
    assertThat(fixture.governance.repository.statuses)
        .containsExactly(AiRunStatus.RUNNING, AiRunStatus.FAILED);
  }

  @Test
  void failsAndReleasesTheLeaseWhenTheLlmFails() {
    Fixture fixture = new Fixture();
    fixture.gateway.failure = new IllegalStateException("provider unavailable");
    DefaultAgentRuntime runtime = fixture.runtime(false, false);

    assertThatThrownBy(() -> runtime.execute(invocation("binary search", false)))
        .isInstanceOf(AgentException.class);

    assertThat(fixture.governance.locks.releaseCalls).isEqualTo(1);
    assertThat(fixture.governance.repository.statuses)
        .containsExactly(AiRunStatus.RUNNING, AiRunStatus.FAILED);
  }

  @Test
  void cancellationSettlesTheLeaseAsCancelled() {
    Fixture fixture = new Fixture();
    fixture.gateway.responses.add(response("partial"));
    DefaultAgentRuntime runtime = fixture.runtime(false, false);
    CollectingSubscriber subscriber = new CollectingSubscriber(true);

    runtime.stream(invocation("binary search", true)).subscribe(subscriber);

    assertThat(fixture.governance.locks.releaseCalls).isEqualTo(1);
    assertThat(fixture.governance.repository.statuses)
        .containsExactly(AiRunStatus.RUNNING, AiRunStatus.CANCELLED);
  }

  @Test
  void rejectsDefinitionPreparationBeforeCreatingAnAuditRunOrGovernanceLease() {
    Fixture fixture = new Fixture();
    DefaultAgentRuntime runtime = fixture.runtime(false, false, true);

    assertThatThrownBy(() -> runtime.execute(invocation("binary search", false)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("definition preparation failed");

    assertThat(fixture.conversations.requests).isEmpty();
    assertThat(fixture.governance.usage.consumeCalls).isZero();
    assertThat(fixture.governance.locks.acquireCalls).isZero();
  }

  @Test
  void usesDefinitionPreparedRunWithoutDuplicatingAuditPreparationAndReleasesItsResource() {
    Fixture fixture = new Fixture();
    fixture.gateway.responses.add(response("prepared run"));
    AtomicInteger releases = new AtomicInteger();
    DefaultAgentRuntime runtime = fixture.runtime(false, false, new PreparedRunDefinition(false, releases));

    AgentRunResult result = runtime.execute(invocation("binary search", false));

    assertThat(result.output().text()).isEqualTo("prepared run");
    assertThat(result.metadata())
        .containsEntry(AgentRuntimeMetadataKeys.TASK_ID, 42L)
        .containsEntry(AgentRuntimeMetadataKeys.TURN_ID, 2L)
        .containsEntry(AgentRuntimeMetadataKeys.RUN_DB_ID, 3L)
        .containsEntry(AgentRuntimeMetadataKeys.AGENT_RUN_ID, "prepared-run-3");
    assertThat(fixture.conversations.requests).isEmpty();
    assertThat(releases).hasValue(1);
    assertThat(fixture.governance.repository.statuses)
        .containsExactly(AiRunStatus.RUNNING, AiRunStatus.COMPLETED);
  }

  @Test
  void releasesDefinitionPreparedRunResourceWhenExecutorSubmissionIsRejected() {
    Fixture fixture = new Fixture();
    AtomicInteger releases = new AtomicInteger();
    DefaultAgentRuntime runtime = fixture.runtime(false, true, new PreparedRunDefinition(false, releases));

    assertThatThrownBy(() -> runtime.execute(invocation("binary search", false)))
        .isInstanceOf(AgentException.class)
        .extracting(error -> ((AgentException) error).code())
        .isEqualTo(AgentErrorCode.AGENT_EXECUTOR_OVERLOADED);

    assertThat(releases).hasValue(1);
    assertThat(fixture.conversations.requests).isEmpty();
    assertThat(fixture.governance.repository.statuses)
        .containsExactly(AiRunStatus.RUNNING, AiRunStatus.FAILED);
  }

  @Test
  void replaysDefinitionPreparedRunWithoutModelOrGovernanceExecution() {
    Fixture fixture = new Fixture();
    AtomicInteger releases = new AtomicInteger();
    DefaultAgentRuntime runtime = fixture.runtime(false, false, new PreparedRunDefinition(true, releases));
    CollectingSubscriber subscriber = new CollectingSubscriber(false);

    runtime.stream(invocation("binary search", true)).subscribe(subscriber);

    assertThat(subscriber.events).extracting(AgentStreamEvent::name)
        .containsExactly("agent_run_start", "agent_run_end");
    assertThat(fixture.gateway.requests).isEmpty();
    assertThat(fixture.conversations.requests).isEmpty();
    assertThat(fixture.governance.usage.consumeCalls).isZero();
    assertThat(releases).hasValue(1);
  }

  @Test
  void returnsTrustedRunIdentityWhenReplayingPreparedRun() {
    Fixture fixture = new Fixture();
    AtomicInteger releases = new AtomicInteger();
    DefaultAgentRuntime runtime = fixture.runtime(false, false, new PreparedRunDefinition(true, releases));

    AgentRunResult result = runtime.execute(invocation("binary search", false));

    assertThat(result.metadata())
        .containsEntry(AgentRuntimeMetadataKeys.TASK_ID, 42L)
        .containsEntry(AgentRuntimeMetadataKeys.TURN_ID, 2L)
        .containsEntry(AgentRuntimeMetadataKeys.RUN_DB_ID, 3L)
        .containsEntry(AgentRuntimeMetadataKeys.AGENT_RUN_ID, "prepared-run-3");
    assertThat(fixture.gateway.requests).isEmpty();
    assertThat(fixture.governance.repository.statuses).isEmpty();
    assertThat(releases).hasValue(1);
  }

  @Test
  void releasesDefinitionPreparedRunResourceWhenLoopPolicyValidationFails() {
    Fixture fixture = new Fixture();
    AtomicInteger releases = new AtomicInteger();
    DefaultAgentRuntime runtime = fixture.runtime(false, false, new PreparedRunDefinition(false, releases, 5));

    assertThatThrownBy(() -> runtime.execute(invocation("binary search", false)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Agent loop max steps exceeds hard limit");

    assertThat(releases).hasValue(1);
    assertThat(fixture.conversations.requests).isEmpty();
    assertThat(fixture.governance.usage.consumeCalls).isZero();
  }

  private static AgentInvocation<String> invocation(String topic, boolean streaming) {
    return new AgentInvocation<>(KEY, topic, new AgentInvocationContext(
        7L,
        AgentInvocationMode.USER_ENTRY,
        "topic-" + topic + "-" + streaming,
        null,
        null,
        topic.length(),
        streaming));
  }

  private static AgentInvocation<String> childInvocation() {
    return new AgentInvocation<>(PRACTICE_REVIEW_KEY, "review code", new AgentInvocationContext(
        7L,
        AgentInvocationMode.CHILD,
        "practice-code-review:50:701",
        "41",
        2,
        11,
        false));
  }

  private static AgentInvocation<String> backgroundInvocation() {
    return new AgentInvocation<>(CODE_REVIEW_PROFILE_KEY, "review facts", new AgentInvocationContext(
        7L,
        AgentInvocationMode.BACKGROUND,
        "code-review-profile:stable-hash",
        null,
        null,
        5,
        false));
  }

  private static List<LlmStreamEvent> response(String text) {
    return List.of(
        new LlmStreamEvent.MessageStart(LlmProviderId.of("openai"), LlmModelId.of("gpt-test")),
        new LlmStreamEvent.ContentDelta(text),
        new LlmStreamEvent.MessageEnd(LlmFinishReason.STOP, Map.of()));
  }

  private static final class Fixture {

    private final RecordingGateway gateway = new RecordingGateway();
    private final RecordingExecutor executor = new RecordingExecutor();
    private final RecordingConversations conversations = new RecordingConversations();
    private final GovernanceFixture governance = new GovernanceFixture();

    private DefaultAgentRuntime runtime(boolean executorThread, boolean reject) {
      return runtime(executorThread, reject, false);
    }

    private DefaultAgentRuntime runtime(boolean executorThread, boolean reject, boolean definitionFailure) {
      return runtime(executorThread, reject, new TextDefinition(definitionFailure));
    }

    private DefaultAgentRuntime runtime(
        boolean executorThread,
        boolean reject,
        AgentDefinition<String> definition
    ) {
      executor.executorThread = executorThread;
      executor.reject = reject;
      ObjectMapper objectMapper = new ObjectMapper();
      ToolResultCompactor toolResultCompactor = new ToolResultCompactor(
          objectMapper, ToolResultCompactionPolicy.defaults(), new InMemoryToolResultStore());
      AgentLoopEngine engine = new AgentLoopEngine(
          gateway,
          new AgentLlmRequestFactory(
              new LlmModelSelector(null, LlmModelId.of("gpt-test"), Set.of(), null),
              new DefaultAgentModelSelectorResolver(),
              governance.targets),
          List.of(),
          List.of(),
          toolResultCompactor,
          new RunMessageCompactor(objectMapper, toolResultCompactor),
          objectMapper,
          new AgentToolPermissionGuard(
              new AgentToolPermissionHookChain(),
              new InMemoryAgentToolPermissionCoordinator(new AgentToolPermissionResultFactory(objectMapper))));
      return new DefaultAgentRuntime(
          new AgentDefinitionRegistry(List.of(definition)),
          new AgentRuntimeGovernanceService(governance.service),
          conversations,
          engine,
          AgentToolRegistry.empty(),
          executor,
          4);
    }
  }

  private static final class TextDefinition implements AgentDefinition<String> {

    private final boolean failsPreparation;
    private final AgentKey<String> key;
    private final Long retryOfRunId;

    private TextDefinition(boolean failsPreparation) {
      this(failsPreparation, KEY, null);
    }

    private TextDefinition(boolean failsPreparation, AgentKey<String> key) {
      this(failsPreparation, key, null);
    }

    private TextDefinition(boolean failsPreparation, AgentKey<String> key, Long retryOfRunId) {
      this.failsPreparation = failsPreparation;
      this.key = key;
      this.retryOfRunId = retryOfRunId;
    }

    @Override
    public AgentKey<String> key() {
      return key;
    }

    @Override
    public AgentExecutionGroup executionGroup() {
      return groupFor(key);
    }

    @Override
    public AgentLoopPolicy loopPolicy() {
      return new AgentLoopPolicy(1);
    }

    @Override
    public AgentOutputContract outputContract() {
      return AgentOutputContract.defaults();
    }

    @Override
    public AgentPreparedRequest prepare(String input, AgentInvocationContext context) {
      if (failsPreparation) {
        throw new IllegalArgumentException("definition preparation failed");
      }
      return new AgentPreparedRequest(
          List.of(LlmMessage.system("mentor"), LlmMessage.user(input)),
          Map.of("topic", input),
          AgentExecutionOptions.defaults(),
          null,
          false,
          null,
          retryOfRunId);
    }
  }

  private static final class PreparedRunDefinition implements AgentDefinition<String> {

    private final boolean replay;
    private final AtomicInteger releases;
    private final int maxSteps;

    private PreparedRunDefinition(boolean replay, AtomicInteger releases) {
      this(replay, releases, 1);
    }

    private PreparedRunDefinition(boolean replay, AtomicInteger releases, int maxSteps) {
      this.replay = replay;
      this.releases = releases;
      this.maxSteps = maxSteps;
    }

    @Override
    public AgentKey<String> key() {
      return KEY;
    }

    @Override
    public AgentExecutionGroup executionGroup() {
      return AgentExecutionGroup.PRACTICE;
    }

    @Override
    public AgentLoopPolicy loopPolicy() {
      return new AgentLoopPolicy(maxSteps);
    }

    @Override
    public AgentOutputContract outputContract() {
      return AgentOutputContract.defaults();
    }

    @Override
    public AgentPreparedRequest prepare(String input, AgentInvocationContext context) {
      PreparedAgentRun preparedRun = new PreparedAgentRun(
          42L,
          2L,
          3L,
          "prepared-run-3",
          context.idempotencyKey(),
          "mentor",
          null,
          Map.of("prepared", true),
          KEY.value(),
          context.mode(),
          null,
          null,
          null,
          maxSteps);
      AgentRunResource resource = releases::incrementAndGet;
      return new AgentPreparedRequest(
          List.of(LlmMessage.system("mentor"), LlmMessage.user(input)),
          Map.of("prepared", true),
          AgentExecutionOptions.defaults(),
          preparedRun,
          replay,
          resource);
    }
  }

  private static final class RecordingExecutor implements AgentExecutor {

    private int executeCalls;
    private boolean executorThread;
    private boolean reject;

    @Override
    public void execute(AgentExecutionGroup group, Runnable task) {
      executeCalls++;
      if (reject) {
        throw new java.util.concurrent.RejectedExecutionException("saturated");
      }
      task.run();
    }

    @Override
    public boolean isShutdown() {
      return false;
    }

    @Override
    public boolean inExecutorThread() {
      return executorThread;
    }

    @Override
    public java.util.Optional<AgentExecutionGroup> currentExecutionGroup() {
      return executorThread ? java.util.Optional.of(AgentExecutionGroup.PRACTICE) : java.util.Optional.empty();
    }
  }

  private static AgentExecutionGroup groupFor(AgentKey<String> key) {
    return key.equals(CODE_REVIEW_PROFILE_KEY)
        ? AgentExecutionGroup.LEARNER_PROFILE_BACKGROUND
        : key.equals(KEY) || key.equals(PRACTICE_REVIEW_KEY)
            ? AgentExecutionGroup.PRACTICE
            : AgentExecutionGroup.LEARNING_PLAN;
  }

  private static final class RecordingConversations implements AgentConversationRepository {

    private final List<AgentRunPreparationRequest> requests = new ArrayList<>();

    @Override
    public PreparedAgentRun createOrReuseRun(AgentRunPreparationRequest request) {
      requests.add(request);
      long id = requests.size();
      return new PreparedAgentRun(
          id,
          id,
          id,
          "run-" + id,
          "request-" + id,
          request.systemPrompt(),
          null,
          request.metadata(),
          request.agentKey(),
          request.mode(),
          request.parentRunId(),
          request.parentStepIndex(),
          request.retryOfRunId(),
          request.maxSteps());
    }

    @Override
    public Optional<PreparedAgentRun> findRunByIdempotencyKey(String idempotencyKey) {
      return Optional.empty();
    }

    @Override
    public List<org.congcong.algomentor.agent.core.runtime.model.AgentMessage> recentMessages(
        long taskId,
        int messageLimit
    ) {
      return List.of();
    }

    @Override
    public List<org.congcong.algomentor.agent.core.runtime.model.AgentMessage> recentMessagesBeforeTurn(
        long taskId,
        long turnId,
        int messageLimit
    ) {
      return List.of();
    }
  }

  private static final class RecordingGateway implements LlmGateway {

    private final ArrayDeque<List<LlmStreamEvent>> responses = new ArrayDeque<>();
    private final List<LlmCompletionRequest> requests = new ArrayList<>();
    private RuntimeException failure;

    @Override
    public LlmCompletionResult complete(LlmCompletionRequest request) {
      throw new UnsupportedOperationException();
    }

    @Override
    public Flow.Publisher<LlmStreamEvent> stream(LlmCompletionRequest request) {
      requests.add(request);
      if (failure != null) {
        throw failure;
      }
      List<LlmStreamEvent> events = responses.removeFirst();
      return subscriber -> subscriber.onSubscribe(new Flow.Subscription() {
        private boolean done;

        @Override
        public void request(long count) {
          if (done) {
            return;
          }
          done = true;
          events.forEach(subscriber::onNext);
          subscriber.onComplete();
        }

        @Override
        public void cancel() {
          done = true;
        }
      });
    }
  }

  private static final class CollectingSubscriber implements Flow.Subscriber<AgentStreamEvent> {

    private final boolean cancelOnContent;
    private final StringBuilder content = new StringBuilder();
    private final List<AgentStreamEvent> events = new ArrayList<>();
    private Flow.Subscription subscription;

    private CollectingSubscriber(boolean cancelOnContent) {
      this.cancelOnContent = cancelOnContent;
    }

    @Override
    public void onSubscribe(Flow.Subscription nextSubscription) {
      subscription = nextSubscription;
      subscription.request(Long.MAX_VALUE);
    }

    @Override
    public void onNext(AgentStreamEvent event) {
      events.add(event);
      if (event instanceof AgentStreamEvent.Llm llm
          && llm.event() instanceof LlmStreamEvent.ContentDelta delta) {
        content.append(delta.content());
        if (cancelOnContent) {
          subscription.cancel();
        }
      }
    }

    @Override
    public void onError(Throwable error) {
    }

    @Override
    public void onComplete() {
    }

    private String content() {
      return content.toString();
    }
  }

  private static final class GovernanceFixture {

    private final AiGovernanceProperties properties = new AiGovernanceProperties();
    private final AiPurposePolicyResolver policyResolver = new AiPurposePolicyResolver(properties);
    private final RecordingUsage usage = new RecordingUsage();
    private final RecordingLocks locks = new RecordingLocks();
    private final RecordingAdmissionRepository repository = new RecordingAdmissionRepository();
    private final RecordingRoutes routes = new RecordingRoutes();
    private final AiRunInvocationTargetStore targets = new AiRunInvocationTargetStore();
    private final AiRunAdmissionService admission = new AiRunAdmissionService(
        properties, policyResolver, usage, locks, repository, null, routes, targets);
    private final AiRunLifecycleService lifecycle = new AiRunLifecycleService(
        properties, repository, usage, locks, targets);
    private final AiRunGovernanceService service = new AiRunGovernanceService(
        admission, lifecycle, policyResolver, enabledRuntimePolicy(), routes, targets);
  }

  private static AiRuntimePolicyService enabledRuntimePolicy() {
    return new AiRuntimePolicyService(null, null, null) {
      @Override
      public EffectiveAiRuntimePolicy resolve(AiPurposePolicy staticPolicy, long userId) {
        return new EffectiveAiRuntimePolicy(true, null, true, null, 10, null, 10, null, null);
      }
    };
  }

  private static final class RecordingUsage implements AiDailyUsageStore {

    private int consumeCalls;

    @Override
    public boolean tryConsumeRequest(long userId, LocalDate quotaDate, String scope, long limitCount) {
      consumeCalls++;
      return true;
    }

    @Override
    public void addUsage(long userId, LocalDate quotaDate, String scope, AiUsage usage) {
    }
  }

  private static final class RecordingLocks extends AiRunLockService {

    private int acquireCalls;
    private int releaseCalls;

    private RecordingLocks() {
      super(null, null, null);
    }

    @Override
    public Optional<AgentRunLockToken> tryAcquire(long userId, String runId, Map<String, Object> metadata) {
      acquireCalls++;
      return Optional.of(new AgentRunLockToken("user:7:ai:all", "node-1", "token-" + acquireCalls, null));
    }

    @Override
    public void release(AgentRunLockToken token) {
      releaseCalls++;
    }
  }

  private static final class RecordingAdmissionRepository extends PostgresAiRunAdmissionRepository {

    private final List<AiRunStatus> statuses = new ArrayList<>();

    private RecordingAdmissionRepository() {
      super(null);
    }

    @Override
    public Long insert(AiRunContext context, AiRunStatus status, AiGovernanceErrorCode rejectionCode) {
      return (long) statuses.size() + 1;
    }

    @Override
    public void updateStatus(
        Long admissionId,
        String runId,
        AiRunStatus status,
        AiGovernanceErrorCode errorCode,
        AiUsage usage,
        String provider,
        String model,
        Instant completedAt
    ) {
      statuses.add(status);
    }
  }

  private static final class RecordingRoutes implements AiModelRouteResolver {

    private AiBusinessScenario lastScenario;

    @Override
    public ResolvedAiModelSnapshot resolve(AiBusinessScenario scenario, long userId) {
      lastScenario = scenario;
      return new ResolvedAiModelSnapshot(
          scenario,
          17L,
          2L,
          PolicyMatchSource.GROUP,
          9L,
          101L,
          "gpt-test",
          11L,
          "openai",
          Instant.parse("2026-07-27T00:00:00Z"),
          new LlmProviderClient() {
            @Override
            public LlmCompletionResult complete(LlmModelId upstreamModelId, LlmCompletionRequest request) {
              throw new UnsupportedOperationException();
            }

            @Override
            public Flow.Publisher<LlmStreamEvent> stream(
                LlmModelId upstreamModelId,
                LlmCompletionRequest request
            ) {
              throw new UnsupportedOperationException();
            }
          },
          Set.of(LlmCapability.CHAT_COMPLETION));
    }
  }
}
