package org.congcong.algomentor.api.controller;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Flow;
import java.util.concurrent.SubmissionPublisher;
import org.congcong.algomentor.agent.core.AgentLoopRunner;
import org.congcong.algomentor.agent.core.AgentRequest;
import org.congcong.algomentor.agent.core.AgentStreamEvent;
import org.congcong.algomentor.agent.core.AgentToolRegistry;
import org.congcong.algomentor.agent.core.runlock.AgentRunLockConstants;
import org.congcong.algomentor.agent.core.runlock.AgentRunLockManager;
import org.congcong.algomentor.agent.core.runlock.AgentRunLockRequest;
import org.congcong.algomentor.agent.core.runlock.AgentRunLockToken;
import org.congcong.algomentor.agent.core.runtime.model.AgentMessage;
import org.congcong.algomentor.agent.core.runtime.model.AgentRunPreparationRequest;
import org.congcong.algomentor.agent.core.runtime.model.PreparedAgentRun;
import org.congcong.algomentor.agent.core.runtime.repository.AgentConversationRepository;
import org.congcong.algomentor.llm.core.gateway.LlmGateway;
import org.congcong.algomentor.llm.core.request.LlmCompletionRequest;
import org.congcong.algomentor.llm.core.response.LlmCompletionResult;
import org.congcong.algomentor.llm.core.stream.LlmStreamEvent;
import org.junit.jupiter.api.AfterEach;
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

@SpringBootTest
@AutoConfigureMockMvc
class AgentConversationControllerTest {

  @Autowired
  private MockMvc mockMvc;

  @Autowired
  private StubAgentConversationRepository conversationRepository;

  @Autowired
  private StubAgentLoopRunner agentLoopRunner;

  @Autowired
  private AgentRunLockManager lockManager;

  @AfterEach
  void releaseCapturedLock() {
    try {
      if (agentLoopRunner.lastRequest == null) {
        return;
      }
      Object token = agentLoopRunner.lastRequest.metadata().get(AgentRunLockConstants.LOCK_TOKEN_METADATA_KEY);
      if (token instanceof AgentRunLockToken lockToken) {
        lockManager.release(lockToken);
      }
    } finally {
      agentLoopRunner.lastRequest = null;
      conversationRepository.lastRequest = null;
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
        .andExpect(content().string(containsString("event:content_delta")))
        .andExpect(content().string(containsString("\"content\":\"ok\"")));

    org.assertj.core.api.Assertions.assertThat(conversationRepository.lastRequest.idempotencyKey()).isEqualTo("idem-1");
    org.assertj.core.api.Assertions.assertThat(conversationRepository.lastRequest.userMessage())
        .isEqualTo("Explain two pointers.");
    org.assertj.core.api.Assertions.assertThat(agentLoopRunner.lastRequest.runId()).isEqualTo("run-1");
    org.assertj.core.api.Assertions.assertThat(agentLoopRunner.lastRequest.metadata())
        .containsKey(AgentRunLockConstants.LOCK_TOKEN_METADATA_KEY);
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
