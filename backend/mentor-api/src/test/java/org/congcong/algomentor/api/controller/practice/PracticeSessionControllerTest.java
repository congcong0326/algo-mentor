package org.congcong.algomentor.api.controller.practice;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Flow;
import java.util.concurrent.SubmissionPublisher;
import org.congcong.algomentor.agent.core.runtime.model.AgentActiveRun;
import org.congcong.algomentor.agent.core.AgentStreamEvent;
import org.congcong.algomentor.ai.governance.admission.AiRunAdmission;
import org.congcong.algomentor.ai.governance.admission.AiRunAdmissionService;
import org.congcong.algomentor.ai.governance.model.AiActor;
import org.congcong.algomentor.ai.governance.model.AiGovernanceMetadataKeys;
import org.congcong.algomentor.ai.governance.model.AiPurpose;
import org.congcong.algomentor.ai.governance.model.AiRunContext;
import org.congcong.algomentor.ai.governance.model.AiRunSource;
import org.congcong.algomentor.ai.governance.model.AiRunStatus;
import org.congcong.algomentor.ai.governance.policy.AiPurposePolicy;
import org.congcong.algomentor.api.controller.AiGovernanceExceptionHandler;
import org.congcong.algomentor.api.service.AiActorResolver;
import org.congcong.algomentor.api.service.LlmStreamSseMapper;
import org.congcong.algomentor.auth.model.AuthUserStatus;
import org.congcong.algomentor.auth.security.AuthenticatedUserPrincipal;
import org.congcong.algomentor.auth.security.CurrentUserIdProvider;
import org.congcong.algomentor.mentor.application.conversation.AgentConversationRunInProgressException;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanException;
import org.congcong.algomentor.mentor.application.practice.PracticeChatProblemDetail;
import org.congcong.algomentor.mentor.application.practice.PracticeChatPromptConstants;
import org.congcong.algomentor.mentor.application.practice.PracticeChatReference;
import org.congcong.algomentor.mentor.application.practice.PracticeCodeReview;
import org.congcong.algomentor.mentor.application.practice.PracticeCodeReviewEvidence;
import org.congcong.algomentor.mentor.application.practice.PracticeCodeReviewHistory;
import org.congcong.algomentor.mentor.application.practice.PracticeCodeReviewScore;
import org.congcong.algomentor.mentor.application.practice.PracticeCodeReviewSummary;
import org.congcong.algomentor.mentor.application.practice.PracticeCompletionGate;
import org.congcong.algomentor.mentor.application.practice.PracticeMessageStreamService;
import org.congcong.algomentor.mentor.application.practice.PracticeProgressStatus;
import org.congcong.algomentor.mentor.application.practice.PracticeSession;
import org.congcong.algomentor.mentor.application.practice.PracticeSessionMessage;
import org.congcong.algomentor.mentor.application.practice.PracticeSessionResult;
import org.congcong.algomentor.mentor.application.practice.PracticeSessionService;
import org.congcong.algomentor.mentor.application.practice.PracticeSessionStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@WebMvcTest(controllers = PracticeSessionController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import({
    PracticeSessionControllerTest.TestConfig.class,
    PracticeSessionExceptionHandler.class,
    AiGovernanceExceptionHandler.class,
    LlmStreamSseMapper.class
})
class PracticeSessionControllerTest {

  @Autowired
  private MockMvc mockMvc;

  @Autowired
  private PracticeSessionService practiceSessionService;

  @Autowired
  private PracticeMessageStreamService streamService;

  @Autowired
  private CurrentUserIdProvider currentUserIdProvider;

  @Autowired
  private AiActorResolver actorResolver;

  @Autowired
  private AiRunAdmissionService admissionService;

  @BeforeEach
  void resetMocks() {
    reset(practiceSessionService, streamService, currentUserIdProvider, actorResolver, admissionService);
  }

  @Test
  void createPracticeSessionUsesCurrentUserAndLocale() throws Exception {
    when(currentUserIdProvider.currentUser()).thenReturn(Optional.of(currentUser()));
    when(practiceSessionService.createOrReuse(eq(42L), any(PracticeChatReference.class)))
        .thenReturn(result(PracticeProgressStatus.IN_PROGRESS));

    mockMvc.perform(post("/api/learning-plans/900/phases/1/problems/two-sum/practice-session?locale=zh-CN"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.session.id").value(50))
        .andExpect(jsonPath("$.data.messages[0].messageType").value("PROBLEM_STATEMENT"));

    ArgumentCaptor<PracticeChatReference> referenceCaptor = ArgumentCaptor.forClass(PracticeChatReference.class);
    verify(practiceSessionService).createOrReuse(eq(42L), referenceCaptor.capture());
    org.assertj.core.api.Assertions.assertThat(referenceCaptor.getValue().planId()).isEqualTo(900L);
    org.assertj.core.api.Assertions.assertThat(referenceCaptor.getValue().phaseIndex()).isEqualTo(1);
    org.assertj.core.api.Assertions.assertThat(referenceCaptor.getValue().problemSlug()).isEqualTo("two-sum");
    org.assertj.core.api.Assertions.assertThat(referenceCaptor.getValue().locale()).isEqualTo("zh-CN");
  }

  @Test
  void getPracticeSessionReturnsProgressStatus() throws Exception {
    when(currentUserIdProvider.currentUser()).thenReturn(Optional.of(currentUser()));
    when(practiceSessionService.get(42L, 50L)).thenReturn(result(PracticeProgressStatus.IN_PROGRESS));

    mockMvc.perform(get("/api/practice-sessions/50"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.session.progressStatus").value("IN_PROGRESS"));
  }

  @Test
  void sessionResponseIncludesLatestReviewAndCompletionGate() throws Exception {
    when(currentUserIdProvider.currentUser()).thenReturn(Optional.of(currentUser()));
    when(practiceSessionService.get(42L, 50L)).thenReturn(resultWithPassedReview());

    mockMvc.perform(get("/api/practice-sessions/50"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.latestReview.totalScore").value(7.0))
        .andExpect(jsonPath("$.data.completionGate.reasonCode").value("PASSED"));
  }

  @Test
  void reviewHistoryEndpointReturnsReviewsAndGate() throws Exception {
    when(currentUserIdProvider.currentUser()).thenReturn(Optional.of(currentUser()));
    when(practiceSessionService.history(42L, 50L)).thenReturn(reviewHistory());

    mockMvc.perform(get("/api/practice-sessions/50/reviews"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.latestReview.totalScore").value(7.0))
        .andExpect(jsonPath("$.data.reviews[0].versionNo").value(2))
        .andExpect(jsonPath("$.data.completionGate.reasonCode").value("PASSED"));

    verify(practiceSessionService).history(42L, 50L);
  }

  @Test
  void reviewDetailEndpointReturnsReviewBody() throws Exception {
    when(currentUserIdProvider.currentUser()).thenReturn(Optional.of(currentUser()));
    when(practiceSessionService.detail(42L, 50L, 1002L)).thenReturn(review());

    mockMvc.perform(get("/api/practice-sessions/50/reviews/1002"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.id").value(1002))
        .andExpect(jsonPath("$.data.scores.correctness").value(3.0))
        .andExpect(jsonPath("$.data.evidence[0].type").value("FENCED_CODE_BLOCK"))
        .andExpect(jsonPath("$.data.reviewMarkdown").value("整体思路正确，注意边界。"));

    verify(practiceSessionService).detail(42L, 50L, 1002L);
  }

  @Test
  void reviewHistoryRequiresAuthentication() throws Exception {
    when(currentUserIdProvider.currentUser()).thenReturn(Optional.empty());

    mockMvc.perform(get("/api/practice-sessions/50/reviews"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.error.code").value("AUTH_UNAUTHENTICATED"));

    verifyNoInteractions(practiceSessionService);
  }

  @Test
  void getPracticeSessionReturnsActiveRun() throws Exception {
    when(currentUserIdProvider.currentUser()).thenReturn(Optional.of(currentUser()));
    when(practiceSessionService.get(42L, 50L)).thenReturn(resultWithActiveRun());

    mockMvc.perform(get("/api/practice-sessions/50"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.activeRun.runUuid").value("run-80"))
        .andExpect(jsonPath("$.data.activeRun.idempotencyKey").value("idem-80"));
  }

  @Test
  void activeRunEndpointReturnsCurrentActiveRun() throws Exception {
    when(currentUserIdProvider.currentUser()).thenReturn(Optional.of(currentUser()));
    when(practiceSessionService.get(42L, 50L)).thenReturn(resultWithActiveRun());

    mockMvc.perform(get("/api/practice-sessions/50/active-run"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.runUuid").value("run-80"));
  }

  @Test
  void messagesEndpointReturnsHistoryMessages() throws Exception {
    when(currentUserIdProvider.currentUser()).thenReturn(Optional.of(currentUser()));
    when(practiceSessionService.get(42L, 50L, 50)).thenReturn(result(PracticeProgressStatus.IN_PROGRESS));

    mockMvc.perform(get("/api/practice-sessions/50/messages?limit=50"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data[0].messageType").value("PROBLEM_STATEMENT"));

    verify(practiceSessionService).get(42L, 50L, 50);
  }

  @Test
  void updateProgressStatusReturnsRefreshedSession() throws Exception {
    when(currentUserIdProvider.currentUser()).thenReturn(Optional.of(currentUser()));
    when(practiceSessionService.updateProgressStatus(42L, 50L, PracticeProgressStatus.COMPLETED))
        .thenReturn(session(PracticeProgressStatus.COMPLETED));
    when(practiceSessionService.get(42L, 50L)).thenReturn(result(PracticeProgressStatus.COMPLETED));

    mockMvc.perform(patch("/api/practice-sessions/50/progress-status")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"status\":\"COMPLETED\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.session.progressStatus").value("COMPLETED"));

    verify(practiceSessionService).updateProgressStatus(42L, 50L, PracticeProgressStatus.COMPLETED);
    verify(practiceSessionService).get(42L, 50L);
  }

  @Test
  void completionBlockedByNoReviewReturnsStableCode() throws Exception {
    when(currentUserIdProvider.currentUser()).thenReturn(Optional.of(currentUser()));
    when(practiceSessionService.updateProgressStatus(42L, 50L, PracticeProgressStatus.COMPLETED))
        .thenThrow(new LearningPlanException("PRACTICE_COMPLETION_REVIEW_REQUIRED",
            "完成前需要先粘贴完整代码完成一次 AI Review。"));

    mockMvc.perform(patch("/api/practice-sessions/50/progress-status")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"status\":\"COMPLETED\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("PRACTICE_COMPLETION_REVIEW_REQUIRED"));
  }

  @Test
  void completionBlockedByFailedReviewReturnsStableCode() throws Exception {
    when(currentUserIdProvider.currentUser()).thenReturn(Optional.of(currentUser()));
    when(practiceSessionService.updateProgressStatus(42L, 50L, PracticeProgressStatus.COMPLETED))
        .thenThrow(new LearningPlanException("PRACTICE_COMPLETION_REVIEW_NOT_PASSED",
            "最近一次 Review 为 5/10，达到 6 分后可标记完成。"));

    mockMvc.perform(patch("/api/practice-sessions/50/progress-status")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"status\":\"COMPLETED\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("PRACTICE_COMPLETION_REVIEW_NOT_PASSED"));
  }

  @Test
  void streamPracticeMessageUsesPracticeGovernanceAndEffectiveIdempotencyKey() throws Exception {
    when(currentUserIdProvider.currentUser()).thenReturn(Optional.of(currentUser()));
    when(actorResolver.currentActor()).thenReturn(new AiActor(42L, Set.of(), true));
    when(admissionService.admit(any(AiRunContext.class))).thenAnswer(invocation -> admitted(invocation.getArgument(0)));
    when(streamService.stream(eq(42L), eq(50L), eq("提示一下思路"), eq("idem-50"), eq("zh-CN"), any()))
        .thenReturn(streamPublisher());

    MvcResult result = mockMvc.perform(post("/api/practice-sessions/50/messages/stream")
            .contentType(MediaType.APPLICATION_JSON)
            .accept(MediaType.TEXT_EVENT_STREAM)
            .header("Idempotency-Key", "idem-50")
            .content("{\"message\":\"提示一下思路\"}"))
        .andExpect(request().asyncStarted())
        .andReturn();

    mockMvc.perform(asyncDispatch(result))
        .andExpect(status().isOk())
        .andExpect(header().string(HttpHeaders.CONTENT_TYPE, containsString(MediaType.TEXT_EVENT_STREAM_VALUE)))
        .andExpect(content().string(containsString("event:agent_run_start")));

    ArgumentCaptor<AiRunContext> governanceCaptor = ArgumentCaptor.forClass(AiRunContext.class);
    verify(admissionService).admit(governanceCaptor.capture());
    org.assertj.core.api.Assertions.assertThat(governanceCaptor.getValue().purpose()).isEqualTo(AiPurpose.LEARNING_CHAT);
    org.assertj.core.api.Assertions.assertThat(governanceCaptor.getValue().source()).isEqualTo(AiRunSource.PRACTICE_CHAT);
    org.assertj.core.api.Assertions.assertThat(governanceCaptor.getValue().streaming()).isTrue();
    org.assertj.core.api.Assertions.assertThat(governanceCaptor.getValue().metadata())
        .containsEntry(PracticeChatPromptConstants.METADATA_PRACTICE_SESSION_ID, 50L);

    ArgumentCaptor<Map<String, Object>> metadataCaptor = ArgumentCaptor.captor();
    verify(streamService).stream(eq(42L), eq(50L), eq("提示一下思路"), eq("idem-50"), eq("zh-CN"),
        metadataCaptor.capture());
    org.assertj.core.api.Assertions.assertThat(metadataCaptor.getValue())
        .containsEntry(AiGovernanceMetadataKeys.SOURCE, "PRACTICE_CHAT")
        .containsEntry(PracticeChatPromptConstants.METADATA_PRACTICE_SESSION_ID, 50L);
  }

  @Test
  void streamBlankMessageReturns400BeforeGovernance() throws Exception {
    mockMvc.perform(post("/api/practice-sessions/50/messages/stream")
            .contentType(MediaType.APPLICATION_JSON)
            .accept(MediaType.TEXT_EVENT_STREAM)
            .content("{\"message\":\"   \"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("PRACTICE_MESSAGE_INVALID"));

    verifyNoInteractions(admissionService, streamService);
  }

  @Test
  void streamNullMessageReturns400BeforeGovernance() throws Exception {
    mockMvc.perform(post("/api/practice-sessions/50/messages/stream")
            .contentType(MediaType.APPLICATION_JSON)
            .accept(MediaType.TEXT_EVENT_STREAM)
            .content("{\"message\":null}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("PRACTICE_MESSAGE_INVALID"));

    verifyNoInteractions(admissionService, streamService);
  }

  @Test
  void streamMissingBodyReturns400BeforeGovernance() throws Exception {
    mockMvc.perform(post("/api/practice-sessions/50/messages/stream")
            .contentType(MediaType.APPLICATION_JSON)
            .accept(MediaType.TEXT_EVENT_STREAM))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("PRACTICE_MESSAGE_INVALID"));

    verifyNoInteractions(admissionService, streamService);
  }

  @Test
  void unauthenticatedRequestReturns401() throws Exception {
    when(currentUserIdProvider.currentUser()).thenReturn(Optional.empty());

    mockMvc.perform(get("/api/practice-sessions/50"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.error.code").value("AUTH_UNAUTHENTICATED"));
  }

  @Test
  void invalidProgressStatusReturns400() throws Exception {
    when(currentUserIdProvider.currentUser()).thenReturn(Optional.of(currentUser()));

    mockMvc.perform(patch("/api/practice-sessions/50/progress-status")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"status\":\"DONE\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("PRACTICE_PROGRESS_STATUS_INVALID"));
  }

  @Test
  void streamRunInProgressReturns409() throws Exception {
    when(currentUserIdProvider.currentUser()).thenReturn(Optional.of(currentUser()));
    when(actorResolver.currentActor()).thenReturn(new AiActor(42L, Set.of(), true));
    when(admissionService.admit(any(AiRunContext.class))).thenAnswer(invocation -> admitted(invocation.getArgument(0)));
    when(streamService.stream(eq(42L), eq(50L), eq("提示一下思路"), eq("idem-50"), eq("zh-CN"), any()))
        .thenThrow(new AgentConversationRunInProgressException(50L));

    mockMvc.perform(post("/api/practice-sessions/50/messages/stream")
            .contentType(MediaType.APPLICATION_JSON)
            .accept(MediaType.TEXT_EVENT_STREAM)
            .header("Idempotency-Key", "idem-50")
            .content("{\"message\":\"提示一下思路\"}"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.error.code").value("AGENT_RUN_IN_PROGRESS"));
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

  private PracticeSessionResult result(PracticeProgressStatus progressStatus) {
    return new PracticeSessionResult(
        session(progressStatus),
        new PracticeChatProblemDetail(
            "two-sum",
            1,
            "Two Sum",
            "EASY",
            List.of("Array", "Hash Table"),
            "Given an array of integers...",
            "https://leetcode.com/problems/two-sum/"),
        List.of(new PracticeSessionMessage(
            70L,
            "ASSISTANT",
            PracticeChatPromptConstants.MESSAGE_TYPE_PROBLEM_STATEMENT,
            "Given an array of integers...",
            Instant.parse("2026-06-24T00:00:02Z"))),
        Optional.empty());
  }

  private PracticeSessionResult resultWithActiveRun() {
    return new PracticeSessionResult(
        session(PracticeProgressStatus.IN_PROGRESS),
        new PracticeChatProblemDetail(
            "two-sum",
            1,
            "Two Sum",
            "EASY",
            List.of("Array", "Hash Table"),
            "Given an array of integers...",
            "https://leetcode.com/problems/two-sum/"),
        List.of(new PracticeSessionMessage(
            70L,
            "ASSISTANT",
            PracticeChatPromptConstants.MESSAGE_TYPE_PROBLEM_STATEMENT,
            "Given an array of integers...",
            Instant.parse("2026-06-24T00:00:02Z"))),
        Optional.of(new AgentActiveRun(
            80L,
            80L,
            "run-80",
            "idem-80",
            Instant.parse("2026-06-24T00:00:04Z"))));
  }

  private PracticeSessionResult resultWithPassedReview() {
    return new PracticeSessionResult(
        session(PracticeProgressStatus.IN_PROGRESS),
        problemDetail(),
        List.of(new PracticeSessionMessage(
            70L,
            "ASSISTANT",
            PracticeChatPromptConstants.MESSAGE_TYPE_PROBLEM_STATEMENT,
            "Given an array of integers...",
            Instant.parse("2026-06-24T00:00:02Z"))),
        Optional.empty(),
        latestReview(),
        passedGate());
  }

  private PracticeCodeReviewHistory reviewHistory() {
    return new PracticeCodeReviewHistory(
        latestReview(),
        List.of(latestReview(), new PracticeCodeReviewSummary(
            1001L,
            1,
            "java",
            new BigDecimal("5.0"),
            false,
            Instant.parse("2026-06-24T00:10:00Z"))),
        passedGate());
  }

  private PracticeCodeReview review() {
    return new PracticeCodeReview(
        1002L,
        42L,
        900L,
        1,
        "two-sum",
        50L,
        2,
        71L,
        72L,
        80L,
        "class Solution { int[] twoSum(int[] nums, int target) { return nums; } }",
        "class Solution { int[] twoSum(int[] nums, int target) { return nums; } }",
        "java",
        List.of(new PracticeCodeReviewEvidence("FENCED_CODE_BLOCK", "markdown fenced code block")),
        "用户提交了 Java 解法。",
        new PracticeCodeReviewScore(
            new BigDecimal("3.0"),
            new BigDecimal("1.5"),
            new BigDecimal("1.0"),
            new BigDecimal("0.8"),
            new BigDecimal("0.7"),
            new BigDecimal("7.0")),
        true,
        List.of("边界条件说明不足"),
        List.of("补充空数组和重复元素说明"),
        "整体思路正确，注意边界。",
        Instant.parse("2026-06-24T00:20:00Z"));
  }

  private PracticeCodeReviewSummary latestReview() {
    return new PracticeCodeReviewSummary(
        1002L,
        2,
        "java",
        new BigDecimal("7.0"),
        true,
        Instant.parse("2026-06-24T00:20:00Z"));
  }

  private PracticeCompletionGate passedGate() {
    return new PracticeCompletionGate(
        true,
        PracticeCompletionGate.ReasonCode.PASSED,
        "标记为已完成",
        Optional.of(new BigDecimal("7.0")),
        new BigDecimal("6.0"));
  }

  private PracticeChatProblemDetail problemDetail() {
    return new PracticeChatProblemDetail(
        "two-sum",
        1,
        "Two Sum",
        "EASY",
        List.of("Array", "Hash Table"),
        "Given an array of integers...",
        "https://leetcode.com/problems/two-sum/");
  }

  private PracticeSession session(PracticeProgressStatus progressStatus) {
    return new PracticeSession(
        50L,
        42L,
        900L,
        1,
        "two-sum",
        PracticeSessionStatus.ACTIVE,
        80L,
        70L,
        progressStatus,
        Instant.parse("2026-06-24T00:00:03Z"),
        Instant.parse("2026-06-24T00:00:00Z"),
        Instant.parse("2026-06-24T00:00:01Z"),
        "zh-CN");
  }

  private AiRunAdmission admitted(AiRunContext context) {
    AiPurposePolicy policy = new AiPurposePolicy(
        true, 50, 1, 16384, 2048, 8, true, true, false, false,
        null, null, "practice-chat-p0");
    return new AiRunAdmission(
        1L,
        context.runId(),
        context.actor().userId(),
        context.purpose(),
        context.source(),
        AiRunStatus.ADMITTED,
        "ALL",
        null,
        policy,
        Map.of(
            AiGovernanceMetadataKeys.RUN_ID, context.runId(),
            AiGovernanceMetadataKeys.PURPOSE, context.purpose().name(),
            AiGovernanceMetadataKeys.SOURCE, context.source().name(),
            PracticeChatPromptConstants.METADATA_PRACTICE_SESSION_ID, 50L),
        Instant.now());
  }

  private Flow.Publisher<AgentStreamEvent> streamPublisher() {
    return subscriber -> {
      SubmissionPublisher<AgentStreamEvent> publisher = new SubmissionPublisher<>();
      publisher.subscribe(subscriber);
      publisher.submit(new AgentStreamEvent.AgentRunStart("run-50", "practice", 8));
      publisher.close();
    };
  }

  @TestConfiguration(proxyBeanMethods = false)
  static class TestConfig {

    @Bean
    PracticeSessionService practiceSessionService() {
      return mock(PracticeSessionService.class);
    }

    @Bean
    PracticeMessageStreamService practiceMessageStreamService() {
      return mock(PracticeMessageStreamService.class);
    }

    @Bean
    CurrentUserIdProvider currentUserIdProvider() {
      return mock(CurrentUserIdProvider.class);
    }

    @Bean
    AiActorResolver aiActorResolver() {
      return mock(AiActorResolver.class);
    }

    @Bean
    AiRunAdmissionService aiRunAdmissionService() {
      return mock(AiRunAdmissionService.class);
    }

    @Bean
    PracticeSessionController practiceSessionController(
        ObjectProvider<PracticeSessionService> practiceSessionService,
        ObjectProvider<PracticeMessageStreamService> streamService,
        CurrentUserIdProvider currentUserIdProvider,
        ObjectProvider<AiActorResolver> actorResolver,
        ObjectProvider<AiRunAdmissionService> admissionService,
        ObjectProvider<LlmStreamSseMapper> sseMapper
    ) {
      return new PracticeSessionController(
          practiceSessionService,
          streamService,
          currentUserIdProvider,
          actorResolver,
          admissionService,
          sseMapper);
    }
  }
}
