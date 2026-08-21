package org.congcong.algomentor.auth.controller.admin;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import org.congcong.algomentor.auth.loginsettings.model.AuthLoginSettings;
import org.congcong.algomentor.auth.loginsettings.service.AuthLoginSettingsService;
import org.congcong.algomentor.common.api.ApiErrorMessageResolver;
import org.congcong.algomentor.common.api.ApiErrorResponseFactory;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class AuthLoginSettingsControllerTest {

  private static final Instant UPDATED_AT = Instant.parse("2026-08-21T00:00:00Z");

  @Test
  void returnsCurrentSettings() throws Exception {
    AuthLoginSettingsService service = mock(AuthLoginSettingsService.class);
    when(service.current()).thenReturn(settings());
    MockMvc mockMvc = mockMvc(service);

    mockMvc.perform(get(AuthLoginSettingsApiContractConstants.BASE_PATH))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.accountRegistrationEnabled").value(true))
        .andExpect(jsonPath("$.data.googleLoginEnabled").value(false));
  }

  @Test
  void updatesSettingsForTheAuthenticatedAdministrator() throws Exception {
    AuthLoginSettingsService service = mock(AuthLoginSettingsService.class);
    AuthLoginSettings updated = new AuthLoginSettings((short) 1, false, false, true, false, true, 7L, "Admin", UPDATED_AT);
    when(service.update(false, false, true, false, true, 7L)).thenReturn(updated);
    MockMvc mockMvc = mockMvc(service);

    mockMvc.perform(patch(AuthLoginSettingsApiContractConstants.BASE_PATH)
            .contentType("application/json")
            .content(new ObjectMapper().writeValueAsString(new Object() {
              public final boolean accountRegistrationEnabled = false;
              public final boolean passwordLoginEnabled = false;
              public final boolean passwordRegistrationEnabled = true;
              public final boolean googleLoginEnabled = false;
              public final boolean githubLoginEnabled = true;
            }))
            .principal(new org.springframework.security.authentication.UsernamePasswordAuthenticationToken("7", null)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.passwordRegistrationEnabled").value(true));

    verify(service).update(false, false, true, false, true, 7L);
  }

  private static MockMvc mockMvc(AuthLoginSettingsService service) {
    return MockMvcBuilders.standaloneSetup(new AuthLoginSettingsController(service))
        .setControllerAdvice(new AuthLoginSettingsExceptionHandler(
            new ApiErrorResponseFactory(new ApiErrorMessageResolver())))
        .build();
  }

  private static AuthLoginSettings settings() {
    return new AuthLoginSettings((short) 1, true, true, true, false, true, 7L, "Admin", UPDATED_AT);
  }
}
