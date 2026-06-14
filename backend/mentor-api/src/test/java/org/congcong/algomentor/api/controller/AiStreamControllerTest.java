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

import org.congcong.algomentor.mentor.application.ExplainTopicUseCase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
class AiStreamControllerTest {

  @Autowired
  private MockMvc mockMvc;

  @MockitoBean
  private ExplainTopicUseCase explainTopicUseCase;

  @Test
  void streamExplanationSendsUseCaseResultAsSseEvent() throws Exception {
    when(explainTopicUseCase.explain("two pointers"))
        .thenReturn("Use two indices to shrink the search space.");

    MvcResult result = mockMvc.perform(get("/api/ai/explanations/stream")
            .param("topic", "two pointers"))
        .andExpect(request().asyncStarted())
        .andReturn();

    mockMvc.perform(asyncDispatch(result))
        .andExpect(status().isOk())
        .andExpect(header().string(HttpHeaders.CONTENT_TYPE, containsString(MediaType.TEXT_EVENT_STREAM_VALUE)))
        .andExpect(content().string(containsString("event:explanation")))
        .andExpect(content().string(containsString("Use two indices to shrink the search space.")));

    verify(explainTopicUseCase).explain("two pointers");
  }
}
