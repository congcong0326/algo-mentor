package org.congcong.algomentor.api.controller.learningplan;

import jakarta.servlet.http.HttpServletResponse;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Flow;
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
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanException;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanContentLocale;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanLoadService;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanService;
import org.congcong.algomentor.mentor.application.learningplan.proposal.LearningPlanExtensionApplyService;
import org.congcong.algomentor.mentor.application.learningplan.proposal.LearningPlanProposalGroupService;
import org.congcong.algomentor.mentor.application.learningplan.proposal.stream.LearningPlanDraftRevisionStreamService;
import org.congcong.algomentor.mentor.application.learningplan.proposal.stream.LearningPlanExtensionProposalStreamService;
import org.congcong.algomentor.mentor.application.learningplan.proposal.stream.LearningPlanProposalStreamEvent;
import org.congcong.algomentor.mentor.application.learningplan.stream.LearningPlanDraftStreamService;
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
  private final ObjectProvider<LearningPlanDraftRevisionStreamService> draftRevisionStreamServiceProvider;
  private final ObjectProvider<LearningPlanExtensionProposalStreamService> extensionProposalStreamServiceProvider;
  private final ObjectProvider<LearningPlanExtensionApplyService> extensionApplyServiceProvider;
  private final ObjectProvider<LearningPlanProposalGroupService> proposalGroupServiceProvider;
  private final ObjectProvider<PracticeSessionRepository> practiceSessionRepositoryProvider;
  private final ObjectProvider<LearningPlanContractStateRepository> contractStateRepositoryProvider;
  private final ObjectProvider<LearningPlanTemplateDraftService> templateDraftServiceProvider;
  private final ObjectProvider<LearningPlanActivationService> activationServiceProvider;
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
      ObjectProvider<LearningPlanDraftRevisionStreamService> draftRevisionStreamServiceProvider,
      ObjectProvider<LearningPlanExtensionProposalStreamService> extensionProposalStreamServiceProvider,
      ObjectProvider<LearningPlanExtensionApplyService> extensionApplyServiceProvider,
      ObjectProvider<LearningPlanProposalGroupService> proposalGroupServiceProvider,
      ObjectProvider<PracticeSessionRepository> practiceSessionRepositoryProvider,
      ObjectProvider<LearningPlanContractStateRepository> contractStateRepositoryProvider,
      ObjectProvider<LearningPlanTemplateDraftService> templateDraftServiceProvider,
      ObjectProvider<LearningPlanActivationService> activationServiceProvider,
      ObjectProvider<LearningPlanLoadService> loadServiceProvider,
      ObjectProvider<LearningPlanContractService> contractServiceProvider,
      ApiSseProperties sseProperties,
      ObjectProvider<SseOpsRecorder> sseOpsRecorder,
      ObjectProvider<LearningOpsRecorder> learningOpsRecorder) {
    this.draftService = draftService;
    this.planService = planService;
    this.currentUserIdProvider = currentUserIdProvider;
    this.draftStreamServiceProvider = draftStreamServiceProvider;
    this.draftRevisionStreamServiceProvider = draftRevisionStreamServiceProvider;
    this.extensionProposalStreamServiceProvider = extensionProposalStreamServiceProvider;
    this.extensionApplyServiceProvider = extensionApplyServiceProvider;
    this.proposalGroupServiceProvider = proposalGroupServiceProvider;
    this.practiceSessionRepositoryProvider = practiceSessionRepositoryProvider;
    this.contractStateRepositoryProvider = contractStateRepositoryProvider;
    this.templateDraftServiceProvider = templateDraftServiceProvider;
    this.activationServiceProvider = activationServiceProvider;
    this.loadService = loadServiceProvider.getIfAvailable(LearningPlanLoadService::new);
    this.contractService = contractServiceProvider.getIfAvailable(LearningPlanContractService::new);
    this.draftStreamSseMapper = new LearningPlanDraftStreamSseMapper();
    this.proposalStreamSseMapper = new LearningPlanProposalStreamSseMapper();
    this.sseProperties = sseProperties;
    this.sseOpsRecorder = sseOpsRecorder.getIfAvailable(NoopOpsRecorders::sse);
    this.learningOpsRecorder = learningOpsRecorder.getIfAvailable(NoopOpsRecorders::learning);
    this.opsLogger = new StructuredOpsLogger();
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
              request.toCommand(LearningPlanContentLocale.fromAcceptLanguage(acceptLanguage)),
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
    String instruction = normalizedInstruction(request);
    return proposalStream(runId -> requiredDraftRevisionStreamService()
        .stream(userId, draftId, instruction, runId, Map.of()));
  }

  @PostMapping(value = ApiContractConstants.LEARNING_PLAN_EXTENSION_PROPOSALS_STREAM_PATH,
      produces = MediaType.TEXT_EVENT_STREAM_VALUE)
  public SseEmitter streamExtensionProposal(
      @PathVariable long planId,
      @RequestBody LearningPlanRevisionRequest request) {
    long userId = requireCurrentUserId();
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
    String instruction = normalizedInstruction(request);
    return proposalStream(runId -> requiredExtensionProposalStreamService()
        .streamNextRevision(userId, planId, proposalGroupId, instruction, runId, Map.of()));
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
        opsLogger);
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

  private LearningPlanDraftRevisionStreamService requiredDraftRevisionStreamService() {
    return draftRevisionStreamServiceProvider.getIfAvailable(() -> {
      throw unavailableGovernance();
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
