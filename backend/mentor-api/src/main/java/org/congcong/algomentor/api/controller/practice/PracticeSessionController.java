package org.congcong.algomentor.api.controller.practice;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Flow;
import org.congcong.algomentor.agent.core.AgentStreamEvent;
import org.congcong.algomentor.ai.governance.admission.AiRunAdmission;
import org.congcong.algomentor.ai.governance.admission.AiRunAdmissionService;
import org.congcong.algomentor.ai.governance.model.AiActor;
import org.congcong.algomentor.ai.governance.model.AiPurpose;
import org.congcong.algomentor.ai.governance.model.AiRunContext;
import org.congcong.algomentor.ai.governance.model.AiRunSource;
import org.congcong.algomentor.api.config.ApiContractConstants;
import org.congcong.algomentor.api.practice.model.PracticeMessageRequest;
import org.congcong.algomentor.api.practice.model.PracticeProgressStatusRequest;
import org.congcong.algomentor.api.practice.model.PracticeSessionResponse;
import org.congcong.algomentor.api.practice.model.PracticeSessionResponseMapper;
import org.congcong.algomentor.api.service.AiActorResolver;
import org.congcong.algomentor.api.service.LlmStreamSseMapper;
import org.congcong.algomentor.api.service.SseLlmStreamSubscriber;
import org.congcong.algomentor.auth.security.AuthenticatedUserPrincipal;
import org.congcong.algomentor.auth.security.CurrentUserIdProvider;
import org.congcong.algomentor.common.api.ApiResponse;
import org.congcong.algomentor.mentor.application.practice.PracticeChatPromptConstants;
import org.congcong.algomentor.mentor.application.practice.PracticeChatReference;
import org.congcong.algomentor.mentor.application.practice.PracticeMessageStreamService;
import org.congcong.algomentor.mentor.application.practice.PracticeProgressStatus;
import org.congcong.algomentor.mentor.application.practice.PracticeSessionService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
public class PracticeSessionController {

  private static final String DEFAULT_LOCALE = "zh-CN";

  private final PracticeSessionService practiceSessionService;
  private final PracticeMessageStreamService streamService;
  private final CurrentUserIdProvider currentUserIdProvider;
  private final AiActorResolver actorResolver;
  private final AiRunAdmissionService admissionService;
  private final LlmStreamSseMapper sseMapper;

  public PracticeSessionController(
      PracticeSessionService practiceSessionService,
      PracticeMessageStreamService streamService,
      CurrentUserIdProvider currentUserIdProvider,
      AiActorResolver actorResolver,
      AiRunAdmissionService admissionService,
      LlmStreamSseMapper sseMapper
  ) {
    this.practiceSessionService = practiceSessionService;
    this.streamService = streamService;
    this.currentUserIdProvider = currentUserIdProvider;
    this.actorResolver = actorResolver;
    this.admissionService = admissionService;
    this.sseMapper = sseMapper;
  }

  @PostMapping(ApiContractConstants.LEARNING_PLANS_BASE_PATH
      + ApiContractConstants.LEARNING_PLAN_PROBLEM_PRACTICE_SESSION_PATH)
  public ApiResponse<PracticeSessionResponse> createOrReuse(
      @PathVariable long planId,
      @PathVariable int phaseIndex,
      @PathVariable String slug,
      @RequestParam(required = false, defaultValue = DEFAULT_LOCALE) String locale
  ) {
    long userId = requireCurrentUserId();
    return ApiResponse.success(PracticeSessionResponseMapper.toResponse(practiceSessionService.createOrReuse(
        userId,
        new PracticeChatReference(planId, phaseIndex, slug, locale))));
  }

  @GetMapping(ApiContractConstants.PRACTICE_SESSIONS_BASE_PATH + "/{sessionId}")
  public ApiResponse<PracticeSessionResponse> get(@PathVariable long sessionId) {
    long userId = requireCurrentUserId();
    return ApiResponse.success(PracticeSessionResponseMapper.toResponse(practiceSessionService.get(userId, sessionId)));
  }

  @PatchMapping(ApiContractConstants.PRACTICE_SESSIONS_BASE_PATH
      + ApiContractConstants.PRACTICE_SESSION_PROGRESS_STATUS_PATH)
  public ApiResponse<PracticeSessionResponse> updateProgressStatus(
      @PathVariable long sessionId,
      @RequestBody PracticeProgressStatusRequest request
  ) {
    long userId = requireCurrentUserId();
    PracticeProgressStatus status = parseProgressStatus(request);
    practiceSessionService.updateProgressStatus(userId, sessionId, status);
    return ApiResponse.success(PracticeSessionResponseMapper.toResponse(practiceSessionService.get(userId, sessionId)));
  }

  @PostMapping(
      value = ApiContractConstants.PRACTICE_SESSIONS_BASE_PATH
          + ApiContractConstants.PRACTICE_SESSION_MESSAGES_STREAM_PATH,
      produces = MediaType.TEXT_EVENT_STREAM_VALUE)
  public SseEmitter stream(
      @PathVariable long sessionId,
      @RequestHeader(name = ApiContractConstants.IDEMPOTENCY_KEY_HEADER, required = false) String idempotencyKey,
      @RequestBody PracticeMessageRequest request
  ) {
    long userId = requireCurrentUserId();
    String effectiveKey = idempotencyKey == null || idempotencyKey.isBlank()
        ? UUID.randomUUID().toString()
        : idempotencyKey;
    AiActor actor = actorResolver.currentActor();
    Map<String, Object> requestMetadata = Map.of(
        PracticeChatPromptConstants.METADATA_PRACTICE_SESSION_ID, sessionId);
    AiRunAdmission admission = admissionService.admit(new AiRunContext(
        UUID.randomUUID().toString(),
        actor,
        AiPurpose.LEARNING_CHAT,
        AiRunSource.PRACTICE_CHAT,
        effectiveKey,
        requestSize(request),
        true,
        requestMetadata,
        Instant.now()));
    Flow.Publisher<AgentStreamEvent> publisher = streamService.stream(
        userId,
        sessionId,
        request == null ? null : request.message(),
        effectiveKey,
        DEFAULT_LOCALE,
        admission.metadata());

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

  private long requireCurrentUserId() {
    return currentUserIdProvider.currentUser()
        .map(AuthenticatedUserPrincipal::userId)
        .orElseThrow(() -> new PracticeSessionUnauthenticatedException("当前请求未登录或无法解析当前用户。"));
  }

  private int requestSize(PracticeMessageRequest request) {
    if (request == null || request.message() == null) {
      return 0;
    }
    return request.message().getBytes(StandardCharsets.UTF_8).length;
  }

  private PracticeProgressStatus parseProgressStatus(PracticeProgressStatusRequest request) {
    try {
      return PracticeProgressStatus.valueOf(request.status());
    } catch (RuntimeException exception) {
      throw new PracticeProgressStatusInvalidException("练习进度状态不合法。", exception);
    }
  }
}
