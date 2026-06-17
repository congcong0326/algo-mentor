package org.congcong.algomentor.api.controller;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import java.util.UUID;
import org.congcong.algomentor.agent.core.AgentLoopRunner;
import org.congcong.algomentor.api.service.LlmStreamSseMapper;
import org.congcong.algomentor.api.service.SseLlmStreamSubscriber;
import org.congcong.algomentor.mentor.application.conversation.AgentConversationCommand;
import org.congcong.algomentor.mentor.application.conversation.AgentConversationRun;
import org.congcong.algomentor.mentor.application.conversation.AgentConversationService;
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
@RequestMapping("/api/agent/conversations")
@ConditionalOnBean(AgentConversationService.class)
public class AgentConversationController {

  private final AgentConversationService conversationService;
  private final AgentLoopRunner agentLoopRunner;
  private final LlmStreamSseMapper sseMapper;

  public AgentConversationController(
      AgentConversationService conversationService,
      AgentLoopRunner agentLoopRunner,
      LlmStreamSseMapper sseMapper
  ) {
    this.conversationService = conversationService;
    this.agentLoopRunner = agentLoopRunner;
    this.sseMapper = sseMapper;
  }

  @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
  public SseEmitter stream(
      @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
      @Valid @RequestBody ConversationStreamRequest request
  ) {
    String effectiveKey = idempotencyKey == null || idempotencyKey.isBlank()
        ? UUID.randomUUID().toString()
        : idempotencyKey;
    AgentConversationRun run = conversationService.prepareRun(new AgentConversationCommand(
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
      agentLoopRunner.stream(run.agentRequest()).subscribe(subscriber);
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
