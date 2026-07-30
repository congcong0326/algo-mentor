package org.congcong.algomentor.api.controller;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.Flow;
import org.congcong.algomentor.agent.core.AgentStreamEvent;
import org.congcong.algomentor.agent.core.runtime.api.AgentInvocation;
import org.congcong.algomentor.agent.core.runtime.api.AgentInvocationContext;
import org.congcong.algomentor.agent.core.runtime.api.AgentInvocationMode;
import org.congcong.algomentor.agent.core.runtime.api.AgentRuntime;
import org.congcong.algomentor.ai.governance.model.AiActor;
import org.congcong.algomentor.api.config.ApiContractConstants;
import org.congcong.algomentor.api.config.ApiSseProperties;
import org.congcong.algomentor.api.service.AiActorResolver;
import org.congcong.algomentor.api.service.LlmStreamSseMapper;
import org.congcong.algomentor.api.service.SseLlmStreamSubscriber;
import org.congcong.algomentor.mentor.application.conversation.MentorConversationAgentDefinition;
import org.congcong.algomentor.mentor.application.conversation.MentorConversationAgentInput;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanException;
import org.congcong.algomentor.mentor.application.practice.PracticeMessageStreamService;
import org.congcong.algomentor.ops.observability.LearningOpsRecorder;
import org.congcong.algomentor.ops.observability.NoopOpsRecorders;
import org.congcong.algomentor.ops.observability.SseOpsRecorder;
import org.congcong.algomentor.ops.observability.SseStreamType;
import org.congcong.algomentor.ops.observability.StructuredOpsLogger;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Validated
@RestController
@RequestMapping(ApiContractConstants.AGENT_CONVERSATIONS_BASE_PATH)
@ConditionalOnBean(AgentRuntime.class)
public class AgentConversationController {

  private final AgentRuntime agentRuntime;
  private final LlmStreamSseMapper sseMapper;
  private final AiActorResolver actorResolver;
  private final ObjectProvider<PracticeMessageStreamService> practiceMessageStreamService;
  private final ApiSseProperties sseProperties;
  private final SseOpsRecorder sseOpsRecorder;
  private final LearningOpsRecorder learningOpsRecorder;
  private final StructuredOpsLogger opsLogger;

  public AgentConversationController(
      AgentRuntime agentRuntime,
      LlmStreamSseMapper sseMapper,
      AiActorResolver actorResolver,
      ObjectProvider<PracticeMessageStreamService> practiceMessageStreamService
  ) {
    this(agentRuntime, sseMapper, actorResolver, practiceMessageStreamService, new ApiSseProperties());
  }

  public AgentConversationController(
      AgentRuntime agentRuntime,
      LlmStreamSseMapper sseMapper,
      AiActorResolver actorResolver,
      ObjectProvider<PracticeMessageStreamService> practiceMessageStreamService,
      ApiSseProperties sseProperties
  ) {
    this.agentRuntime = agentRuntime;
    this.sseMapper = sseMapper;
    this.actorResolver = actorResolver;
    this.practiceMessageStreamService = practiceMessageStreamService;
    this.sseProperties = Objects.requireNonNull(sseProperties, "sseProperties must not be null");
    this.sseOpsRecorder = NoopOpsRecorders.sse();
    this.learningOpsRecorder = NoopOpsRecorders.learning();
    this.opsLogger = new StructuredOpsLogger();
  }

  @Autowired
  public AgentConversationController(
      AgentRuntime agentRuntime,
      LlmStreamSseMapper sseMapper,
      AiActorResolver actorResolver,
      ObjectProvider<PracticeMessageStreamService> practiceMessageStreamService,
      ObjectProvider<SseOpsRecorder> sseOpsRecorder,
      ObjectProvider<LearningOpsRecorder> learningOpsRecorder,
      ApiSseProperties sseProperties
  ) {
    this.agentRuntime = agentRuntime;
    this.sseMapper = sseMapper;
    this.actorResolver = actorResolver;
    this.practiceMessageStreamService = practiceMessageStreamService;
    this.sseProperties = Objects.requireNonNull(sseProperties, "sseProperties must not be null");
    this.sseOpsRecorder = sseOpsRecorder.getIfAvailable(NoopOpsRecorders::sse);
    this.learningOpsRecorder = learningOpsRecorder.getIfAvailable(NoopOpsRecorders::learning);
    this.opsLogger = new StructuredOpsLogger();
  }

  @PreAuthorize("hasRole('ADMIN')")
  @PostMapping(value = ApiContractConstants.STREAM_PATH, produces = MediaType.TEXT_EVENT_STREAM_VALUE)
  public SseEmitter stream(
      @RequestHeader(name = ApiContractConstants.IDEMPOTENCY_KEY_HEADER, required = false) String idempotencyKey,
      @Valid @RequestBody ConversationStreamRequest request
  ) {
    String effectiveKey = idempotencyKey == null || idempotencyKey.isBlank()
        ? UUID.randomUUID().toString()
        : idempotencyKey;
    AiActor actor = actorResolver.currentActor();
    int requestSize = request.message().getBytes(StandardCharsets.UTF_8).length;
    Flow.Publisher<AgentStreamEvent> publisher;
    if (request.practice() == null) {
      publisher = agentRuntime.stream(new AgentInvocation<>(
          MentorConversationAgentDefinition.KEY,
          new MentorConversationAgentInput(
              request.taskId(),
              actor.userId(),
              request.message(),
              effectiveKey,
              requestSize),
          new AgentInvocationContext(
              actor.userId(),
              AgentInvocationMode.USER_ENTRY,
              effectiveKey,
              null,
              null,
              requestSize,
              true)));
    } else {
      PracticeChatRequest practice = request.practice();
      publisher = requiredPracticeMessageStreamService().stream(
          actor.userId(),
          practice.sessionId(),
          request.message(),
          effectiveKey,
          practice.locale(),
          requestSize);
    }
    /*
     * 本接口使用 Spring MVC 的 SseEmitter 把 Agent 的异步事件流桥接成 HTTP SSE：
     *
     * 1. Agent Runtime 或 Practice application service 返回 Flow.Publisher<AgentStreamEvent>。Publisher 是“事件源”，
     *    它背后会启动 Agent loop，并陆续发布 run start、LLM token、工具调用、run end/error 等事件。
     * 2. SseEmitter 是 Spring MVC 提供的“长连接响应句柄”。Controller 返回它以后，HTTP 响应不会立刻结束，
     *    后续可以在其他线程中持续调用 emitter.send(...) 向浏览器写入 text/event-stream 数据。
     * 3. SseLlmStreamSubscriber 是本项目的桥接订阅者：它订阅 Publisher，收到 AgentStreamEvent 后先通过
     *    LlmStreamSseMapper 映射成 SSE 的 event/data，再写入 SseEmitter。
     *
     * 简化链路：
     *   前端 POST /stream
     *     -> Controller 创建 Publisher + SseEmitter + Subscriber
     *     -> publisher.subscribe(subscriber)
     *     -> Agent loop 发布事件
     *     -> subscriber.onNext(...) 调用 emitter.send(...)
     *     -> 浏览器 EventSource/fetch stream 按 SSE 事件名消费数据
     */
    SseEmitter emitter = new SseEmitter(sseProperties.agentConversationTimeoutMillis());
    SseLlmStreamSubscriber subscriber = new SseLlmStreamSubscriber(
        emitter,
        sseMapper,
        true,
        SseStreamType.AGENT_CONVERSATION,
        sseOpsRecorder,
        learningOpsRecorder,
        opsLogger);

    /*
     * 客户端断开、SSE 超时或写响应失败时，需要取消订阅。
     * 取消后上游 Publisher/Agent loop 可以停止继续生产 token 和工具事件，避免后台任务无意义运行。
     */
    emitter.onCompletion(() -> subscriber.clientDisconnected(null));
    emitter.onTimeout(subscriber::timeout);
    emitter.onError(subscriber::clientDisconnected);

    try {
      // subscribe 是整个流式链路的启动点；之后由 Subscriber 的回调方法接收并转发事件。
      publisher.subscribe(subscriber);
    } catch (RuntimeException ex) {
      subscriber.onError(ex);
    }
    return emitter;
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record ConversationStreamRequest(
      @Positive Long taskId,
      @NotBlank String message,
      @Valid
      PracticeChatRequest practice
  ) {
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record PracticeChatRequest(
      @NotNull @Positive Long sessionId,
      String locale
  ) {
  }

  private PracticeMessageStreamService requiredPracticeMessageStreamService() {
    return practiceMessageStreamService.getIfAvailable(() -> {
      throw new LearningPlanException(
          "PRACTICE_MESSAGE_STREAM_UNAVAILABLE",
          "题目训练消息流服务不可用。");
    });
  }
}
