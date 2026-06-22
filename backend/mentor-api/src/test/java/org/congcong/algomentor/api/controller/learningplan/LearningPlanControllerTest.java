package org.congcong.algomentor.api.controller.learningplan;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.congcong.algomentor.auth.security.CurrentUserIdProvider;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlan;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanConfirmResult;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanDifficultyPreference;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanDraftPlan;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanDraftResult;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanDraftService;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanDraftStatus;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanIntent;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanLevel;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanPhaseDraft;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanProblemDraft;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanService;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanStatus;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = LearningPlanController.class)
@Import(LearningPlanExceptionHandler.class)
class LearningPlanControllerTest {

  @Autowired
  private MockMvc mockMvc;

  @MockBean
  private LearningPlanDraftService draftService;

  @MockBean
  private LearningPlanService planService;

  @MockBean
  private CurrentUserIdProvider currentUserIdProvider;

  @Test
  void createDraftUsesCurrentUserAndReturnsGeneratedDraft() throws Exception {
    when(currentUserIdProvider.currentUserId()).thenReturn(Optional.of(42L));
    when(draftService.createDraft(eq(42L), any())).thenReturn(new LearningPlanDraftResult(
        100L,
        LearningPlanDraftStatus.GENERATED,
        "已生成学习计划草案。",
        List.of(),
        draftPlan()));

    mockMvc.perform(post("/api/learning-plans/drafts")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {
                  "userId": 999,
                  "intent": "INTERVIEW_SPRINT",
                  "goal": "准备 Java 后端算法面试",
                  "durationWeeks": 4,
                  "level": "INTERMEDIATE",
                  "weeklyHours": 6,
                  "programmingLanguage": "Java",
                  "difficultyPreference": "MEDIUM",
                  "interviewOriented": true,
                  "topicPreferences": ["Array", "Hash Table"]
                }
                """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.draftId").value(100))
        .andExpect(jsonPath("$.data.status").value("GENERATED"))
        .andExpect(jsonPath("$.data.draftPlan.phases[0].problems[0].slug").value("two-sum"));

    ArgumentCaptor<org.congcong.algomentor.mentor.application.learningplan.LearningPlanDraftCommand> captor =
        ArgumentCaptor.forClass(org.congcong.algomentor.mentor.application.learningplan.LearningPlanDraftCommand.class);
    verify(draftService).createDraft(eq(42L), captor.capture());
    org.assertj.core.api.Assertions.assertThat(captor.getValue().goal()).isEqualTo("准备 Java 后端算法面试");
  }

  @Test
  void continueDraftReturnsAssistantQuestion() throws Exception {
    when(currentUserIdProvider.currentUserId()).thenReturn(Optional.of(42L));
    when(draftService.continueDraft(42L, 100L, "想练数组")).thenReturn(new LearningPlanDraftResult(
        100L,
        LearningPlanDraftStatus.COLLECTING,
        "你每周可以投入几小时？",
        List.of("weeklyHours"),
        null));

    mockMvc.perform(post("/api/learning-plans/drafts/100/messages")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"message\":\"想练数组\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.status").value("COLLECTING"))
        .andExpect(jsonPath("$.data.assistantMessage").value("你每周可以投入几小时？"))
        .andExpect(jsonPath("$.data.missingFields[0]").value("weeklyHours"));
  }

  @Test
  void confirmDraftReturnsPlanSummary() throws Exception {
    when(currentUserIdProvider.currentUserId()).thenReturn(Optional.of(42L));
    when(draftService.confirmDraft(42L, 100L)).thenReturn(new LearningPlanConfirmResult(
        900L,
        "四周 Java 算法面试冲刺计划",
        LearningPlanStatus.ACTIVE));

    mockMvc.perform(post("/api/learning-plans/drafts/100/confirm"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.planId").value(900))
        .andExpect(jsonPath("$.data.title").value("四周 Java 算法面试冲刺计划"))
        .andExpect(jsonPath("$.data.status").value("ACTIVE"));
  }

  @Test
  void listAndDetailUseCurrentUser() throws Exception {
    when(currentUserIdProvider.currentUserId()).thenReturn(Optional.of(42L));
    LearningPlan plan = new LearningPlan(900L, 42L, LearningPlanStatus.ACTIVE, draftPlan(), Instant.now(), Instant.now());
    when(planService.listPlans(42L)).thenReturn(List.of(plan));
    when(planService.getPlan(42L, 900L)).thenReturn(plan);

    mockMvc.perform(get("/api/learning-plans"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data[0].id").value(900))
        .andExpect(jsonPath("$.data[0].title").value("四周 Java 算法面试冲刺计划"));

    mockMvc.perform(get("/api/learning-plans/900"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.id").value(900))
        .andExpect(jsonPath("$.data.phases[0].title").value("基础题型恢复"));
  }

  @Test
  void unauthenticatedRequestReturns401() throws Exception {
    when(currentUserIdProvider.currentUserId()).thenReturn(Optional.empty());

    mockMvc.perform(get("/api/learning-plans"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.error.code").value("AUTH_UNAUTHENTICATED"));
  }

  private LearningPlanDraftPlan draftPlan() {
    return new LearningPlanDraftPlan(
        "四周 Java 算法面试冲刺计划",
        "围绕数组和哈希表建立高频题型能力。",
        LearningPlanIntent.INTERVIEW_SPRINT,
        "准备 Java 后端算法面试",
        4,
        LearningPlanLevel.INTERMEDIATE,
        6,
        "Java",
        LearningPlanDifficultyPreference.MEDIUM,
        true,
        List.of("Array", "Hash Table"),
        "中级，每周 6 小时。",
        List.of(new LearningPlanPhaseDraft(
            1,
            "基础题型恢复",
            1,
            "数组和哈希表",
            List.of("恢复基础题型手感"),
            List.of("Array", "Hash Table"),
            List.of("能说明哈希表查找边界"),
            "整理错误原因。",
            List.of(new LearningPlanProblemDraft(
                "two-sum",
                1,
                "Two Sum",
                "两数之和",
                "EASY",
                List.of("Array", "Hash Table"),
                "恢复哈希表查找。",
                1)))),
        Map.of("problemRecommendationIncomplete", false));
  }
}
