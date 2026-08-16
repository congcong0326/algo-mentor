package org.congcong.algomentor.api.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.congcong.algomentor.api.config.ApiContractConstants;
import org.congcong.algomentor.api.config.UserInputLimitProperties;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class UserInputLimitControllerTest {

  @Test
  void exposesEffectiveNumericLimits() throws Exception {
    UserInputLimitProperties properties = new UserInputLimitProperties();
    MockMvc mockMvc = MockMvcBuilders
        .standaloneSetup(new UserInputLimitController(properties))
        .build();

    mockMvc.perform(get(ApiContractConstants.USER_INPUT_LIMITS_PATH))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.reviewNote.coreIdeaMaxChars").value(2_000))
        .andExpect(jsonPath("$.data.learningPlanCreate.durationWeeksMax").value(52))
        .andExpect(jsonPath("$.data.practiceMessage.messageMaxBytes").value(8_192));
  }
}
