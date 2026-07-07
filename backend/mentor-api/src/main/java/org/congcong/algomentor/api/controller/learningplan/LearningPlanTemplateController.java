package org.congcong.algomentor.api.controller.learningplan;

import java.util.List;
import org.congcong.algomentor.api.config.ApiContractConstants;
import org.congcong.algomentor.api.learningplan.model.LearningPlanTemplateDetailResponse;
import org.congcong.algomentor.api.learningplan.model.LearningPlanTemplateResponseMapper;
import org.congcong.algomentor.api.learningplan.model.LearningPlanTemplateSummaryResponse;
import org.congcong.algomentor.common.api.ApiResponse;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanException;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanLoadService;
import org.congcong.algomentor.mentor.application.learningplan.template.LearningPlanTemplateDraftService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(ApiContractConstants.LEARNING_PLAN_TEMPLATES_BASE_PATH)
public class LearningPlanTemplateController {

  private final ObjectProvider<LearningPlanTemplateDraftService> templateDraftServiceProvider;
  private final LearningPlanLoadService loadService;

  public LearningPlanTemplateController(
      ObjectProvider<LearningPlanTemplateDraftService> templateDraftServiceProvider,
      ObjectProvider<LearningPlanLoadService> loadServiceProvider
  ) {
    this.templateDraftServiceProvider = templateDraftServiceProvider;
    this.loadService = loadServiceProvider.getIfAvailable(LearningPlanLoadService::new);
  }

  @GetMapping
  public ApiResponse<List<LearningPlanTemplateSummaryResponse>> listTemplates() {
    return ApiResponse.success(LearningPlanTemplateResponseMapper.toSummaryResponses(
        requiredTemplateDraftService().listTemplates(),
        loadService));
  }

  @GetMapping("/{templateId}")
  public ApiResponse<LearningPlanTemplateDetailResponse> getTemplate(@PathVariable String templateId) {
    return ApiResponse.success(LearningPlanTemplateResponseMapper.toDetailResponse(
        requiredTemplateDraftService().getTemplate(templateId),
        loadService));
  }

  private LearningPlanTemplateDraftService requiredTemplateDraftService() {
    LearningPlanTemplateDraftService service = templateDraftServiceProvider.getIfAvailable();
    if (service == null) {
      throw new LearningPlanException("LEARNING_PLAN_REPOSITORY_UNAVAILABLE", "学习计划模板服务不可用。");
    }
    return service;
  }
}
