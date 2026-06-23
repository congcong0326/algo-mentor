package org.congcong.algomentor.api.controller;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
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
import org.congcong.algomentor.agent.core.runlock.AgentRunLockConstants;
import org.congcong.algomentor.agent.core.runlock.AgentRunLockToken;
import org.congcong.algomentor.ai.governance.admission.AiRunAdmission;
import org.congcong.algomentor.ai.governance.admission.AiRunAdmissionService;
import org.congcong.algomentor.ai.governance.model.AiGovernanceMetadataKeys;
import org.congcong.algomentor.ai.governance.model.AiPurpose;
import org.congcong.algomentor.ai.governance.model.AiRunContext;
import org.congcong.algomentor.ai.governance.model.AiRunSource;
import org.congcong.algomentor.ai.governance.model.AiRunStatus;
import org.congcong.algomentor.ai.governance.policy.AiPurposePolicy;
import org.congcong.algomentor.api.service.AiActorResolver;
import org.congcong.algomentor.auth.model.AuthRole;
import org.congcong.algomentor.agent.core.runlock.AgentRunLockManager;
import org.congcong.algomentor.agent.core.runlock.AgentRunLockRequest;
import org.congcong.algomentor.agent.core.runtime.model.AgentMessage;
import org.congcong.algomentor.agent.core.runtime.model.AgentRunPreparationRequest;
import org.congcong.algomentor.agent.core.runtime.model.PreparedAgentRun;
import org.congcong.algomentor.agent.core.runtime.repository.AgentConversationRepository;
import org.congcong.algomentor.llm.core.gateway.LlmGateway;
import org.congcong.algomentor.llm.core.request.LlmCompletionRequest;
import org.congcong.algomentor.llm.core.response.LlmCompletionResult;
import org.congcong.algomentor.llm.core.stream.LlmStreamEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;

@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
class AgentConversationControllerTest {

  @Autowired
  private MockMvc mockMvc;

  @Autowired
  private StubAgentConversationRepository conversationRepository;

  @Autowired
  private StubAgentLoopRunner agentLoopRunner;

  @Autowired
  private AgentRunLockManager lockManager;

  @Autowired
  private AiRunAdmissionService governance;

  private AiRunContext lastGovernanceContext;

  @BeforeEach
  void configureGovernance() {
    when(governance.admit(any(AiRunContext.class))).thenAnswer(invocation -> {
      AiRunContext context = invocation.getArgument(0);
      lastGovernanceContext = context;
      AiPurposePolicy policy = new AiPurposePolicy(
          true, 50, 1, 16384, 2048, 8, true, true, false, false,
          null, null, "learning-chat-p0");
      return new AiRunAdmission(
          1L,
          context.runId(),
          context.actor().userId(),
          context.purpose(),
          context.source(),
          AiRunStatus.ADMITTED,
          "ALL",
          new AgentRunLockToken("user:7:ai:all", "node-1", "ai-token", null),
          policy,
          Map.of(
              AiGovernanceMetadataKeys.RUN_ID, context.runId(),
              AiGovernanceMetadataKeys.PURPOSE, context.purpose().name(),
              AiGovernanceMetadataKeys.SOURCE, context.source().name(),
              AiGovernanceMetadataKeys.QUOTA_SCOPE, "ALL"),
          java.time.Instant.now());
    });
  }

  @AfterEach
  void releaseCapturedLock() {
    try {
      if (agentLoopRunner.lastRequest == null) {
        lastGovernanceContext = null;
        return;
      }
      Object token = agentLoopRunner.lastRequest.metadata().get(AgentRunLockConstants.LOCK_TOKEN_METADATA_KEY);
      if (token instanceof AgentRunLockToken lockToken) {
        lockManager.release(lockToken);
      }
    } finally {
      agentLoopRunner.lastRequest = null;
      conversationRepository.lastRequest = null;
      lastGovernanceContext = null;
    }
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

    org.assertj.core.api.Assertions.assertThat(conversationRepository.lastRequest.idempotencyKey()).isEqualTo("idem-1");
    org.assertj.core.api.Assertions.assertThat(conversationRepository.lastRequest.userId()).isEqualTo(7L);
    org.assertj.core.api.Assertions.assertThat(conversationRepository.lastRequest.userMessage())
        .isEqualTo("Explain two pointers.");
    org.assertj.core.api.Assertions.assertThat(lastGovernanceContext.purpose()).isEqualTo(AiPurpose.LEARNING_CHAT);
    org.assertj.core.api.Assertions.assertThat(lastGovernanceContext.source()).isEqualTo(AiRunSource.LEARNING_CHAT);
    org.assertj.core.api.Assertions.assertThat(agentLoopRunner.lastRequest.runId()).isEqualTo("run-1");
    org.assertj.core.api.Assertions.assertThat(agentLoopRunner.lastRequest.metadata())
        .containsKey(AgentRunLockConstants.LOCK_TOKEN_METADATA_KEY)
        .containsEntry(AiGovernanceMetadataKeys.PURPOSE, "LEARNING_CHAT");
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

    org.assertj.core.api.Assertions.assertThat(conversationRepository.lastRequest.userId()).isEqualTo(7L);
    org.assertj.core.api.Assertions.assertThat(lastGovernanceContext.actor().userId()).isEqualTo(7L);
  }

  @Test
  void streamConversationReturnsConflictWhenTaskLockIsHeld() throws Exception {
    AgentRunLockToken token = lockManager.tryAcquire(new AgentRunLockRequest(
        AgentRunLockConstants.TASK_LOCK_KEY_PREFIX + 1,
        "test-owner",
        null,
        Map.of("taskId", 1L))).token();

    try {
      mockMvc.perform(post("/api/agent/conversations/stream")
              .contentType(MediaType.APPLICATION_JSON)
              .accept(MediaType.TEXT_EVENT_STREAM)
              .header("Idempotency-Key", "idem-2")
              .content("{\"taskId\":1,\"message\":\"Explain sliding window.\"}"))
          .andExpect(status().isConflict())
          .andExpect(jsonPath("$.success").value(false))
          .andExpect(jsonPath("$.error.code").value("AGENT_RUN_IN_PROGRESS"))
          .andExpect(jsonPath("$.error.metadata.taskId").value(1));

      org.assertj.core.api.Assertions.assertThat(agentLoopRunner.lastRequest).isNull();
      org.assertj.core.api.Assertions.assertThat(conversationRepository.lastRequest).isNull();
    } finally {
      lockManager.release(token);
    }
  }

  @TestConfiguration(proxyBeanMethods = false)
  static class TestConfig {

    @Bean
    StubAgentConversationRepository stubAgentConversationRepository() {
      return new StubAgentConversationRepository();
    }

    @Bean
    @Primary
    StubAgentLoopRunner stubAgentLoopRunner() {
      return new StubAgentLoopRunner();
    }

    @Bean
    @Primary
    AiActorResolver aiActorResolver() {
      return new AiActorResolver(() -> Optional.of(new org.congcong.algomentor.auth.security.AuthenticatedUserPrincipal(
          7L, "user@example.com", "User", null, List.of(AuthRole.USER), null)));
    }

    @Bean
    AiRunAdmissionService aiRunAdmissionService() {
      return org.mockito.Mockito.mock(AiRunAdmissionService.class);
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

  static class StubAgentLoopRunner extends AgentLoopRunner {
    private AgentRequest lastRequest;

    StubAgentLoopRunner() {
      super(new UnusedLlmGateway(), "stub-model", AgentToolRegistry.empty(), 1);
    }

    @Override
    public Flow.Publisher<AgentStreamEvent> stream(AgentRequest request) {
      lastRequest = request;
      return subscriber -> {
        SubmissionPublisher<AgentStreamEvent> publisher = new SubmissionPublisher<>();
        publisher.subscribe(subscriber);
        publisher.submit(new AgentStreamEvent.AgentRunStart(
            request.runId(),
            request.displayTitle(),
            1,
            request.metadata()));
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
}
