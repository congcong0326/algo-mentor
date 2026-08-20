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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
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
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanBrief;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanConfirmResult;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanContentLocale;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanDraftMetadataKeys;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanDraftPlan;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanDraftResult;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanDraftService;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanDraftStatus;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanException;
import org.congcong.algomentor.mentor.application.learningplan.policy.LearningPlanAiRevisionAccessService;
import org.congcong.algomentor.mentor.application.learningplan.policy.LearningPlanAiRevisionCapabilities;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanIntent;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanLevel;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanPage;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanPhaseDraft;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanProblemDraft;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanProgressSummary;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanService;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanStatus;
import org.congcong.algomentor.mentor.application.learningplan.proposal.LearningPlanDraftRevisionResult;
import org.congcong.algomentor.mentor.application.learningplan.proposal.LearningPlanExtensionApplyResult;
import org.congcong.algomentor.mentor.application.learningplan.proposal.LearningPlanExtensionApplyService;
import org.congcong.algomentor.mentor.application.learningplan.proposal.LearningPlanExtensionDraft;
import org.congcong.algomentor.mentor.application.learningplan.proposal.LearningPlanExtensionResult;
import org.congcong.algomentor.mentor.application.learningplan.proposal.LearningPlanProposalGroupService;
import org.congcong.algomentor.mentor.application.learningplan.proposal.LearningPlanProposalGroupStatus;
import org.congcong.algomentor.mentor.application.learningplan.proposal.LearningPlanProposalRevisionStatus;
import org.congcong.algomentor.mentor.application.learningplan.proposal.stream.LearningPlanDraftRevisionStreamService;
import org.congcong.algomentor.mentor.application.learningplan.proposal.stream.LearningPlanExtensionProposalStreamService;
import org.congcong.algomentor.mentor.application.learningplan.proposal.stream.LearningPlanProposalEvent;
import org.congcong.algomentor.mentor.application.learningplan.proposal.stream.LearningPlanProposalStreamEvent;
import org.congcong.algomentor.mentor.application.learningplan.stream.LearningPlanDraftEvent;
import org.congcong.algomentor.mentor.application.learningplan.stream.LearningPlanDraftStreamEvent;
import org.congcong.algomentor.mentor.application.learningplan.stream.LearningPlanDraftStreamService;
import org.congcong.algomentor.mentor.application.learningplan.template.LearningPlanTemplateDraftCommand;
import org.congcong.algomentor.mentor.application.learningplan.template.LearningPlanTemplateDraftService;
import org.congcong.algomentor.mentor.application.practice.PracticeProgress;
import org.congcong.algomentor.mentor.application.practice.PracticeProgressStatus;
import org.congcong.algomentor.mentor.application.practice.PracticeSessionRepository;
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
  private LearningPlanDraftRevisionStreamService draftRevisionStreamService;

  @MockBean
  private LearningPlanExtensionProposalStreamService extensionProposalStreamService;

  @MockBean
  private LearningPlanExtensionApplyService extensionApplyService;

  @MockBean
  private LearningPlanProposalGroupService proposalGroupService;

  @MockBean
  private PracticeSessionRepository practiceSessionRepository;

  @MockBean
  private LearningPlanTemplateDraftService templateDraftService;

  @MockBean
  private LearningPlanAiRevisionAccessService aiRevisionAccessService;

  @MockBean
  private ApiSseProperties sseProperties;

  @org.junit.jupiter.api.BeforeEach
  void enableAiRevisionCapabilitiesForControllerTests() {
    when(aiRevisionAccessService.capabilities(42L))
        .thenReturn(new LearningPlanAiRevisionCapabilities(true, true, true));
  }

  @Test
  void createDraftWithoutStreamIsNotExposed() throws Exception {
    mockMvc.perform(post("/api/learning-plans/drafts")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {
                  "intent": "INTERVIEW_SPRINT",
                  "objective": "准备 Java 后端算法面试",
                  "targetProblemCount": 15,
                  "level": "INTERMEDIATE",
                  "difficultyDistribution": {"easyPercent": 35, "mediumPercent": 55, "hardPercent": 10}
                }
                """))
        .andExpect(status().isMethodNotAllowed());

    verifyNoInteractions(draftService, admissionService, lifecycleService);
  }

  @Test
  void continueDraftReturnsAssistantQuestion() throws Exception {
    when(currentUserIdProvider.currentUser()).thenReturn(Optional.of(currentUser()));
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
    verifyNoInteractions(admissionService, lifecycleService);
  }

  @Test
  void streamDraftDefaultsBlankObjectiveAndPersonalizationAndUsesStreamingGovernance() throws Exception {
    when(currentUserIdProvider.currentUser()).thenReturn(Optional.of(currentUser()));
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
            .header("Accept-Language", "en-US,en;q=0.9")
            .content("""
                {
                  "intent": "INTERVIEW_SPRINT",
                  "objective": "  ",
                  "targetProblemCount": 15,
                  "level": "INTERMEDIATE",
                  "programmingLanguage": "Java",
                  "difficultyDistribution": {"easyPercent": 35, "mediumPercent": 55, "hardPercent": 10},
                  "topicPreferences": ["Array", "Hash Table"]
                }
                """))
        .andReturn();

    mockMvc.perform(asyncDispatch(result))
        .andExpect(status().isOk())
        .andExpect(header().string("Vary", "Accept-Language"))
        .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content()
            .string(org.hamcrest.Matchers.containsString("event:draft_ready")));

    ArgumentCaptor<LearningPlanBrief> briefCaptor = ArgumentCaptor.forClass(LearningPlanBrief.class);
    verify(draftStreamService).stream(eq(42L), briefCaptor.capture(), any(), eq(Map.of()));
    org.assertj.core.api.Assertions.assertThat(briefCaptor.getValue().contentLocale())
        .isEqualTo(LearningPlanContentLocale.EN_US);
    org.assertj.core.api.Assertions.assertThat(briefCaptor.getValue().objective())
        .isEqualTo("Improve problem-solving consistency for coding interviews");
    org.assertj.core.api.Assertions.assertThat(briefCaptor.getValue().targetProblemCount()).isEqualTo(15);
    org.assertj.core.api.Assertions.assertThat(briefCaptor.getValue().durationWeeks()).isEqualTo(3);
    org.assertj.core.api.Assertions.assertThat(briefCaptor.getValue().weeklyHours()).isEqualTo(5);
    org.assertj.core.api.Assertions.assertThat(briefCaptor.getValue().personalizationEnabled()).isTrue();
    verifyNoInteractions(admissionService, lifecycleService);
    verify(sseProperties).learningPlanDraftTimeoutMillis();
  }

  @Test
  void streamDraftPreservesExplicitObjectiveAndDisabledPersonalization() throws Exception {
    when(currentUserIdProvider.currentUser()).thenReturn(Optional.of(currentUser()));
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
            .header("Accept-Language", "zh-CN")
            .content("""
                {
                  "intent": "INTERVIEW_SPRINT",
                  "objective": "为 Java 面试集中练习动态规划",
                  "targetProblemCount": 15,
                  "level": "INTERMEDIATE",
                  "difficultyDistribution": {"easyPercent": 35, "mediumPercent": 55, "hardPercent": 10},
                  "topicPreferences": ["Dynamic Programming"],
                  "additionalConstraints": "每周复盘一次",
                  "personalizationEnabled": false
                }
                """))
        .andReturn();

    mockMvc.perform(asyncDispatch(result)).andExpect(status().isOk());

    ArgumentCaptor<LearningPlanBrief> briefCaptor = ArgumentCaptor.forClass(LearningPlanBrief.class);
    verify(draftStreamService).stream(eq(42L), briefCaptor.capture(), any(), eq(Map.of()));
    org.assertj.core.api.Assertions.assertThat(briefCaptor.getValue().objective())
        .isEqualTo("为 Java 面试集中练习动态规划");
    org.assertj.core.api.Assertions.assertThat(briefCaptor.getValue().additionalConstraints()).isEqualTo("每周复盘一次");
    org.assertj.core.api.Assertions.assertThat(briefCaptor.getValue().personalizationEnabled()).isFalse();
  }

  @Test
  void streamDraftRejectsUnknownRequestFields() throws Exception {
    when(currentUserIdProvider.currentUser()).thenReturn(Optional.of(currentUser()));
    mockMvc.perform(post("/api/learning-plans/drafts/stream")
            .contentType(MediaType.APPLICATION_JSON)
            .accept(MediaType.TEXT_EVENT_STREAM)
            .content("""
                {
                  "intent": "INTERVIEW_SPRINT",
                  "objective": "准备 Java 后端算法面试",
                  "unexpectedInput": "不支持的字段",
                  "targetProblemCount": 15,
                  "level": "INTERMEDIATE",
                  "difficultyDistribution": {"easyPercent": 35, "mediumPercent": 55, "hardPercent": 10}
                }
                """))
        .andExpect(status().isBadRequest());

    verifyNoInteractions(draftStreamService);
  }

  @Test
  void streamDraftRevisionReturnsSseAndUsesLearningPlanDraftRevisionSource() throws Exception {
    when(currentUserIdProvider.currentUser()).thenReturn(Optional.of(currentUser()));
    when(sseProperties.learningPlanDraftTimeoutMillis()).thenReturn(360_000L);
    when(draftRevisionStreamService.stream(eq(42L), eq(100L), eq("请增加动态规划训练"), any(), any()))
        .thenReturn(proposalPublisher(new LearningPlanProposalStreamEvent.Proposal(
            LearningPlanProposalStreamEvent.ProposalProfile.DRAFT_REVISION,
            new LearningPlanProposalEvent.DraftRevisionReady(new LearningPlanDraftRevisionResult(
                700L,
                701L,
                100L,
                2,
                LearningPlanProposalRevisionStatus.READY,
                List.of(699L),
                new LearningPlanDraftResult(
                    100L,
                    LearningPlanDraftStatus.GENERATED,
                    "已生成学习计划修订草案。",
                    List.of(),
                    draftPlan()))))));

    MvcResult result = mockMvc.perform(post("/api/learning-plans/drafts/100/revisions/stream")
            .contentType(MediaType.APPLICATION_JSON)
            .accept(MediaType.TEXT_EVENT_STREAM)
            .content("{\"instruction\":\"  请增加动态规划训练  \"}"))
        .andReturn();

    mockMvc.perform(asyncDispatch(result))
        .andExpect(status().isOk())
        .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content()
            .string(org.hamcrest.Matchers.allOf(
                org.hamcrest.Matchers.containsString("event:draft_revision_ready"),
                org.hamcrest.Matchers.containsString("\"proposalGroupId\":700"),
                org.hamcrest.Matchers.containsString("\"proposalId\":701"),
                org.hamcrest.Matchers.containsString("\"draftId\":100"),
                org.hamcrest.Matchers.containsString("\"supersededProposalIds\":[699]"),
                org.hamcrest.Matchers.containsString("\"draftPlan\""),
                org.hamcrest.Matchers.containsString("\"programmingLanguage\":\"Java\""),
                org.hamcrest.Matchers.containsString("\"slug\":\"two-sum\""))));

    verify(draftRevisionStreamService).stream(eq(42L), eq(100L), eq("请增加动态规划训练"), any(), eq(Map.of()));
    verifyNoInteractions(admissionService, lifecycleService);
  }

  @Test
  void streamExtensionProposalReturnsSseAndUsesLearningPlanExtensionSource() throws Exception {
    when(currentUserIdProvider.currentUser()).thenReturn(Optional.of(currentUser()));
    when(sseProperties.learningPlanDraftTimeoutMillis()).thenReturn(360_000L);
    when(extensionProposalStreamService.streamFirstRevision(eq(42L), eq(900L), eq("补充图论训练"), any(), any()))
        .thenReturn(proposalPublisher(new LearningPlanProposalStreamEvent.Proposal(
            LearningPlanProposalStreamEvent.ProposalProfile.PLAN_EXTENSION,
            new LearningPlanProposalEvent.PlanExtensionReady(new LearningPlanExtensionResult(
                800L,
                801L,
                900L,
                1,
                LearningPlanProposalRevisionStatus.READY,
                List.of(),
                "补充图论训练",
                extensionDraft())))));

    MvcResult result = mockMvc.perform(post("/api/learning-plans/900/extension-proposals/stream")
            .contentType(MediaType.APPLICATION_JSON)
            .accept(MediaType.TEXT_EVENT_STREAM)
            .content("{\"instruction\":\" 补充图论训练 \"}"))
        .andReturn();

    mockMvc.perform(asyncDispatch(result))
        .andExpect(status().isOk())
        .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content()
            .string(org.hamcrest.Matchers.allOf(
                org.hamcrest.Matchers.containsString("event:plan_extension_ready"),
                org.hamcrest.Matchers.containsString("\"proposalGroupId\":800"),
                org.hamcrest.Matchers.containsString("\"proposalId\":801"),
                org.hamcrest.Matchers.containsString("\"planId\":900"),
                org.hamcrest.Matchers.containsString("\"supersededProposalIds\":[]"),
                org.hamcrest.Matchers.containsString("\"extensionDraft\""),
                org.hamcrest.Matchers.containsString("\"phaseIndex\":2"),
                org.hamcrest.Matchers.containsString("\"tags\":[\"Graph\",\"DFS\"]"),
                org.hamcrest.Matchers.containsString("\"title\":\"Number of Islands\""))));

    verify(extensionProposalStreamService).streamFirstRevision(eq(42L), eq(900L), eq("补充图论训练"), any(), eq(Map.of()));
    verifyNoInteractions(admissionService, lifecycleService);
  }

  @Test
  void applyExtensionProposalReturnsAppliedResponse() throws Exception {
    when(currentUserIdProvider.currentUser()).thenReturn(Optional.of(currentUser()));
    when(extensionApplyService.apply(42L, 900L, 800L)).thenReturn(new LearningPlanExtensionApplyResult(
        900L,
        800L,
        801L,
        LearningPlanProposalGroupStatus.APPLIED,
        2));

    mockMvc.perform(post("/api/learning-plans/900/extension-proposals/800/apply"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.planId").value(900))
        .andExpect(jsonPath("$.data.proposalGroupId").value(800))
        .andExpect(jsonPath("$.data.proposalId").value(801))
        .andExpect(jsonPath("$.data.status").value("APPLIED"))
        .andExpect(jsonPath("$.data.appendedPhaseCount").value(2));
  }

  @Test
  void discardExtensionProposalUsesCurrentUser() throws Exception {
    when(currentUserIdProvider.currentUser()).thenReturn(Optional.of(currentUser()));

    mockMvc.perform(post("/api/learning-plans/900/extension-proposals/800/discard"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true));

    verify(proposalGroupService).discardExtensionProposal(42L, 900L, 800L);
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
  void createDraftFromTemplateAcceptsRhythmSettingsAndReturnsGeneratedDraftWithoutAiGovernance() throws Exception {
    when(currentUserIdProvider.currentUser()).thenReturn(Optional.of(currentUser()));
    when(templateDraftService.createDraft(eq(42L), any(LearningPlanTemplateDraftCommand.class)))
        .thenReturn(new LearningPlanDraftResult(
            101L,
            LearningPlanDraftStatus.GENERATED,
            "已根据模板生成学习计划草案。",
            List.of(),
            draftPlan()));

    mockMvc.perform(post("/api/learning-plans/drafts/from-template")
            .contentType(MediaType.APPLICATION_JSON)
            .header("Accept-Language", "en-US,en;q=0.9")
            .content("""
                {
                  "templateId": "tih_best_practice_50_5weeks",
                  "programmingLanguage": "Java",
                  "dailyProblemCount": 3,
                  "trainingDaysPerWeek": 4
                }
                """))
        .andExpect(status().isOk())
        .andExpect(header().string("Vary", "Accept-Language"))
        .andExpect(jsonPath("$.data.draftId").value(101))
        .andExpect(jsonPath("$.data.status").value("GENERATED"))
        .andExpect(jsonPath("$.data.draftPlan.title").value("四周 Java 算法面试冲刺计划"))
        .andExpect(jsonPath("$.data.draftPlan.objective").value("准备 Java 后端算法面试"))
        .andExpect(jsonPath("$.data.draftPlan.difficultyDistribution.easyPercent").value(35))
        .andExpect(jsonPath("$.data.draftPlan.difficultyDistribution.mediumPercent").value(55))
        .andExpect(jsonPath("$.data.draftPlan.difficultyDistribution.hardPercent").value(10))
        .andExpect(jsonPath("$.data.draftPlan.additionalConstraints").value("中级，每周 6 小时。"))
        .andExpect(jsonPath("$.data.draftPlan.metadata.dailyProblemCount").value(1))
        .andExpect(jsonPath("$.data.draftPlan.metadata.personalizationEnabled").doesNotExist())
        .andExpect(jsonPath("$.data.draftPlan.metadata.template").doesNotExist())
        .andExpect(jsonPath("$.data.draftPlan.metadata.internalOnly").doesNotExist());

    ArgumentCaptor<LearningPlanTemplateDraftCommand> commandCaptor =
        ArgumentCaptor.forClass(LearningPlanTemplateDraftCommand.class);
    verify(templateDraftService).createDraft(eq(42L), commandCaptor.capture());
    org.assertj.core.api.Assertions.assertThat(commandCaptor.getValue().dailyProblemCount()).isEqualTo(3);
    org.assertj.core.api.Assertions.assertThat(commandCaptor.getValue().trainingDaysPerWeek()).isEqualTo(4);
    org.assertj.core.api.Assertions.assertThat(commandCaptor.getValue().recommendationReasonLocale())
        .isEqualTo("en-US");
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
        Instant.parse("2026-06-22T00:00:00Z"),
        Map.of(900L, LearningPlanProgressSummary.fromCounts(75, 36))));
    when(planService.getPlan(42L, 900L)).thenReturn(plan);
    when(practiceSessionRepository.findProgressByPlan(42L, 900L)).thenReturn(List.of());

    mockMvc.perform(get("/api/learning-plans?page=2&pageSize=5"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.items[0].id").value(900))
        .andExpect(jsonPath("$.data.items[0].title").value("四周 Java 算法面试冲刺计划"))
        .andExpect(jsonPath("$.data.items[0].objective").value("准备 Java 后端算法面试"))
        .andExpect(jsonPath("$.data.items[0].progressSummary.totalProblemCount").value(75))
        .andExpect(jsonPath("$.data.items[0].progressSummary.completedProblemCount").value(36))
        .andExpect(jsonPath("$.data.items[0].progressSummary.progressPercent").value(48.0))
        .andExpect(jsonPath("$.data.total").value(12))
        .andExpect(jsonPath("$.data.page").value(2))
        .andExpect(jsonPath("$.data.pageSize").value(5))
        .andExpect(jsonPath("$.data.activeCount").value(8))
        .andExpect(jsonPath("$.data.archivedCount").value(4))
        .andExpect(jsonPath("$.data.latestCreatedAt").value("2026-06-22T00:00:00Z"));

    mockMvc.perform(get("/api/learning-plans/900"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.id").value(900))
        .andExpect(jsonPath("$.data.objective").value("准备 Java 后端算法面试"))
        .andExpect(jsonPath("$.data.difficultyDistribution.easyPercent").value(35))
        .andExpect(jsonPath("$.data.difficultyDistribution.mediumPercent").value(55))
        .andExpect(jsonPath("$.data.difficultyDistribution.hardPercent").value(10))
        .andExpect(jsonPath("$.data.additionalConstraints").value("中级，每周 6 小时。"))
        .andExpect(jsonPath("$.data.phases[0].title").value("基础题型恢复"))
        .andExpect(jsonPath("$.data.phases[0].problems[0].progressStatus").value("NOT_STARTED"))
        .andExpect(jsonPath("$.data.loadSummary.plannedProblemCount").value(1))
        .andExpect(jsonPath("$.data.nextTrainingPackage.newProblemCount").exists())
        .andExpect(jsonPath("$.data.rhythmSettings.dailyProblemCount").value(1))
        .andExpect(jsonPath("$.data.rhythmSettings.trainingDaysPerWeek").value(5))
        .andExpect(jsonPath("$.data.metadata.dailyProblemCount").value(1))
        .andExpect(jsonPath("$.data.metadata.targetProblemCount").value(15))
        .andExpect(jsonPath("$.data.metadata.personalizationEnabled").doesNotExist())
        .andExpect(jsonPath("$.data.metadata.template").doesNotExist())
        .andExpect(jsonPath("$.data.metadata.internalOnly").doesNotExist())
        .andExpect(jsonPath("$.data.paceSummary.currentWeek").exists());
  }

  @Test
  void updateRhythmUsesCurrentUserAndReturnsRefreshedDetail() throws Exception {
    when(currentUserIdProvider.currentUser()).thenReturn(Optional.of(currentUser()));
    LearningPlan plan = new LearningPlan(900L, 42L, LearningPlanStatus.ACTIVE, draftPlan(), Instant.now(), Instant.now());
    when(planService.updateRhythm(42L, 900L, 3, 4)).thenReturn(plan);
    when(planService.getPlan(42L, 900L)).thenReturn(plan);
    when(practiceSessionRepository.findProgressByPlan(42L, 900L)).thenReturn(List.of());

    mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch("/api/learning-plans/900/rhythm")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {
                  "dailyProblemCount": 3,
                  "trainingDaysPerWeek": 4
                }
                """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.id").value(900))
        .andExpect(jsonPath("$.data.rhythmSettings.dailyProblemCount").exists());

    verify(planService).updateRhythm(42L, 900L, 3, 4);
  }

  @Test
  void detailMergesPracticeProgressStatus() throws Exception {
    when(currentUserIdProvider.currentUser()).thenReturn(Optional.of(currentUser()));
    LearningPlan plan = new LearningPlan(
        900L,
        42L,
        LearningPlanStatus.ACTIVE,
        draftPlanWithMultipleProblems(),
        Instant.now(),
        Instant.now());
    when(planService.getPlan(42L, 900L)).thenReturn(plan);
    when(practiceSessionRepository.findProgressByPlan(42L, 900L)).thenReturn(List.of(
        progress("two-sum", 1, PracticeProgressStatus.COMPLETED),
        progress("valid-palindrome", 1, PracticeProgressStatus.IN_PROGRESS),
        progress("number-of-islands", 2, PracticeProgressStatus.SKIPPED)));

    mockMvc.perform(get("/api/learning-plans/900"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.phases[0].problems[0].progressStatus").value("COMPLETED"))
        .andExpect(jsonPath("$.data.phases[0].problems[1].progressStatus").value("IN_PROGRESS"))
        .andExpect(jsonPath("$.data.phases[0].problems[2].progressStatus").value("NOT_STARTED"))
        .andExpect(jsonPath("$.data.phases[1].problems[0].progressStatus").value("SKIPPED"))
        .andExpect(jsonPath("$.data.paceSummary.completedProblemCountToDate").exists());
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

  private Flow.Publisher<LearningPlanProposalStreamEvent> proposalPublisher(LearningPlanProposalStreamEvent event) {
    return subscriber -> {
      SubmissionPublisher<LearningPlanProposalStreamEvent> publisher = new SubmissionPublisher<>();
      publisher.subscribe(subscriber);
      publisher.submit(event);
      publisher.close();
    };
  }

  private LearningPlanExtensionDraft extensionDraft() {
    return new LearningPlanExtensionDraft(
        "补充图论训练",
        List.of(new LearningPlanPhaseDraft(
            2,
            "图论补强",
            1,
            "图遍历",
            List.of(new LearningPlanProblemDraft(
                "number-of-islands",
                1,
                "Number of Islands",
                "岛屿数量",
                "MEDIUM",
                List.of("Graph", "DFS"),
                "练习图遍历。",
                1)))),
        Map.of());
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
        new org.congcong.algomentor.mentor.application.learningplan.LearningPlanDifficultyDistribution(35, 55, 10),
        List.of("Array", "Hash Table"),
        "中级，每周 6 小时。",
        List.of(new LearningPlanPhaseDraft(
            1,
            "基础题型恢复",
            1,
            "数组和哈希表",
            List.of(new LearningPlanProblemDraft(
                "two-sum",
                1,
                "Two Sum",
                "两数之和",
                "EASY",
                List.of("Array", "Hash Table"),
                "恢复哈希表查找。",
                1)))),
        Map.of(
            "dailyProblemCount", 1,
            LearningPlanDraftMetadataKeys.TARGET_PROBLEM_COUNT, 15,
            LearningPlanDraftMetadataKeys.CONTENT_LOCALE, "en-US",
            LearningPlanDraftMetadataKeys.PERSONALIZATION_ENABLED, true,
            "template", Map.of("templateId", "internal-template", "matchedProblemCount", 1),
            "internalOnly", "internal-value"));
  }

  private LearningPlanDraftPlan draftPlanWithMultipleProblems() {
    return new LearningPlanDraftPlan(
        "四周 Java 算法面试冲刺计划",
        "围绕数组和哈希表建立高频题型能力。",
        LearningPlanIntent.INTERVIEW_SPRINT,
        "准备 Java 后端算法面试",
        4,
        LearningPlanLevel.INTERMEDIATE,
        6,
        "Java",
        new org.congcong.algomentor.mentor.application.learningplan.LearningPlanDifficultyDistribution(35, 55, 10),
        List.of("Array", "Hash Table"),
        "中级，每周 6 小时。",
        List.of(
            new LearningPlanPhaseDraft(
                1,
                "基础题型恢复",
                1,
                "数组和哈希表",
                List.of(
                    problem("two-sum", 1, "Two Sum", "两数之和", "EASY", "恢复哈希表查找。", 1),
                    problem("valid-palindrome", 125, "Valid Palindrome", "验证回文串", "EASY", "练习双指针。", 2),
                    problem("merge-intervals", 56, "Merge Intervals", "合并区间", "MEDIUM", "练习区间归并。", 3))),
            new LearningPlanPhaseDraft(
                2,
                "图论补强",
                1,
                "图遍历",
                List.of(problem("number-of-islands", 200, "Number of Islands", "岛屿数量", "MEDIUM", "练习图遍历。", 1)))),
        Map.of());
  }

  private LearningPlanProblemDraft problem(
      String slug,
      int frontendId,
      String title,
      String titleCn,
      String difficulty,
      String reason,
      int sortOrder) {
    return new LearningPlanProblemDraft(
        slug,
        frontendId,
        title,
        titleCn,
        difficulty,
        List.of("Array"),
        reason,
        sortOrder);
  }

  private PracticeProgress progress(String problemSlug, int phaseIndex, PracticeProgressStatus status) {
    return new PracticeProgress(
        phaseIndex * 100L + problemSlug.length(),
        42L,
        900L,
        phaseIndex,
        problemSlug,
        status,
        Instant.parse("2026-06-25T00:00:00Z"),
        status == PracticeProgressStatus.COMPLETED ? Instant.parse("2026-06-25T00:00:00Z") : null,
        status == PracticeProgressStatus.SKIPPED ? Instant.parse("2026-06-25T00:00:00Z") : null,
        Instant.parse("2026-06-25T00:00:00Z"),
        Instant.parse("2026-06-25T00:00:00Z"));
  }
}
