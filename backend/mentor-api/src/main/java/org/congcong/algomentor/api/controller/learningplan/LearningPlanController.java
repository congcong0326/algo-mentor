package org.congcong.algomentor.api.controller.learningplan;

import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicBoolean;
import org.congcong.algomentor.ai.governance.admission.AiRunAdmissionException;
import org.congcong.algomentor.ai.governance.model.AiGovernanceErrorCode;
import org.congcong.algomentor.ai.governance.model.AiRunStatus;
import org.congcong.algomentor.api.config.ApiSseProperties;
import org.congcong.algomentor.api.config.ApiContractConstants;
import org.congcong.algomentor.api.learningplan.model.LearningPlanConfirmResponse;
import org.congcong.algomentor.api.learningplan.model.LearningPlanActivationResponse;
import org.congcong.algomentor.api.learningplan.model.LearningPlanCreateDraftRequest;
import org.congcong.algomentor.api.learningplan.model.LearningPlanDetailResponse;
import org.congcong.algomentor.api.learningplan.model.LearningPlanDraftResponse;
import org.congcong.algomentor.api.learningplan.model.LearningPlanDraftGenerationResponse;
import org.congcong.algomentor.api.learningplan.model.LearningPlanDraftRevisionGenerationResponse;
import org.congcong.algomentor.api.learningplan.model.LearningPlanDraftRevisionStatusResponse;
import org.congcong.algomentor.api.learningplan.model.LearningPlanExtensionApplyResponse;
import org.congcong.algomentor.api.learningplan.model.LearningPlanMessageRequest;
import org.congcong.algomentor.api.learningplan.model.LearningPlanPageResponse;
import org.congcong.algomentor.api.learningplan.model.LearningPlanResponseMapper;
import org.congcong.algomentor.api.learningplan.model.LearningPlanRhythmUpdateRequest;
import org.congcong.algomentor.api.learningplan.model.LearningPlanRevisionRequest;
import org.congcong.algomentor.api.learningplan.model.LearningPlanTemplateDraftRequest;
import org.congcong.algomentor.api.learningplan.service.LearningPlanDraftStreamSseMapper;
import org.congcong.algomentor.api.learningplan.service.LearningPlanProposalStreamSseMapper;
import org.congcong.algomentor.api.learningplan.service.SseLearningPlanDraftStreamSubscriber;
import org.congcong.algomentor.api.learningplan.service.SseLearningPlanProposalStreamSubscriber;
import org.congcong.algomentor.api.learningplan.realtime.LearningPlanGenerationRealtimeCursor;
import org.congcong.algomentor.api.learningplan.realtime.LearningPlanGenerationRealtimeCursorInvalidException;
import org.congcong.algomentor.api.learningplan.realtime.LearningPlanGenerationRealtimeEvent;
import org.congcong.algomentor.api.learningplan.realtime.LearningPlanGenerationRealtimeEventStore;
import org.congcong.algomentor.api.learningplan.realtime.LearningPlanGenerationRealtimeProtocol;
import org.congcong.algomentor.api.learningplan.realtime.UnavailableLearningPlanGenerationRealtimeEventStore;
import org.congcong.algomentor.api.learningplan.realtime.LearningPlanDraftRevisionRealtimeEvent;
import org.congcong.algomentor.api.learningplan.realtime.LearningPlanDraftRevisionRealtimeEventStore;
import org.congcong.algomentor.api.learningplan.realtime.LearningPlanDraftRevisionRealtimeProtocol;
import org.congcong.algomentor.api.learningplan.realtime.UnavailableLearningPlanDraftRevisionRealtimeEventStore;
import org.congcong.algomentor.auth.security.AuthenticatedUserPrincipal;
import org.congcong.algomentor.auth.security.CurrentUserIdProvider;
import org.congcong.algomentor.common.api.ApiResponse;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanDraftResult;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanActivation;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanActivationService;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanContractService;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanContractState;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanContractStateRepository;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanDraftService;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanDraft;
import org.congcong.algomentor.mentor.application.learningplan.policy.LearningPlanAiRevisionAccessService;
import org.congcong.algomentor.mentor.application.learningplan.policy.LearningPlanAiRevisionCapabilities;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanException;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanContentLocale;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanLoadService;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanService;
import org.congcong.algomentor.mentor.application.learningplan.proposal.LearningPlanExtensionApplyService;
import org.congcong.algomentor.mentor.application.learningplan.proposal.LearningPlanProposalGroupService;
import org.congcong.algomentor.mentor.application.learningplan.proposal.LearningPlanProposalRepository;
import org.congcong.algomentor.mentor.application.learningplan.proposal.LearningPlanProposalRevisionStatus;
import org.congcong.algomentor.mentor.application.learningplan.proposal.stream.LearningPlanDraftRevisionStreamService;
import org.congcong.algomentor.mentor.application.learningplan.proposal.stream.LearningPlanDraftRevisionGenerationService;
import org.congcong.algomentor.mentor.application.learningplan.proposal.stream.LearningPlanDraftRevisionGenerationStart;
import org.congcong.algomentor.mentor.application.learningplan.proposal.stream.LearningPlanExtensionProposalStreamService;
import org.congcong.algomentor.mentor.application.learningplan.proposal.stream.LearningPlanProposalStreamEvent;
import org.congcong.algomentor.mentor.application.learningplan.stream.LearningPlanDraftStreamService;
import org.congcong.algomentor.mentor.application.learningplan.stream.LearningPlanDraftGenerationService;
import org.congcong.algomentor.mentor.application.learningplan.stream.LearningPlanDraftGenerationStart;
import org.congcong.algomentor.mentor.application.learningplan.template.LearningPlanTemplateDraftService;
import org.congcong.algomentor.mentor.application.practice.PracticeProgress;
import org.congcong.algomentor.mentor.application.practice.PracticeSessionRepository;
import org.congcong.algomentor.ops.observability.LearningOpsRecorder;
import org.congcong.algomentor.ops.observability.NoopOpsRecorders;
import org.congcong.algomentor.ops.observability.SseOpsRecorder;
import org.congcong.algomentor.ops.observability.SseStreamType;
import org.congcong.algomentor.ops.observability.StructuredOpsLogger;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping(ApiContractConstants.LEARNING_PLANS_BASE_PATH)
public class LearningPlanController {

  private final LearningPlanDraftService draftService;
  private final LearningPlanService planService;
  private final CurrentUserIdProvider currentUserIdProvider;
  private final ObjectProvider<LearningPlanDraftStreamService> draftStreamServiceProvider;
  private final ObjectProvider<LearningPlanDraftGenerationService> draftGenerationServiceProvider;
  private final ObjectProvider<LearningPlanGenerationRealtimeEventStore> generationRealtimeEventStoreProvider;
  private final ObjectProvider<LearningPlanDraftRevisionStreamService> draftRevisionStreamServiceProvider;
  private final ObjectProvider<LearningPlanDraftRevisionGenerationService> draftRevisionGenerationServiceProvider;
  private final ObjectProvider<LearningPlanDraftRevisionRealtimeEventStore> draftRevisionRealtimeEventStoreProvider;
  private final ObjectProvider<LearningPlanProposalRepository> proposalRepositoryProvider;
  private final ObjectProvider<LearningPlanExtensionProposalStreamService> extensionProposalStreamServiceProvider;
  private final ObjectProvider<LearningPlanExtensionApplyService> extensionApplyServiceProvider;
  private final ObjectProvider<LearningPlanProposalGroupService> proposalGroupServiceProvider;
  private final ObjectProvider<PracticeSessionRepository> practiceSessionRepositoryProvider;
  private final ObjectProvider<LearningPlanContractStateRepository> contractStateRepositoryProvider;
  private final ObjectProvider<LearningPlanTemplateDraftService> templateDraftServiceProvider;
  private final ObjectProvider<LearningPlanActivationService> activationServiceProvider;
  private final LearningPlanAiRevisionAccessService aiRevisionAccessService;
  private final LearningPlanLoadService loadService;
  private final LearningPlanContractService contractService;
  private final LearningPlanDraftStreamSseMapper draftStreamSseMapper;
  private final LearningPlanProposalStreamSseMapper proposalStreamSseMapper;
  private final ApiSseProperties sseProperties;
  private final SseOpsRecorder sseOpsRecorder;
  private final LearningOpsRecorder learningOpsRecorder;
  private final StructuredOpsLogger opsLogger;

  public LearningPlanController(
      LearningPlanDraftService draftService,
      LearningPlanService planService,
      CurrentUserIdProvider currentUserIdProvider,
      ObjectProvider<LearningPlanDraftStreamService> draftStreamServiceProvider,
      ObjectProvider<LearningPlanDraftGenerationService> draftGenerationServiceProvider,
      ObjectProvider<LearningPlanGenerationRealtimeEventStore> generationRealtimeEventStoreProvider,
      ObjectProvider<LearningPlanDraftRevisionStreamService> draftRevisionStreamServiceProvider,
      ObjectProvider<LearningPlanDraftRevisionGenerationService> draftRevisionGenerationServiceProvider,
      ObjectProvider<LearningPlanDraftRevisionRealtimeEventStore> draftRevisionRealtimeEventStoreProvider,
      ObjectProvider<LearningPlanProposalRepository> proposalRepositoryProvider,
      ObjectProvider<LearningPlanExtensionProposalStreamService> extensionProposalStreamServiceProvider,
      ObjectProvider<LearningPlanExtensionApplyService> extensionApplyServiceProvider,
      ObjectProvider<LearningPlanProposalGroupService> proposalGroupServiceProvider,
      ObjectProvider<PracticeSessionRepository> practiceSessionRepositoryProvider,
      ObjectProvider<LearningPlanContractStateRepository> contractStateRepositoryProvider,
      ObjectProvider<LearningPlanTemplateDraftService> templateDraftServiceProvider,
      ObjectProvider<LearningPlanActivationService> activationServiceProvider,
      ObjectProvider<LearningPlanLoadService> loadServiceProvider,
      ObjectProvider<LearningPlanContractService> contractServiceProvider,
      ObjectProvider<LearningPlanAiRevisionAccessService> aiRevisionAccessServiceProvider,
      ApiSseProperties sseProperties,
      ObjectProvider<SseOpsRecorder> sseOpsRecorder,
      ObjectProvider<LearningOpsRecorder> learningOpsRecorder) {
    this.draftService = draftService;
    this.planService = planService;
    this.currentUserIdProvider = currentUserIdProvider;
    this.draftStreamServiceProvider = draftStreamServiceProvider;
    this.draftGenerationServiceProvider = draftGenerationServiceProvider;
    this.generationRealtimeEventStoreProvider = generationRealtimeEventStoreProvider;
    this.draftRevisionStreamServiceProvider = draftRevisionStreamServiceProvider;
    this.draftRevisionGenerationServiceProvider = draftRevisionGenerationServiceProvider;
    this.draftRevisionRealtimeEventStoreProvider = draftRevisionRealtimeEventStoreProvider;
    this.proposalRepositoryProvider = proposalRepositoryProvider;
    this.extensionProposalStreamServiceProvider = extensionProposalStreamServiceProvider;
    this.extensionApplyServiceProvider = extensionApplyServiceProvider;
    this.proposalGroupServiceProvider = proposalGroupServiceProvider;
    this.practiceSessionRepositoryProvider = practiceSessionRepositoryProvider;
    this.contractStateRepositoryProvider = contractStateRepositoryProvider;
    this.templateDraftServiceProvider = templateDraftServiceProvider;
    this.activationServiceProvider = activationServiceProvider;
    this.loadService = loadServiceProvider.getIfAvailable(LearningPlanLoadService::new);
    this.contractService = contractServiceProvider.getIfAvailable(LearningPlanContractService::new);
    this.aiRevisionAccessService = aiRevisionAccessServiceProvider.getIfAvailable(
        () -> new LearningPlanAiRevisionAccessService(
            ignored -> LearningPlanAiRevisionCapabilities.disabled()));
    this.draftStreamSseMapper = new LearningPlanDraftStreamSseMapper();
    this.proposalStreamSseMapper = new LearningPlanProposalStreamSseMapper();
    this.sseProperties = sseProperties;
    this.sseOpsRecorder = sseOpsRecorder.getIfAvailable(NoopOpsRecorders::sse);
    this.learningOpsRecorder = learningOpsRecorder.getIfAvailable(NoopOpsRecorders::learning);
    this.opsLogger = new StructuredOpsLogger();
  }

  @PostMapping(ApiContractConstants.LEARNING_PLAN_DRAFT_GENERATIONS_PATH)
  public ResponseEntity<?> startDraftGeneration(
      @RequestHeader(name = ApiContractConstants.IDEMPOTENCY_KEY_HEADER, required = false) String idempotencyKey,
      @RequestHeader(name = ApiContractConstants.ACCEPT_LANGUAGE_HEADER, required = false) String acceptLanguage,
      @RequestBody LearningPlanCreateDraftRequest request,
      HttpServletResponse response
  ) {
    addLanguageVaryHeader(response);
    long userId = requireCurrentUserId();
    LearningPlanDraftGenerationStart start = requiredDraftGenerationService().start(
        userId,
        request.toBrief(LearningPlanContentLocale.fromAcceptLanguage(acceptLanguage)),
        idempotencyKey);
    LearningPlanDraft generated = start.draft();
    if (generated.status() == org.congcong.algomentor.mentor.application.learningplan.LearningPlanDraftStatus.GENERATING) {
      return ResponseEntity.status(HttpStatus.ACCEPTED)
          .location(URI.create(draftUrl(generated.id())))
          .body(ApiResponse.success(new LearningPlanDraftGenerationResponse(
              generated.id(),
              generated.status(),
              draftEventsUrl(generated.id()),
              LearningPlanGenerationRealtimeProtocol.INITIAL_AFTER,
              LearningPlanGenerationRealtimeProtocol.REALTIME_PROTOCOL_VERSION)));
    }
    return ResponseEntity.status(start.newlyStarted() ? HttpStatus.CREATED : HttpStatus.OK)
        .location(URI.create(draftUrl(generated.id())))
        .body(ApiResponse.success(LearningPlanResponseMapper.toDraftResponse(generated)));
  }

  @GetMapping(ApiContractConstants.LEARNING_PLAN_DRAFTS_PATH + "/{draftId}")
  public ApiResponse<LearningPlanDraftResponse> getDraft(@PathVariable long draftId) {
    return ApiResponse.success(LearningPlanResponseMapper.toDraftResponse(
        draftService.findDraft(requireCurrentUserId(), draftId)));
  }

  @GetMapping(value = ApiContractConstants.LEARNING_PLAN_DRAFT_GENERATION_EVENTS_PATH,
      produces = MediaType.TEXT_EVENT_STREAM_VALUE)
  public SseEmitter draftGenerationEvents(
      @PathVariable long draftId,
      @RequestParam(name = ApiContractConstants.LEARNING_PLAN_DRAFT_GENERATION_AFTER_PARAM, required = false) String after
  ) {
    long userId = requireCurrentUserId();
    draftService.findDraft(userId, draftId);
    String cursor;
    try {
      cursor = LearningPlanGenerationRealtimeCursor.normalizeAfter(after);
    } catch (LearningPlanGenerationRealtimeCursorInvalidException exception) {
      throw new LearningPlanException(
          LearningPlanGenerationRealtimeProtocol.CURSOR_INVALID_CODE, "学习计划生成事件游标无效。");
    }
    if (!requiredGenerationRealtimeEventStore().available()) {
      throw new LearningPlanException(
          LearningPlanGenerationRealtimeProtocol.REALTIME_UNAVAILABLE_CODE,
          "学习计划实时进度暂不可用，请查询草案状态。");
    }
    SseEmitter emitter = new SseEmitter(sseProperties.learningPlanDraftTimeoutMillis());
    AtomicBoolean connectionOpen = new AtomicBoolean(true);
    emitter.onCompletion(() -> connectionOpen.set(false));
    emitter.onTimeout(() -> connectionOpen.set(false));
    emitter.onError(ignored -> connectionOpen.set(false));
    Thread reader = new Thread(
        () -> replayDraftGenerationEvents(emitter, userId, draftId, cursor, connectionOpen),
        "learning-plan-draft-events");
    reader.setDaemon(true);
    reader.start();
    return emitter;
  }

  @PostMapping(value = ApiContractConstants.LEARNING_PLAN_DRAFTS_STREAM_PATH,
      produces = MediaType.TEXT_EVENT_STREAM_VALUE)
  public SseEmitter streamDraft(
      @RequestHeader(name = ApiContractConstants.ACCEPT_LANGUAGE_HEADER, required = false) String acceptLanguage,
      @RequestBody LearningPlanCreateDraftRequest request,
      HttpServletResponse response
  ) {
    addLanguageVaryHeader(response);
    long userId = requireCurrentUserId();
    String runId = UUID.randomUUID().toString();
    SseEmitter emitter = new SseEmitter(sseProperties.learningPlanDraftTimeoutMillis());
    SseLearningPlanDraftStreamSubscriber subscriber = new SseLearningPlanDraftStreamSubscriber(
        emitter,
        draftStreamSseMapper,
        SseStreamType.LEARNING_PLAN_DRAFT,
        sseOpsRecorder,
        learningOpsRecorder,
        opsLogger);
    emitter.onCompletion(() -> subscriber.clientDisconnected(null));
    emitter.onTimeout(subscriber::timeout);
    emitter.onError(subscriber::clientDisconnected);

    try {
      requiredDraftStreamService()
          .stream(
              userId,
              request.toBrief(LearningPlanContentLocale.fromAcceptLanguage(acceptLanguage)),
              runId,
              Map.of())
          .subscribe(subscriber);
    } catch (RuntimeException exception) {
      subscriber.onError(exception);
    }
    return emitter;
  }

  @PostMapping(value = ApiContractConstants.LEARNING_PLAN_DRAFTS_PATH
      + ApiContractConstants.LEARNING_PLAN_DRAFT_REVISIONS_STREAM_PATH,
      produces = MediaType.TEXT_EVENT_STREAM_VALUE)
  public SseEmitter streamDraftRevision(
      @PathVariable long draftId,
      @RequestBody LearningPlanRevisionRequest request) {
    long userId = requireCurrentUserId();
    LearningPlanDraft draft = draftService.findDraft(userId, draftId);
    if (draft != null) {
      aiRevisionAccessService.requireDraftRevision(userId, draft.source());
    }
    String instruction = normalizedInstruction(request);
    return proposalStream(runId -> requiredDraftRevisionStreamService()
        .stream(userId, draftId, instruction, runId, Map.of()));
  }

  @PostMapping(ApiContractConstants.LEARNING_PLAN_DRAFT_REVISION_GENERATIONS_PATH)
  public ResponseEntity<ApiResponse<LearningPlanDraftRevisionGenerationResponse>> startDraftRevisionGeneration(
      @PathVariable long draftId,
      @RequestHeader(name = ApiContractConstants.IDEMPOTENCY_KEY_HEADER, required = false) String idempotencyKey,
      @RequestBody LearningPlanRevisionRequest request) {
    LearningPlanDraftRevisionGenerationStart start = requiredDraftRevisionGenerationService().start(
        requireCurrentUserId(), draftId, normalizedInstruction(request), idempotencyKey);
    var revision = start.revision();
    return ResponseEntity.status(HttpStatus.ACCEPTED)
        .location(URI.create(draftRevisionUrl(draftId, revision.id())))
        .body(ApiResponse.success(new LearningPlanDraftRevisionGenerationResponse(
            revision.id(), revision.proposalGroupId(), revision.draftId(), revision.revisionNo(), revision.status(),
            draftRevisionEventsUrl(draftId, revision.id()), LearningPlanDraftRevisionRealtimeProtocol.INITIAL_AFTER,
            LearningPlanDraftRevisionRealtimeProtocol.REALTIME_PROTOCOL_VERSION)));
  }

  @GetMapping(ApiContractConstants.LEARNING_PLAN_DRAFT_REVISION_STATUS_PATH)
  public ApiResponse<LearningPlanDraftRevisionStatusResponse> getDraftRevisionStatus(
      @PathVariable long draftId,
      @PathVariable long revisionId) {
    var revision = requiredProposalRepository().findDraftRevisionForUserAndDraft(
            revisionId, requireCurrentUserId(), draftId)
        .orElseThrow(() -> new LearningPlanException("LEARNING_PLAN_PROPOSAL_REVISION_NOT_FOUND", "学习计划草案修订记录不存在。"));
    return ApiResponse.success(LearningPlanDraftRevisionStatusResponse.fromRevision(revision));
  }

  @GetMapping(value = ApiContractConstants.LEARNING_PLAN_DRAFT_REVISION_EVENTS_PATH,
      produces = MediaType.TEXT_EVENT_STREAM_VALUE)
  public SseEmitter draftRevisionEvents(
      @PathVariable long draftId,
      @PathVariable long revisionId,
      @RequestParam(name = ApiContractConstants.LEARNING_PLAN_DRAFT_REVISION_AFTER_PARAM, required = false) String after) {
    long userId = requireCurrentUserId();
    requiredProposalRepository().findDraftRevisionForUserAndDraft(revisionId, userId, draftId)
        .orElseThrow(() -> new LearningPlanException("LEARNING_PLAN_PROPOSAL_REVISION_NOT_FOUND", "学习计划草案修订记录不存在。"));
    String cursor;
    try {
      cursor = LearningPlanGenerationRealtimeCursor.normalizeAfter(after);
    } catch (LearningPlanGenerationRealtimeCursorInvalidException exception) {
      throw new LearningPlanException(
          LearningPlanDraftRevisionRealtimeProtocol.CURSOR_INVALID_CODE, "学习计划草案修订事件游标无效。");
    }
    if (!requiredDraftRevisionRealtimeEventStore().available()) {
      throw new LearningPlanException(
          LearningPlanDraftRevisionRealtimeProtocol.REALTIME_UNAVAILABLE_CODE,
          "学习计划修订实时进度暂不可用，请查询修订状态。");
    }
    SseEmitter emitter = new SseEmitter(sseProperties.learningPlanDraftTimeoutMillis());
    AtomicBoolean connectionOpen = new AtomicBoolean(true);
    emitter.onCompletion(() -> connectionOpen.set(false));
    emitter.onTimeout(() -> connectionOpen.set(false));
    emitter.onError(ignored -> connectionOpen.set(false));
    Thread reader = new Thread(
        () -> replayDraftRevisionEvents(emitter, userId, draftId, revisionId, cursor, connectionOpen),
        "learning-plan-draft-revision-events");
    reader.setDaemon(true);
    reader.start();
    return emitter;
  }

  @PostMapping(value = ApiContractConstants.LEARNING_PLAN_EXTENSION_PROPOSALS_STREAM_PATH,
      produces = MediaType.TEXT_EVENT_STREAM_VALUE)
  public SseEmitter streamExtensionProposal(
      @PathVariable long planId,
      @RequestBody LearningPlanRevisionRequest request) {
    long userId = requireCurrentUserId();
    aiRevisionAccessService.requireSavedPlanRevision(userId);
    String instruction = normalizedInstruction(request);
    return proposalStream(runId -> requiredExtensionProposalStreamService()
        .streamFirstRevision(userId, planId, instruction, runId, Map.of()));
  }

  @PostMapping(value = ApiContractConstants.LEARNING_PLAN_EXTENSION_PROPOSAL_REVISIONS_STREAM_PATH,
      produces = MediaType.TEXT_EVENT_STREAM_VALUE)
  public SseEmitter streamExtensionProposalRevision(
      @PathVariable long planId,
      @PathVariable long proposalGroupId,
      @RequestBody LearningPlanRevisionRequest request) {
    long userId = requireCurrentUserId();
    aiRevisionAccessService.requireSavedPlanRevision(userId);
    String instruction = normalizedInstruction(request);
    return proposalStream(runId -> requiredExtensionProposalStreamService()
        .streamNextRevision(userId, planId, proposalGroupId, instruction, runId, Map.of()));
  }

  @GetMapping("/ai-revision-capabilities")
  public ApiResponse<LearningPlanAiRevisionCapabilities> aiRevisionCapabilities(HttpServletResponse response) {
    response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
    return ApiResponse.success(aiRevisionAccessService.capabilities(requireCurrentUserId()));
  }

  @PostMapping(ApiContractConstants.LEARNING_PLAN_DRAFTS_PATH
      + ApiContractConstants.LEARNING_PLAN_DRAFT_MESSAGES_PATH)
  public ApiResponse<LearningPlanDraftResponse> continueDraft(
      @PathVariable long draftId,
      @RequestBody LearningPlanMessageRequest request) {
    long userId = requireCurrentUserId();
    return ApiResponse.success(LearningPlanResponseMapper.toDraftResponse(
        draftService.continueDraft(userId, draftId, request.message())));
  }

  @PostMapping(ApiContractConstants.LEARNING_PLAN_DRAFT_FROM_TEMPLATE_PATH)
  public ApiResponse<LearningPlanDraftResponse> createDraftFromTemplate(
      @RequestHeader(name = ApiContractConstants.ACCEPT_LANGUAGE_HEADER, required = false) String acceptLanguage,
      @RequestBody LearningPlanTemplateDraftRequest request,
      HttpServletResponse response) {
    addLanguageVaryHeader(response);
    long userId = requireCurrentUserId();
    LearningPlanDraftResult result = requiredTemplateDraftService().createDraft(
        userId,
        request.toCommand(LearningPlanContentLocale.fromAcceptLanguage(acceptLanguage)));
    return ApiResponse.success(LearningPlanResponseMapper.toDraftResponse(result));
  }

  private void addLanguageVaryHeader(HttpServletResponse response) {
    response.addHeader(HttpHeaders.VARY, ApiContractConstants.ACCEPT_LANGUAGE_HEADER);
  }

  @PostMapping(ApiContractConstants.LEARNING_PLAN_DRAFTS_PATH
      + ApiContractConstants.LEARNING_PLAN_DRAFT_CONFIRM_PATH)
  public ApiResponse<LearningPlanConfirmResponse> confirmDraft(@PathVariable long draftId) {
    long userId = requireCurrentUserId();
    LearningPlanConfirmResponse response = LearningPlanResponseMapper.toConfirmResponse(draftService.confirmDraft(userId, draftId));
    activationServiceProvider.ifAvailable(service -> service.activateIfAbsent(userId, response.planId()));
    return ApiResponse.success(response);
  }

  @PostMapping(ApiContractConstants.LEARNING_PLAN_EXTENSION_PROPOSAL_APPLY_PATH)
  public ApiResponse<LearningPlanExtensionApplyResponse> applyExtensionProposal(
      @PathVariable long planId,
      @PathVariable long proposalGroupId) {
    long userId = requireCurrentUserId();
    return ApiResponse.success(LearningPlanExtensionApplyResponse.fromResult(
        requiredExtensionApplyService().apply(userId, planId, proposalGroupId)));
  }

  @PostMapping(ApiContractConstants.LEARNING_PLAN_EXTENSION_PROPOSAL_DISCARD_PATH)
  public ApiResponse<Void> discardExtensionProposal(
      @PathVariable long planId,
      @PathVariable long proposalGroupId) {
    long userId = requireCurrentUserId();
    requiredProposalGroupService().discardExtensionProposal(userId, planId, proposalGroupId);
    return ApiResponse.success(null);
  }

  @GetMapping
  public ApiResponse<LearningPlanPageResponse> listPlans(
      @RequestParam(required = false) Integer page,
      @RequestParam(required = false) Integer pageSize) {
    long userId = requireCurrentUserId();
    return ApiResponse.success(LearningPlanResponseMapper.toPageResponse(
        planService.listPlans(userId, page, pageSize),
        activePlanId(userId)));
  }

  @GetMapping("/{planId}")
  public ApiResponse<LearningPlanDetailResponse> getPlan(@PathVariable long planId) {
    long userId = requireCurrentUserId();
    return ApiResponse.success(detailResponse(userId, planId));
  }

  @PatchMapping(ApiContractConstants.LEARNING_PLAN_RHYTHM_PATH)
  public ApiResponse<LearningPlanDetailResponse> updateRhythm(
      @PathVariable long planId,
      @RequestBody LearningPlanRhythmUpdateRequest request) {
    long userId = requireCurrentUserId();
    planService.updateRhythm(
        userId,
        planId,
        request == null ? null : request.dailyProblemCount(),
        request == null ? null : request.trainingDaysPerWeek());
    return ApiResponse.success(detailResponse(userId, planId));
  }

  @PostMapping(ApiContractConstants.LEARNING_PLAN_ACTIVATION_PATH)
  public ApiResponse<LearningPlanActivationResponse> activatePlan(@PathVariable long planId) {
    long userId = requireCurrentUserId();
    LearningPlanActivation activation = requiredActivationService().activate(userId, planId);
    return ApiResponse.success(new LearningPlanActivationResponse(activation.planId(), activation.activatedAt()));
  }

  @PostMapping(ApiContractConstants.LEARNING_PLAN_CONTRACT_PAUSE_PATH)
  public ApiResponse<LearningPlanDetailResponse> pauseContract(@PathVariable long planId) {
    long userId = requireCurrentUserId();
    LearningPlanDetailResponse current = detailResponse(userId, planId);
    requiredContractStateRepository().pause(
        userId,
        planId,
        current.livingContractSummary() == null ? null : current.livingContractSummary().estimatedCompletionDate());
    return ApiResponse.success(detailResponse(userId, planId));
  }

  @PostMapping(ApiContractConstants.LEARNING_PLAN_CONTRACT_RESUME_PATH)
  public ApiResponse<LearningPlanDetailResponse> resumeContract(@PathVariable long planId) {
    long userId = requireCurrentUserId();
    requiredContractStateRepository().resume(userId, planId, Instant.now());
    return ApiResponse.success(detailResponse(userId, planId));
  }

  @PostMapping(ApiContractConstants.LEARNING_PLAN_CONTRACT_CLOSE_OUT_PATH)
  public ApiResponse<LearningPlanDetailResponse> closeOutContract(@PathVariable long planId) {
    long userId = requireCurrentUserId();
    LearningPlanDetailResponse current = detailResponse(userId, planId);
    requiredContractStateRepository().closeOut(
        userId,
        planId,
        current.livingContractSummary() == null ? null : current.livingContractSummary().estimatedCompletionDate());
    return ApiResponse.success(detailResponse(userId, planId));
  }

  @DeleteMapping("/{planId}")
  public ApiResponse<Void> deletePlan(@PathVariable long planId) {
    long userId = requireCurrentUserId();
    planService.deletePlan(userId, planId);
    return ApiResponse.success(null);
  }

  private long requireCurrentUserId() {
    return currentUserIdProvider.currentUser()
        .map(AuthenticatedUserPrincipal::userId)
        .orElseThrow(() -> new LearningPlanUnauthenticatedException("当前请求未登录或无法解析当前用户。"));
  }

  private void replayDraftGenerationEvents(
      SseEmitter emitter,
      long userId,
      long draftId,
      String initialCursor,
      AtomicBoolean connectionOpen
  ) {
    String cursor = initialCursor;
    boolean replay = true;
    try {
      while (connectionOpen.get()) {
        List<LearningPlanGenerationRealtimeEvent> events = requiredGenerationRealtimeEventStore()
            .readAfter(draftId, cursor, !replay);
        replay = false;
        for (LearningPlanGenerationRealtimeEvent event : events) {
          if (!connectionOpen.get()) {
            return;
          }
          emitter.send(SseEmitter.event().id(event.cursor()).name(event.eventName()).data(event.data()));
          cursor = event.cursor();
          if (isDraftGenerationTerminalEvent(event.eventName())) {
            emitter.complete();
            return;
          }
        }
        LearningPlanDraft current = draftService.findDraft(userId, draftId);
        if (current.status() != org.congcong.algomentor.mentor.application.learningplan.LearningPlanDraftStatus.GENERATING) {
          // Stream 过期、Redis 故障恢复后的 entry 缺失都由前端 EOF 后回读 PostgreSQL 收束。
          emitter.complete();
          return;
        }
      }
    } catch (IOException | RuntimeException exception) {
      if (connectionOpen.get()) {
        emitter.completeWithError(exception);
      }
    }
  }

  private boolean isDraftGenerationTerminalEvent(String eventName) {
    return LearningPlanGenerationRealtimeProtocol.DRAFT_COMPLETED.equals(eventName)
        || LearningPlanGenerationRealtimeProtocol.DRAFT_FAILED.equals(eventName);
  }

  private void replayDraftRevisionEvents(
      SseEmitter emitter,
      long userId,
      long draftId,
      long revisionId,
      String initialCursor,
      AtomicBoolean connectionOpen) {
    String cursor = initialCursor;
    boolean replay = true;
    try {
      while (connectionOpen.get()) {
        List<LearningPlanDraftRevisionRealtimeEvent> events = requiredDraftRevisionRealtimeEventStore()
            .readAfter(draftId, revisionId, cursor, !replay);
        replay = false;
        for (LearningPlanDraftRevisionRealtimeEvent event : events) {
          if (!connectionOpen.get()) {
            return;
          }
          emitter.send(SseEmitter.event().id(event.cursor()).name(event.eventName()).data(event.data()));
          cursor = event.cursor();
          if (isDraftRevisionTerminalEvent(event.eventName())) {
            emitter.complete();
            return;
          }
        }
        var revision = requiredProposalRepository().findDraftRevisionForUserAndDraft(revisionId, userId, draftId)
            .orElse(null);
        if (revision == null || revision.status() != LearningPlanProposalRevisionStatus.GENERATING) {
          emitter.complete();
          return;
        }
      }
    } catch (IOException | RuntimeException exception) {
      if (connectionOpen.get()) {
        emitter.completeWithError(exception);
      }
    }
  }

  private boolean isDraftRevisionTerminalEvent(String eventName) {
    return LearningPlanDraftRevisionRealtimeProtocol.REVISION_COMPLETED.equals(eventName)
        || LearningPlanDraftRevisionRealtimeProtocol.REVISION_FAILED.equals(eventName)
        || LearningPlanDraftRevisionRealtimeProtocol.REVISION_SUPERSEDED.equals(eventName);
  }

  private String draftUrl(long draftId) {
    return ApiContractConstants.LEARNING_PLANS_BASE_PATH
        + ApiContractConstants.LEARNING_PLAN_DRAFTS_PATH + "/" + draftId;
  }

  private String draftEventsUrl(long draftId) {
    return ApiContractConstants.LEARNING_PLANS_BASE_PATH + "/drafts/" + draftId + "/events";
  }

  private String draftRevisionUrl(long draftId, long revisionId) {
    return ApiContractConstants.LEARNING_PLANS_BASE_PATH + "/drafts/" + draftId + "/revisions/" + revisionId;
  }

  private String draftRevisionEventsUrl(long draftId, long revisionId) {
    return draftRevisionUrl(draftId, revisionId) + "/events";
  }

  private List<PracticeProgress> progressByPlan(long userId, long planId) {
    PracticeSessionRepository repository = practiceSessionRepositoryProvider.getIfAvailable();
    if (repository == null) {
      return List.of();
    }
    try {
      return repository.findProgressByPlan(userId, planId);
    } catch (UnsupportedOperationException exception) {
      return List.of();
    }
  }

  private LearningPlanDetailResponse detailResponse(long userId, long planId) {
    List<PracticeProgress> progress = progressByPlan(userId, planId);
    LearningPlanContractState state = contractStateByPlan(userId, planId);
    Long activePlanId = activePlanId(userId);
    return LearningPlanResponseMapper.toDetailResponse(
        planService.getPlan(userId, planId),
        progress,
        loadService,
        contractService,
        state,
        activePlanId != null && activePlanId == planId);
  }

  private Long activePlanId(long userId) {
    LearningPlanActivationService service = activationServiceProvider.getIfAvailable();
    if (service == null) {
      return null;
    }
    return service.findActivePlanId(userId).orElse(null);
  }

  private LearningPlanContractState contractStateByPlan(long userId, long planId) {
    LearningPlanContractStateRepository repository = contractStateRepositoryProvider.getIfAvailable();
    if (repository == null) {
      return LearningPlanContractState.empty(userId, planId);
    }
    return repository.findByPlan(userId, planId).orElseGet(() -> LearningPlanContractState.empty(userId, planId));
  }

  private LearningPlanContractStateRepository requiredContractStateRepository() {
    return contractStateRepositoryProvider.getIfAvailable(() -> {
      throw new LearningPlanException(
          "LEARNING_PLAN_CONTRACT_STATE_UNAVAILABLE",
          "学习计划契约状态服务不可用。");
    });
  }

  private SseEmitter proposalStream(ProposalStreamFactory streamFactory) {
    String runId = UUID.randomUUID().toString();
    SseEmitter emitter = new SseEmitter(sseProperties.learningPlanDraftTimeoutMillis());
    SseLearningPlanProposalStreamSubscriber subscriber = new SseLearningPlanProposalStreamSubscriber(
        emitter,
        proposalStreamSseMapper,
        SseStreamType.LEARNING_PLAN_PROPOSAL,
        sseOpsRecorder,
        opsLogger,
        runId);
    emitter.onCompletion(() -> subscriber.clientDisconnected(null));
    emitter.onTimeout(subscriber::timeout);
    emitter.onError(subscriber::clientDisconnected);

    try {
      streamFactory.stream(runId).subscribe(subscriber);
    } catch (RuntimeException exception) {
      subscriber.onError(exception);
    }
    return emitter;
  }

  private String normalizedInstruction(LearningPlanRevisionRequest request) {
    return request == null ? "" : request.normalizedInstruction();
  }

  private LearningPlanDraftStreamService requiredDraftStreamService() {
    return draftStreamServiceProvider.getIfAvailable(() -> {
      throw unavailableGovernance();
    });
  }

  private LearningPlanDraftGenerationService requiredDraftGenerationService() {
    return draftGenerationServiceProvider.getIfAvailable(() -> {
      throw unavailableGovernance();
    });
  }

  private LearningPlanGenerationRealtimeEventStore requiredGenerationRealtimeEventStore() {
    return generationRealtimeEventStoreProvider.getIfAvailable(UnavailableLearningPlanGenerationRealtimeEventStore::new);
  }

  private LearningPlanDraftRevisionStreamService requiredDraftRevisionStreamService() {
    return draftRevisionStreamServiceProvider.getIfAvailable(() -> {
      throw unavailableGovernance();
    });
  }

  private LearningPlanDraftRevisionGenerationService requiredDraftRevisionGenerationService() {
    return draftRevisionGenerationServiceProvider.getIfAvailable(() -> {
      throw unavailableGovernance();
    });
  }

  private LearningPlanDraftRevisionRealtimeEventStore requiredDraftRevisionRealtimeEventStore() {
    return draftRevisionRealtimeEventStoreProvider.getIfAvailable(UnavailableLearningPlanDraftRevisionRealtimeEventStore::new);
  }

  private LearningPlanProposalRepository requiredProposalRepository() {
    return proposalRepositoryProvider.getIfAvailable(() -> {
      throw new LearningPlanException("LEARNING_PLAN_REPOSITORY_UNAVAILABLE", "学习计划草案修订服务不可用。");
    });
  }

  private LearningPlanExtensionProposalStreamService requiredExtensionProposalStreamService() {
    return extensionProposalStreamServiceProvider.getIfAvailable(() -> {
      throw unavailableGovernance();
    });
  }

  private LearningPlanExtensionApplyService requiredExtensionApplyService() {
    return extensionApplyServiceProvider.getIfAvailable(() -> {
      throw unavailableGovernance();
    });
  }

  private LearningPlanProposalGroupService requiredProposalGroupService() {
    return proposalGroupServiceProvider.getIfAvailable(() -> {
      throw unavailableGovernance();
    });
  }

  private LearningPlanTemplateDraftService requiredTemplateDraftService() {
    LearningPlanTemplateDraftService service = templateDraftServiceProvider.getIfAvailable();
    if (service == null) {
      throw new LearningPlanException("LEARNING_PLAN_REPOSITORY_UNAVAILABLE", "学习计划模板服务不可用。");
    }
    return service;
  }

  private LearningPlanActivationService requiredActivationService() {
    LearningPlanActivationService service = activationServiceProvider.getIfAvailable();
    if (service == null) {
      throw new LearningPlanException("LEARNING_PLAN_ACTIVE_SELECTION_UNAVAILABLE", "学习计划激活服务不可用。");
    }
    return service;
  }

  private AiRunAdmissionException unavailableGovernance() {
    return new AiRunAdmissionException(
        AiGovernanceErrorCode.AI_PROVIDER_UNAVAILABLE,
        AiRunStatus.REJECTED_DISABLED,
        "AI 治理服务暂不可用。",
        HttpStatus.SERVICE_UNAVAILABLE,
        Map.of());
  }

  @FunctionalInterface
  private interface ProposalStreamFactory {
    Flow.Publisher<LearningPlanProposalStreamEvent> stream(String runId);
  }
}
