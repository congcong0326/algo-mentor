package org.congcong.algomentor.api.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.congcong.algomentor.mentor.application.learningplan.LearningPlanException;
import org.congcong.algomentor.mentor.application.learningplan.policy.LearningPlanCreationPolicyConstants;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@WebMvcTest(controllers = LearningPlanGovernanceExceptionHandlerTest.TestController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import({
    LocalizedApiExceptionHandler.class,
    LearningPlanGovernanceExceptionHandlerTest.TestController.class
})
class LearningPlanGovernanceExceptionHandlerTest {

  @Autowired
  MockMvc mockMvc;

  @Test
  void mapsSavedPlanLimitToConflict() throws Exception {
    mockMvc.perform(get("/test/learning-plan-governance/plan-limit"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.error.code").value(
            LearningPlanCreationPolicyConstants.PLAN_LIMIT_EXCEEDED_CODE))
        .andExpect(jsonPath("$.error.messageKey").value(
            "api.error." + LearningPlanCreationPolicyConstants.PLAN_LIMIT_EXCEEDED_CODE));
  }

  @Test
  void mapsDailyDraftLimitToTooManyRequests() throws Exception {
    mockMvc.perform(get("/test/learning-plan-governance/draft-limit")
            .header("Accept-Language", "en-US"))
        .andExpect(status().isTooManyRequests())
        .andExpect(jsonPath("$.error.code").value(
            LearningPlanCreationPolicyConstants.DRAFT_DAILY_LIMIT_EXCEEDED_CODE))
        .andExpect(jsonPath("$.error.message").value(
            "You have reached today's learning plan draft limit. Please try again tomorrow."));
  }

  @Test
  void mapsPolicyFailureToServiceUnavailable() throws Exception {
    mockMvc.perform(get("/test/learning-plan-governance/policy-unavailable"))
        .andExpect(status().isServiceUnavailable())
        .andExpect(jsonPath("$.error.code").value(
            LearningPlanCreationPolicyConstants.POLICY_UNAVAILABLE_CODE));
  }

  @RestController
  static class TestController {

    @GetMapping("/test/learning-plan-governance/plan-limit")
    void planLimit() {
      throw exception(LearningPlanCreationPolicyConstants.PLAN_LIMIT_EXCEEDED_CODE);
    }

    @GetMapping("/test/learning-plan-governance/draft-limit")
    void draftLimit() {
      throw exception(LearningPlanCreationPolicyConstants.DRAFT_DAILY_LIMIT_EXCEEDED_CODE);
    }

    @GetMapping("/test/learning-plan-governance/policy-unavailable")
    void policyUnavailable() {
      throw exception(LearningPlanCreationPolicyConstants.POLICY_UNAVAILABLE_CODE);
    }

    private static LearningPlanException exception(String code) {
      return new LearningPlanException(code, "api.error." + code, "fallback");
    }
  }
}
