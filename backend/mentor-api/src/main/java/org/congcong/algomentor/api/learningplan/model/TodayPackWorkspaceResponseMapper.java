package org.congcong.algomentor.api.learningplan.model;

import org.congcong.algomentor.mentor.application.learningplan.LearningPlan;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanContractService;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanContractState;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanLoadService;
import org.congcong.algomentor.mentor.application.learningplan.TodayPackWorkspace;

/** Maps the internal today-pack snapshot to the page-specific public response. */
public final class TodayPackWorkspaceResponseMapper {

  private TodayPackWorkspaceResponseMapper() {
  }

  public static TodayPackWorkspaceResponse toResponse(
      TodayPackWorkspace workspace,
      LearningPlanLoadService loadService,
      LearningPlanContractService contractService,
      LearningPlanContractState contractState) {
    LearningPlan plan = workspace.plan();
    return new TodayPackWorkspaceResponse(
        TodayPackResponseMapper.toResponse(workspace.pack()),
        new TodayPackPlanContextResponse(
            plan.id(),
            plan.plan().durationWeeks(),
            loadService.rhythmSettings(plan.plan(), workspace.progress()),
            loadService.paceSummary(plan, workspace.progress()),
            contractService.summarize(plan, workspace.progress(), contractState)));
  }
}
