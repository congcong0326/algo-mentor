package org.congcong.algomentor.auth.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.congcong.algomentor.auth.config.AuthProperties;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
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

  @Test
  void suppressesPasswordRegistrationWhenNewAccountRegistrationIsDisabled() throws Exception {
    AuthProperties properties = new AuthProperties();
    properties.setAccountRegistrationEnabled(false);
    MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new AuthCapabilitiesController(properties)).build();

    mockMvc.perform(get("/api/auth/capabilities"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.passwordLoginEnabled").value(true))
        .andExpect(jsonPath("$.data.passwordRegistrationEnabled").value(false));
  }

  @Test
  void reportsConfiguredOAuthProviders() throws Exception {
    AuthProperties properties = new AuthProperties();
    ClientRegistrationRepository registrations = mock(ClientRegistrationRepository.class);
    when(registrations.findByRegistrationId("github")).thenReturn(mock(ClientRegistration.class));
    MockMvc mockMvc = MockMvcBuilders.standaloneSetup(
        new AuthCapabilitiesController(properties, registrations)).build();

    mockMvc.perform(get("/api/auth/capabilities"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.oauthProviders.length()").value(1))
        .andExpect(jsonPath("$.data.oauthProviders[0]").value("github"));
  }
}
