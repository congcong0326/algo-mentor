package org.congcong.algomentor.api.controller.learningplan;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Flow;
import java.util.function.Supplier;
import org.congcong.algomentor.ai.governance.admission.AiRunAdmission;
import org.congcong.algomentor.ai.governance.admission.AiRunAdmissionException;
import org.congcong.algomentor.ai.governance.admission.AiRunAdmissionService;
import org.congcong.algomentor.ai.governance.admission.AiRunLifecycleService;
import org.congcong.algomentor.ai.governance.model.AiActor;
import org.congcong.algomentor.ai.governance.model.AiGovernanceErrorCode;
import org.congcong.algomentor.ai.governance.model.AiPurpose;
import org.congcong.algomentor.ai.governance.model.AiRunContext;
import org.congcong.algomentor.ai.governance.model.AiRunSource;
import org.congcong.algomentor.ai.governance.model.AiRunStatus;
import org.congcong.algomentor.ai.governance.model.AiUsage;
import org.congcong.algomentor.api.config.ApiSseProperties;
import org.congcong.algomentor.api.config.ApiContractConstants;
import org.congcong.algomentor.api.learningplan.model.LearningPlanConfirmResponse;
import org.congcong.algomentor.api.learningplan.model.LearningPlanCreateDraftRequest;
import org.congcong.algomentor.api.learningplan.model.LearningPlanDetailResponse;
import org.congcong.algomentor.api.learningplan.model.LearningPlanDraftResponse;
import org.congcong.algomentor.api.learningplan.model.LearningPlanExtensionApplyResponse;
import org.congcong.algomentor.api.learningplan.model.LearningPlanMessageRequest;
import org.congcong.algomentor.api.learningplan.model.LearningPlanPageResponse;
import org.congcong.algomentor.api.learningplan.model.LearningPlanResponseMapper;
import org.congcong.algomentor.api.learningplan.model.LearningPlanRevisionRequest;
import org.congcong.algomentor.api.learningplan.service.LearningPlanDraftStreamSseMapper;
import org.congcong.algomentor.api.learningplan.service.LearningPlanProposalStreamSseMapper;
import org.congcong.algomentor.api.learningplan.service.SseLearningPlanDraftStreamSubscriber;
import org.congcong.algomentor.api.learningplan.service.SseLearningPlanProposalStreamSubscriber;
import org.congcong.algomentor.api.service.AiActorResolver;
import org.congcong.algomentor.auth.security.AuthenticatedUserPrincipal;
import org.congcong.algomentor.auth.security.CurrentUserIdProvider;
import org.congcong.algomentor.common.api.ApiResponse;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanDraftResult;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanDraftService;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanService;
import org.congcong.algomentor.mentor.application.learningplan.proposal.LearningPlanExtensionApplyService;
import org.congcong.algomentor.mentor.application.learningplan.proposal.LearningPlanProposalGroupService;
import org.congcong.algomentor.mentor.application.learningplan.proposal.stream.LearningPlanDraftRevisionStreamService;
import org.congcong.algomentor.mentor.application.learningplan.proposal.stream.LearningPlanExtensionProposalStreamService;
import org.congcong.algomentor.mentor.application.learningplan.proposal.stream.LearningPlanProposalStreamEvent;
import org.congcong.algomentor.mentor.application.learningplan.stream.LearningPlanDraftStreamService;
import org.congcong.algomentor.ops.observability.LearningOpsRecorder;
import org.congcong.algomentor.ops.observability.NoopOpsRecorders;
import org.congcong.algomentor.ops.observability.SseOpsRecorder;
import org.congcong.algomentor.ops.observability.SseStreamType;
import org.congcong.algomentor.ops.observability.StructuredOpsLogger;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
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
  private final AiActorResolver actorResolver;
  private final ObjectProvider<AiRunAdmissionService> admissionServiceProvider;
  private final ObjectProvider<AiRunLifecycleService> lifecycleServiceProvider;
  private final ObjectProvider<LearningPlanDraftStreamService> draftStreamServiceProvider;
  private final ObjectProvider<LearningPlanDraftRevisionStreamService> draftRevisionStreamServiceProvider;
  private final ObjectProvider<LearningPlanExtensionProposalStreamService> extensionProposalStreamServiceProvider;
  private final ObjectProvider<LearningPlanExtensionApplyService> extensionApplyServiceProvider;
  private final ObjectProvider<LearningPlanProposalGroupService> proposalGroupServiceProvider;
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
      AiActorResolver actorResolver,
      ObjectProvider<AiRunAdmissionService> admissionServiceProvider,
      ObjectProvider<AiRunLifecycleService> lifecycleServiceProvider,
      ObjectProvider<LearningPlanDraftStreamService> draftStreamServiceProvider,
      ObjectProvider<LearningPlanDraftRevisionStreamService> draftRevisionStreamServiceProvider,
      ObjectProvider<LearningPlanExtensionProposalStreamService> extensionProposalStreamServiceProvider,
      ObjectProvider<LearningPlanExtensionApplyService> extensionApplyServiceProvider,
      ObjectProvider<LearningPlanProposalGroupService> proposalGroupServiceProvider,
      ApiSseProperties sseProperties,
      ObjectProvider<SseOpsRecorder> sseOpsRecorder,
      ObjectProvider<LearningOpsRecorder> learningOpsRecorder) {
    this.draftService = draftService;
    this.planService = planService;
    this.currentUserIdProvider = currentUserIdProvider;
    this.actorResolver = actorResolver;
    this.admissionServiceProvider = admissionServiceProvider;
    this.lifecycleServiceProvider = lifecycleServiceProvider;
    this.draftStreamServiceProvider = draftStreamServiceProvider;
    this.draftRevisionStreamServiceProvider = draftRevisionStreamServiceProvider;
    this.extensionProposalStreamServiceProvider = extensionProposalStreamServiceProvider;
    this.extensionApplyServiceProvider = extensionApplyServiceProvider;
    this.proposalGroupServiceProvider = proposalGroupServiceProvider;
    this.draftStreamSseMapper = new LearningPlanDraftStreamSseMapper();
    this.proposalStreamSseMapper = new LearningPlanProposalStreamSseMapper();
    this.sseProperties = sseProperties;
    this.sseOpsRecorder = sseOpsRecorder.getIfAvailable(NoopOpsRecorders::sse);
    this.learningOpsRecorder = learningOpsRecorder.getIfAvailable(NoopOpsRecorders::learning);
    this.opsLogger = new StructuredOpsLogger();
  }

  @PostMapping(value = ApiContractConstants.LEARNING_PLAN_DRAFTS_STREAM_PATH,
      produces = MediaType.TEXT_EVENT_STREAM_VALUE)
  public SseEmitter streamDraft(@RequestBody LearningPlanCreateDraftRequest request) {
    long userId = requireCurrentUserId();
    String runId = UUID.randomUUID().toString();
    AiActor actor = actorResolver.currentActor();
    AiRunAdmission admission = requiredAdmissionService().admit(new AiRunContext(
        runId,
        actor,
        AiPurpose.LEARNING_PLAN,
        AiRunSource.LEARNING_PLAN_DRAFT,
        runId,
        requestSize(request),
        true,
        Map.of(),
        Instant.now()));
    AiRunLifecycleService lifecycleService = requiredLifecycleService();
    lifecycleService.markRunning(admission, null, null);

    SseEmitter emitter = new SseEmitter(sseProperties.learningPlanDraftTimeoutMillis());
    SseLearningPlanDraftStreamSubscriber subscriber = new SseLearningPlanDraftStreamSubscriber(
        emitter,
        draftStreamSseMapper,
        lifecycleService,
        admission,
        SseStreamType.LEARNING_PLAN_DRAFT,
        sseOpsRecorder,
        learningOpsRecorder,
        opsLogger);
    emitter.onCompletion(() -> subscriber.clientDisconnected(null));
    emitter.onTimeout(subscriber::timeout);
    emitter.onError(subscriber::clientDisconnected);

    try {
      requiredDraftStreamService()
          .stream(userId, request.toCommand(), runId, admission.metadata())
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
    return proposalStream(
        AiRunSource.LEARNING_PLAN_DRAFT_REVISION,
        requestSize(instruction),
        (runId, metadata) -> requiredDraftRevisionStreamService()
            .stream(userId, draftId, instruction, runId, metadata));
  }

  @PostMapping(value = ApiContractConstants.LEARNING_PLAN_EXTENSION_PROPOSALS_STREAM_PATH,
      produces = MediaType.TEXT_EVENT_STREAM_VALUE)
  public SseEmitter streamExtensionProposal(
      @PathVariable long planId,
      @RequestBody LearningPlanRevisionRequest request) {
    long userId = requireCurrentUserId();
    String instruction = normalizedInstruction(request);
    return proposalStream(
        AiRunSource.LEARNING_PLAN_EXTENSION_PROPOSAL,
        requestSize(instruction),
        (runId, metadata) -> requiredExtensionProposalStreamService()
            .streamFirstRevision(userId, planId, instruction, runId, metadata));
  }

  @PostMapping(value = ApiContractConstants.LEARNING_PLAN_EXTENSION_PROPOSAL_REVISIONS_STREAM_PATH,
      produces = MediaType.TEXT_EVENT_STREAM_VALUE)
  public SseEmitter streamExtensionProposalRevision(
      @PathVariable long planId,
      @PathVariable long proposalGroupId,
      @RequestBody LearningPlanRevisionRequest request) {
    long userId = requireCurrentUserId();
    String instruction = normalizedInstruction(request);
    return proposalStream(
        AiRunSource.LEARNING_PLAN_EXTENSION_PROPOSAL,
        requestSize(instruction),
        (runId, metadata) -> requiredExtensionProposalStreamService()
            .streamNextRevision(userId, planId, proposalGroupId, instruction, runId, metadata));
  }

  @PostMapping(ApiContractConstants.LEARNING_PLAN_DRAFTS_PATH
      + ApiContractConstants.LEARNING_PLAN_DRAFT_MESSAGES_PATH)
  public ApiResponse<LearningPlanDraftResponse> continueDraft(
      @PathVariable long draftId,
      @RequestBody LearningPlanMessageRequest request) {
    long userId = requireCurrentUserId();
    return governedDraft(
        "learning-plan-draft-" + draftId + "-" + UUID.randomUUID(),
        request.message() == null ? 0 : request.message().getBytes(StandardCharsets.UTF_8).length,
        () -> draftService.continueDraft(userId, draftId, request.message()));
  }

  @PostMapping(ApiContractConstants.LEARNING_PLAN_DRAFTS_PATH
      + ApiContractConstants.LEARNING_PLAN_DRAFT_CONFIRM_PATH)
  public ApiResponse<LearningPlanConfirmResponse> confirmDraft(@PathVariable long draftId) {
    long userId = requireCurrentUserId();
    return ApiResponse.success(LearningPlanResponseMapper.toConfirmResponse(draftService.confirmDraft(userId, draftId)));
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
    return ApiResponse.success(LearningPlanResponseMapper.toPageResponse(planService.listPlans(userId, page, pageSize)));
  }

  @GetMapping("/{planId}")
  public ApiResponse<LearningPlanDetailResponse> getPlan(@PathVariable long planId) {
    long userId = requireCurrentUserId();
    return ApiResponse.success(LearningPlanResponseMapper.toDetailResponse(planService.getPlan(userId, planId)));
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

  private ApiResponse<LearningPlanDraftResponse> governedDraft(
      String runId,
      int requestSize,
      Supplier<LearningPlanDraftResult> action) {
    AiActor actor = actorResolver.currentActor();
    AiRunAdmissionService admissionService = requiredAdmissionService();
    AiRunLifecycleService lifecycleService = requiredLifecycleService();
    AiRunAdmission admission = admissionService.admit(new AiRunContext(
        runId,
        actor,
        AiPurpose.LEARNING_PLAN,
        AiRunSource.LEARNING_PLAN_DRAFT,
        runId,
        requestSize,
        false,
        Map.of(),
        Instant.now()));
    lifecycleService.markRunning(admission, null, null);
    try {
      LearningPlanDraftResult result = action.get();
      lifecycleService.markCompleted(admission, AiUsage.zero(), null, null);
      return ApiResponse.success(LearningPlanResponseMapper.toDraftResponse(result));
    } catch (RuntimeException exception) {
      lifecycleService.markFailed(admission, AiGovernanceErrorCode.AI_UNKNOWN, AiUsage.zero(), null, null);
      throw exception;
    }
  }

  private SseEmitter proposalStream(
      AiRunSource source,
      int requestSize,
      ProposalStreamFactory streamFactory) {
    String runId = UUID.randomUUID().toString();
    AiActor actor = actorResolver.currentActor();
    AiRunAdmission admission = requiredAdmissionService().admit(new AiRunContext(
        runId,
        actor,
        AiPurpose.LEARNING_PLAN,
        source,
        runId,
        requestSize,
        true,
        Map.of(),
        Instant.now()));
    AiRunLifecycleService lifecycleService = requiredLifecycleService();
    lifecycleService.markRunning(admission, null, null);

    SseEmitter emitter = new SseEmitter(sseProperties.learningPlanDraftTimeoutMillis());
    SseLearningPlanProposalStreamSubscriber subscriber = new SseLearningPlanProposalStreamSubscriber(
        emitter,
        proposalStreamSseMapper,
        lifecycleService,
        admission,
        SseStreamType.LEARNING_PLAN_PROPOSAL,
        sseOpsRecorder,
        opsLogger);
    emitter.onCompletion(() -> subscriber.clientDisconnected(null));
    emitter.onTimeout(subscriber::timeout);
    emitter.onError(subscriber::clientDisconnected);

    try {
      streamFactory.stream(runId, admission.metadata()).subscribe(subscriber);
    } catch (RuntimeException exception) {
      subscriber.onError(exception);
    }
    return emitter;
  }

  private int requestSize(LearningPlanCreateDraftRequest request) {
    int size = 0;
    if (request.goal() != null) {
      size += request.goal().getBytes(StandardCharsets.UTF_8).length;
    }
    if (request.programmingLanguage() != null) {
      size += request.programmingLanguage().getBytes(StandardCharsets.UTF_8).length;
    }
    if (request.topicPreferences() != null) {
      size += request.topicPreferences().stream()
          .filter(topic -> topic != null)
          .mapToInt(topic -> topic.getBytes(StandardCharsets.UTF_8).length)
          .sum();
    }
    return size;
  }

  private String normalizedInstruction(LearningPlanRevisionRequest request) {
    return request == null ? "" : request.normalizedInstruction();
  }

  private int requestSize(String content) {
    return content == null ? 0 : content.getBytes(StandardCharsets.UTF_8).length;
  }

  private AiRunAdmissionService requiredAdmissionService() {
    return admissionServiceProvider.getIfAvailable(() -> {
      throw unavailableGovernance();
    });
  }

  private AiRunLifecycleService requiredLifecycleService() {
    return lifecycleServiceProvider.getIfAvailable(() -> {
      throw unavailableGovernance();
    });
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
    Flow.Publisher<LearningPlanProposalStreamEvent> stream(String runId, Map<String, Object> metadata);
  }
}
