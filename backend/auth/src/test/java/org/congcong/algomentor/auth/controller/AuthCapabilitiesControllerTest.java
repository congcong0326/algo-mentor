package org.congcong.algomentor.auth.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.congcong.algomentor.auth.config.AuthProperties;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class AuthCapabilitiesControllerTest {

  @Test
  void reportsPasswordAuthenticationFeatureFlags() throws Exception {
    AuthProperties properties = new AuthProperties();
    properties.setPasswordLoginEnabled(false);
    properties.setPasswordRegistrationEnabled(false);
    MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new AuthCapabilitiesController(properties)).build();

    mockMvc.perform(get("/api/auth/capabilities"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.passwordLoginEnabled").value(false))
        .andExpect(jsonPath("$.data.passwordRegistrationEnabled").value(false));
  }

  @Test
  void suppressesPasswordRegistrationWhenPasswordLoginIsDisabled() throws Exception {
    AuthProperties properties = new AuthProperties();
    properties.setPasswordLoginEnabled(false);
    properties.setPasswordRegistrationEnabled(true);
    MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new AuthCapabilitiesController(properties)).build();

    mockMvc.perform(get("/api/auth/capabilities"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.passwordLoginEnabled").value(false))
        .andExpect(jsonPath("$.data.passwordRegistrationEnabled").value(false));
  }
}
