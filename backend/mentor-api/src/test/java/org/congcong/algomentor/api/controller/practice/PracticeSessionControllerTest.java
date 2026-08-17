package org.congcong.algomentor.api.controller.practice;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.congcong.algomentor.agent.core.AgentErrorCode;
import org.congcong.algomentor.agent.core.AgentException;
import org.congcong.algomentor.agent.core.runtime.model.AgentActiveRun;
import org.congcong.algomentor.agent.core.runtime.repository.AgentTaskMessageRepository;
import org.congcong.algomentor.ai.governance.admission.AiRunAdmission;
import org.congcong.algomentor.ai.governance.admission.AiRunAdmissionService;
import org.congcong.algomentor.ai.governance.model.AiActor;
import org.congcong.algomentor.ai.governance.model.AiGovernanceMetadataKeys;
import org.congcong.algomentor.ai.governance.model.AiPurpose;
import org.congcong.algomentor.ai.governance.model.AiRunContext;
import org.congcong.algomentor.ai.governance.model.AiRunSource;
import org.congcong.algomentor.ai.governance.model.AiRunStatus;
import org.congcong.algomentor.ai.governance.policy.AiPurposePolicy;
import org.congcong.algomentor.api.config.ApiContractConstants;
import org.congcong.algomentor.api.config.ApiSseProperties;
import org.congcong.algomentor.api.practice.realtime.PracticeRealtimeEvent;
import org.congcong.algomentor.api.practice.realtime.PracticeRealtimeEventStore;
import org.congcong.algomentor.api.controller.LocalizedApiExceptionHandler;
import org.congcong.algomentor.api.service.AiActorResolver;
import org.congcong.algomentor.identity.model.AuthUserStatus;
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
import org.congcong.algomentor.mentor.application.practice.PracticeChatRunSubscription;
import org.congcong.algomentor.mentor.application.practice.PracticeProgressStatus;
import org.congcong.algomentor.mentor.application.practice.PracticeSession;
import org.congcong.algomentor.mentor.application.practice.PracticeSessionMessage;
import org.congcong.algomentor.mentor.application.practice.PracticeSessionResult;
import org.congcong.algomentor.mentor.application.practice.PracticeSessionService;
import org.congcong.algomentor.mentor.application.practice.PracticeSessionStatus;
import org.congcong.algomentor.mentor.application.practice.coachsummary.CoachSummaryMessageAction;
import org.congcong.algomentor.mentor.application.practice.coachsummary.CoachSummaryProposalOperation;
import org.congcong.algomentor.mentor.application.practice.coachsummary.CoachSummaryProposalStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@WebMvcTest(controllers = PracticeSessionController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import({
    PracticeSessionControllerTest.TestConfig.class,
    LocalizedApiExceptionHandler.class,
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
  private AgentTaskMessageRepository agentTaskMessageRepository;

  @Autowired
  private PracticeRealtimeEventStore realtimeEventStore;

  @Autowired
  private ApiSseProperties sseProperties;

  @BeforeEach
  void resetMocks() {
    reset(practiceSessionService, streamService, currentUserIdProvider, sseProperties, agentTaskMessageRepository,
        realtimeEventStore);
    when(sseProperties.practiceMessageTimeoutMillis()).thenReturn(360_000L);
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
        .andExpect(jsonPath("$.data.problem.title").value("Two Sum"))
        .andExpect(jsonPath("$.data.problem.titleCn").value("两数之和"))
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
        .andExpect(jsonPath("$.data.latestReview.contentLocale").value("zh-CN"))
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
        .andExpect(jsonPath("$.data.contentLocale").value("zh-CN"))
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
  void applyCoachSummaryProposalUsesOnlyTrustedUserSessionAndProposalId() throws Exception {
    when(currentUserIdProvider.currentUser()).thenReturn(Optional.of(currentUser()));
    when(practiceSessionService.applyCoachSummaryProposal(42L, 50L, "proposal-1"))
        .thenReturn(new CoachSummaryMessageAction(
            0L,
            "proposal-1",
            CoachSummaryProposalStatus.APPLIED,
            CoachSummaryProposalOperation.REPLACE,
            "# 完整总结",
            3L,
            Instant.parse("2026-07-25T00:00:00Z"),
            Instant.parse("2026-07-25T00:01:00Z")));

    mockMvc.perform(post("/api/practice-sessions/50/coach-summary-proposals/proposal-1/apply"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.proposalId").value("proposal-1"))
        .andExpect(jsonPath("$.data.status").value("APPLIED"))
        .andExpect(jsonPath("$.data.operation").value("REPLACE"))
        .andExpect(jsonPath("$.data.appliedCoachSummaryRevision").value(3));

    verify(practiceSessionService).applyCoachSummaryProposal(42L, 50L, "proposal-1");
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
            "完成前需要先粘贴完整代码生成一次代码提交记录，并且通过后才能标记完成。"));

    mockMvc.perform(patch("/api/practice-sessions/50/progress-status")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"status\":\"COMPLETED\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("PRACTICE_COMPLETION_REVIEW_REQUIRED"))
        .andExpect(jsonPath("$.error.messageKey").value("api.error.PRACTICE_COMPLETION_REVIEW_REQUIRED"))
        .andExpect(jsonPath("$.error.message")
            .value("完成前需要先粘贴完整代码生成一次代码提交记录，并且通过后才能标记完成。"));
  }

  @Test
  void completionBlockedByFailedReviewReturnsStableCode() throws Exception {
    when(currentUserIdProvider.currentUser()).thenReturn(Optional.of(currentUser()));
    when(practiceSessionService.updateProgressStatus(42L, 50L, PracticeProgressStatus.COMPLETED))
        .thenThrow(new LearningPlanException("PRACTICE_COMPLETION_REVIEW_NOT_PASSED",
            "最近一次代码提交记录为 5/10，达到 6 分后可标记完成。"));

    mockMvc.perform(patch("/api/practice-sessions/50/progress-status")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"status\":\"COMPLETED\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("PRACTICE_COMPLETION_REVIEW_NOT_PASSED"));
  }

  @Test
  void startPracticeMessageReturnsAcceptedSubscription() throws Exception {
    when(currentUserIdProvider.currentUser()).thenReturn(Optional.of(currentUser()));
    when(streamService.start(eq(42L), eq(50L), eq("提示一下思路"), eq("idem-50"), eq("zh-CN"), anyInt(), any()))
        .thenReturn(new PracticeChatRunSubscription(80L, "run-80", PracticeChatRunSubscription.ACCEPTED));

    mockMvc.perform(post("/api/practice-sessions/50/messages")
            .contentType(MediaType.APPLICATION_JSON)
            .header(ApiContractConstants.IDEMPOTENCY_KEY_HEADER, "idem-50")
            .content("{\"message\":\"提示一下思路\"}"))
        .andExpect(status().isAccepted())
        .andExpect(jsonPath("$.data.type").value("accepted"))
        .andExpect(jsonPath("$.data.taskId").value(80))
        .andExpect(jsonPath("$.data.runUuid").value("run-80"))
        .andExpect(jsonPath("$.data.eventsUrl").value("/api/practice-sessions/50/runs/run-80/events"));

    verify(streamService).start(eq(42L), eq(50L), eq("提示一下思路"), eq("idem-50"), eq("zh-CN"),
        eq("提示一下思路".getBytes(java.nio.charset.StandardCharsets.UTF_8).length), any());
  }

  @Test
  void startPracticeMessageUsesAcceptLanguageAsDynamicResponseContext() throws Exception {
    when(currentUserIdProvider.currentUser()).thenReturn(Optional.of(currentUser()));
    when(streamService.start(eq(42L), eq(50L), eq("give me a hint"), eq("idem-50"), eq("en-US"), anyInt(), any()))
        .thenReturn(new PracticeChatRunSubscription(80L, "run-80", PracticeChatRunSubscription.ACCEPTED));

    mockMvc.perform(post("/api/practice-sessions/50/messages")
            .contentType(MediaType.APPLICATION_JSON)
            .header(ApiContractConstants.IDEMPOTENCY_KEY_HEADER, "idem-50")
            .header(ApiContractConstants.ACCEPT_LANGUAGE_HEADER, "en-US")
            .content("{\"message\":\"give me a hint\"}"))
        .andExpect(status().isAccepted());

    verify(streamService).start(eq(42L), eq(50L), eq("give me a hint"), eq("idem-50"), eq("en-US"), anyInt(), any());
  }

  @Test
  void startPracticeMessageReturnsCapacityErrorWhenWorkerSubmissionIsRejected() throws Exception {
    when(currentUserIdProvider.currentUser()).thenReturn(Optional.of(currentUser()));
    when(streamService.start(eq(42L), eq(50L), eq("提示一下思路"), eq("idem-50"), eq("zh-CN"), anyInt(), any()))
        .thenThrow(new AgentException(
            AgentErrorCode.AGENT_EXECUTOR_OVERLOADED,
            "Agent service is temporarily busy",
            true,
            Map.of(),
            null));

    mockMvc.perform(post("/api/practice-sessions/50/messages")
            .contentType(MediaType.APPLICATION_JSON)
            .header(ApiContractConstants.IDEMPOTENCY_KEY_HEADER, "idem-50")
            .content("{\"message\":\"提示一下思路\"}"))
        .andExpect(status().isServiceUnavailable())
        .andExpect(jsonPath("$.error.code").value("AGENT_EXECUTOR_OVERLOADED"));
  }

  @Test
  void eventsRejectsMalformedAfterCursorBeforeOpeningSseConnection() throws Exception {
    when(currentUserIdProvider.currentUser()).thenReturn(Optional.of(currentUser()));

    mockMvc.perform(get("/api/practice-sessions/50/runs/run-80/events?after=not-a-redis-id"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("PRACTICE_REALTIME_CURSOR_INVALID"));

    verifyNoInteractions(practiceSessionService);
  }

  @Test
  void eventsRejectsNonSequenceCursorForAPersistedV2Run() throws Exception {
    when(currentUserIdProvider.currentUser()).thenReturn(Optional.of(currentUser()));
    when(practiceSessionService.get(42L, 50L)).thenReturn(result(PracticeProgressStatus.IN_PROGRESS));
    when(agentTaskMessageRepository.hasRun(80L, "run-80")).thenReturn(true);
    when(agentTaskMessageRepository.realtimeProtocolVersion(80L, "run-80")).thenReturn(2);

    mockMvc.perform(get("/api/practice-sessions/50/runs/run-80/events?after=1-1"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("PRACTICE_REALTIME_CURSOR_INVALID"));

    verify(agentTaskMessageRepository).hasRun(80L, "run-80");
    verify(agentTaskMessageRepository).realtimeProtocolVersion(80L, "run-80");
  }

  @Test
  void eventsRequireAuthenticationBeforeResolvingTheSession() throws Exception {
    when(currentUserIdProvider.currentUser()).thenReturn(Optional.empty());

    mockMvc.perform(get("/api/practice-sessions/50/runs/run-80/events"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.error.code").value("AUTH_UNAUTHENTICATED"));

    verifyNoInteractions(practiceSessionService);
  }

  @Test
  void eventsRejectsRunThatDoesNotBelongToTheSessionTask() throws Exception {
    when(currentUserIdProvider.currentUser()).thenReturn(Optional.of(currentUser()));
    when(practiceSessionService.get(42L, 50L)).thenReturn(result(PracticeProgressStatus.IN_PROGRESS));
    when(agentTaskMessageRepository.hasRun(80L, "run-80")).thenReturn(false);

    mockMvc.perform(get("/api/practice-sessions/50/runs/run-80/events"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.error.code").value("PRACTICE_RUN_NOT_FOUND"));

    verify(practiceSessionService).get(42L, 50L);
    verify(agentTaskMessageRepository).hasRun(80L, "run-80");
  }

  @Test
  void eventsReplaysTerminalStreamForAnEndedRunThatBelongsToTheSessionTask() throws Exception {
    when(currentUserIdProvider.currentUser()).thenReturn(Optional.of(currentUser()));
    when(practiceSessionService.get(42L, 50L)).thenReturn(result(PracticeProgressStatus.IN_PROGRESS));
    when(agentTaskMessageRepository.hasRun(80L, "run-80")).thenReturn(true);
    when(agentTaskMessageRepository.realtimeProtocolVersion(80L, "run-80")).thenReturn(2);
    when(realtimeEventStore.readAfter("run-80", "0-0", true)).thenReturn(List.of(new PracticeRealtimeEvent(
        "1-0",
        "agent_run_end",
        JsonNodeFactory.instance.objectNode()
            .put("runId", "run-80")
            .put("steps", 1)
            .put("finishReason", "STOP"))));

    MvcResult result = mockMvc.perform(get("/api/practice-sessions/50/runs/run-80/events")
            .accept(MediaType.TEXT_EVENT_STREAM))
        .andExpect(request().asyncStarted())
        .andReturn();

    mockMvc.perform(asyncDispatch(result))
        .andExpect(status().isOk())
        .andExpect(content().string(org.hamcrest.Matchers.containsString("event:agent_run_end")));

    verify(realtimeEventStore).readAfter("run-80", "0-0", true);
    verify(agentTaskMessageRepository, never()).isActiveRun(80L, "run-80");
  }

  @Test
  void streamBlankMessageReturns400BeforeGovernance() throws Exception {
    mockMvc.perform(post("/api/practice-sessions/50/messages")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"message\":\"   \"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("PRACTICE_MESSAGE_INVALID"))
        .andExpect(jsonPath("$.error.messageKey").value("api.error.PRACTICE_MESSAGE_INVALID"))
        .andExpect(jsonPath("$.error.message").value("练习消息不能为空。"));

    verifyNoInteractions(streamService);
  }

  @Test
  void streamNullMessageReturns400BeforeGovernance() throws Exception {
    mockMvc.perform(post("/api/practice-sessions/50/messages")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"message\":null}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("PRACTICE_MESSAGE_INVALID"));

    verifyNoInteractions(streamService);
  }

  @Test
  void streamMissingBodyReturns400BeforeGovernance() throws Exception {
    mockMvc.perform(post("/api/practice-sessions/50/messages")
            .contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("REQUEST_BODY_INVALID"))
        .andExpect(jsonPath("$.error.messageKey").value("api.error.REQUEST_BODY_INVALID"));

    verifyNoInteractions(streamService);
  }

  @Test
  void streamMalformedJsonReturnsRequestBodyInvalidBeforeGovernance() throws Exception {
    mockMvc.perform(post("/api/practice-sessions/50/messages")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"message\":"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("REQUEST_BODY_INVALID"))
        .andExpect(jsonPath("$.error.messageKey").value("api.error.REQUEST_BODY_INVALID"))
        .andExpect(jsonPath("$.error.message").value("请求体不是合法 JSON 或与接口结构不匹配。"));

    verifyNoInteractions(streamService);
  }

  @Test
  void unauthenticatedRequestReturns401() throws Exception {
    when(currentUserIdProvider.currentUser()).thenReturn(Optional.empty());

    mockMvc.perform(get("/api/practice-sessions/50").header("Accept-Language", "en-US"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.error.code").value("AUTH_UNAUTHENTICATED"))
        .andExpect(jsonPath("$.error.message").value("You are not signed in or your session has expired."));
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
  void startRunInProgressReturns409() throws Exception {
    when(currentUserIdProvider.currentUser()).thenReturn(Optional.of(currentUser()));
    when(streamService.start(eq(42L), eq(50L), eq("提示一下思路"), eq("idem-50"), eq("zh-CN"), anyInt(), any()))
        .thenThrow(new AgentConversationRunInProgressException(50L));

    mockMvc.perform(post("/api/practice-sessions/50/messages")
            .contentType(MediaType.APPLICATION_JSON)
            .header("Idempotency-Key", "idem-50")
            .content("{\"message\":\"提示一下思路\"}"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.error.code").value("AGENT_RUN_IN_PROGRESS"))
        .andExpect(jsonPath("$.error.messageKey").value("api.error.AGENT_RUN_IN_PROGRESS"))
        .andExpect(jsonPath("$.error.metadata.taskId").value(50));
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
            "两数之和",
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
            "两数之和",
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
        "两数之和",
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

  @TestConfiguration(proxyBeanMethods = false)
  static class TestConfig {

    @Bean
    PracticeSessionService practiceSessionService() {
      return mock(PracticeSessionService.class);
    }

    @Bean
    @Primary
    PracticeMessageStreamService practiceMessageStreamService() {
      return mock(PracticeMessageStreamService.class);
    }

    @Bean
    @Primary
    org.congcong.algomentor.api.practice.realtime.PracticeRealtimeEventStore practiceRealtimeEventStore() {
      return mock(org.congcong.algomentor.api.practice.realtime.PracticeRealtimeEventStore.class);
    }

    @Bean
    CurrentUserIdProvider currentUserIdProvider() {
      return mock(CurrentUserIdProvider.class);
    }

    @Bean
    @Primary
    AgentTaskMessageRepository agentTaskMessageRepository() {
      return mock(AgentTaskMessageRepository.class);
    }

    @Bean
    ApiSseProperties apiSseProperties() {
      return mock(ApiSseProperties.class);
    }

    @Bean
    PracticeSessionController practiceSessionController(
        ObjectProvider<PracticeSessionService> practiceSessionService,
        ObjectProvider<PracticeMessageStreamService> streamService,
        CurrentUserIdProvider currentUserIdProvider,
        ObjectProvider<org.congcong.algomentor.api.practice.realtime.PracticeRealtimeEventStore> realtimeEventStore,
        ObjectProvider<org.congcong.algomentor.agent.core.runtime.repository.AgentTaskMessageRepository> agentTaskMessageRepository,
        ApiSseProperties sseProperties,
        org.congcong.algomentor.ops.observability.SseOpsRecorder sseOpsRecorder
    ) {
      return new PracticeSessionController(
          practiceSessionService,
          streamService,
          currentUserIdProvider,
          realtimeEventStore,
          agentTaskMessageRepository,
          sseProperties,
          sseOpsRecorder);
    }

    @Bean
    org.congcong.algomentor.ops.observability.SseOpsRecorder sseOpsRecorder() {
      return mock(org.congcong.algomentor.ops.observability.SseOpsRecorder.class);
    }
  }
}
