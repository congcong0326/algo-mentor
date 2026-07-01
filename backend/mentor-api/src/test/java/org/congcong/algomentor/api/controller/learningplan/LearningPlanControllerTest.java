package org.congcong.algomentor.api.controller.learningplan;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Flow;
import java.util.concurrent.SubmissionPublisher;
import org.congcong.algomentor.agent.core.runlock.AgentRunLockToken;
import org.congcong.algomentor.ai.governance.admission.AiRunAdmission;
import org.congcong.algomentor.ai.governance.admission.AiRunAdmissionService;
import org.congcong.algomentor.ai.governance.admission.AiRunLifecycleService;
import org.congcong.algomentor.ai.governance.model.AiActor;
import org.congcong.algomentor.ai.governance.model.AiGovernanceMetadataKeys;
import org.congcong.algomentor.ai.governance.model.AiPurpose;
import org.congcong.algomentor.ai.governance.model.AiRunContext;
import org.congcong.algomentor.ai.governance.model.AiRunSource;
import org.congcong.algomentor.ai.governance.model.AiRunStatus;
import org.congcong.algomentor.ai.governance.policy.AiPurposePolicy;
import org.congcong.algomentor.api.config.ApiSseProperties;
import org.congcong.algomentor.api.controller.LocalizedApiExceptionHandler;
import org.congcong.algomentor.api.service.AiActorResolver;
import org.congcong.algomentor.identity.model.AuthUserStatus;
import org.congcong.algomentor.auth.security.AuthenticatedUserPrincipal;
import org.congcong.algomentor.auth.security.CurrentUserIdProvider;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlan;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanConfirmResult;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanDifficultyPreference;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanDraftPlan;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanDraftResult;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanDraftService;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanDraftStatus;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanException;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanIntent;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanLevel;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanPage;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanPhaseDraft;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanProblemDraft;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanService;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanStatus;
import org.congcong.algomentor.mentor.application.learningplan.stream.LearningPlanDraftEvent;
import org.congcong.algomentor.mentor.application.learningplan.stream.LearningPlanDraftStreamEvent;
import org.congcong.algomentor.mentor.application.learningplan.stream.LearningPlanDraftStreamService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@WebMvcTest(controllers = LearningPlanController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(LocalizedApiExceptionHandler.class)
class LearningPlanControllerTest {

  @Autowired
  private MockMvc mockMvc;

  @MockBean
  private LearningPlanDraftService draftService;

  @MockBean
  private LearningPlanService planService;

  @MockBean
  private CurrentUserIdProvider currentUserIdProvider;

  @MockBean
  private AiActorResolver actorResolver;

  @MockBean
  private AiRunAdmissionService admissionService;

  @MockBean
  private AiRunLifecycleService lifecycleService;

  @MockBean
  private LearningPlanDraftStreamService draftStreamService;

  @MockBean
  private ApiSseProperties sseProperties;

  @Test
  void createDraftWithoutStreamIsNotExposed() throws Exception {
    mockMvc.perform(post("/api/learning-plans/drafts")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {
                  "intent": "INTERVIEW_SPRINT",
                  "goal": "准备 Java 后端算法面试",
                  "durationWeeks": 4,
                  "level": "INTERMEDIATE",
                  "weeklyHours": 6
                }
                """))
        .andExpect(status().isMethodNotAllowed());

    verifyNoInteractions(draftService, admissionService, lifecycleService);
  }

  @Test
  void continueDraftReturnsAssistantQuestion() throws Exception {
    when(currentUserIdProvider.currentUser()).thenReturn(Optional.of(currentUser()));
    when(actorResolver.currentActor()).thenReturn(new AiActor(42L, Set.of(), true));
    when(admissionService.admit(any(AiRunContext.class))).thenAnswer(invocation -> admitted(invocation.getArgument(0)));
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
    verify(admissionService).admit(any(AiRunContext.class));
    verify(lifecycleService).markCompleted(any(AiRunAdmission.class), any(), eq(null), eq(null));
  }

  @Test
  void streamDraftReturnsSseAndUsesStreamingGovernance() throws Exception {
    when(currentUserIdProvider.currentUser()).thenReturn(Optional.of(currentUser()));
    when(actorResolver.currentActor()).thenReturn(new AiActor(42L, Set.of(), true));
    when(admissionService.admit(any(AiRunContext.class))).thenAnswer(invocation -> admitted(invocation.getArgument(0)));
    when(sseProperties.learningPlanDraftTimeoutMillis()).thenReturn(360_000L);
    when(draftStreamService.stream(eq(42L), any(), any(), any())).thenReturn(streamPublisher(new LearningPlanDraftResult(
        100L,
        LearningPlanDraftStatus.GENERATED,
        "已生成学习计划草案。",
        List.of(),
        draftPlan())));

    MvcResult result = mockMvc.perform(post("/api/learning-plans/drafts/stream")
            .contentType(MediaType.APPLICATION_JSON)
            .accept(MediaType.TEXT_EVENT_STREAM)
            .content("""
                {
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
        .andReturn();

    mockMvc.perform(asyncDispatch(result))
        .andExpect(status().isOk())
        .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content()
            .string(org.hamcrest.Matchers.containsString("event:draft_ready")));

    ArgumentCaptor<AiRunContext> governanceCaptor = ArgumentCaptor.forClass(AiRunContext.class);
    verify(admissionService).admit(governanceCaptor.capture());
    org.assertj.core.api.Assertions.assertThat(governanceCaptor.getValue().streaming()).isTrue();
    verify(sseProperties).learningPlanDraftTimeoutMillis();
    verify(lifecycleService).markRunning(any(AiRunAdmission.class), eq(null), eq(null));
    verify(lifecycleService).markCompleted(any(AiRunAdmission.class), any(), eq(null), eq(null));
  }

  @Test
  void confirmDraftReturnsPlanSummary() throws Exception {
    when(currentUserIdProvider.currentUser()).thenReturn(Optional.of(currentUser()));
    when(draftService.confirmDraft(42L, 100L)).thenReturn(new LearningPlanConfirmResult(
        900L,
        "四周 Java 算法面试冲刺计划",
        LearningPlanStatus.ACTIVE));

    mockMvc.perform(post("/api/learning-plans/drafts/100/confirm"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.planId").value(900))
        .andExpect(jsonPath("$.data.title").value("四周 Java 算法面试冲刺计划"))
        .andExpect(jsonPath("$.data.status").value("ACTIVE"));
    verifyNoInteractions(admissionService, lifecycleService);
  }

  @Test
  void listAndDetailUseCurrentUser() throws Exception {
    when(currentUserIdProvider.currentUser()).thenReturn(Optional.of(currentUser()));
    LearningPlan plan = new LearningPlan(900L, 42L, LearningPlanStatus.ACTIVE, draftPlan(), Instant.now(), Instant.now());
    when(planService.listPlans(42L, 2, 5)).thenReturn(new LearningPlanPage(
        List.of(plan),
        12,
        2,
        5,
        8,
        4,
        Instant.parse("2026-06-22T00:00:00Z")));
    when(planService.getPlan(42L, 900L)).thenReturn(plan);

    mockMvc.perform(get("/api/learning-plans?page=2&pageSize=5"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.items[0].id").value(900))
        .andExpect(jsonPath("$.data.items[0].title").value("四周 Java 算法面试冲刺计划"))
        .andExpect(jsonPath("$.data.total").value(12))
        .andExpect(jsonPath("$.data.page").value(2))
        .andExpect(jsonPath("$.data.pageSize").value(5))
        .andExpect(jsonPath("$.data.activeCount").value(8))
        .andExpect(jsonPath("$.data.archivedCount").value(4))
        .andExpect(jsonPath("$.data.latestCreatedAt").value("2026-06-22T00:00:00Z"));

    mockMvc.perform(get("/api/learning-plans/900"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.id").value(900))
        .andExpect(jsonPath("$.data.phases[0].title").value("基础题型恢复"));
  }

  @Test
  void deletePlanUsesCurrentUser() throws Exception {
    when(currentUserIdProvider.currentUser()).thenReturn(Optional.of(currentUser()));

    mockMvc.perform(delete("/api/learning-plans/900"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true));

    verify(planService).deletePlan(42L, 900L);
  }

  @Test
  void unauthenticatedRequestReturns401() throws Exception {
    when(currentUserIdProvider.currentUser()).thenReturn(Optional.empty());

    mockMvc.perform(get("/api/learning-plans").header("Accept-Language", "en-US"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.error.code").value("AUTH_UNAUTHENTICATED"))
        .andExpect(jsonPath("$.error.messageKey").value("api.error.AUTH_UNAUTHENTICATED"))
        .andExpect(jsonPath("$.error.message").value("You are not signed in or your session has expired."));
  }

  private AuthenticatedUserPrincipal currentUser() {
    return new AuthenticatedUserPrincipal(
        42L,
        "learner@example.com",
        "Learner",
        null,
        List.of(),
        AuthUserStatus.ACTIVE);
  }

  private AiRunAdmission admitted(AiRunContext context) {
    AiPurposePolicy policy = new AiPurposePolicy(
        true, 50, 1, 16384, 2048, 8, true, true, false, false,
        null, null, "learning-plan-p0");
    return new AiRunAdmission(
        1L,
        context.runId(),
        context.actor().userId(),
        context.purpose(),
        context.source(),
        AiRunStatus.ADMITTED,
        "ALL",
        new AgentRunLockToken("user:42:ai:all", "node-1", "token-1", null),
        policy,
        Map.of(
            AiGovernanceMetadataKeys.RUN_ID, context.runId(),
            AiGovernanceMetadataKeys.PURPOSE, context.purpose().name(),
            AiGovernanceMetadataKeys.SOURCE, context.source().name()),
        Instant.now());
  }

  private Flow.Publisher<LearningPlanDraftStreamEvent> streamPublisher(LearningPlanDraftResult result) {
    return subscriber -> {
      SubmissionPublisher<LearningPlanDraftStreamEvent> publisher = new SubmissionPublisher<>();
      publisher.subscribe(subscriber);
      publisher.submit(new LearningPlanDraftStreamEvent.Draft(new LearningPlanDraftEvent.DraftReady(result)));
      publisher.close();
    };
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
