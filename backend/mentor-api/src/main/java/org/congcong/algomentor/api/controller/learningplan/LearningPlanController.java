package org.congcong.algomentor.api.controller.learningplan;

import java.util.List;
import org.congcong.algomentor.api.config.ApiContractConstants;
import org.congcong.algomentor.api.learningplan.model.LearningPlanConfirmResponse;
import org.congcong.algomentor.api.learningplan.model.LearningPlanCreateDraftRequest;
import org.congcong.algomentor.api.learningplan.model.LearningPlanDetailResponse;
import org.congcong.algomentor.api.learningplan.model.LearningPlanDraftResponse;
import org.congcong.algomentor.api.learningplan.model.LearningPlanMessageRequest;
import org.congcong.algomentor.api.learningplan.model.LearningPlanResponseMapper;
import org.congcong.algomentor.api.learningplan.model.LearningPlanSummaryResponse;
import org.congcong.algomentor.auth.security.AuthenticatedUserPrincipal;
import org.congcong.algomentor.auth.security.CurrentUserIdProvider;
import org.congcong.algomentor.common.api.ApiResponse;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanDraftService;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanService;
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

  public LearningPlanController(
      LearningPlanDraftService draftService,
      LearningPlanService planService,
      CurrentUserIdProvider currentUserIdProvider) {
    this.draftService = draftService;
    this.planService = planService;
    this.currentUserIdProvider = currentUserIdProvider;
  }

  @PostMapping(ApiContractConstants.LEARNING_PLAN_DRAFTS_PATH)
  public ApiResponse<LearningPlanDraftResponse> createDraft(@RequestBody LearningPlanCreateDraftRequest request) {
    long userId = requireCurrentUserId();
    return ApiResponse.success(LearningPlanResponseMapper.toDraftResponse(
        draftService.createDraft(userId, request.toCommand())));
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
}
