package org.congcong.algomentor.api.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;
import org.congcong.algomentor.ai.governance.admission.AiRunAdmissionException;
import org.congcong.algomentor.ai.governance.model.AiGovernanceErrorCode;
import org.congcong.algomentor.ai.governance.model.AiRunStatus;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@WebMvcTest(controllers = AiGovernanceExceptionHandlerTest.TestAiGovernanceController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import({
    LocalizedApiExceptionHandler.class,
    AiGovernanceExceptionHandlerTest.TestAiGovernanceController.class
})
class AiGovernanceExceptionHandlerTest {

  @Autowired
  MockMvc mockMvc;

  @Test
  void mapsQuotaExceededToStableApiResponse() throws Exception {
    mockMvc.perform(get("/test/ai-governance/quota"))
        .andExpect(status().isTooManyRequests())
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.error.code").value("AI_QUOTA_EXCEEDED"))
        .andExpect(jsonPath("$.error.messageKey").value("api.error.AI_QUOTA_EXCEEDED"))
        .andExpect(jsonPath("$.error.message").value("今日 AI 使用次数已达上限，请明天再试。"));
  }

  @Test
  void mapsConcurrentRunConflictToStableApiResponse() throws Exception {
    mockMvc.perform(get("/test/ai-governance/concurrent").header("Accept-Language", "en-US"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.error.code").value("AI_CONCURRENT_RUN_CONFLICT"))
        .andExpect(jsonPath("$.error.messageKey").value("api.error.AI_CONCURRENT_RUN_CONFLICT"))
        .andExpect(jsonPath("$.error.message")
            .value("Another AI task is already running. Please wait until it finishes."))
        .andExpect(jsonPath("$.error.metadata.taskId").value(42));
  }

  @RestController
  public static class TestAiGovernanceController {

    @GetMapping("/test/ai-governance/quota")
    void quota() {
      throw new AiRunAdmissionException(
          AiGovernanceErrorCode.AI_QUOTA_EXCEEDED,
          AiRunStatus.REJECTED_QUOTA,
          "今日 AI 使用次数已达上限，请明天再试。",
          HttpStatus.TOO_MANY_REQUESTS,
          Map.of());
    }

    @GetMapping("/test/ai-governance/concurrent")
    void concurrent() {
      throw new AiRunAdmissionException(
          AiGovernanceErrorCode.AI_CONCURRENT_RUN_CONFLICT,
          AiRunStatus.REJECTED_CONCURRENT,
          "已有一个 AI 任务正在运行，请等待完成后再试。",
          HttpStatus.CONFLICT,
          Map.of("taskId", 42));
    }
  }
}
