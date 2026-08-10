package org.congcong.algomentor.agent.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicReference;
import org.congcong.algomentor.agent.core.compaction.RunMessageCompactionResult;
import org.congcong.algomentor.agent.core.compaction.RunMessageCompactor;
import org.congcong.algomentor.agent.core.compaction.ToolResultCompaction;
import org.congcong.algomentor.agent.core.compaction.ToolResultCompactor;
import org.congcong.algomentor.agent.core.permission.AgentToolPermissionAuthorization;
import org.congcong.algomentor.agent.core.permission.AgentToolPermissionGuard;
import org.congcong.algomentor.agent.core.permission.AgentToolPermissionHookChain;
import org.congcong.algomentor.agent.core.permission.AgentToolPermissionResultFactory;
import org.congcong.algomentor.agent.core.permission.InMemoryAgentToolPermissionCoordinator;
import org.congcong.algomentor.agent.core.runtime.model.AgentRuntimeMetadataKeys;
import org.congcong.algomentor.agent.core.structuredoutput.AgentStructuredOutputValidator;
import org.congcong.algomentor.agent.core.structuredoutput.StructuredOutputRepairPrompt;
import org.congcong.algomentor.agent.core.structuredoutput.StructuredOutputRepairEvent;
import org.congcong.algomentor.agent.core.structuredoutput.StructuredOutputValidationError;
import org.congcong.algomentor.agent.core.structuredoutput.StructuredOutputValidationResult;
import org.congcong.algomentor.agent.core.structuredoutput.StructuredOutputValidationStatus;
import org.congcong.algomentor.llm.core.exception.LlmException;
import org.congcong.algomentor.llm.core.gateway.LlmGateway;
import org.congcong.algomentor.llm.core.provider.LlmProviderContinuation;
import org.congcong.algomentor.llm.core.request.LlmCompletionRequest;
import org.congcong.algomentor.llm.core.request.LlmGenerationOptions;
import org.congcong.algomentor.llm.core.request.LlmMessage;
import org.congcong.algomentor.llm.core.request.LlmReasoningEffort;
import org.congcong.algomentor.llm.core.request.LlmResponseFormat;
import org.congcong.algomentor.llm.core.response.LlmFinishReason;
import org.congcong.algomentor.llm.core.stream.LlmStreamEvent;
import org.congcong.algomentor.llm.core.tool.LlmToolCall;
import org.congcong.algomentor.llm.core.tool.LlmToolChoice;

/**
 * 可在调用线程同步执行的 Agent loop 控制流。
 *
 * <p>线程调度、订阅和背压由外层适配器负责；本类只维护 run、step、工具、终态和生命周期事件语义。</p>
 */
public final class AgentLoopEngine {
  private final LlmGateway llmGateway;
  private final AgentLlmRequestFactory requestFactory;
  private final List<AgentLoopObserver> observers;
  private final List<AgentLoopInterceptor> interceptors;
  private final ToolResultCompactor toolResultCompactor;
  private final RunMessageCompactor runMessageCompactor;
  private final ObjectMapper objectMapper;
  private final AgentStructuredOutputValidator structuredOutputValidator;
  private final AgentToolPermissionGuard permissionGuard;

  public AgentLoopEngine(
      LlmGateway llmGateway,
      AgentLlmRequestFactory requestFactory,
      List<AgentLoopObserver> observers,
      List<AgentLoopInterceptor> interceptors,
      ToolResultCompactor toolResultCompactor,
      RunMessageCompactor runMessageCompactor,
      ObjectMapper objectMapper,
      AgentToolPermissionGuard permissionGuard
  ) {
    this.llmGateway = Objects.requireNonNull(llmGateway, "llmGateway must not be null");
    this.requestFactory = Objects.requireNonNull(requestFactory, "agent LLM request factory must not be null");
    this.observers = observers == null ? List.of() : List.copyOf(observers);
    this.interceptors = interceptors == null ? List.of() : List.copyOf(interceptors);
    this.toolResultCompactor = Objects.requireNonNull(
        toolResultCompactor, "tool result compactor must not be null");
    this.runMessageCompactor = Objects.requireNonNull(
        runMessageCompactor, "run message compactor must not be null");
    this.objectMapper = Objects.requireNonNull(objectMapper, "object mapper must not be null");
    this.structuredOutputValidator = new AgentStructuredOutputValidator(objectMapper);
    this.permissionGuard = Objects.requireNonNull(permissionGuard, "permission guard must not be null");
  }

  public void failSubmission(
      AgentRequest request,
      AgentLoopExecution execution,
      AgentCancellationToken cancellationToken,
      Throwable failure
  ) {
    AgentLoopContext context = newContext(
        Objects.requireNonNull(request, "request must not be null"),
        Objects.requireNonNull(execution, "agent loop execution must not be null"),
        Objects.requireNonNull(cancellationToken, "cancellation token must not be null"));
    AgentLoopLifecycle lifecycle = new AgentLoopLifecycle(event -> true, observers, interceptors, permissionGuard);
    lifecycle.error(context, toAgentException(failure));
  }

  public AgentRunResult run(
      AgentRequest request,
      AgentLoopExecution execution,
      AgentStreamEventSink eventSink,
      AgentCancellationToken cancellationToken
  ) {
    // runId 由上游传入时用于恢复/串联已有会话，否则本地生成，保证每个流式事件都有稳定关联键。
    AgentLoopExecution currentExecution = Objects.requireNonNull(execution, "agent loop execution must not be null");
    AgentLoopContext context = newContext(request, currentExecution, cancellationToken);
    AgentLoopLifecycle lifecycle = new AgentLoopLifecycle(eventSink, observers, interceptors, permissionGuard);
    try {
      lifecycle.runStarted(context);
      // messages 是本次 run 内的可变工作上下文：初始用户/系统消息、assistant tool_calls、tool result 都按顺序追加。
      List<LlmMessage> messages = new ArrayList<>(requestFactory.initialMessages(request));
      for (int stepIndex = 1; stepIndex <= currentExecution.maxSteps(); stepIndex++) {
        throwIfCancelled(context);
        AgentStepResult stepResult = runStep(context, currentExecution, stepIndex, messages, lifecycle);
        throwIfCancelled(context);
        if (!stepResult.requiresTools()) {
          // 无工具调用表示模型已经给出最终输出；正文 token 已在 runStep 中作为流式事件透传给客户端。
          FinalOutputResult finalOutput = finalizeOutput(
              context,
              request,
              stepIndex,
              stepResult,
              lifecycle);
          lifecycle.finalOutput(context, finalOutput.output());
          AgentRunResult runResult = new AgentRunResult(
              finalOutput.steps(),
              finalOutput.finishReason(),
              finalOutput.output(),
              finalOutput.metadata());
          lifecycle.runEnded(context, runResult);
          return runResult;
        }
        List<LlmToolCall> effectiveToolCalls = new ArrayList<>();
        for (LlmToolCall toolCall : stepResult.toolCalls()) {
          throwIfCancelled(context);
          effectiveToolCalls.add(lifecycle.beforeToolCall(context, stepIndex, toolCall));
        }
        // 必须记录改写后的 assistant tool_calls。LLM tool-calling 协议要求后续 tool message 能通过
        // toolCallId 对应到上一条 assistant 消息，否则下一轮请求会丢失“模型为什么调用工具”的上下文。
        //   一轮工具调用的上下文通常必须长这样：
        //
        //  user: 请帮我查一下...
        //  assistant: 我需要调用工具 fake_lookup，参数是 {...}
        //  tool: call_1 的执行结果是 {...}
        //  assistant: 根据工具结果，最终答案是...
        //  对应到上面的案例，这里其实是向message里添加 我需要调用工具 fake_lookup，参数是 {...}
        messages.add(LlmMessage.assistantToolCalls(effectiveToolCalls, stepResult.providerContinuation()));
        for (LlmToolCall toolCall : effectiveToolCalls) {
          throwIfCancelled(context);
          AgentTool tool = currentExecution.toolRegistry().find(toolCall.name())
              .orElseThrow(() -> toolNotAvailable(currentExecution, toolCall));
          AgentToolPermissionAuthorization authorization = lifecycle.beforeToolExecution(
              context,
              stepIndex,
              toolCall,
              tool);
          JsonNode result;
          if (authorization instanceof AgentToolPermissionAuthorization.Allowed) {
            lifecycle.toolStarted(context, stepIndex, toolCall);
            result = executeTool(context, stepIndex, toolCall, tool, lifecycle);
            throwIfCancelled(context);
          } else if (authorization instanceof AgentToolPermissionAuthorization.SyntheticResult syntheticResult) {
            result = syntheticResult.result();
          } else {
            throw new AgentException(
                AgentErrorCode.UNKNOWN,
                "Unsupported agent tool permission authorization",
                false,
                Map.of(
                    AgentRuntimeMetadataKeys.TOOL_NAME, toolCall.name(),
                    AgentRuntimeMetadataKeys.TOOL_CALL_ID, toolCall.id()),
                null);
          }
          result = lifecycle.afterToolCall(context, stepIndex, toolCall, result);
          lifecycle.toolEnded(context, stepIndex, toolCall, result);
          ToolResultCompaction compaction = toolResultCompactor.compactForModel(context, stepIndex, toolCall, result);
          // 下一轮模型只需要“可用于推理的结果”，不一定需要完整原始 payload。压缩层负责在准确性和上下文预算间取舍。
          // 这里对应上面案例的 tool: call_1 的执行结果是 {...}
          messages.add(LlmMessage.toolResult(toolCall.id(), compaction.visibleResult()));
        }
      }
      // 走到这里说明每一步都继续要求工具，但已经耗尽 maxSteps。此时不能再默默请求模型，否则会破坏成本和时延边界。
      lifecycle.error(context, new AgentException(
          AgentErrorCode.MAX_STEPS_EXCEEDED,
          "Agent loop exceeded max steps",
          false,
          Map.of(AgentRuntimeMetadataKeys.MAX_STEPS, currentExecution.maxSteps()),
          null));
      return null;
    } catch (AgentException ex) {
      lifecycle.error(context, ex);
      return null;
    } catch (RuntimeException ex) {
      lifecycle.error(context, new AgentException(AgentErrorCode.UNKNOWN, "Agent loop failed", false, Map.of(), ex));
      return null;
    }
  }

  private AgentLoopContext newContext(
      AgentRequest request,
      AgentLoopExecution execution,
      AgentCancellationToken cancellationToken
  ) {
    int maxStepsWithStructuredOutputRepair = execution.maxSteps()
        + request.executionOptions().structuredOutput().maxRepairAttempts();
    return new AgentLoopContext(
        request.runId() == null ? UUID.randomUUID().toString() : request.runId(),
        request,
        maxStepsWithStructuredOutputRepair,
        request.metadata(),
        cancellationToken);
  }

  private AgentException toolNotAvailable(AgentLoopExecution execution, LlmToolCall toolCall) {
    AgentErrorCode errorCode = execution.isRegisteredTool(toolCall.name())
        ? AgentErrorCode.TOOL_NOT_ALLOWED
        : AgentErrorCode.UNKNOWN_TOOL;
    String message = errorCode == AgentErrorCode.TOOL_NOT_ALLOWED
        ? "Agent tool is not allowed for this run: " + toolCall.name()
        : "Unknown agent tool: " + toolCall.name();
    return new AgentException(
        errorCode,
        message,
        false,
        Map.of(
            AgentRuntimeMetadataKeys.TOOL_NAME, toolCall.name(),
            AgentRuntimeMetadataKeys.TOOL_CALL_ID, toolCall.id()),
        null);
  }

  private void throwIfCancelled(AgentLoopContext context) {
    if (context.cancelled() || Thread.currentThread().isInterrupted()) {
      Thread.currentThread().interrupt();
      throw new AgentException(
          AgentErrorCode.CANCELLED,
          "Agent run was cancelled",
          false,
          Map.of(AgentRuntimeMetadataKeys.CANCELLATION_REASON, AgentCancellationToken.STREAM_CANCELLED_REASON),
          null);
    }
  }

  /**
   * 执行单个工具并把异常统一映射为 Agent 语义。
   *
   * <p>工具实现允许直接抛出 {@link AgentException} 表达业务可识别错误；其他运行时异常会被包成
   * {@link AgentErrorCode#TOOL_EXECUTION_FAILED}，并带上 toolName/toolCallId，便于 API 层、日志和观测系统定位。</p>
   */
  private com.fasterxml.jackson.databind.JsonNode executeTool(
      AgentLoopContext context,
      int stepIndex,
      LlmToolCall toolCall,
      AgentTool tool,
      AgentLoopLifecycle lifecycle
  ) {
    try {
          return tool.execute(
          toolCall.arguments(),
          new AgentExecutionContext(
              context.runId(),
              stepIndex,
              toolCall.id(),
              context.request().metadata(),
              context.cancelled()));
    } catch (AgentException ex) {
      AgentException error = enrichToolError(toolCall, ex);
      lifecycle.toolErrored(context, stepIndex, toolCall, error);
      throw error;
    } catch (RuntimeException ex) {
      AgentException error = new AgentException(
          AgentErrorCode.TOOL_EXECUTION_FAILED,
          "Agent tool execution failed: " + toolCall.name(),
          false,
          toolErrorMetadata(toolCall, ex, Map.of()),
          ex);
      lifecycle.toolErrored(context, stepIndex, toolCall, error);
      throw error;
    }
  }

  private AgentException enrichToolError(LlmToolCall toolCall, AgentException error) {
    Map<String, Object> metadata = toolErrorMetadata(toolCall, error, error.metadata());
    if (metadata.equals(error.metadata())) {
      return error;
    }
    return new AgentException(error.code(), error.getMessage(), error.retryable(), metadata, error.getCause());
  }

  private Map<String, Object> toolErrorMetadata(
      LlmToolCall toolCall,
      Throwable error,
      Map<String, Object> existingMetadata
  ) {
    Map<String, Object> metadata = new java.util.LinkedHashMap<>();
    if (existingMetadata != null) {
      metadata.putAll(existingMetadata);
    }
    metadata.putIfAbsent(AgentRuntimeMetadataKeys.TOOL_NAME, toolCall.name());
    metadata.putIfAbsent(AgentRuntimeMetadataKeys.TOOL_CALL_ID, toolCall.id());
    putIfAbsent(metadata, AgentRuntimeMetadataKeys.ERROR_TYPE, error.getClass().getName());
    putIfAbsent(metadata, AgentRuntimeMetadataKeys.ERROR_MESSAGE, error.getMessage());
    if (error.getCause() != null) {
      putIfAbsent(metadata, AgentRuntimeMetadataKeys.CAUSE_TYPE, error.getCause().getClass().getName());
      putIfAbsent(metadata, AgentRuntimeMetadataKeys.CAUSE_MESSAGE, error.getCause().getMessage());
    }
    Throwable rootCause = rootCause(error);
    putIfAbsent(metadata, AgentRuntimeMetadataKeys.ROOT_CAUSE_TYPE, rootCause.getClass().getName());
    putIfAbsent(metadata, AgentRuntimeMetadataKeys.ROOT_CAUSE_MESSAGE, rootCause.getMessage());
    return Map.copyOf(metadata);
  }

  private void putIfAbsent(Map<String, Object> metadata, String key, String value) {
    if (value != null && !value.isBlank()) {
      metadata.putIfAbsent(key, value);
    }
  }

  private Throwable rootCause(Throwable throwable) {
    Throwable current = throwable;
    while (current.getCause() != null) {
      current = current.getCause();
    }
    return current;
  }

  /**
   * 执行一次“向模型发请求并收集结果”的 step。
   *
   * <p>每一步请求前先做 run-local 消息压缩，原因是同一次 run 可能累积多轮工具结果；
   * 如果不在请求边界收敛上下文，长工具输出会持续放大后续每次 LLM 调用的 token 成本。
   * 压缩产生的 metadata 会合并进请求 metadata，让下游 provider、observer 或持久化层可以知道本次请求是否发生过裁剪。</p>
   *
   * <p>interceptor 先于 {@code llmRequestReady} 执行，observer 看到的是最终将发送给 gateway 的请求。
   * 这个顺序对审计和持久化很重要：记录下来的请求必须等价于真实出站请求。</p>
   */
  private AgentStepResult runStep(
      AgentLoopContext context,
      AgentLoopExecution execution,
      int stepIndex,
      List<LlmMessage> messages,
      AgentLoopLifecycle lifecycle
  ) {
    lifecycle.stepStarted(context, stepIndex);
    RunMessageCompactionResult compaction = runMessageCompactor.compactBeforeRequest(context, stepIndex, messages);
    messages.clear();
    messages.addAll(compaction.messages());
    Map<String, Object> requestMetadata = mergedMetadata(context.request().metadata(), compaction.metadata());
    LlmCompletionRequest llmRequest = lifecycle.beforeLlmRequest(context, stepIndex, requestFactory.build(
        context.request(),
        stepIndex,
        messages,
        execution.toolRegistry().specs(),
        execution.toolChoice(),
        requestMetadata));
    return executeLlmRequest(context, stepIndex, lifecycle, llmRequest);
  }

  private AgentStepResult executeLlmRequest(
      AgentLoopContext context,
      int stepIndex,
      AgentLoopLifecycle lifecycle,
      LlmCompletionRequest llmRequest
  ) {
    lifecycle.llmRequestReady(context, stepIndex, llmRequest);
    StepCollector collector = new StepCollector(context, stepIndex, lifecycle);
    try {
      llmGateway.stream(llmRequest).subscribe(collector);
      collector.await();
    } catch (RuntimeException ex) {
      throw toAgentException(ex);
    }
    throwIfCancelled(context);
    if (collector.error.get() != null) {
      throw toAgentException(collector.error.get());
    }
    AgentStepResult result = collector.result();
    lifecycle.stepEnded(context, stepIndex, result);
    return result;
  }

  /**
   * 合并请求 metadata 和运行时新增 metadata。
   *
   * <p>使用 copy 后的不可变 Map 返回，避免后续 interceptor、observer 或 provider 无意修改上游请求对象。
   * 当新增字段和基础字段同名时，新增字段覆盖基础字段，因为它描述的是更靠近实际出站请求的运行时事实。</p>
   */
  private Map<String, Object> mergedMetadata(Map<String, Object> base, Map<String, Object> additions) {
    if (additions == null || additions.isEmpty()) {
      return base == null ? Map.of() : base;
    }
    Map<String, Object> merged = new java.util.LinkedHashMap<>();
    if (base != null) {
      merged.putAll(base);
    }
    merged.putAll(additions);
    return Map.copyOf(merged);
  }

  /**
   * 把底层异常转换成 Agent 层错误模型。
   *
   * <p>LLM provider 抛出的 {@link LlmException} 会保留 retryable 和 metadata，方便 API 层决定是否提示重试；
   * 已经是 {@link AgentException} 的错误不重复包装，避免丢失更精确的错误码。</p>
   */
  private AgentException toAgentException(Throwable throwable) {
    if (throwable instanceof AgentException agentException) {
      return agentException;
    }
    if (throwable instanceof LlmException llmException) {
      return new AgentException(
          AgentErrorCode.LLM_STREAM_FAILED,
          llmException.getMessage(),
          llmException.retryable(),
          llmException.metadata(),
          llmException);
    }
    return new AgentException(AgentErrorCode.UNKNOWN, "Agent loop failed", false, Map.of(), throwable);
  }

  private FinalOutputResult finalizeOutput(
      AgentLoopContext context,
      AgentRequest request,
      int finalStepIndex,
      AgentStepResult initialStepResult,
      AgentLoopLifecycle lifecycle
  ) {
    AgentExecutionOptions executionOptions = request.executionOptions();
    AgentStructuredOutputOptions structuredOutput = executionOptions.structuredOutput();
    if (!isJsonResponseFormat(executionOptions.responseFormat())) {
      AgentOutput output = buildFinalOutput(request, initialStepResult.content(), null, 0, null, null);
      return new FinalOutputResult(
          output,
          finalStepIndex,
          initialStepResult.finishReason(),
          Map.of());
    }

    String candidateContent = initialStepResult.content();
    LlmFinishReason finishReason = initialStepResult.finishReason();
    StructuredOutputValidationResult validation = validateStructuredOutput(request, candidateContent);
    if (validation.valid()) {
      AgentOutput output = buildFinalOutput(
          request,
          candidateContent,
          validation.structuredOutput(),
          0,
          null,
          null);
      return new FinalOutputResult(output, finalStepIndex, finishReason, structuredOutputRunMetadata(0));
    }
    if (!structuredOutput.required()) {
      AgentOutput output = buildFinalOutput(
          request,
          candidateContent,
          null,
          0,
          null,
          validation.error());
      return new FinalOutputResult(output, finalStepIndex, finishReason, structuredOutputRunMetadata(0));
    }

    StructuredOutputValidationError initialError = validation.error();
    int repairAttempts = 0;
    while (repairAttempts < structuredOutput.maxRepairAttempts()) {
      repairAttempts++;
      int repairStepIndex = finalStepIndex + repairAttempts;
      StructuredOutputValidationError triggeringError = validation.error();
      lifecycle.structuredOutputRepair(
          context,
          structuredOutputRepairEvent(
              repairStepIndex,
              repairAttempts,
              structuredOutput.maxRepairAttempts(),
              triggeringError,
              StructuredOutputRepairEvent.Outcome.TRIGGERED));
      AgentStepResult repairResult;
      try {
        repairResult = runStructuredOutputRepairStep(
            context,
            request,
            repairStepIndex,
            repairAttempts,
            candidateContent,
            triggeringError,
            lifecycle);
      } catch (RuntimeException exception) {
        lifecycle.structuredOutputRepair(
            context,
            structuredOutputRepairEvent(
                repairStepIndex,
                repairAttempts,
                structuredOutput.maxRepairAttempts(),
                triggeringError,
                StructuredOutputRepairEvent.Outcome.FAILED));
        throw exception;
      }
      candidateContent = repairResult.content();
      finishReason = repairResult.finishReason();
      validation = repairResult.requiresTools()
          ? StructuredOutputValidationResult.invalid(new StructuredOutputValidationError(
              StructuredOutputValidationError.Type.UNEXPECTED_TOOL_CALL,
              "Structured-output repair returned tool calls even though tools were disabled",
              repairResult.toolCalls().stream().map(LlmToolCall::name).distinct().toList()))
          : validateStructuredOutput(request, candidateContent);
      lifecycle.structuredOutputRepair(
          context,
          structuredOutputRepairEvent(
              repairStepIndex,
              repairAttempts,
              structuredOutput.maxRepairAttempts(),
              triggeringError,
              validation.valid()
                  ? StructuredOutputRepairEvent.Outcome.SUCCEEDED
                  : StructuredOutputRepairEvent.Outcome.FAILED));
      if (validation.valid()) {
        AgentOutput output = buildFinalOutput(
            request,
            candidateContent,
            validation.structuredOutput(),
            repairAttempts,
            initialError,
            null);
        return new FinalOutputResult(
            output,
            repairStepIndex,
            finishReason,
            structuredOutputRunMetadata(repairAttempts));
      }
    }

    throw structuredOutputInvalid(structuredOutput, validation.error(), repairAttempts);
  }

  private StructuredOutputRepairEvent structuredOutputRepairEvent(
      int stepIndex,
      int repairAttempt,
      int maxRepairAttempts,
      StructuredOutputValidationError validationError,
      StructuredOutputRepairEvent.Outcome outcome
  ) {
    return new StructuredOutputRepairEvent(
        stepIndex,
        repairAttempt,
        maxRepairAttempts,
        validationError.type(),
        outcome);
  }

  private StructuredOutputValidationResult validateStructuredOutput(
      AgentRequest request,
      String content
  ) {
    try {
      return structuredOutputValidator.validate(request.executionOptions().responseFormat(), content);
    } catch (RuntimeException exception) {
      AgentStructuredOutputOptions structuredOutput = request.executionOptions().structuredOutput();
      throw new AgentException(
          AgentErrorCode.STRUCTURED_OUTPUT_SCHEMA_INVALID,
          "Agent structured output schema is invalid",
          false,
          structuredOutputSchemaMetadata(structuredOutput),
          exception);
    }
  }

  private AgentStepResult runStructuredOutputRepairStep(
      AgentLoopContext context,
      AgentRequest request,
      int stepIndex,
      int repairAttempt,
      String invalidOutput,
      StructuredOutputValidationError validationError,
      AgentLoopLifecycle lifecycle
  ) {
    lifecycle.stepStarted(context, stepIndex);
    Map<String, Object> repairMetadata = new LinkedHashMap<>();
    repairMetadata.put(AgentRuntimeMetadataKeys.STRUCTURED_OUTPUT_REPAIR_ATTEMPT, repairAttempt);
    repairMetadata.put(
        AgentRuntimeMetadataKeys.STRUCTURED_OUTPUT_FAILURE_TYPE,
        validationError.type().name());
    repairMetadata.put(
        AgentRuntimeMetadataKeys.STRUCTURED_OUTPUT_VALIDATION_ERROR,
        validationError.message());
    LlmCompletionRequest repairRequest = requestFactory.build(
        request,
        stepIndex,
        StructuredOutputRepairPrompt.messages(objectMapper, invalidOutput, validationError),
        List.of(),
        LlmToolChoice.none(),
        mergedMetadata(request.metadata(), repairMetadata))
        .withOptions(repairGenerationOptions(request.executionOptions().generationOptions()));
    repairRequest = lifecycle.beforeLlmRequest(context, stepIndex, repairRequest);
    return executeLlmRequest(context, stepIndex, lifecycle, repairRequest);
  }

  private LlmGenerationOptions repairGenerationOptions(LlmGenerationOptions source) {
    return new LlmGenerationOptions(
        0.0,
        null,
        source.maxOutputTokens(),
        List.of(),
        source.seed(),
        source.timeout(),
        LlmReasoningEffort.NONE);
  }

  private AgentOutput buildFinalOutput(
      AgentRequest request,
      String finalContent,
      JsonNode structured,
      int repairAttempts,
      StructuredOutputValidationError initialError,
      StructuredOutputValidationError finalError
  ) {
    AgentExecutionOptions executionOptions = request.executionOptions();
    AgentStructuredOutputOptions structuredOutput = executionOptions.structuredOutput();
    Map<String, Object> outputMetadata = new LinkedHashMap<>();
    outputMetadata.put(AgentRuntimeMetadataKeys.STRUCTURED_OUTPUT_STRATEGY, structuredOutput.strategy().name());
    outputMetadata.put(AgentRuntimeMetadataKeys.OUTPUT_CHAR_COUNT, finalContent == null ? 0 : finalContent.length());
    if (isJsonResponseFormat(executionOptions.responseFormat())) {
      outputMetadata.put(
          AgentRuntimeMetadataKeys.STRUCTURED_OUTPUT_VALIDATION_STATUS,
          (structured == null
              ? StructuredOutputValidationStatus.INVALID
              : StructuredOutputValidationStatus.VALID).name());
      outputMetadata.put(AgentRuntimeMetadataKeys.STRUCTURED_OUTPUT_REPAIR_ATTEMPTS, repairAttempts);
      outputMetadata.put(AgentRuntimeMetadataKeys.STRUCTURED_OUTPUT_REPAIRED, repairAttempts > 0);
      outputMetadata.put(
          AgentRuntimeMetadataKeys.STRUCTURED_OUTPUT_MAX_REPAIR_ATTEMPTS,
          structuredOutput.maxRepairAttempts());
      if (initialError != null) {
        outputMetadata.put(
            AgentRuntimeMetadataKeys.STRUCTURED_OUTPUT_INITIAL_FAILURE_TYPE,
            initialError.type().name());
      }
      if (finalError != null) {
        addValidationErrorMetadata(outputMetadata, finalError);
      }
    }
    return new AgentOutput(
        finalContent,
        structured,
        structuredOutput.schemaName(),
        structuredOutput.schemaVersion(),
        outputMetadata);
  }

  private boolean isJsonResponseFormat(LlmResponseFormat responseFormat) {
    return responseFormat instanceof LlmResponseFormat.JsonObject
        || responseFormat instanceof LlmResponseFormat.JsonSchema;
  }

  private AgentException structuredOutputInvalid(
      AgentStructuredOutputOptions structuredOutput,
      StructuredOutputValidationError validationError,
      int repairAttempts
  ) {
    Map<String, Object> metadata = new LinkedHashMap<>();
    addStructuredOutputContractMetadata(metadata, structuredOutput);
    addValidationErrorMetadata(metadata, validationError);
    metadata.put(AgentRuntimeMetadataKeys.STRUCTURED_OUTPUT_REPAIR_ATTEMPTS, repairAttempts);
    metadata.put(
        AgentRuntimeMetadataKeys.STRUCTURED_OUTPUT_MAX_REPAIR_ATTEMPTS,
        structuredOutput.maxRepairAttempts());
    return new AgentException(
        AgentErrorCode.STRUCTURED_OUTPUT_INVALID,
        "Agent structured output failed JSON or schema validation",
        false,
        Map.copyOf(metadata),
        null);
  }

  private Map<String, Object> structuredOutputSchemaMetadata(
      AgentStructuredOutputOptions structuredOutput
  ) {
    Map<String, Object> metadata = new LinkedHashMap<>();
    addStructuredOutputContractMetadata(metadata, structuredOutput);
    return Map.copyOf(metadata);
  }

  private void addStructuredOutputContractMetadata(
      Map<String, Object> metadata,
      AgentStructuredOutputOptions structuredOutput
  ) {
    if (structuredOutput.schemaName() != null) {
      metadata.put(AgentRuntimeMetadataKeys.SCHEMA_NAME, structuredOutput.schemaName());
    }
    if (structuredOutput.schemaVersion() != null) {
      metadata.put(AgentRuntimeMetadataKeys.SCHEMA_VERSION, structuredOutput.schemaVersion());
    }
    metadata.put(AgentRuntimeMetadataKeys.STRUCTURED_OUTPUT_STRATEGY, structuredOutput.strategy().name());
  }

  private void addValidationErrorMetadata(
      Map<String, Object> metadata,
      StructuredOutputValidationError validationError
  ) {
    metadata.put(AgentRuntimeMetadataKeys.STRUCTURED_OUTPUT_FAILURE_TYPE, validationError.type().name());
    metadata.put(AgentRuntimeMetadataKeys.STRUCTURED_OUTPUT_VALIDATION_ERROR, validationError.message());
    if (!validationError.details().isEmpty()) {
      metadata.put(AgentRuntimeMetadataKeys.STRUCTURED_OUTPUT_VALIDATION_ERRORS, validationError.details());
    }
    if (validationError.type() == StructuredOutputValidationError.Type.JSON_PARSE) {
      metadata.put(AgentRuntimeMetadataKeys.PARSE_ERROR, validationError.message());
    }
  }

  private Map<String, Object> structuredOutputRunMetadata(int repairAttempts) {
    return Map.of(
        AgentRuntimeMetadataKeys.STRUCTURED_OUTPUT_REPAIR_ATTEMPTS, repairAttempts,
        AgentRuntimeMetadataKeys.STRUCTURED_OUTPUT_REPAIRED, repairAttempts > 0);
  }

  private record FinalOutputResult(
      AgentOutput output,
      int steps,
      LlmFinishReason finishReason,
      Map<String, Object> metadata
  ) {
  }

  /**
   * 单个 LLM step 的流式事件收集器。
   *
   * <p>runner 需要一边把 LLM 事件实时转发给客户端，一边在流结束时知道本 step 是否产生了工具调用。
   * 因此 collector 在 {@link #onNext(LlmStreamEvent)} 中同步转发生命周期事件，并只保留驱动下一步所需的最小状态：
   * 工具调用列表、结束原因和错误引用。</p>
   */
  private static final class StepCollector implements Flow.Subscriber<LlmStreamEvent> {
    private final AgentLoopContext context;
    private final int stepIndex;
    private final AgentLoopLifecycle lifecycle;
    private final CountDownLatch done = new CountDownLatch(1);
    private final List<LlmToolCall> toolCalls = new ArrayList<>();
    private final StringBuilder content = new StringBuilder();
    private final AtomicReference<Throwable> error = new AtomicReference<>();
    private Flow.Subscription subscription;
    private LlmFinishReason finishReason = LlmFinishReason.UNKNOWN;
    private LlmProviderContinuation providerContinuation;

    private StepCollector(AgentLoopContext context, int stepIndex, AgentLoopLifecycle lifecycle) {
      this.context = context;
      this.stepIndex = stepIndex;
      this.lifecycle = lifecycle;
    }

    @Override
    public void onSubscribe(Flow.Subscription subscription) {
      // LLM 输出通常由 provider 控制节奏，这里一次性请求全部事件，避免本地 backpressure 让 SSE token 转发变得复杂。
      this.subscription = subscription;
      context.cancellationToken().llmSubscription(subscription);
      subscription.request(Long.MAX_VALUE);
    }

    @Override
    public void onNext(LlmStreamEvent item) {
      if (context.cancelled()) {
        return;
      }
      lifecycle.llmEvent(context, stepIndex, item);
      if (item instanceof LlmStreamEvent.ContentDelta delta) {
        content.append(delta.content());
      }
      if (item instanceof LlmStreamEvent.ToolCallEnd toolCallEnd) {
        toolCalls.add(toolCallEnd.toolCall());
      }
      if (item instanceof LlmStreamEvent.MessageEnd messageEnd) {
        finishReason = messageEnd.finishReason();
        providerContinuation = messageEnd.providerContinuation();
      }
      if (item instanceof LlmStreamEvent.Error llmError) {
        error.compareAndSet(null, llmError.error());
      }
    }

    @Override
    public void onError(Throwable throwable) {
      context.cancellationToken().clearLlmSubscription(subscription);
      error.compareAndSet(null, throwable);
      done.countDown();
    }

    @Override
    public void onComplete() {
      context.cancellationToken().clearLlmSubscription(subscription);
      done.countDown();
    }

    /**
     * 阻塞等待当前 step 的 LLM 流结束。
     *
     * <p>外层 runLoop 在专用后台线程执行，因此这里可以用 CountDownLatch 把异步 stream 汇聚成顺序控制流；
     * 这让“模型响应 -> 工具执行 -> 下一轮模型响应”的状态机更容易维护。被中断时恢复中断标记，并转成 CANCELLED
     * 事件交给统一错误出口处理。</p>
     */
    private void await() {
      try {
        done.await();
      } catch (InterruptedException ex) {
        Thread.currentThread().interrupt();
        throw new AgentException(
            AgentErrorCode.CANCELLED,
            "Agent run was cancelled",
            false,
            Map.of(AgentRuntimeMetadataKeys.CANCELLATION_REASON, AgentCancellationToken.STREAM_CANCELLED_REASON),
            ex);
      }
    }

    /**
     * 生成当前 step 的决策结果。
     *
     * <p>这里只返回驱动下一步和最终输出捕获所需的最小状态。只有不再需要工具调用的 step
     * content 才会被外层 runner 作为最终 assistant 输出。</p>
     */
    private AgentStepResult result() {
      return new AgentStepResult(toolCalls, finishReason, content.toString(), providerContinuation);
    }
  }
}
