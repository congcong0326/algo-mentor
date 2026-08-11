package org.congcong.algomentor.agent.runtime;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Flow;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.congcong.algomentor.agent.core.AgentCancellationToken;
import org.congcong.algomentor.agent.core.AgentErrorCode;
import org.congcong.algomentor.agent.core.AgentException;
import org.congcong.algomentor.agent.core.AgentLoopEngine;
import org.congcong.algomentor.agent.core.AgentLoopExecution;
import org.congcong.algomentor.agent.core.AgentRequest;
import org.congcong.algomentor.agent.core.AgentRunResult;
import org.congcong.algomentor.agent.core.AgentStreamEvent;
import org.congcong.algomentor.agent.core.AgentStreamEventSink;
import org.congcong.algomentor.agent.core.AgentToolRegistry;
import org.congcong.algomentor.agent.core.SingleSubscriberAgentStreamPublisher;
import org.congcong.algomentor.agent.core.execution.AgentExecutionRejectedException;
import org.congcong.algomentor.agent.core.execution.AgentExecutionRejectionReason;
import org.congcong.algomentor.agent.core.execution.AgentExecutor;
import org.congcong.algomentor.agent.core.runtime.api.AgentInvocation;
import org.congcong.algomentor.agent.core.runtime.api.AgentInvocationContext;
import org.congcong.algomentor.agent.core.runtime.api.AgentRuntime;
import org.congcong.algomentor.agent.core.runtime.definition.AgentDefinition;
import org.congcong.algomentor.agent.core.runtime.definition.AgentKey;
import org.congcong.algomentor.agent.core.runtime.definition.AgentPreparedRequest;
import org.congcong.algomentor.agent.core.runtime.definition.AgentRunResource;
import org.congcong.algomentor.agent.core.runtime.model.AgentRunPreparationRequest;
import org.congcong.algomentor.agent.core.runtime.model.AgentRuntimeMetadataKeys;
import org.congcong.algomentor.agent.core.runtime.model.PreparedAgentRun;
import org.congcong.algomentor.agent.core.runtime.repository.AgentConversationRepository;
import org.congcong.algomentor.agent.runtime.definition.AgentDefinitionRegistry;
import org.congcong.algomentor.agent.runtime.governance.AgentRuntimeGovernanceLease;
import org.congcong.algomentor.agent.runtime.governance.AgentRuntimeGovernanceService;
import org.congcong.algomentor.ai.governance.execution.AiRunGovernanceService;
import org.congcong.algomentor.ai.governance.model.AiGovernanceErrorCode;
import org.congcong.algomentor.ai.governance.model.AiUsage;
import org.congcong.algomentor.llm.core.response.LlmUsage;
import org.congcong.algomentor.llm.core.response.LlmFinishReason;
import org.congcong.algomentor.llm.core.stream.LlmStreamEvent;
import org.congcong.algomentor.llm.core.request.LlmMessage;

/** 生产 Runtime：统一 Definition、审计运行、治理租约和同步 loop 执行。 */
public final class DefaultAgentRuntime implements AgentRuntime {

  private final AgentDefinitionRegistry definitionRegistry;
  private final AgentRuntimeGovernanceService governanceService;
  private final AgentConversationRepository conversationRepository;
  private final AgentLoopEngine loopEngine;
  private final AgentToolRegistry toolRegistry;
  private final AgentExecutor executor;
  private final int maxStepsHardLimit;

  public DefaultAgentRuntime(
      AgentDefinitionRegistry definitionRegistry,
      AgentRuntimeGovernanceService governanceService,
      AgentConversationRepository conversationRepository,
      AgentLoopEngine loopEngine,
      AgentToolRegistry toolRegistry,
      AgentExecutor executor,
      int maxStepsHardLimit
  ) {
    this.definitionRegistry = Objects.requireNonNull(definitionRegistry, "Agent definition registry must not be null");
    this.governanceService = Objects.requireNonNull(governanceService, "Agent governance service must not be null");
    this.conversationRepository = Objects.requireNonNull(
        conversationRepository, "Agent conversation repository must not be null");
    this.loopEngine = Objects.requireNonNull(loopEngine, "Agent loop engine must not be null");
    this.toolRegistry = Objects.requireNonNull(toolRegistry, "Agent tool registry must not be null");
    this.executor = Objects.requireNonNull(executor, "Agent executor must not be null");
    if (maxStepsHardLimit < 1) {
      throw new IllegalArgumentException("Agent runtime max steps hard limit must be positive");
    }
    this.maxStepsHardLimit = maxStepsHardLimit;
  }

  @Override
  public AgentRunResult execute(AgentInvocation<?> invocation) {
    AgentCancellationToken cancellationToken = new AgentCancellationToken();
    RuntimeRun run = prepare(invocation, cancellationToken);
    if (executor.inExecutorThread()) {
      return requireSuccess(runLoop(run, event -> true, cancellationToken));
    }

    CountDownLatch completed = new CountDownLatch(1);
    AtomicReference<RunOutcome> outcome = new AtomicReference<>();
    try {
      executor.execute(() -> {
        try {
          outcome.set(runLoop(run, event -> true, cancellationToken));
        } finally {
          completed.countDown();
        }
      });
    } catch (RuntimeException failure) {
      failSubmission(run, cancellationToken, failure);
      throw toAgentException(failure);
    }
    try {
      completed.await();
    } catch (InterruptedException interrupted) {
      cancellationToken.cancel();
      Thread.currentThread().interrupt();
      AgentException failure = cancelled(interrupted);
      settleFailure(run, new OutcomeCollector(), failure, true);
      throw failure;
    }
    return requireSuccess(outcome.get());
  }

  @Override
  public Flow.Publisher<AgentStreamEvent> stream(AgentInvocation<?> invocation) {
    AgentCancellationToken cancellationToken = new AgentCancellationToken();
    AtomicReference<RuntimeRun> preparedRun = new AtomicReference<>();
    return new SingleSubscriberAgentStreamPublisher(
        cancellationToken,
        executor,
        executor.inExecutorThread(),
        () -> preparedRun.set(prepare(invocation, cancellationToken)),
        eventSink -> {
          RuntimeRun run = preparedRun.get();
          if (run == null) {
            throw new IllegalStateException("Agent runtime stream was not prepared");
          }
          runLoop(run, eventSink, cancellationToken);
        },
        failure -> {
          RuntimeRun run = preparedRun.get();
          if (run != null) {
            failSubmission(run, cancellationToken, failure);
          }
        });
  }

  private RuntimeRun prepare(AgentInvocation<?> invocation, AgentCancellationToken cancellationToken) {
    AgentInvocation<?> candidate = Objects.requireNonNull(invocation, "Agent invocation must not be null");
    AgentDefinition<?> definition = definitionRegistry.resolve(candidate.agentKey());
    AgentPreparedRequest preparedRequest = definitionRegistry.prepare(candidate);
    AgentLoopExecution execution = null;
    AgentRequest request = null;
    try {
      AgentLoopExecution.validateMaxSteps(definition.loopPolicy().maxSteps(), maxStepsHardLimit);
      execution = AgentLoopExecution.forRuntime(
          toolRegistry,
          definition.allowedToolNames(),
          definition.loopPolicy().maxSteps());
      PreparedAgentRun preparedRun = preparedRequest.preparedRun() == null
          ? conversationRepository.createOrReuseRun(
              preparationRequest(candidate, definition, preparedRequest, execution))
          : preparedRequest.preparedRun();
      request = request(preparedRun, preparedRequest, Map.of());
      if (preparedRequest.idempotentReplay()) {
        return RuntimeRun.replay(request, execution, preparedRequest.runResource());
      }
      AgentRuntimeGovernanceLease lease = governanceService.begin(candidate.agentKey(), candidate.context(), preparedRun);
      return RuntimeRun.active(
          request(preparedRun, preparedRequest, lease.metadata()),
          execution,
          lease,
          preparedRequest.runResource());
    } catch (RuntimeException failure) {
      try {
        if (request != null && execution != null) {
          loopEngine.failSubmission(request, execution, cancellationToken, failure);
        }
      } finally {
        preparedRequest.runResource().release();
      }
      throw failure;
    }
  }

  private AgentRunPreparationRequest preparationRequest(
      AgentInvocation<?> invocation,
      AgentDefinition<?> definition,
      AgentPreparedRequest preparedRequest,
      AgentLoopExecution execution
  ) {
    AgentInvocationContext context = invocation.context();
    return new AgentRunPreparationRequest(
        null,
        context.userId(),
        userMessage(preparedRequest.messages()),
        context.idempotencyKey(),
        systemPrompt(preparedRequest.messages()),
        preparedRequest.metadata(),
        Map.of(),
        definition.key().value(),
        context.mode(),
        parentRunId(context),
        context.parentStepIndex(),
        preparedRequest.retryOfRunId(),
        execution.maxSteps());
  }

  private AgentRequest request(
      PreparedAgentRun preparedRun,
      AgentPreparedRequest preparedRequest,
      Map<String, Object> governanceMetadata
  ) {
    Map<String, Object> metadata = new LinkedHashMap<>(preparedRun.metadata());
    metadata.putAll(preparedRequest.metadata());
    metadata.putAll(preparedRun.identityMetadata());
    if (governanceMetadata != null) {
      metadata.putAll(governanceMetadata);
    }
    return new AgentRequest(
        preparedRun.runUuid(),
        preparedRun.requestId(),
        preparedRequest.messages(),
        Map.copyOf(metadata),
        preparedRequest.executionOptions());
  }

  private RunOutcome runLoop(
      RuntimeRun run,
      AgentStreamEventSink downstream,
      AgentCancellationToken cancellationToken
  ) {
    if (run.idempotentReplay()) {
      return replay(run, downstream);
    }
    OutcomeCollector collector = new OutcomeCollector();
    AgentRunResult result;
    try {
      result = loopEngine.run(run.request(), run.execution(), event -> {
        collector.capture(event);
        return downstream.emit(event);
      }, cancellationToken);
    } catch (RuntimeException failure) {
      settleFailure(run, collector, failure, cancellationToken.isCancelled());
      return new RunOutcome(null, toAgentException(failure));
    }
    AgentException error = collector.error();
    if (result != null) {
      settleSuccess(run, collector);
      return new RunOutcome(withRuntimeResultMetadata(result, run.request().metadata(), collector), null);
    }
    if (error == null && cancellationToken.isCancelled()) {
      error = cancelled(null);
    }
    if (error == null) {
      error = new AgentException(AgentErrorCode.UNKNOWN, "Agent run ended without a terminal result", false, Map.of(), null);
    }
    settleFailure(run, collector, error, error.code() == AgentErrorCode.CANCELLED);
    return new RunOutcome(null, error);
  }

  private RunOutcome replay(RuntimeRun run, AgentStreamEventSink downstream) {
    try {
      downstream.emit(new AgentStreamEvent.AgentRunStart(
          run.request().runId(),
          run.request().displayTitle(),
          run.execution().maxSteps(),
          run.request().metadata()));
      downstream.emit(new AgentStreamEvent.AgentRunEnd(
          run.request().runId(),
          1,
          LlmFinishReason.UNKNOWN,
          run.request().metadata()));
      return new RunOutcome(new AgentRunResult(1, LlmFinishReason.UNKNOWN, run.request().metadata()), null);
    } catch (RuntimeException failure) {
      return new RunOutcome(null, toAgentException(failure));
    } finally {
      run.releaseResource();
    }
  }

  private void failSubmission(RuntimeRun run, AgentCancellationToken cancellationToken, Throwable failure) {
    try {
      loopEngine.failSubmission(run.request(), run.execution(), cancellationToken, failure);
    } finally {
      settleFailure(run, new OutcomeCollector(), failure, cancellationToken.isCancelled());
    }
  }

  private void settleSuccess(RuntimeRun run, OutcomeCollector collector) {
    try {
      run.lease().complete(toAiUsage(collector.usage()), collector.provider(), collector.model());
    } finally {
      run.releaseResource();
    }
  }

  private AgentRunResult withRuntimeResultMetadata(
      AgentRunResult result,
      Map<String, Object> requestMetadata,
      OutcomeCollector collector
  ) {
    Map<String, Object> metadata = new LinkedHashMap<>(requestMetadata);
    metadata.putAll(result.metadata());
    metadata.put(AgentRuntimeMetadataKeys.RUNTIME_USAGE, collector.usage());
    if (collector.provider() != null && !collector.provider().isBlank()) {
      metadata.put(AgentRuntimeMetadataKeys.RUNTIME_PROVIDER, collector.provider());
    }
    if (collector.model() != null && !collector.model().isBlank()) {
      metadata.put(AgentRuntimeMetadataKeys.RUNTIME_MODEL, collector.model());
    }
    return new AgentRunResult(result.steps(), result.finishReason(), result.output(), Map.copyOf(metadata));
  }

  private void settleFailure(
      RuntimeRun run,
      OutcomeCollector collector,
      Throwable failure,
      boolean cancelled
  ) {
    if (cancelled || failure instanceof AgentException agentException
        && agentException.code() == AgentErrorCode.CANCELLED) {
      try {
        run.lease().cancel(toAiUsage(collector.usage()), collector.provider(), collector.model());
      } finally {
        run.releaseResource();
      }
      return;
    }
    try {
      run.lease().fail(errorCode(failure), toAiUsage(collector.usage()), collector.provider(), collector.model());
    } finally {
      run.releaseResource();
    }
  }

  private AgentRunResult requireSuccess(RunOutcome outcome) {
    if (outcome != null && outcome.result() != null) {
      return outcome.result();
    }
    if (outcome != null && outcome.error() != null) {
      throw outcome.error();
    }
    throw new AgentException(AgentErrorCode.UNKNOWN, "Agent execution did not produce a result", false, Map.of(), null);
  }

  private static Long parentRunId(AgentInvocationContext context) {
    if (context.parentRunId() == null) {
      return null;
    }
    try {
      return Long.parseLong(context.parentRunId());
    } catch (NumberFormatException exception) {
      throw new IllegalArgumentException("Agent invocation parent run id must be a database id", exception);
    }
  }

  private static String systemPrompt(List<LlmMessage> messages) {
    return messages.stream()
        .filter(message -> message.role() == LlmMessage.Role.SYSTEM)
        .map(LlmMessage::text)
        .findFirst()
        .orElse("");
  }

  private static String userMessage(List<LlmMessage> messages) {
    return messages.stream()
        .filter(message -> message.role() == LlmMessage.Role.USER)
        .reduce((first, second) -> second)
        .map(LlmMessage::text)
        .filter(value -> !value.isBlank())
        .orElseThrow(() -> new IllegalArgumentException("Agent Definition must prepare a user message"));
  }

  private static AiUsage toAiUsage(LlmUsage usage) {
    if (usage == null) {
      return AiUsage.zero();
    }
    return new AiUsage(
        usage.inputTokens(),
        usage.outputTokens(),
        usage.cachedTokens(),
        usage.reasoningTokens(),
        usage.totalTokens());
  }

  private static AiGovernanceErrorCode errorCode(Throwable failure) {
    if (failure instanceof AgentException exception && exception.getCause() != null) {
      return AiRunGovernanceService.errorCode(exception.getCause());
    }
    return AiRunGovernanceService.errorCode(failure);
  }

  private static AgentException toAgentException(Throwable failure) {
    if (failure instanceof AgentException exception) {
      return exception;
    }
    if (failure instanceof AgentExecutionRejectedException rejected) {
      AgentErrorCode code = rejected.reason() == AgentExecutionRejectionReason.SHUTDOWN
          ? AgentErrorCode.AGENT_EXECUTOR_SHUTDOWN
          : AgentErrorCode.AGENT_EXECUTOR_OVERLOADED;
      return new AgentException(code, rejected.getMessage(), true, Map.of(), rejected);
    }
    if (failure instanceof RejectedExecutionException rejected) {
      return new AgentException(
          AgentErrorCode.AGENT_EXECUTOR_OVERLOADED,
          "Agent executor rejected the run",
          true,
          Map.of(),
          rejected);
    }
    String message = failure == null || failure.getMessage() == null ? "Agent execution failed" : failure.getMessage();
    return new AgentException(AgentErrorCode.UNKNOWN, message, false, Map.of(), failure);
  }

  private static AgentException cancelled(Throwable cause) {
    return new AgentException(AgentErrorCode.CANCELLED, "Agent run was cancelled", false, Map.of(), cause);
  }

  private static final class RuntimeRun {

    private final AgentRequest request;
    private final AgentLoopExecution execution;
    private final AgentRuntimeGovernanceLease lease;
    private final AgentRunResource runResource;
    private final boolean idempotentReplay;
    private final AtomicBoolean resourceReleased = new AtomicBoolean();

    private RuntimeRun(
        AgentRequest request,
        AgentLoopExecution execution,
        AgentRuntimeGovernanceLease lease,
        AgentRunResource runResource,
        boolean idempotentReplay
    ) {
      this.request = request;
      this.execution = execution;
      this.lease = lease;
      this.runResource = runResource;
      this.idempotentReplay = idempotentReplay;
    }

    private static RuntimeRun active(
        AgentRequest request,
        AgentLoopExecution execution,
        AgentRuntimeGovernanceLease lease,
        AgentRunResource runResource
    ) {
      return new RuntimeRun(request, execution, lease, runResource, false);
    }

    private static RuntimeRun replay(
        AgentRequest request,
        AgentLoopExecution execution,
        AgentRunResource runResource
    ) {
      return new RuntimeRun(request, execution, null, runResource, true);
    }

    private AgentRequest request() {
      return request;
    }

    private AgentLoopExecution execution() {
      return execution;
    }

    private AgentRuntimeGovernanceLease lease() {
      return lease;
    }

    private boolean idempotentReplay() {
      return idempotentReplay;
    }

    private void releaseResource() {
      if (resourceReleased.compareAndSet(false, true)) {
        runResource.release();
      }
    }
  }

  private record RunOutcome(AgentRunResult result, AgentException error) {
  }

  private static final class OutcomeCollector {

    private LlmUsage usage = LlmUsage.empty();
    private String provider;
    private String model;
    private AgentException error;

    private void capture(AgentStreamEvent event) {
      if (event instanceof AgentStreamEvent.Llm llm) {
        if (llm.event() instanceof LlmStreamEvent.MessageStart start) {
          provider = start.provider().value();
          model = start.model().value();
        } else if (llm.event() instanceof LlmStreamEvent.Usage usageEvent) {
          usage = usageEvent.usage();
        }
      } else if (event instanceof AgentStreamEvent.AgentError agentError) {
        error = agentError.error();
      }
    }

    private LlmUsage usage() {
      return usage;
    }

    private String provider() {
      return provider;
    }

    private String model() {
      return model;
    }

    private AgentException error() {
      return error;
    }
  }
}
