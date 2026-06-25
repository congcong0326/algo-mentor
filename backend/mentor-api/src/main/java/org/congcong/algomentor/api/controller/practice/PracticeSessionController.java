package org.congcong.algomentor.api.controller.practice;

import jakarta.validation.Valid;
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
import org.congcong.algomentor.api.config.ApiSseProperties;
import org.congcong.algomentor.api.practice.model.PracticeCodeReviewDetailResponse;
import org.congcong.algomentor.api.practice.model.PracticeCodeReviewHistoryResponse;
import org.congcong.algomentor.api.practice.model.PracticeCodeReviewResponseMapper;
import org.congcong.algomentor.api.practice.model.PracticeMessageRequest;
import org.congcong.algomentor.api.practice.model.PracticeMessageResponse;
import org.congcong.algomentor.api.practice.model.PracticeActiveRunResponse;
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
import org.springframework.beans.factory.ObjectProvider;
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

  private final ObjectProvider<PracticeSessionService> practiceSessionService;
  private final ObjectProvider<PracticeMessageStreamService> streamService;
  private final CurrentUserIdProvider currentUserIdProvider;
  private final ObjectProvider<AiActorResolver> actorResolver;
  private final ObjectProvider<AiRunAdmissionService> admissionService;
  private final ObjectProvider<LlmStreamSseMapper> sseMapper;
  private final ApiSseProperties sseProperties;

  public PracticeSessionController(
      ObjectProvider<PracticeSessionService> practiceSessionService,
      ObjectProvider<PracticeMessageStreamService> streamService,
      CurrentUserIdProvider currentUserIdProvider,
      ObjectProvider<AiActorResolver> actorResolver,
      ObjectProvider<AiRunAdmissionService> admissionService,
      ObjectProvider<LlmStreamSseMapper> sseMapper,
      ApiSseProperties sseProperties
  ) {
    this.practiceSessionService = practiceSessionService;
    this.streamService = streamService;
    this.currentUserIdProvider = currentUserIdProvider;
    this.actorResolver = actorResolver;
    this.admissionService = admissionService;
    this.sseMapper = sseMapper;
    this.sseProperties = sseProperties;
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
    return ApiResponse.success(PracticeSessionResponseMapper.toResponse(requiredPracticeSessionService().createOrReuse(
        userId,
        new PracticeChatReference(planId, phaseIndex, slug, locale))));
  }

  @GetMapping(ApiContractConstants.PRACTICE_SESSIONS_BASE_PATH + "/{sessionId}")
  public ApiResponse<PracticeSessionResponse> get(@PathVariable long sessionId) {
    long userId = requireCurrentUserId();
    return ApiResponse.success(PracticeSessionResponseMapper.toResponse(requiredPracticeSessionService().get(userId, sessionId)));
  }

  @GetMapping(ApiContractConstants.PRACTICE_SESSIONS_BASE_PATH
      + ApiContractConstants.PRACTICE_SESSION_ACTIVE_RUN_PATH)
  public ApiResponse<PracticeActiveRunResponse> activeRun(@PathVariable long sessionId) {
    long userId = requireCurrentUserId();
    PracticeSessionResponse response = PracticeSessionResponseMapper.toResponse(requiredPracticeSessionService().get(userId, sessionId));
    return ApiResponse.success(response.activeRun());
  }

  @GetMapping(ApiContractConstants.PRACTICE_SESSIONS_BASE_PATH
      + ApiContractConstants.PRACTICE_SESSION_MESSAGES_PATH)
  public ApiResponse<java.util.List<PracticeMessageResponse>> messages(
      @PathVariable long sessionId,
      @RequestParam(required = false, defaultValue = "50") int limit
  ) {
    long userId = requireCurrentUserId();
    PracticeSessionResponse response = PracticeSessionResponseMapper.toResponse(
        requiredPracticeSessionService().get(userId, sessionId, limit));
    return ApiResponse.success(response.messages());
  }

  @GetMapping(ApiContractConstants.PRACTICE_SESSIONS_BASE_PATH
      + ApiContractConstants.PRACTICE_SESSION_REVIEWS_PATH)
  public ApiResponse<PracticeCodeReviewHistoryResponse> reviews(@PathVariable long sessionId) {
    long userId = requireCurrentUserId();
    return ApiResponse.success(PracticeCodeReviewResponseMapper.toHistoryResponse(
        requiredPracticeSessionService().history(userId, sessionId)));
  }

  @GetMapping(ApiContractConstants.PRACTICE_SESSIONS_BASE_PATH
      + ApiContractConstants.PRACTICE_SESSION_REVIEW_DETAIL_PATH)
  public ApiResponse<PracticeCodeReviewDetailResponse> reviewDetail(
      @PathVariable long sessionId,
      @PathVariable long reviewId
  ) {
    long userId = requireCurrentUserId();
    return ApiResponse.success(PracticeCodeReviewResponseMapper.toDetailResponse(
        requiredPracticeSessionService().detail(userId, sessionId, reviewId)));
  }

  @PatchMapping(ApiContractConstants.PRACTICE_SESSIONS_BASE_PATH
      + ApiContractConstants.PRACTICE_SESSION_PROGRESS_STATUS_PATH)
  public ApiResponse<PracticeSessionResponse> updateProgressStatus(
      @PathVariable long sessionId,
      @RequestBody PracticeProgressStatusRequest request
  ) {
    long userId = requireCurrentUserId();
    PracticeProgressStatus status = parseProgressStatus(request);
    PracticeSessionService sessionService = requiredPracticeSessionService();
    sessionService.updateProgressStatus(userId, sessionId, status);
    return ApiResponse.success(PracticeSessionResponseMapper.toResponse(sessionService.get(userId, sessionId)));
  }

  @PostMapping(
      value = ApiContractConstants.PRACTICE_SESSIONS_BASE_PATH
          + ApiContractConstants.PRACTICE_SESSION_MESSAGES_STREAM_PATH,
      produces = MediaType.TEXT_EVENT_STREAM_VALUE)
  public SseEmitter stream(
      @PathVariable long sessionId,
      @RequestHeader(name = ApiContractConstants.IDEMPOTENCY_KEY_HEADER, required = false) String idempotencyKey,
      @Valid @RequestBody PracticeMessageRequest request
  ) {
    long userId = requireCurrentUserId();
    String effectiveKey = idempotencyKey == null || idempotencyKey.isBlank()
        ? UUID.randomUUID().toString()
        : idempotencyKey;
    AiActor actor = requiredActorResolver().currentActor();
    Map<String, Object> requestMetadata = Map.of(
        PracticeChatPromptConstants.METADATA_PRACTICE_SESSION_ID, sessionId);
    AiRunAdmission admission = requiredAdmissionService().admit(new AiRunContext(
        UUID.randomUUID().toString(),
        actor,
        AiPurpose.LEARNING_CHAT,
        AiRunSource.PRACTICE_CHAT,
        effectiveKey,
        requestSize(request),
        true,
        requestMetadata,
        Instant.now()));
    PracticeMessageStreamService practiceMessageStreamService = streamService.getIfAvailable(() -> {
      throw new org.congcong.algomentor.mentor.application.learningplan.LearningPlanException(
          "PRACTICE_MESSAGE_STREAM_UNAVAILABLE",
          "题目训练消息流服务不可用。");
    });
    Flow.Publisher<AgentStreamEvent> publisher = practiceMessageStreamService.stream(
        userId,
        sessionId,
        request.message(),
        effectiveKey,
        DEFAULT_LOCALE,
        admission.metadata());

    SseEmitter emitter = new SseEmitter(sseProperties.practiceMessageTimeoutMillis());
    SseLlmStreamSubscriber subscriber = new SseLlmStreamSubscriber(emitter, requiredSseMapper(), false);

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

  private PracticeSessionService requiredPracticeSessionService() {
    return practiceSessionService.getIfAvailable(() -> {
      throw new org.congcong.algomentor.mentor.application.learningplan.LearningPlanException(
          "PRACTICE_SESSION_SERVICE_UNAVAILABLE",
          "题目训练会话服务不可用。");
    });
  }

  private AiActorResolver requiredActorResolver() {
    return actorResolver.getIfAvailable(this::streamUnavailable);
  }

  private AiRunAdmissionService requiredAdmissionService() {
    return admissionService.getIfAvailable(this::streamUnavailable);
  }

  private LlmStreamSseMapper requiredSseMapper() {
    return sseMapper.getIfAvailable(this::streamUnavailable);
  }

  private <T> T streamUnavailable() {
    throw new org.congcong.algomentor.mentor.application.learningplan.LearningPlanException(
        "PRACTICE_MESSAGE_STREAM_UNAVAILABLE",
        "题目训练消息流服务不可用。");
  }

  private int requestSize(PracticeMessageRequest request) {
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
