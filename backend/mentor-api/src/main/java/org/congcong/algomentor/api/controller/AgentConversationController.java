package org.congcong.algomentor.api.controller;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import java.util.UUID;
import java.util.concurrent.Flow;
import org.congcong.algomentor.agent.core.AgentStreamEvent;
import org.congcong.algomentor.api.config.ApiContractConstants;
import org.congcong.algomentor.api.service.LlmStreamSseMapper;
import org.congcong.algomentor.api.service.SseLlmStreamSubscriber;
import org.congcong.algomentor.mentor.application.conversation.AgentConversationCommand;
import org.congcong.algomentor.mentor.application.conversation.AgentConversationRunCoordinator;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.http.MediaType;
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
@ConditionalOnBean(AgentConversationRunCoordinator.class)
public class AgentConversationController {

  private final AgentConversationRunCoordinator runCoordinator;
  private final LlmStreamSseMapper sseMapper;

  public AgentConversationController(
      AgentConversationRunCoordinator runCoordinator,
      LlmStreamSseMapper sseMapper
  ) {
    this.runCoordinator = runCoordinator;
    this.sseMapper = sseMapper;
  }

  @PostMapping(value = ApiContractConstants.STREAM_PATH, produces = MediaType.TEXT_EVENT_STREAM_VALUE)
  public SseEmitter stream(
      @RequestHeader(name = ApiContractConstants.IDEMPOTENCY_KEY_HEADER, required = false) String idempotencyKey,
      @Valid @RequestBody ConversationStreamRequest request
  ) {
    String effectiveKey = idempotencyKey == null || idempotencyKey.isBlank()
        ? UUID.randomUUID().toString()
        : idempotencyKey;
    Flow.Publisher<AgentStreamEvent> publisher = runCoordinator.stream(new AgentConversationCommand(
        request.taskId(),
        request.userId(),
        request.message(),
        effectiveKey));
    /*
     * 本接口使用 Spring MVC 的 SseEmitter 把 Agent 的异步事件流桥接成 HTTP SSE：
     *
     * 1. runCoordinator.stream(...) 返回 Flow.Publisher<AgentStreamEvent>。Publisher 是“事件源”，
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
    SseEmitter emitter = new SseEmitter(30_000L);
    SseLlmStreamSubscriber subscriber = new SseLlmStreamSubscriber(emitter, sseMapper);

    /*
     * 客户端断开、SSE 超时或写响应失败时，需要取消订阅。
     * 取消后上游 Publisher/Agent loop 可以停止继续生产 token 和工具事件，避免后台任务无意义运行。
     */
    emitter.onCompletion(subscriber::cancel);
    emitter.onTimeout(subscriber::cancel);
    emitter.onError(ignored -> subscriber.cancel());

    try {
      // subscribe 是整个流式链路的启动点；之后由 Subscriber 的回调方法接收并转发事件。
      publisher.subscribe(subscriber);
    } catch (RuntimeException ex) {
      subscriber.onError(ex);
    }
    return emitter;
  }

  public record ConversationStreamRequest(
      @Positive Long taskId,
      @Positive Long userId,
      @NotBlank String message
  ) {
  }
}
