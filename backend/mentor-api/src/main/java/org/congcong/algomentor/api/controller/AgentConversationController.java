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
    SseEmitter emitter = new SseEmitter(30_000L);
    SseLlmStreamSubscriber subscriber = new SseLlmStreamSubscriber(emitter, sseMapper);

    emitter.onCompletion(subscriber::cancel);
    emitter.onTimeout(subscriber::cancel);
    emitter.onError(ignored -> subscriber.cancel());

    try {
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
