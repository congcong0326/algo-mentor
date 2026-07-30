package org.congcong.algomentor.api.controller;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Flow;
import java.util.concurrent.SubmissionPublisher;
import org.congcong.algomentor.agent.core.AgentLoopRunner;
import org.congcong.algomentor.agent.core.AgentRequest;
import org.congcong.algomentor.agent.core.AgentStreamEvent;
import org.congcong.algomentor.agent.core.AgentToolRegistry;
import org.congcong.algomentor.agent.core.prompt.AgentPromptMetadataKeys;
import org.congcong.algomentor.agent.core.runlock.AgentRunLockConstants;
import org.congcong.algomentor.agent.core.runlock.AgentRunLockToken;
import org.congcong.algomentor.agent.core.runlock.AgentRunLockOwnerProvider;
import org.congcong.algomentor.ai.governance.admission.AiRunAdmission;
import org.congcong.algomentor.ai.governance.admission.AiRunAdmissionService;
import org.congcong.algomentor.ai.governance.model.AiBusinessScenario;
import org.congcong.algomentor.ai.governance.model.AiGovernanceMetadataKeys;
import org.congcong.algomentor.ai.governance.model.AiPurpose;
import org.congcong.algomentor.ai.governance.model.AiRunContext;
import org.congcong.algomentor.ai.governance.model.AiRunSource;
import org.congcong.algomentor.ai.governance.model.AiRunStatus;
import org.congcong.algomentor.ai.governance.policy.AiPurposePolicy;
import org.congcong.algomentor.api.service.AiActorResolver;
import org.congcong.algomentor.api.service.LlmStreamSseMapper;
import org.congcong.algomentor.api.config.ApiSseProperties;
import org.congcong.algomentor.identity.model.AuthRole;
import org.congcong.algomentor.agent.core.runlock.AgentRunLockManager;
import org.congcong.algomentor.agent.core.runlock.AgentRunLockRequest;
import org.congcong.algomentor.agent.core.runlock.InMemoryAgentRunLockManager;
import org.congcong.algomentor.agent.core.runlock.LocalAgentRunLockOwnerProvider;
import org.congcong.algomentor.agent.core.runtime.api.AgentInvocation;
import org.congcong.algomentor.agent.core.runtime.api.AgentRuntime;
import org.congcong.algomentor.agent.core.runtime.context.ContextAssembler;
import org.congcong.algomentor.agent.core.runtime.model.AgentRuntimeMetadataKeys;
import org.congcong.algomentor.agent.core.runtime.model.AgentMessage;
import org.congcong.algomentor.agent.core.runtime.model.AgentRunPreparationRequest;
import org.congcong.algomentor.agent.core.runtime.model.PreparedAgentRun;
import org.congcong.algomentor.agent.core.runtime.repository.AgentConversationRepository;
import org.congcong.algomentor.llm.core.gateway.LlmGateway;
import org.congcong.algomentor.llm.core.request.LlmCompletionRequest;
import org.congcong.algomentor.llm.core.response.LlmCompletionResult;
import org.congcong.algomentor.agent.core.AgentRunResult;
import org.congcong.algomentor.llm.core.stream.LlmStreamEvent;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlan;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanDifficultyPreference;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanDraftPlan;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanIntent;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanLevel;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanPage;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanPhaseDraft;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanProblemDraft;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanRepository;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanStatus;
import org.congcong.algomentor.mentor.application.practice.PracticeChatProblemCatalog;
import org.congcong.algomentor.mentor.application.practice.PracticeChatProblemDetail;
import org.congcong.algomentor.mentor.application.practice.PracticeChatPromptConstants;
import org.congcong.algomentor.mentor.application.practice.PracticeMessageStreamService;
import org.congcong.algomentor.mentor.application.conversation.AgentConversationService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;

@WebMvcTest(controllers = AgentConversationController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(AgentConversationControllerTest.TestConfig.class)
class AgentConversationControllerTest {

  @Autowired
  private MockMvc mockMvc;

  @Autowired
  private StubAgentRuntime agentRuntime;

  @Autowired
  private PracticeMessageStreamService practiceMessageStreamService;

  @BeforeEach
  void configureAuthentication() {
    org.mockito.Mockito.reset(practiceMessageStreamService);
    SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
        "admin@example.com",
        "n/a",
        List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_ADMIN"))));
  }

  @AfterEach
  void clearTestState() {
    SecurityContextHolder.clearContext();
    agentRuntime.lastInvocation = null;
  }

  @Test
  void streamConversationRouteIsRegisteredWhenRepositoryBeanExists() throws Exception {
    MvcResult result = mockMvc.perform(post("/api/agent/conversations/stream")
            .contentType(MediaType.APPLICATION_JSON)
            .accept(MediaType.TEXT_EVENT_STREAM)
            .header("Idempotency-Key", "idem-1")
            .content("{\"message\":\"Explain two pointers.\"}"))
        .andExpect(request().asyncStarted())
        .andReturn();

    mockMvc.perform(asyncDispatch(result))
        .andExpect(status().isOk())
        .andExpect(header().string(HttpHeaders.CONTENT_TYPE, containsString(MediaType.TEXT_EVENT_STREAM_VALUE)))
        .andExpect(content().string(containsString("event:agent_run_start")))
        .andExpect(content().string(containsString("\"taskId\":1")))
        .andExpect(content().string(containsString("\"turnId\":2")))
        .andExpect(content().string(containsString("\"runDbId\":3")))
        .andExpect(content().string(containsString("event:content_delta")))
        .andExpect(content().string(containsString("\"content\":\"ok\"")));

    org.assertj.core.api.Assertions.assertThat(agentRuntime.lastInvocation.agentKey().value())
        .isEqualTo(AiBusinessScenario.MENTOR_CONVERSATION.code());
    org.assertj.core.api.Assertions.assertThat(agentRuntime.lastInvocation.context().userId()).isEqualTo(7L);
    org.assertj.core.api.Assertions.assertThat(agentRuntime.lastInvocation.context().idempotencyKey()).isEqualTo("idem-1");
  }

  @Test
  void ignoresClientUserIdAndUsesAuthenticatedActorForGovernanceAndPreparation() throws Exception {
    MvcResult result = mockMvc.perform(post("/api/agent/conversations/stream")
            .contentType(MediaType.APPLICATION_JSON)
            .accept(MediaType.TEXT_EVENT_STREAM)
            .header("Idempotency-Key", "idem-user")
            .content("{\"taskId\":1,\"userId\":999,\"message\":\"Explain two pointers.\"}"))
        .andExpect(request().asyncStarted())
        .andReturn();

    mockMvc.perform(asyncDispatch(result)).andExpect(status().isOk());

    org.assertj.core.api.Assertions.assertThat(agentRuntime.lastInvocation.context().userId()).isEqualTo(7L);
    org.assertj.core.api.Assertions.assertThat(agentRuntime.lastInvocation.input())
        .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.type(
            org.congcong.algomentor.mentor.application.conversation.MentorConversationAgentInput.class))
        .extracting(org.congcong.algomentor.mentor.application.conversation.MentorConversationAgentInput::userId)
        .isEqualTo(7L);
  }

  @Test
  void streamPracticeConversationUsesPracticePromptMetadata() throws Exception {
    String message = "直接给答案和 Java 代码";
    when(practiceMessageStreamService.stream(
        eq(7L), eq(50L), eq(message), eq("idem-practice"), eq("zh-CN"), anyInt()))
        .thenReturn(practiceStreamPublisher());

    MvcResult result = mockMvc.perform(post("/api/agent/conversations/stream")
            .contentType(MediaType.APPLICATION_JSON)
            .accept(MediaType.TEXT_EVENT_STREAM)
            .header("Idempotency-Key", "idem-practice")
            .content("""
                {
                  "message": "直接给答案和 Java 代码",
                  "practice": {
                    "sessionId": 50,
                    "locale": "zh-CN"
                  }
                }
                """))
        .andExpect(request().asyncStarted())
        .andReturn();

    mockMvc.perform(asyncDispatch(result))
        .andExpect(status().isOk())
        .andExpect(content().string(containsString("event:agent_run_start")));

    org.mockito.Mockito.verify(practiceMessageStreamService).stream(
        7L, 50L, message, "idem-practice", "zh-CN", message.getBytes(java.nio.charset.StandardCharsets.UTF_8).length);
    org.assertj.core.api.Assertions.assertThat(agentRuntime.lastInvocation).isNull();
  }

  @Test
  void streamConversationBlankMessageReturnsValidationFailure() throws Exception {
    mockMvc.perform(post("/api/agent/conversations/stream")
            .contentType(MediaType.APPLICATION_JSON)
            .accept(MediaType.TEXT_EVENT_STREAM)
            .content("{\"message\":\"   \"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"))
        .andExpect(jsonPath("$.error.messageKey").value("api.error.VALIDATION_FAILED"))
        .andExpect(jsonPath("$.error.message").value("请求参数校验失败。"));

    org.assertj.core.api.Assertions.assertThat(agentRuntime.lastInvocation).isNull();
  }

  @TestConfiguration(proxyBeanMethods = false)
  static class TestConfig {

    @Bean
    ApiSseProperties apiSseProperties() {
      return new ApiSseProperties();
    }

    @Bean
    LlmStreamSseMapper llmStreamSseMapper() {
      return new LlmStreamSseMapper();
    }

    @Bean
    AgentConversationController agentConversationController(
        StubAgentRuntime agentRuntime,
        LlmStreamSseMapper sseMapper,
        AiActorResolver actorResolver,
        ObjectProvider<PracticeMessageStreamService> practiceMessageStreamService,
        ApiSseProperties sseProperties
    ) {
      return new AgentConversationController(
          agentRuntime,
          sseMapper,
          actorResolver,
          practiceMessageStreamService,
          sseProperties);
    }

    @Bean
    @Primary
    PracticeMessageStreamService practiceMessageStreamService() {
      return org.mockito.Mockito.mock(PracticeMessageStreamService.class);
    }

    @Bean
    @Primary
    StubAgentRuntime stubAgentRuntime() {
      return new StubAgentRuntime();
    }

    @Bean
    @Primary
    AiActorResolver aiActorResolver() {
      return new AiActorResolver(() -> Optional.of(new org.congcong.algomentor.auth.security.AuthenticatedUserPrincipal(
          7L, "user@example.com", "User", null, List.of(AuthRole.USER), null)));
    }

    @Bean
    PlatformTransactionManager transactionManager() {
      return new PlatformTransactionManager() {
        @Override
        public TransactionStatus getTransaction(TransactionDefinition definition) {
          return new SimpleTransactionStatus();
        }

        @Override
        public void commit(TransactionStatus status) {
        }

        @Override
        public void rollback(TransactionStatus status) {
        }
      };
    }

    @Bean
    @Primary
    LearningPlanRepository learningPlanRepository() {
      return new StubLearningPlanRepository();
    }

    @Bean
    @Primary
    PracticeChatProblemCatalog practiceChatProblemCatalog() {
      return (slug, locale) -> Optional.of(new PracticeChatProblemDetail(
          slug,
          1,
          "Two Sum",
          "两数之和",
          "EASY",
          List.of("Array", "Hash Table"),
          "# Two Sum\nFind two numbers.",
          "https://leetcode.com/problems/two-sum/"));
    }
  }

  static class StubAgentConversationRepository implements AgentConversationRepository {
    private AgentRunPreparationRequest lastRequest;

    @Override
    public PreparedAgentRun createOrReuseRun(AgentRunPreparationRequest request) {
      lastRequest = request;
      return new PreparedAgentRun(
          1L,
          2L,
          3L,
          "run-1",
          UUID.randomUUID().toString(),
          request.systemPrompt(),
          null,
          Map.of());
    }

    @Override
    public Optional<PreparedAgentRun> findRunByIdempotencyKey(String idempotencyKey) {
      return Optional.empty();
    }

    @Override
    public List<AgentMessage> recentMessages(long taskId, int messageLimit) {
      return List.of();
    }
  }

  private Flow.Publisher<AgentStreamEvent> practiceStreamPublisher() {
    return subscriber -> {
      SubmissionPublisher<AgentStreamEvent> publisher = new SubmissionPublisher<>();
      publisher.subscribe(subscriber);
      publisher.submit(new AgentStreamEvent.AgentRunStart("practice-run", "task-100", 1, Map.of()));
      publisher.submit(AgentStreamEvent.fromLlm(new LlmStreamEvent.ContentDelta("ok")));
      publisher.close();
    };
  }

  static class StubAgentRuntime implements AgentRuntime {
    private AgentInvocation<?> lastInvocation;

    @Override
    public AgentRunResult execute(AgentInvocation<?> invocation) {
      throw new UnsupportedOperationException("execute not used");
    }

    @Override
    public Flow.Publisher<AgentStreamEvent> stream(AgentInvocation<?> invocation) {
      lastInvocation = invocation;
      return subscriber -> {
        SubmissionPublisher<AgentStreamEvent> publisher = new SubmissionPublisher<>();
        publisher.subscribe(subscriber);
        Map<String, Object> metadata = Map.of(
            AgentRuntimeMetadataKeys.TASK_ID, 1L,
            AgentRuntimeMetadataKeys.TURN_ID, 2L,
            AgentRuntimeMetadataKeys.RUN_DB_ID, 3L);
        publisher.submit(new AgentStreamEvent.AgentRunStart("run-1", "task-1", 1, metadata));
        publisher.submit(AgentStreamEvent.fromLlm(new LlmStreamEvent.ContentDelta("ok")));
        publisher.close();
      };
    }
  }

  static class UnusedLlmGateway implements LlmGateway {

    @Override
    public LlmCompletionResult complete(LlmCompletionRequest request) {
      throw new UnsupportedOperationException("complete not used");
    }

    @Override
    public Flow.Publisher<LlmStreamEvent> stream(LlmCompletionRequest request) {
      throw new UnsupportedOperationException("stream not used");
    }
  }

  static class StubLearningPlanRepository implements LearningPlanRepository {

    @Override
    public LearningPlan save(LearningPlan plan) {
      return plan;
    }

    @Override
    public List<LearningPlan> findByUserId(long userId) {
      return List.of(plan());
    }

    @Override
    public LearningPlanPage findPageByUserId(long userId, int page, int pageSize) {
      throw new UnsupportedOperationException();
    }

    @Override
    public Optional<LearningPlan> findPlanByIdForUser(long planId, long userId) {
      LearningPlan plan = plan();
      return plan.id() == planId && plan.userId() == userId ? Optional.of(plan) : Optional.empty();
    }

    private LearningPlan plan() {
      LearningPlanProblemDraft problem = new LearningPlanProblemDraft(
          "two-sum",
          1,
          "Two Sum",
          "两数之和",
          "EASY",
          List.of("Array", "Hash Table"),
          "建立哈希查找模式。",
          1);
      LearningPlanPhaseDraft phase = new LearningPlanPhaseDraft(
          1,
          "哈希表基础",
          1,
          "补数查找",
          List.of(),
          List.of(),
          List.of(),
          "",
          List.of(problem));
      LearningPlanDraftPlan snapshot = new LearningPlanDraftPlan(
          "哈希表训练",
          "summary",
          LearningPlanIntent.INTERVIEW_SPRINT,
          "4 周内准备后端面试",
          4,
          LearningPlanLevel.INTERMEDIATE,
          8,
          "Java",
          LearningPlanDifficultyPreference.MEDIUM,
          true,
          List.of("Hash Table"),
          "profile",
          List.of(phase),
          Map.of());
      return new LearningPlan(
          12L,
          7L,
          LearningPlanStatus.ACTIVE,
          snapshot,
          java.time.Instant.parse("2026-01-01T00:00:00Z"),
          java.time.Instant.parse("2026-01-01T00:00:00Z"));
    }
  }
}
