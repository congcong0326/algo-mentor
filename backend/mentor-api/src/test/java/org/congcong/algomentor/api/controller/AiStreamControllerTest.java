package org.congcong.algomentor.api.controller;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.congcong.algomentor.agent.core.AgentStreamEvent;
import org.congcong.algomentor.mentor.application.ExplainTopicUseCase;
import org.congcong.algomentor.llm.core.model.LlmModelId;
import org.congcong.algomentor.llm.core.provider.LlmProviderId;
import org.congcong.algomentor.llm.core.response.LlmFinishReason;
import org.congcong.algomentor.llm.core.response.LlmUsage;
import org.congcong.algomentor.llm.core.stream.LlmStreamEvent;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Map;
import java.util.concurrent.SubmissionPublisher;

@SpringBootTest
@AutoConfigureMockMvc
class AiStreamControllerTest {

  @Autowired
  private MockMvc mockMvc;

  @MockitoBean
  private ExplainTopicUseCase explainTopicUseCase;

  @Test
  void streamExplanationSendsLlmStreamEventsAsSseEvents() throws Exception {
    SubmissionPublisher<AgentStreamEvent> publisher = new SubmissionPublisher<>();
    when(explainTopicUseCase.stream("two pointers")).thenReturn(publisher);

    MvcResult result = mockMvc.perform(get("/api/ai/explanations/stream")
            .param("topic", "two pointers"))
        .andExpect(request().asyncStarted())
        .andReturn();

    publisher.submit(new AgentStreamEvent.AgentRunStart("run_1", "two pointers", 4));
    publisher.submit(new AgentStreamEvent.AgentStepStart("run_1", 1));
    publisher.submit(AgentStreamEvent.fromLlm(
        new LlmStreamEvent.MessageStart(LlmProviderId.of("openai"), LlmModelId.of("gpt-test"))));
    publisher.submit(AgentStreamEvent.fromLlm(new LlmStreamEvent.ContentDelta("Use two ")));
    publisher.submit(AgentStreamEvent.fromLlm(new LlmStreamEvent.ContentDelta("indices.")));
    publisher.submit(AgentStreamEvent.fromLlm(new LlmStreamEvent.Usage(new LlmUsage(3, 5, 0, 0, 8))));
    publisher.submit(AgentStreamEvent.fromLlm(
        new LlmStreamEvent.MessageEnd(LlmFinishReason.STOP, Map.of("responseId", "resp_123"))));
    publisher.submit(new AgentStreamEvent.AgentRunEnd("run_1", 1, LlmFinishReason.STOP, Map.of()));
    publisher.close();

    mockMvc.perform(asyncDispatch(result))
        .andExpect(status().isOk())
        .andExpect(header().string(HttpHeaders.CONTENT_TYPE, containsString(MediaType.TEXT_EVENT_STREAM_VALUE)))
        .andExpect(content().string(containsString("event:agent_run_start")))
        .andExpect(content().string(containsString("\"runId\":\"run_1\"")))
        .andExpect(content().string(containsString("event:agent_step_start")))
        .andExpect(content().string(containsString("event:message_start")))
        .andExpect(content().string(containsString("\"provider\":\"openai\"")))
        .andExpect(content().string(containsString("event:content_delta")))
        .andExpect(content().string(containsString("\"content\":\"Use two \"")))
        .andExpect(content().string(containsString("\"content\":\"indices.\"")))
        .andExpect(content().string(containsString("event:usage")))
        .andExpect(content().string(containsString("\"totalTokens\":8")))
        .andExpect(content().string(containsString("event:message_end")))
        .andExpect(content().string(containsString("\"finishReason\":\"STOP\"")))
        .andExpect(content().string(containsString("event:agent_run_end")));

    verify(explainTopicUseCase).stream("two pointers");
  }
}
