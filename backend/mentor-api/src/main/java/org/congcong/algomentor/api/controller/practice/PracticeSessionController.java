package org.congcong.algomentor.api.controller.practice;

import jakarta.validation.Valid;
import java.nio.charset.StandardCharsets;
import java.io.IOException;
import java.util.UUID;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import org.congcong.algomentor.api.config.ApiContractConstants;
import org.congcong.algomentor.api.config.ApiSseProperties;
import org.congcong.algomentor.api.practice.model.PracticeCodeReviewDetailResponse;
import org.congcong.algomentor.api.practice.model.PracticeCodeReviewHistoryResponse;
import org.congcong.algomentor.api.practice.model.PracticeCodeReviewResponseMapper;
import org.congcong.algomentor.api.practice.model.CoachSummaryProposalActionResponse;
import org.congcong.algomentor.api.practice.model.PracticeMessageRequest;
import org.congcong.algomentor.api.practice.model.PracticeMessageResponse;
import org.congcong.algomentor.api.practice.model.PracticeActiveRunResponse;
import org.congcong.algomentor.api.practice.model.PracticeChatRunSubscriptionResponse;
import org.congcong.algomentor.api.practice.model.PracticeProgressStatusRequest;
import org.congcong.algomentor.api.practice.model.PracticeSessionResponse;
import org.congcong.algomentor.api.practice.model.PracticeSessionResponseMapper;
import org.congcong.algomentor.api.practice.realtime.PracticeRealtimeCursor;
import org.congcong.algomentor.api.practice.realtime.PracticeRealtimeEvent;
import org.congcong.algomentor.api.practice.realtime.PracticeRealtimeEventStore;
import org.congcong.algomentor.api.practice.realtime.PracticeRealtimeProtocol;
import org.congcong.algomentor.auth.security.AuthenticatedUserPrincipal;
import org.congcong.algomentor.auth.security.CurrentUserIdProvider;
import org.congcong.algomentor.common.api.ApiErrorLocales;
import org.congcong.algomentor.common.api.ApiResponse;
import org.congcong.algomentor.mentor.application.practice.PracticeChatReference;
import org.congcong.algomentor.mentor.application.practice.PracticeChatRunSubscription;
import org.congcong.algomentor.mentor.application.practice.PracticeMessageStreamService;
import org.congcong.algomentor.mentor.application.practice.PracticeProgressStatus;
import org.congcong.algomentor.mentor.application.practice.PracticeSessionService;
import org.congcong.algomentor.agent.core.runtime.repository.AgentTaskMessageRepository;
import org.congcong.algomentor.agent.core.AgentStreamEventNames;
import org.congcong.algomentor.ops.observability.SseFailureType;
import org.congcong.algomentor.ops.observability.SseOpsRecorder;
import org.congcong.algomentor.ops.observability.SseStreamType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
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
  private static final Logger log = LoggerFactory.getLogger(PracticeSessionController.class);

  private final ObjectProvider<PracticeSessionService> practiceSessionService;
  private final ObjectProvider<PracticeMessageStreamService> streamService;
  private final CurrentUserIdProvider currentUserIdProvider;
  private final ObjectProvider<PracticeRealtimeEventStore> realtimeEventStore;
  private final ObjectProvider<AgentTaskMessageRepository> agentTaskMessageRepository;
  private final ApiSseProperties sseProperties;
  private final SseOpsRecorder sseOpsRecorder;

  public PracticeSessionController(
      ObjectProvider<PracticeSessionService> practiceSessionService,
      ObjectProvider<PracticeMessageStreamService> streamService,
      CurrentUserIdProvider currentUserIdProvider,
      ObjectProvider<PracticeRealtimeEventStore> realtimeEventStore,
      ObjectProvider<AgentTaskMessageRepository> agentTaskMessageRepository,
      ApiSseProperties sseProperties,
      SseOpsRecorder sseOpsRecorder
  ) {
    this.practiceSessionService = practiceSessionService;
    this.streamService = streamService;
    this.currentUserIdProvider = currentUserIdProvider;
    this.realtimeEventStore = realtimeEventStore;
    this.agentTaskMessageRepository = agentTaskMessageRepository;
    this.sseProperties = sseProperties;
    this.sseOpsRecorder = sseOpsRecorder;
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

  @PostMapping(ApiContractConstants.PRACTICE_SESSIONS_BASE_PATH
      + ApiContractConstants.PRACTICE_SESSION_COACH_SUMMARY_PROPOSAL_APPLY_PATH)
  public ApiResponse<CoachSummaryProposalActionResponse> applyCoachSummaryProposal(
      @PathVariable long sessionId,
      @PathVariable String proposalId
  ) {
    long userId = requireCurrentUserId();
    return ApiResponse.success(PracticeSessionResponseMapper.toCoachSummaryAction(
        requiredPracticeSessionService().applyCoachSummaryProposal(userId, sessionId, proposalId)));
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

  @PostMapping(value = ApiContractConstants.PRACTICE_SESSIONS_BASE_PATH
      + ApiContractConstants.PRACTICE_SESSION_MESSAGES_PATH)
  public ResponseEntity<ApiResponse<PracticeChatRunSubscriptionResponse>> start(
      @PathVariable long sessionId,
      @RequestHeader(name = ApiContractConstants.IDEMPOTENCY_KEY_HEADER, required = false) String idempotencyKey,
      @RequestHeader(name = ApiContractConstants.ACCEPT_LANGUAGE_HEADER, required = false) String acceptLanguage,
      @Valid @RequestBody PracticeMessageRequest request
  ) {
    long userId = requireCurrentUserId();
    String effectiveKey = idempotencyKey == null || idempotencyKey.isBlank()
        ? UUID.randomUUID().toString()
        : idempotencyKey;
    PracticeMessageStreamService practiceMessageStreamService = streamService.getIfAvailable(() -> {
      throw new org.congcong.algomentor.mentor.application.learningplan.LearningPlanException(
          "PRACTICE_MESSAGE_STREAM_UNAVAILABLE",
          "题目训练消息流服务不可用。");
    });
    PracticeChatRunSubscription subscription = practiceMessageStreamService.start(
        userId,
        sessionId,
        request.message(),
        effectiveKey,
        currentRequestLocale(acceptLanguage),
        requestSize(request),
        requiredRealtimeEventStore());
    return ResponseEntity.status(HttpStatus.ACCEPTED).body(ApiResponse.success(
        new PracticeChatRunSubscriptionResponse(
            PracticeChatRunSubscriptionResponse.TYPE_ACCEPTED,
            subscription.taskId(),
            subscription.runUuid(),
            subscription.status(),
            eventsUrl(sessionId, subscription.runUuid()),
            PracticeRealtimeProtocol.INITIAL_AFTER,
            subscription.realtimeProtocolVersion())));
  }

  @GetMapping(value = ApiContractConstants.PRACTICE_SESSIONS_BASE_PATH
      + ApiContractConstants.PRACTICE_SESSION_RUN_EVENTS_PATH, produces = MediaType.TEXT_EVENT_STREAM_VALUE)
  public SseEmitter events(
      @PathVariable long sessionId,
      @PathVariable String runUuid,
    @RequestParam(name = ApiContractConstants.PRACTICE_RUN_EVENTS_AFTER_PARAM, required = false) String after
  ) {
    long userId = requireCurrentUserId();
    // 先拒绝所有协议都不接受的格式，避免无效请求触发数据库查询。
    PracticeRealtimeCursor.normalizeAfter(after);
    PracticeSessionResponse session = PracticeSessionResponseMapper.toResponse(
        requiredPracticeSessionService().get(userId, sessionId));
    long taskId = session.session().agentTaskId();
    AgentTaskMessageRepository taskMessageRepository = requiredAgentTaskMessageRepository();
    if (!taskMessageRepository.hasRun(taskId, runUuid)) {
      throw new org.congcong.algomentor.mentor.application.learningplan.LearningPlanException(
          "PRACTICE_RUN_NOT_FOUND", "题目训练运行不存在。");
    }
    String cursor = taskMessageRepository.realtimeProtocolVersion(taskId, runUuid)
        == PracticeRealtimeProtocol.REALTIME_PROTOCOL_VERSION
        ? PracticeRealtimeCursor.normalizeV2After(after)
        : PracticeRealtimeCursor.normalizeAfter(after);
    SseEmitter emitter = new SseEmitter(sseProperties.practiceMessageTimeoutMillis());
    PracticeRealtimeSseLifecycle lifecycle = new PracticeRealtimeSseLifecycle(sseOpsRecorder);
    lifecycle.opened();
    emitter.onCompletion(lifecycle::onCompletion);
    emitter.onTimeout(lifecycle::onTimeout);
    emitter.onError(lifecycle::onError);
    Thread reader = new Thread(
        () -> replayEvents(emitter, taskId, runUuid, cursor, lifecycle), "practice-realtime-sse");
    reader.setDaemon(true);
    reader.start();
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

  private PracticeRealtimeEventStore requiredRealtimeEventStore() {
    return realtimeEventStore.getIfAvailable(this::streamUnavailable);
  }

  private <T> T streamUnavailable() {
    throw new org.congcong.algomentor.mentor.application.learningplan.LearningPlanException(
        "PRACTICE_MESSAGE_STREAM_UNAVAILABLE",
        "题目训练消息流服务不可用。");
  }

  private void replayEvents(
      SseEmitter emitter,
      long taskId,
      String runUuid,
      String after,
      PracticeRealtimeSseLifecycle lifecycle
  ) {
    String cursor = after;
    try {
      while (lifecycle.active()) {
        List<PracticeRealtimeEvent> events = requiredRealtimeEventStore().readAfter(runUuid, cursor, true);
        for (PracticeRealtimeEvent event : events) {
          emitter.send(SseEmitter.event().id(event.cursor()).name(event.eventName()).data(event.data()));
          cursor = event.cursor();
          if (AgentStreamEventNames.AGENT_RUN_END.equals(event.eventName())
              || AgentStreamEventNames.AGENT_ERROR.equals(event.eventName())) {
            lifecycle.complete(emitter);
            return;
          }
        }
        // Redis 阻塞读取超时后以 PostgreSQL 状态为准，终态直接关闭，运行中继续等待。
        if (events.isEmpty()) {
          if (!requiredAgentTaskMessageRepository().isActiveRun(taskId, runUuid)) {
            lifecycle.complete(emitter);
            return;
          }
        }
      }
    } catch (IOException | RuntimeException exception) {
      lifecycle.fail(emitter, exception);
    }
  }

  private AgentTaskMessageRepository requiredAgentTaskMessageRepository() {
    return agentTaskMessageRepository.getIfAvailable(this::streamUnavailable);
  }

  private String eventsUrl(long sessionId, String runUuid) {
    return ApiContractConstants.PRACTICE_SESSIONS_BASE_PATH + "/" + sessionId
        + "/runs/" + runUuid + "/events";
  }

  private int requestSize(PracticeMessageRequest request) {
    return request.message().getBytes(StandardCharsets.UTF_8).length;
  }

  private String currentRequestLocale(String acceptLanguage) {
    return ApiErrorLocales.parse(acceptLanguage).toLanguageTag();
  }

  private PracticeProgressStatus parseProgressStatus(PracticeProgressStatusRequest request) {
    try {
      return PracticeProgressStatus.valueOf(request.status());
    } catch (RuntimeException exception) {
      throw new PracticeProgressStatusInvalidException("练习进度状态不合法。", exception);
    }
  }

  /** 仅管理 SSE 连接指标；Redis/浏览器故障不改变 Agent run 的业务终态。 */
  private static final class PracticeRealtimeSseLifecycle {

    private final SseOpsRecorder recorder;
    private final AtomicBoolean active = new AtomicBoolean(true);

    private PracticeRealtimeSseLifecycle(SseOpsRecorder recorder) {
      this.recorder = java.util.Objects.requireNonNull(recorder, "SSE ops recorder must not be null");
    }

    private void opened() {
      recorder.opened(SseStreamType.PRACTICE_MESSAGE);
    }

    private boolean active() {
      return active.get();
    }

    private void complete(SseEmitter emitter) {
      if (active.compareAndSet(true, false)) {
        recorder.completed(SseStreamType.PRACTICE_MESSAGE);
        emitter.complete();
      }
    }

    private void fail(SseEmitter emitter, Throwable exception) {
      if (active.compareAndSet(true, false)) {
        recorder.failed(SseStreamType.PRACTICE_MESSAGE, SseFailureType.UNKNOWN);
        log.warn("Practice realtime SSE connection failed. exceptionType={}",
            exception.getClass().getSimpleName());
        emitter.completeWithError(exception);
      }
    }

    private void onCompletion() {
      if (active.compareAndSet(true, false)) {
        recorder.clientDisconnected(SseStreamType.PRACTICE_MESSAGE);
      }
    }

    private void onTimeout() {
      if (active.compareAndSet(true, false)) {
        recorder.timeout(SseStreamType.PRACTICE_MESSAGE);
        recorder.failed(SseStreamType.PRACTICE_MESSAGE, SseFailureType.TIMEOUT);
        log.info("Practice realtime SSE connection timed out.");
      }
    }

    private void onError(Throwable exception) {
      if (active.compareAndSet(true, false)) {
        recorder.failed(SseStreamType.PRACTICE_MESSAGE, SseFailureType.SEND_FAILURE);
        log.info("Practice realtime SSE client disconnected. exceptionType={}",
            exception == null ? "unknown" : exception.getClass().getSimpleName());
      }
    }
  }
}
