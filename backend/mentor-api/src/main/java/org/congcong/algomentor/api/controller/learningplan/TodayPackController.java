package org.congcong.algomentor.api.controller.learningplan;

import org.congcong.algomentor.api.config.ApiContractConstants;
import org.congcong.algomentor.api.learningplan.model.RecommendedTodayPackActivationRequest;
import org.congcong.algomentor.api.learningplan.model.LearningPlanRhythmUpdateRequest;
import org.congcong.algomentor.api.learningplan.model.TodayPackHomeSummaryResponse;
import org.congcong.algomentor.api.learningplan.model.TodayPackHomeSummaryResponseMapper;
import org.congcong.algomentor.api.learningplan.model.TodayPackResponse;
import org.congcong.algomentor.api.learningplan.model.TodayPackResponseMapper;
import org.congcong.algomentor.api.learningplan.model.TodayPackWorkspaceResponse;
import org.congcong.algomentor.api.learningplan.model.TodayPackWorkspaceResponseMapper;
import org.congcong.algomentor.auth.security.AuthenticatedUserPrincipal;
import org.congcong.algomentor.auth.security.CurrentUserIdProvider;
import org.congcong.algomentor.common.api.ApiErrorLocales;
import org.congcong.algomentor.common.api.ApiResponse;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanActivationService;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanContractService;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanContractState;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanContractStateRepository;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanDraftResult;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanDraftService;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanLoadService;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanService;
import org.congcong.algomentor.mentor.application.learningplan.TodayPackWorkspace;
import org.congcong.algomentor.mentor.application.learningplan.TodayPackService;
import org.congcong.algomentor.mentor.application.learningplan.template.LearningPlanTemplateDraftCommand;
import org.congcong.algomentor.mentor.application.learningplan.template.LearningPlanTemplateDraftService;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanException;
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

@RestController
public class TodayPackController {

  private final ObjectProvider<TodayPackService> todayPackServiceProvider;
  private final ObjectProvider<LearningPlanActivationService> activationServiceProvider;
  private final ObjectProvider<LearningPlanDraftService> draftServiceProvider;
  private final ObjectProvider<LearningPlanTemplateDraftService> templateDraftServiceProvider;
  private final ObjectProvider<LearningPlanService> planServiceProvider;
  private final ObjectProvider<LearningPlanContractStateRepository> contractStateRepositoryProvider;
  private final LearningPlanLoadService loadService;
  private final LearningPlanContractService contractService;
  private final CurrentUserIdProvider currentUserIdProvider;

  public TodayPackController(
      ObjectProvider<TodayPackService> todayPackServiceProvider,
      ObjectProvider<LearningPlanActivationService> activationServiceProvider,
      ObjectProvider<LearningPlanDraftService> draftServiceProvider,
      ObjectProvider<LearningPlanTemplateDraftService> templateDraftServiceProvider,
      ObjectProvider<LearningPlanService> planServiceProvider,
      ObjectProvider<LearningPlanContractStateRepository> contractStateRepositoryProvider,
      ObjectProvider<LearningPlanLoadService> loadServiceProvider,
      ObjectProvider<LearningPlanContractService> contractServiceProvider,
      CurrentUserIdProvider currentUserIdProvider) {
    this.todayPackServiceProvider = todayPackServiceProvider;
    this.activationServiceProvider = activationServiceProvider;
    this.draftServiceProvider = draftServiceProvider;
    this.templateDraftServiceProvider = templateDraftServiceProvider;
    this.planServiceProvider = planServiceProvider;
    this.contractStateRepositoryProvider = contractStateRepositoryProvider;
    this.loadService = loadServiceProvider.getIfAvailable(LearningPlanLoadService::new);
    this.contractService = contractServiceProvider.getIfAvailable(LearningPlanContractService::new);
    this.currentUserIdProvider = currentUserIdProvider;
  }

  @GetMapping(ApiContractConstants.TODAY_PACK_BASE_PATH)
  public ApiResponse<TodayPackResponse> getTodayPack(
      @RequestParam(required = false) String timezone,
      @RequestParam(required = false) Integer packOffset) {
    long userId = requireCurrentUserId();
    return ApiResponse.success(TodayPackResponseMapper.toResponse(
        requiredTodayPackService().getTodayPack(userId, timezone, packOffset)));
  }

  @GetMapping(ApiContractConstants.LEARNING_PLAN_TODAY_PACK_PATH)
  public ApiResponse<TodayPackWorkspaceResponse> getPlanTodayPack(
      @PathVariable long planId,
      @RequestParam(required = false) String timezone,
      @RequestParam(required = false) Integer packOffset) {
    long userId = requireCurrentUserId();
    return ApiResponse.success(todayPackWorkspaceResponse(userId, planId, timezone, packOffset));
  }

  @PatchMapping(
      value = ApiContractConstants.LEARNING_PLAN_TODAY_PACK_RHYTHM_PATH,
      consumes = MediaType.APPLICATION_JSON_VALUE)
  public ApiResponse<TodayPackWorkspaceResponse> updatePlanTodayPackRhythm(
      @PathVariable long planId,
      @RequestParam(required = false) String timezone,
      @RequestParam(required = false) Integer packOffset,
      @RequestBody LearningPlanRhythmUpdateRequest request) {
    long userId = requireCurrentUserId();
    requiredPlanService().updateRhythm(
        userId,
        planId,
        request == null ? null : request.dailyProblemCount(),
        request == null ? null : request.trainingDaysPerWeek());
    return ApiResponse.success(todayPackWorkspaceResponse(userId, planId, timezone, packOffset));
  }

  @GetMapping(ApiContractConstants.TODAY_PACK_BASE_PATH
      + ApiContractConstants.TODAY_PACK_HOME_SUMMARY_PATH)
  public ApiResponse<TodayPackHomeSummaryResponse> getHomeSummary(
      @RequestParam(required = false) String timezone) {
    long userId = requireCurrentUserId();
    return ApiResponse.success(TodayPackHomeSummaryResponseMapper.toResponse(
        requiredTodayPackService().getHomeSummary(userId, timezone)));
  }

  @PostMapping(
      value = ApiContractConstants.TODAY_PACK_BASE_PATH
          + ApiContractConstants.TODAY_PACK_RECOMMENDED_ACTIVATION_PATH,
      consumes = MediaType.APPLICATION_JSON_VALUE)
  public ApiResponse<TodayPackResponse> activateRecommendedPlan(
      @RequestHeader(name = ApiContractConstants.ACCEPT_LANGUAGE_HEADER, required = false) String acceptLanguage,
      @RequestBody(required = false) RecommendedTodayPackActivationRequest request) {
    long userId = requireCurrentUserId();
    LearningPlanDraftResult draft = requiredTemplateDraftService().createDraft(userId, new LearningPlanTemplateDraftCommand(
        TodayPackService.RECOMMENDED_TEMPLATE_ID,
        null,
        null,
        null,
        ApiErrorLocales.parse(acceptLanguage).toLanguageTag()));
    long planId = requiredDraftService().confirmDraft(userId, draft.draftId()).planId();
    requiredActivationService().activate(userId, planId);
    String timezone = request == null ? null : request.timezone();
    return ApiResponse.success(TodayPackResponseMapper.toResponse(
        requiredTodayPackService().getTodayPack(userId, timezone, 0)));
  }

  @PostMapping(ApiContractConstants.LEARNING_PLANS_BASE_PATH
      + ApiContractConstants.LEARNING_PLAN_ACTIVATION_RESTART_PATH)
  public ApiResponse<TodayPackResponse> restartActivePlan(
      @PathVariable long planId,
      @RequestBody(required = false) RecommendedTodayPackActivationRequest request) {
    long userId = requireCurrentUserId();
    requiredActivationService().restart(userId, planId);
    String timezone = request == null ? null : request.timezone();
    return ApiResponse.success(TodayPackResponseMapper.toResponse(
        requiredTodayPackService().getTodayPack(userId, timezone, 0)));
  }

  private long requireCurrentUserId() {
    return currentUserIdProvider.currentUser()
        .map(AuthenticatedUserPrincipal::userId)
        .orElseThrow(() -> new LearningPlanUnauthenticatedException("当前请求未登录或无法解析当前用户。"));
  }

  private TodayPackService requiredTodayPackService() {
    TodayPackService service = todayPackServiceProvider.getIfAvailable();
    if (service == null) {
      throw new LearningPlanException("TODAY_PACK_SERVICE_UNAVAILABLE", "今日题包服务不可用。");
    }
    return service;
  }

  private TodayPackWorkspaceResponse todayPackWorkspaceResponse(
      long userId,
      long planId,
      String timezone,
      Integer packOffset) {
    TodayPackWorkspace workspace = requiredTodayPackService()
        .getPlanWorkspace(userId, planId, timezone, packOffset)
        .orElseThrow(() -> new LearningPlanException(
            TodayPackService.ACTIVE_SELECTION_MISMATCH_CODE,
            "只能查看当前采用计划的今日题包。"));
    return TodayPackWorkspaceResponseMapper.toResponse(
        workspace,
        loadService,
        contractService,
        contractStateByPlan(userId, planId));
  }

  private LearningPlanContractState contractStateByPlan(long userId, long planId) {
    LearningPlanContractStateRepository repository = contractStateRepositoryProvider.getIfAvailable();
    if (repository == null) {
      return LearningPlanContractState.empty(userId, planId);
    }
    return repository.findByPlan(userId, planId)
        .orElseGet(() -> LearningPlanContractState.empty(userId, planId));
  }

  private LearningPlanActivationService requiredActivationService() {
    LearningPlanActivationService service = activationServiceProvider.getIfAvailable();
    if (service == null) {
      throw new LearningPlanException("LEARNING_PLAN_ACTIVE_SELECTION_UNAVAILABLE", "学习计划激活服务不可用。");
    }
    return service;
  }

  private LearningPlanService requiredPlanService() {
    LearningPlanService service = planServiceProvider.getIfAvailable();
    if (service == null) {
      throw new LearningPlanException("LEARNING_PLAN_REPOSITORY_UNAVAILABLE", "学习计划服务不可用。");
    }
    return service;
  }

  private LearningPlanDraftService requiredDraftService() {
    LearningPlanDraftService service = draftServiceProvider.getIfAvailable();
    if (service == null) {
      throw new LearningPlanException("LEARNING_PLAN_REPOSITORY_UNAVAILABLE", "学习计划草案服务不可用。");
    }
    return service;
  }

  private LearningPlanTemplateDraftService requiredTemplateDraftService() {
    LearningPlanTemplateDraftService service = templateDraftServiceProvider.getIfAvailable();
    if (service == null) {
      throw new LearningPlanException("LEARNING_PLAN_REPOSITORY_UNAVAILABLE", "学习计划模板服务不可用。");
    }
    return service;
  }
}
