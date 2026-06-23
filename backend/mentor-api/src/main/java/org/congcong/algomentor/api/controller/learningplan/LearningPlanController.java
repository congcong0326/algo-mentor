package org.congcong.algomentor.api.controller.learningplan;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;
import org.congcong.algomentor.ai.governance.admission.AiRunAdmission;
import org.congcong.algomentor.ai.governance.admission.AiRunAdmissionService;
import org.congcong.algomentor.ai.governance.admission.AiRunLifecycleService;
import org.congcong.algomentor.ai.governance.admission.AiRunAdmissionException;
import org.congcong.algomentor.ai.governance.model.AiActor;
import org.congcong.algomentor.ai.governance.model.AiGovernanceErrorCode;
import org.congcong.algomentor.ai.governance.model.AiPurpose;
import org.congcong.algomentor.ai.governance.model.AiRunContext;
import org.congcong.algomentor.ai.governance.model.AiRunSource;
import org.congcong.algomentor.ai.governance.model.AiUsage;
import org.congcong.algomentor.ai.governance.model.AiRunStatus;
import org.congcong.algomentor.api.config.ApiContractConstants;
import org.congcong.algomentor.api.learningplan.model.LearningPlanConfirmResponse;
import org.congcong.algomentor.api.learningplan.model.LearningPlanCreateDraftRequest;
import org.congcong.algomentor.api.learningplan.model.LearningPlanDetailResponse;
import org.congcong.algomentor.api.learningplan.model.LearningPlanDraftResponse;
import org.congcong.algomentor.api.learningplan.model.LearningPlanMessageRequest;
import org.congcong.algomentor.api.learningplan.model.LearningPlanResponseMapper;
import org.congcong.algomentor.api.learningplan.model.LearningPlanSummaryResponse;
import org.congcong.algomentor.api.service.AiActorResolver;
import org.congcong.algomentor.auth.security.AuthenticatedUserPrincipal;
import org.congcong.algomentor.auth.security.CurrentUserIdProvider;
import org.congcong.algomentor.common.api.ApiResponse;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanDraftResult;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanDraftService;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(ApiContractConstants.LEARNING_PLANS_BASE_PATH)
public class LearningPlanController {

  private final LearningPlanDraftService draftService;
  private final LearningPlanService planService;
  private final CurrentUserIdProvider currentUserIdProvider;
  private final AiActorResolver actorResolver;
  private final ObjectProvider<AiRunAdmissionService> admissionServiceProvider;
  private final ObjectProvider<AiRunLifecycleService> lifecycleServiceProvider;

  public LearningPlanController(
      LearningPlanDraftService draftService,
      LearningPlanService planService,
      CurrentUserIdProvider currentUserIdProvider,
      AiActorResolver actorResolver,
      ObjectProvider<AiRunAdmissionService> admissionServiceProvider,
      ObjectProvider<AiRunLifecycleService> lifecycleServiceProvider) {
    this.draftService = draftService;
    this.planService = planService;
    this.currentUserIdProvider = currentUserIdProvider;
    this.actorResolver = actorResolver;
    this.admissionServiceProvider = admissionServiceProvider;
    this.lifecycleServiceProvider = lifecycleServiceProvider;
  }

  @PostMapping(ApiContractConstants.LEARNING_PLAN_DRAFTS_PATH)
  public ApiResponse<LearningPlanDraftResponse> createDraft(@RequestBody LearningPlanCreateDraftRequest request) {
    long userId = requireCurrentUserId();
    return governedDraft(
        UUID.randomUUID().toString(),
        requestSize(request),
        () -> draftService.createDraft(userId, request.toCommand()));
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

  @GetMapping
  public ApiResponse<List<LearningPlanSummaryResponse>> listPlans() {
    long userId = requireCurrentUserId();
    return ApiResponse.success(planService.listPlans(userId).stream()
        .map(LearningPlanResponseMapper::toSummaryResponse)
        .toList());
  }

  @GetMapping("/{planId}")
  public ApiResponse<LearningPlanDetailResponse> getPlan(@PathVariable long planId) {
    long userId = requireCurrentUserId();
    return ApiResponse.success(LearningPlanResponseMapper.toDetailResponse(planService.getPlan(userId, planId)));
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

  private AiRunAdmissionException unavailableGovernance() {
    return new AiRunAdmissionException(
        AiGovernanceErrorCode.AI_PROVIDER_UNAVAILABLE,
        AiRunStatus.REJECTED_DISABLED,
        "AI 治理服务暂不可用。",
        HttpStatus.SERVICE_UNAVAILABLE,
        Map.of());
  }
}
