package org.congcong.algomentor.api.controller.learningplan;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.congcong.algomentor.auth.security.AuthenticatedUserPrincipal;
import org.congcong.algomentor.auth.security.CurrentUserIdProvider;
import org.congcong.algomentor.api.learningplan.model.RecommendedTodayPackActivationRequest;
import org.congcong.algomentor.identity.model.AuthUserStatus;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanActivationService;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanException;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanService;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanConfirmResult;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanDraftResult;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanDraftService;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanDraftStatus;
import org.congcong.algomentor.mentor.application.learningplan.TodayPack;
import org.congcong.algomentor.mentor.application.learningplan.TodayPackHomeActivePlan;
import org.congcong.algomentor.mentor.application.learningplan.TodayPackHomeSummary;
import org.congcong.algomentor.mentor.application.learningplan.TodayPackService;
import org.congcong.algomentor.mentor.application.learningplan.TodayPackState;
import org.congcong.algomentor.mentor.application.learningplan.template.LearningPlanTemplateDraftCommand;
import org.congcong.algomentor.mentor.application.learningplan.template.LearningPlanTemplateDraftService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.support.StaticListableBeanFactory;

class TodayPackControllerTest {

  @Test
  void planTodayPackRejectsAPlanThatIsNoLongerActive() {
    TodayPackService todayPackService = mock(TodayPackService.class);
    when(todayPackService.getPlanWorkspace(42L, 900L, "Asia/Shanghai", 0)).thenReturn(Optional.empty());
    TodayPackController controller = controller(
        todayPackService,
        mock(LearningPlanActivationService.class),
        mock(LearningPlanDraftService.class),
        mock(LearningPlanTemplateDraftService.class));

    assertThatThrownBy(() -> controller.getPlanTodayPack(900L, "Asia/Shanghai", 0))
        .isInstanceOfSatisfying(LearningPlanException.class, exception ->
            assertThat(exception.code()).isEqualTo(TodayPackService.ACTIVE_SELECTION_MISMATCH_CODE));
    verify(todayPackService).getPlanWorkspace(42L, 900L, "Asia/Shanghai", 0);
  }

  @Test
  void homeSummaryReturnsOnlyTheHomepageFields() {
    TodayPackService todayPackService = mock(TodayPackService.class);
    when(todayPackService.getHomeSummary(42L, "Asia/Shanghai")).thenReturn(new TodayPackHomeSummary(
        TodayPackState.READY,
        LocalDate.of(2026, 7, 11),
        new TodayPackHomeActivePlan(900L, "训练计划", 2, 5, 12),
        3,
        null,
        LocalDate.of(2026, 7, 12)));
    TodayPackController controller = controller(
        todayPackService,
        mock(LearningPlanActivationService.class),
        mock(LearningPlanDraftService.class),
        mock(LearningPlanTemplateDraftService.class));

    var response = controller.getHomeSummary("Asia/Shanghai");

    assertThat(response.data()).satisfies(summary -> {
      assertThat(summary.localDate()).isEqualTo(LocalDate.of(2026, 7, 11));
      assertThat(summary.dueProblemCount()).isEqualTo(3);
      assertThat(summary.activePlan()).extracting(plan -> plan.planId()).isEqualTo(900L);
      assertThat(summary.nextPackDate()).isEqualTo(LocalDate.of(2026, 7, 12));
    });
    verify(todayPackService).getHomeSummary(42L, "Asia/Shanghai");
  }

  @Test
  void activateRecommendedPlanPassesAcceptLanguageToTemplateDraft() {
    TodayPackService todayPackService = mock(TodayPackService.class);
    LearningPlanActivationService activationService = mock(LearningPlanActivationService.class);
    LearningPlanDraftService draftService = mock(LearningPlanDraftService.class);
    LearningPlanTemplateDraftService templateDraftService = mock(LearningPlanTemplateDraftService.class);
    when(templateDraftService.createDraft(eq(42L), org.mockito.ArgumentMatchers.any(LearningPlanTemplateDraftCommand.class)))
        .thenReturn(new LearningPlanDraftResult(101L, LearningPlanDraftStatus.GENERATED, "", List.of(), null));
    when(draftService.confirmDraft(42L, 101L)).thenReturn(new LearningPlanConfirmResult(
        900L,
        "推荐训练计划",
        org.congcong.algomentor.mentor.application.learningplan.LearningPlanStatus.ACTIVE));
    when(todayPackService.getTodayPack(42L, "Asia/Shanghai", 0)).thenReturn(new TodayPack(
        TodayPackState.READY,
        LocalDate.of(2026, 7, 11),
        "Asia/Shanghai",
        0,
        null,
        List.of(),
        null,
        null,
        null));
    TodayPackController controller = controller(
        todayPackService,
        activationService,
        draftService,
        templateDraftService);

    controller.activateRecommendedPlan(
        "en-US,en;q=0.9",
        new RecommendedTodayPackActivationRequest("Asia/Shanghai"));

    ArgumentCaptor<LearningPlanTemplateDraftCommand> commandCaptor =
        ArgumentCaptor.forClass(LearningPlanTemplateDraftCommand.class);
    verify(templateDraftService).createDraft(eq(42L), commandCaptor.capture());
    assertThat(commandCaptor.getValue().recommendationReasonLocale()).isEqualTo("en-US");
    verify(activationService).activate(42L, 900L);
  }

  private TodayPackController controller(
      TodayPackService todayPackService,
      LearningPlanActivationService activationService,
      LearningPlanDraftService draftService,
      LearningPlanTemplateDraftService templateDraftService
  ) {
    StaticListableBeanFactory beanFactory = new StaticListableBeanFactory();
    beanFactory.addBean("todayPackService", todayPackService);
    beanFactory.addBean("activationService", activationService);
    beanFactory.addBean("draftService", draftService);
    beanFactory.addBean("templateDraftService", templateDraftService);
    CurrentUserIdProvider currentUserIdProvider = () -> Optional.of(new AuthenticatedUserPrincipal(
        42L,
        "learner@example.com",
        "Learner",
        null,
        List.of(),
        AuthUserStatus.ACTIVE));
    return new TodayPackController(
        beanFactory.getBeanProvider(TodayPackService.class),
        beanFactory.getBeanProvider(LearningPlanActivationService.class),
        beanFactory.getBeanProvider(LearningPlanDraftService.class),
        beanFactory.getBeanProvider(LearningPlanTemplateDraftService.class),
        beanFactory.getBeanProvider(LearningPlanService.class),
        beanFactory.getBeanProvider(org.congcong.algomentor.mentor.application.learningplan.LearningPlanContractStateRepository.class),
        beanFactory.getBeanProvider(org.congcong.algomentor.mentor.application.learningplan.LearningPlanLoadService.class),
        beanFactory.getBeanProvider(org.congcong.algomentor.mentor.application.learningplan.LearningPlanContractService.class),
        currentUserIdProvider);
  }
}
