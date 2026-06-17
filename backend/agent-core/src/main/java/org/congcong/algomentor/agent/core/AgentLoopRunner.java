package org.congcong.algomentor.agent.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Flow;
import java.util.concurrent.SubmissionPublisher;
import java.util.concurrent.atomic.AtomicReference;
import org.congcong.algomentor.agent.core.compaction.RunMessageCompactionResult;
import org.congcong.algomentor.agent.core.compaction.RunMessageCompactor;
import org.congcong.algomentor.agent.core.compaction.ToolResultCompaction;
import org.congcong.algomentor.agent.core.compaction.ToolResultCompactionPolicy;
import org.congcong.algomentor.agent.core.compaction.ToolResultCompactor;
import org.congcong.algomentor.agent.core.toolresult.InMemoryToolResultStore;
import org.congcong.algomentor.agent.core.toolresult.ToolResultStore;
import org.congcong.algomentor.llm.core.exception.LlmException;
import org.congcong.algomentor.llm.core.gateway.LlmGateway;
import org.congcong.algomentor.llm.core.model.LlmModelId;
import org.congcong.algomentor.llm.core.model.LlmModelSelector;
import org.congcong.algomentor.llm.core.request.LlmCompletionRequest;
import org.congcong.algomentor.llm.core.request.LlmMessage;
import org.congcong.algomentor.llm.core.response.LlmFinishReason;
import org.congcong.algomentor.llm.core.stream.LlmStreamEvent;
import org.congcong.algomentor.llm.core.tool.LlmToolCall;
import org.congcong.algomentor.llm.core.tool.LlmToolChoice;

/**
 * Agent 主循环编排器。
 *
 * <p>这个类只负责运行态的“控制流”：准备 LLM 请求、消费 LLM 流式事件、执行模型声明的工具调用、
 * 把工具结果回填到下一轮上下文，并通过 {@link AgentLoopLifecycle} 统一发出 SSE 事件和扩展点通知。
 * 它不直接绑定具体模型供应商、持久化实现或业务场景，因此 mentor-api、mentor-application 和后续 worker
 * 可以复用同一套 Agent loop 语义。</p>
 */
public class AgentLoopRunner {

  private final LlmGateway llmGateway;
  private final LlmModelSelector modelSelector;
  private final AgentToolRegistry toolRegistry;
  private final LlmToolChoice toolChoice;
  private final int maxSteps;
  private final List<AgentLoopObserver> observers;
  private final List<AgentLoopInterceptor> interceptors;
  private final ToolResultCompactor toolResultCompactor;
  private final RunMessageCompactor runMessageCompactor;

  @Deprecated(forRemoval = false)
  public AgentLoopRunner(LlmGateway llmGateway, String model, AgentToolRegistry toolRegistry, int maxSteps) {
    this(llmGateway, selectorFromModel(model), toolRegistry, maxSteps);
  }

  public AgentLoopRunner(
      LlmGateway llmGateway,
      LlmModelSelector modelSelector,
      AgentToolRegistry toolRegistry,
      int maxSteps
  ) {
    this(llmGateway, modelSelector, toolRegistry, null, maxSteps);
  }

  public AgentLoopRunner(
      LlmGateway llmGateway,
      LlmModelSelector modelSelector,
      AgentToolRegistry toolRegistry,
      LlmToolChoice toolChoice,
      int maxSteps
  ) {
    this(llmGateway, modelSelector, toolRegistry, toolChoice, maxSteps, List.of(), List.of());
  }

  public AgentLoopRunner(
      LlmGateway llmGateway,
      LlmModelSelector modelSelector,
      AgentToolRegistry toolRegistry,
      LlmToolChoice toolChoice,
      int maxSteps,
      List<AgentLoopObserver> observers,
      List<AgentLoopInterceptor> interceptors
  ) {
    this(
        llmGateway,
        modelSelector,
        toolRegistry,
        toolChoice,
        maxSteps,
        observers,
        interceptors,
        ToolResultCompactionPolicy.defaults(),
        new InMemoryToolResultStore(),
        new ObjectMapper());
  }

  public AgentLoopRunner(
      LlmGateway llmGateway,
      LlmModelSelector modelSelector,
      AgentToolRegistry toolRegistry,
      LlmToolChoice toolChoice,
      int maxSteps,
      List<AgentLoopObserver> observers,
      List<AgentLoopInterceptor> interceptors,
      ToolResultCompactionPolicy toolResultPolicy,
      ToolResultStore toolResultStore,
      ObjectMapper objectMapper
  ) {
    if (maxSteps < 1) {
      throw new IllegalArgumentException("Agent loop max steps must be positive");
    }
    this.llmGateway = Objects.requireNonNull(llmGateway, "llmGateway must not be null");
    this.modelSelector = Objects.requireNonNull(modelSelector, "modelSelector must not be null");
    this.toolRegistry = toolRegistry == null ? AgentToolRegistry.empty() : toolRegistry;
    this.toolChoice = toolChoice == null ? LlmToolChoice.auto() : toolChoice;
    this.maxSteps = maxSteps;
    this.observers = observers == null ? List.of() : List.copyOf(observers);
    this.interceptors = interceptors == null ? List.of() : List.copyOf(interceptors);
    ObjectMapper mapper = objectMapper == null ? new ObjectMapper() : objectMapper;
    ToolResultStore store = toolResultStore == null ? new InMemoryToolResultStore() : toolResultStore;
    ToolResultCompactionPolicy policy = toolResultPolicy == null ? ToolResultCompactionPolicy.defaults() : toolResultPolicy;
    this.toolResultCompactor = new ToolResultCompactor(mapper, policy, store);
    this.runMessageCompactor = new RunMessageCompactor(mapper, toolResultCompactor);
  }

  /**
   * 以 Reactive Streams 的 {@link Flow.Publisher} 形式启动一次 Agent run。
   *
   * <p>这里为每个订阅者创建独立的 {@link SubmissionPublisher} 和后台线程，而不是在调用方线程中直接运行。
   * 这样 API 层可以立即返回 Publisher，并把后续 LLM token、工具执行状态、错误和完成事件持续转成 SSE；
   * 同时一次订阅对应一次 run，避免多个客户端共享同一组可变消息上下文。</p>
   */
  public Flow.Publisher<AgentStreamEvent> stream(AgentRequest request) {
    Objects.requireNonNull(request, "request must not be null");
    return subscriber -> {
      SubmissionPublisher<AgentStreamEvent> publisher = new SubmissionPublisher<>();
      publisher.subscribe(subscriber);
      Thread worker = new Thread(() -> runLoop(request, publisher), "agent-loop-stream");
      worker.setDaemon(true);
      worker.start();
    };
  }

  /**
   * 执行完整的 Agent loop。
   *
   * <p>主循环遵循 ReAct/tool-calling 的基本节奏：</p>
   *
   * <ol>
   *   <li>用当前 messages 向 LLM 发起一次流式请求。</li>
   *   <li>如果 LLM 正常结束且没有工具调用，说明最终回答已经通过流式事件发给客户端，run 结束。</li>
   *   <li>如果 LLM 返回工具调用，则逐个执行工具，把工具结果追加为 tool message，再进入下一步。</li>
   * </ol>
   *
   * <p>几个关键设计点：</p>
   *
   * <ul>
   *   <li>{@code maxSteps} 是硬性熔断，防止模型反复要求工具调用导致无限循环、费用失控或 SSE 连接长期占用。</li>
   *   <li>所有对外事件和扩展点都经由 {@link AgentLoopLifecycle}，让 runner 保持编排职责，
   *       observer/interceptor/SSE 的顺序也集中在一个边界内维护。</li>
   *   <li>工具调用在真正执行前先经过 interceptor 改写，然后把“有效工具调用”写入 assistant tool_calls 消息。
   *       这样下一轮 LLM 看到的历史记录与实际执行的工具名称、参数保持一致，避免模型上下文和真实副作用不一致。</li>
   *   <li>工具返回值先允许 interceptor 做脱敏、裁剪或结构化改写，再通过 {@link ToolResultCompactor} 压缩后进入
   *       下一轮 LLM 上下文。完整结果仍可由 store 保存，模型只拿到预算内的可见结果，避免大 payload 挤占上下文窗口。</li>
   *   <li>无论成功、业务错误还是未知运行时异常，方法都会提交终态事件后关闭 publisher，保证 SSE 客户端不会悬挂等待。</li>
   * </ul>
   */
  private void runLoop(AgentRequest request, SubmissionPublisher<AgentStreamEvent> publisher) {
    // runId 由上游传入时用于恢复/串联已有会话，否则本地生成，保证每个流式事件都有稳定关联键。
    AgentLoopContext context = new AgentLoopContext(
        request.runId() == null ? UUID.randomUUID().toString() : request.runId(),
        request,
        maxSteps,
        request.metadata());
    AgentLoopLifecycle lifecycle = new AgentLoopLifecycle(publisher, observers, interceptors);
    // messages 是本次 run 内的可变工作上下文：初始用户/系统消息、assistant tool_calls、tool result 都按顺序追加。
    List<LlmMessage> messages = new ArrayList<>(AgentLlmRequestFactory.initialMessages(request));
    lifecycle.runStarted(context);
    try {
      for (int stepIndex = 1; stepIndex <= maxSteps; stepIndex++) {
        AgentStepResult stepResult = runStep(context, stepIndex, messages, lifecycle);
        if (!stepResult.requiresTools()) {
          // 无工具调用表示模型已经给出最终输出；正文 token 已在 runStep 中作为流式事件透传给客户端。
          lifecycle.runEnded(context, new AgentRunResult(
              stepIndex,
              stepResult.finishReason(),
              Map.of()));
          publisher.close();
          return;
        }
        List<LlmToolCall> effectiveToolCalls = new ArrayList<>();
        for (LlmToolCall toolCall : stepResult.toolCalls()) {
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
        messages.add(LlmMessage.assistantToolCalls(effectiveToolCalls));
        for (LlmToolCall toolCall : effectiveToolCalls) {
          AgentTool tool = toolRegistry.find(toolCall.name())
              .orElseThrow(() -> new AgentException(
                  AgentErrorCode.UNKNOWN_TOOL,
                  "Unknown agent tool: " + toolCall.name(),
                  false,
                  Map.of("toolName", toolCall.name(), "toolCallId", toolCall.id()),
                  null));
          lifecycle.toolStarted(context, stepIndex, toolCall);
          var result = executeTool(context, stepIndex, toolCall, tool, lifecycle);
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
          Map.of("maxSteps", maxSteps),
          null));
      publisher.close();
    } catch (AgentException ex) {
      lifecycle.error(context, ex);
      publisher.close();
    } catch (RuntimeException ex) {
      lifecycle.error(context, new AgentException(AgentErrorCode.UNKNOWN, "Agent loop failed", false, Map.of(), ex));
      publisher.close();
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
          new AgentExecutionContext(context.runId(), stepIndex, context.request().metadata(), false));
    } catch (AgentException ex) {
      lifecycle.toolErrored(context, stepIndex, toolCall, ex);
      throw ex;
    } catch (RuntimeException ex) {
      AgentException error = new AgentException(
          AgentErrorCode.TOOL_EXECUTION_FAILED,
          "Agent tool execution failed: " + toolCall.name(),
          false,
          Map.of("toolName", toolCall.name(), "toolCallId", toolCall.id()),
          ex);
      lifecycle.toolErrored(context, stepIndex, toolCall, error);
      throw error;
    }
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
      int stepIndex,
      List<LlmMessage> messages,
      AgentLoopLifecycle lifecycle
  ) {
    lifecycle.stepStarted(context, stepIndex);
    RunMessageCompactionResult compaction = runMessageCompactor.compactBeforeRequest(context, stepIndex, messages);
    messages.clear();
    messages.addAll(compaction.messages());
    Map<String, Object> requestMetadata = mergedMetadata(context.request().metadata(), compaction.metadata());
    LlmCompletionRequest llmRequest = lifecycle.beforeLlmRequest(context, stepIndex, AgentLlmRequestFactory.build(
        modelSelector,
        messages,
        toolRegistry.specs(),
        toolChoice,
        requestMetadata));
    lifecycle.llmRequestReady(context, stepIndex, llmRequest);
    StepCollector collector = new StepCollector(context, stepIndex, lifecycle);
    try {
      llmGateway.stream(llmRequest).subscribe(collector);
      collector.await();
    } catch (RuntimeException ex) {
      throw toAgentException(ex);
    }
    if (collector.error.get() != null) {
      throw toAgentException(collector.error.get());
    }
    AgentStepResult result = collector.result();
    lifecycle.stepEnded(context, stepIndex, result);
    return result;
  }

  /**
   * 兼容旧构造函数的模型选择器转换。
   *
   * <p>新代码应优先传入 {@link LlmModelSelector}，因为 provider、model、capabilities 和 purpose
   * 是模型路由需要的完整信息；旧 String model 只保留向后兼容能力。</p>
   */
  private static LlmModelSelector selectorFromModel(String model) {
    if (model == null || model.isBlank()) {
      throw new IllegalArgumentException("Agent loop model must not be blank");
    }
    return new LlmModelSelector(null, LlmModelId.of(model), Set.of(), "topic-explanation");
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
    private final AtomicReference<Throwable> error = new AtomicReference<>();
    private LlmFinishReason finishReason = LlmFinishReason.UNKNOWN;

    private StepCollector(AgentLoopContext context, int stepIndex, AgentLoopLifecycle lifecycle) {
      this.context = context;
      this.stepIndex = stepIndex;
      this.lifecycle = lifecycle;
    }

    @Override
    public void onSubscribe(Flow.Subscription subscription) {
      // LLM 输出通常由 provider 控制节奏，这里一次性请求全部事件，避免本地 backpressure 让 SSE token 转发变得复杂。
      subscription.request(Long.MAX_VALUE);
    }

    @Override
    public void onNext(LlmStreamEvent item) {
      lifecycle.llmEvent(context, stepIndex, item);
      if (item instanceof LlmStreamEvent.ToolCallEnd toolCallEnd) {
        toolCalls.add(toolCallEnd.toolCall());
      }
      if (item instanceof LlmStreamEvent.MessageEnd messageEnd) {
        finishReason = messageEnd.finishReason();
      }
      if (item instanceof LlmStreamEvent.Error llmError) {
        error.compareAndSet(null, llmError.error());
      }
    }

    @Override
    public void onError(Throwable throwable) {
      error.compareAndSet(null, throwable);
      done.countDown();
    }

    @Override
    public void onComplete() {
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
        throw new AgentException(AgentErrorCode.CANCELLED, "Agent loop was interrupted", false, Map.of(), ex);
      }
    }

    /**
     * 生成当前 step 的决策结果。
     *
     * <p>这里只返回工具调用和 finish reason；文本内容已经作为流式事件发出，不再在 runner 内聚合，
     * 避免同时维护“完整回答缓存”和“SSE 增量输出”两套状态。</p>
     */
    private AgentStepResult result() {
      return new AgentStepResult(toolCalls, finishReason);
    }
  }
}
