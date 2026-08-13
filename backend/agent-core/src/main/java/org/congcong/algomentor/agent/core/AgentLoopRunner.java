package org.congcong.algomentor.agent.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.Flow;
import org.congcong.algomentor.agent.core.execution.AgentExecutor;
import org.congcong.algomentor.agent.core.execution.AgentExecutionGroup;
import org.congcong.algomentor.agent.core.compaction.RunMessageCompactionResult;
import org.congcong.algomentor.agent.core.compaction.RunMessageCompactor;
import org.congcong.algomentor.agent.core.compaction.ToolResultCompaction;
import org.congcong.algomentor.agent.core.compaction.ToolResultCompactionPolicy;
import org.congcong.algomentor.agent.core.compaction.ToolResultCompactor;
import org.congcong.algomentor.agent.core.permission.AgentToolPermissionAuthorization;
import org.congcong.algomentor.agent.core.permission.AgentToolPermissionGuard;
import org.congcong.algomentor.agent.core.permission.AgentToolPermissionHookChain;
import org.congcong.algomentor.agent.core.permission.AgentToolPermissionResultFactory;
import org.congcong.algomentor.agent.core.permission.InMemoryAgentToolPermissionCoordinator;
import org.congcong.algomentor.agent.core.toolresult.InMemoryToolResultStore;
import org.congcong.algomentor.agent.core.toolresult.ToolResultStore;
import org.congcong.algomentor.llm.core.gateway.LlmGateway;
import org.congcong.algomentor.llm.core.model.LlmModelId;
import org.congcong.algomentor.llm.core.model.LlmModelSelector;
import org.congcong.algomentor.llm.core.tool.LlmToolChoice;

/**
 * Agent 流式兼容适配器。
 *
 * <p>保留既有 {@link #stream(AgentRequest)} 外观，把订阅、背压和 executor 提交适配为
 * {@link AgentLoopEngine} 的同步执行。控制流、工具调用和生命周期事件仅由同步内核维护。</p>
 */
public class AgentLoopRunner {

  private static final String DEFAULT_PURPOSE = "practice-chat";
  private static final AgentExecutor UNCONFIGURED_EXECUTOR = new AgentExecutor() {
    @Override
    public void execute(AgentExecutionGroup group, Runnable task) {
      throw new IllegalStateException("Agent executor must be configured before starting a stream");
    }

    @Override
    public boolean isShutdown() {
      return false;
    }
  };

  private final LlmGateway llmGateway;
  private final AgentLlmRequestFactory requestFactory;
  private final AgentToolRegistry toolRegistry;
  private final LlmToolChoice toolChoice;
  private final int maxSteps;
  private final List<AgentLoopObserver> observers;
  private final List<AgentLoopInterceptor> interceptors;
  private final ToolResultCompactor toolResultCompactor;
  private final RunMessageCompactor runMessageCompactor;
  private final ObjectMapper objectMapper;
  private final AgentToolPermissionGuard permissionGuard;
  private final AgentExecutor executor;
  private final AgentLoopEngine loopEngine;
  private final AgentLoopExecution legacyExecution;

  @Deprecated(forRemoval = false)
  public AgentLoopRunner(LlmGateway llmGateway, String model, AgentToolRegistry toolRegistry, int maxSteps) {
    this(llmGateway, selectorFromModel(model), toolRegistry, maxSteps);
  }

  @Deprecated(forRemoval = false)
  public AgentLoopRunner(
      LlmGateway llmGateway,
      String model,
      AgentToolRegistry toolRegistry,
      int maxSteps,
      AgentExecutor executor
  ) {
    this(llmGateway, selectorFromModel(model), toolRegistry, maxSteps, executor);
  }

  public AgentLoopRunner(
      LlmGateway llmGateway,
      LlmModelSelector modelSelector,
      AgentToolRegistry toolRegistry,
      int maxSteps
  ) {
    this(llmGateway, new AgentLlmRequestFactory(modelSelector), toolRegistry, null, maxSteps);
  }

  public AgentLoopRunner(
      LlmGateway llmGateway,
      LlmModelSelector modelSelector,
      AgentToolRegistry toolRegistry,
      int maxSteps,
      AgentExecutor executor
  ) {
    this(llmGateway, new AgentLlmRequestFactory(modelSelector), toolRegistry, null, maxSteps, executor);
  }

  public AgentLoopRunner(
      LlmGateway llmGateway,
      AgentLlmRequestFactory requestFactory,
      AgentToolRegistry toolRegistry,
      int maxSteps
  ) {
    this(llmGateway, requestFactory, toolRegistry, null, maxSteps);
  }

  public AgentLoopRunner(
      LlmGateway llmGateway,
      AgentLlmRequestFactory requestFactory,
      AgentToolRegistry toolRegistry,
      int maxSteps,
      AgentExecutor executor
  ) {
    this(llmGateway, requestFactory, toolRegistry, null, maxSteps, executor);
  }

  public AgentLoopRunner(
      LlmGateway llmGateway,
      LlmModelSelector modelSelector,
      AgentToolRegistry toolRegistry,
      LlmToolChoice toolChoice,
      int maxSteps
  ) {
    this(llmGateway, new AgentLlmRequestFactory(modelSelector), toolRegistry, toolChoice, maxSteps, List.of(), List.of());
  }

  public AgentLoopRunner(
      LlmGateway llmGateway,
      LlmModelSelector modelSelector,
      AgentToolRegistry toolRegistry,
      LlmToolChoice toolChoice,
      int maxSteps,
      AgentExecutor executor
  ) {
    this(
        llmGateway,
        new AgentLlmRequestFactory(modelSelector),
        toolRegistry,
        toolChoice,
        maxSteps,
        List.of(),
        List.of(),
        executor);
  }

  public AgentLoopRunner(
      LlmGateway llmGateway,
      AgentLlmRequestFactory requestFactory,
      AgentToolRegistry toolRegistry,
      LlmToolChoice toolChoice,
      int maxSteps
  ) {
    this(llmGateway, requestFactory, toolRegistry, toolChoice, maxSteps, List.of(), List.of());
  }

  public AgentLoopRunner(
      LlmGateway llmGateway,
      AgentLlmRequestFactory requestFactory,
      AgentToolRegistry toolRegistry,
      LlmToolChoice toolChoice,
      int maxSteps,
      AgentExecutor executor
  ) {
    this(llmGateway, requestFactory, toolRegistry, toolChoice, maxSteps, List.of(), List.of(), executor);
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
        new AgentLlmRequestFactory(modelSelector),
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
      AgentExecutor executor
  ) {
    this(
        llmGateway,
        new AgentLlmRequestFactory(modelSelector),
        toolRegistry,
        toolChoice,
        maxSteps,
        observers,
        interceptors,
        ToolResultCompactionPolicy.defaults(),
        new InMemoryToolResultStore(),
        new ObjectMapper(),
        null,
        executor);
  }

  public AgentLoopRunner(
      LlmGateway llmGateway,
      AgentLlmRequestFactory requestFactory,
      AgentToolRegistry toolRegistry,
      LlmToolChoice toolChoice,
      int maxSteps,
      List<AgentLoopObserver> observers,
      List<AgentLoopInterceptor> interceptors
  ) {
    this(
        llmGateway,
        requestFactory,
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
      AgentLlmRequestFactory requestFactory,
      AgentToolRegistry toolRegistry,
      LlmToolChoice toolChoice,
      int maxSteps,
      List<AgentLoopObserver> observers,
      List<AgentLoopInterceptor> interceptors,
      AgentExecutor executor
  ) {
    this(
        llmGateway,
        requestFactory,
        toolRegistry,
        toolChoice,
        maxSteps,
        observers,
        interceptors,
        ToolResultCompactionPolicy.defaults(),
        new InMemoryToolResultStore(),
        new ObjectMapper(),
        null,
        executor);
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
    this(
        llmGateway,
        new AgentLlmRequestFactory(modelSelector),
        toolRegistry,
        toolChoice,
        maxSteps,
        observers,
        interceptors,
        toolResultPolicy,
        toolResultStore,
        objectMapper,
        null);
  }

  public AgentLoopRunner(
      LlmGateway llmGateway,
      AgentLlmRequestFactory requestFactory,
      AgentToolRegistry toolRegistry,
      LlmToolChoice toolChoice,
      int maxSteps,
      List<AgentLoopObserver> observers,
      List<AgentLoopInterceptor> interceptors,
      ToolResultCompactionPolicy toolResultPolicy,
      ToolResultStore toolResultStore,
      ObjectMapper objectMapper
  ) {
    this(
        llmGateway,
        requestFactory,
        toolRegistry,
        toolChoice,
        maxSteps,
        observers,
        interceptors,
        toolResultPolicy,
        toolResultStore,
        objectMapper,
        null);
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
      ObjectMapper objectMapper,
      AgentToolPermissionGuard permissionGuard
  ) {
    this(
        llmGateway,
        new AgentLlmRequestFactory(modelSelector),
        toolRegistry,
        toolChoice,
        maxSteps,
        observers,
        interceptors,
        toolResultPolicy,
        toolResultStore,
        objectMapper,
        permissionGuard,
        UNCONFIGURED_EXECUTOR);
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
      ObjectMapper objectMapper,
      AgentToolPermissionGuard permissionGuard,
      AgentExecutor executor
  ) {
    this(
        llmGateway,
        new AgentLlmRequestFactory(modelSelector),
        toolRegistry,
        toolChoice,
        maxSteps,
        observers,
        interceptors,
        toolResultPolicy,
        toolResultStore,
        objectMapper,
        permissionGuard,
        executor);
  }

  public AgentLoopRunner(
      LlmGateway llmGateway,
      AgentLlmRequestFactory requestFactory,
      AgentToolRegistry toolRegistry,
      LlmToolChoice toolChoice,
      int maxSteps,
      List<AgentLoopObserver> observers,
      List<AgentLoopInterceptor> interceptors,
      ToolResultCompactionPolicy toolResultPolicy,
      ToolResultStore toolResultStore,
      ObjectMapper objectMapper,
      AgentToolPermissionGuard permissionGuard
  ) {
    this(
        llmGateway,
        requestFactory,
        toolRegistry,
        toolChoice,
        maxSteps,
        observers,
        interceptors,
        toolResultPolicy,
        toolResultStore,
        objectMapper,
        permissionGuard,
        UNCONFIGURED_EXECUTOR);
  }

  public AgentLoopRunner(
      LlmGateway llmGateway,
      AgentLlmRequestFactory requestFactory,
      AgentToolRegistry toolRegistry,
      LlmToolChoice toolChoice,
      int maxSteps,
      List<AgentLoopObserver> observers,
      List<AgentLoopInterceptor> interceptors,
      ToolResultCompactionPolicy toolResultPolicy,
      ToolResultStore toolResultStore,
      ObjectMapper objectMapper,
      AgentToolPermissionGuard permissionGuard,
      AgentExecutor executor
  ) {
    if (maxSteps < 1) {
      throw new IllegalArgumentException("Agent loop max steps must be positive");
    }
    this.llmGateway = Objects.requireNonNull(llmGateway, "llmGateway must not be null");
    this.requestFactory = Objects.requireNonNull(requestFactory, "agent LLM request factory must not be null");
    this.toolRegistry = Objects.requireNonNull(toolRegistry, "agent tool registry must not be null");
    this.toolChoice = toolChoice == null ? LlmToolChoice.auto() : toolChoice;
    this.maxSteps = maxSteps;
    this.observers = observers == null ? List.of() : List.copyOf(observers);
    this.interceptors = interceptors == null ? List.of() : List.copyOf(interceptors);
    ObjectMapper mapper = objectMapper == null ? new ObjectMapper() : objectMapper;
    ToolResultStore store = toolResultStore == null ? new InMemoryToolResultStore() : toolResultStore;
    ToolResultCompactionPolicy policy = toolResultPolicy == null ? ToolResultCompactionPolicy.defaults() : toolResultPolicy;
    this.toolResultCompactor = new ToolResultCompactor(mapper, policy, store);
    this.runMessageCompactor = new RunMessageCompactor(mapper, toolResultCompactor);
    this.objectMapper = mapper;
    this.permissionGuard = permissionGuard == null ? defaultPermissionGuard(mapper) : permissionGuard;
    this.executor = Objects.requireNonNull(executor, "agent executor must not be null");
    this.legacyExecution = AgentLoopExecution.legacy(this.toolRegistry, this.maxSteps, this.toolChoice);
    this.loopEngine = new AgentLoopEngine(
        this.llmGateway,
        this.requestFactory,
        this.observers,
        this.interceptors,
        this.toolResultCompactor,
        this.runMessageCompactor,
        this.objectMapper,
        this.permissionGuard);
  }

  /**
   * 以 Reactive Streams 的 {@link Flow.Publisher} 形式启动一次 Agent run。
   *
   * <p>这里为每个 run 创建单订阅者同步事件出口，并把任务提交给专用 Agent executor。下游声明 demand 后，
   * Agent 工作线程
   * 会直接调用 Subscriber 回调，因此慢客户端只会阻塞自己的 run，不会占用公共投递线程池或堆积异步缓冲。</p>
   */
  public Flow.Publisher<AgentStreamEvent> stream(AgentRequest request) {
    Objects.requireNonNull(request, "request must not be null");
    AgentCancellationToken cancellationToken = new AgentCancellationToken();
    return new SingleSubscriberAgentStreamPublisher(
        cancellationToken,
        executor,
        AgentExecutionGroup.PRACTICE,
        false,
        () -> {},
        eventSink -> loopEngine.run(request, legacyExecution, eventSink, cancellationToken),
        failure -> failRunSubmission(request, cancellationToken, failure));
  }

  private void failRunSubmission(
      AgentRequest request,
      AgentCancellationToken cancellationToken,
      Throwable failure
  ) {
    loopEngine.failSubmission(request, legacyExecution, cancellationToken, failure);
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
    return new LlmModelSelector(null, LlmModelId.of(model), Set.of(), DEFAULT_PURPOSE);
  }

  private static AgentToolPermissionGuard defaultPermissionGuard(ObjectMapper objectMapper) {
    return new AgentToolPermissionGuard(
        new AgentToolPermissionHookChain(),
        new InMemoryAgentToolPermissionCoordinator(new AgentToolPermissionResultFactory(objectMapper)));
  }

}
