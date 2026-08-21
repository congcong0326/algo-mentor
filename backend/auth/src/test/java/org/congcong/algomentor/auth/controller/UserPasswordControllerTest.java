package org.congcong.algomentor.auth.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.congcong.algomentor.auth.model.UserPasswordUpdateRequest;
import org.congcong.algomentor.auth.password.UserPasswordErrorCode;
import org.congcong.algomentor.auth.password.UserPasswordException;
import org.congcong.algomentor.auth.password.UserPasswordService;
import org.congcong.algomentor.auth.password.UserPasswordUpdateOperation;
import org.congcong.algomentor.auth.password.UserPasswordUpdateResult;
import org.congcong.algomentor.common.api.ApiErrorMessageResolver;
import org.congcong.algomentor.common.api.ApiErrorResponseFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class UserPasswordControllerTest {

  private final ObjectMapper objectMapper = new ObjectMapper();
  private final UserPasswordService service = mock(UserPasswordService.class);
  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    mockMvc = MockMvcBuilders.standaloneSetup(new UserPasswordController(
        service,
        new ApiErrorResponseFactory(new ApiErrorMessageResolver()))).build();
  }

  @Test
  void returnsPasswordUpdateResponseAndPassesCurrentSessionId() throws Exception {
    when(service.updatePassword(any())).thenReturn(new UserPasswordUpdateResult(
        true,
        UserPasswordUpdateOperation.UPDATED,
        2));
    MockHttpSession session = new MockHttpSession();

    mockMvc.perform(put("/api/auth/password")
            .session(session)
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsBytes(new UserPasswordUpdateRequest(
                "old-password",
                "new-password",
                "new-password"))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.passwordConfigured").value(true))
        .andExpect(jsonPath("$.data.operation").value("UPDATED"))
        .andExpect(jsonPath("$.data.revokedSessionCount").value(2));

    verify(service).updatePassword(org.mockito.ArgumentMatchers.argThat(command ->
        session.getId().equals(command.currentSessionId())
            && "old-password".equals(command.currentPassword())
            && "new-password".equals(command.newPassword())));
  }

  @Test
  void allowsAnAlreadyLoggedInUserToUpdatePasswordAfterLoginIsDisabled() throws Exception {
    when(service.updatePassword(any())).thenReturn(new UserPasswordUpdateResult(
        true,
        UserPasswordUpdateOperation.UPDATED,
        0));

    mockMvc.perform(put("/api/auth/password")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsBytes(new UserPasswordUpdateRequest(
                "old-password",
                "new-password",
                "new-password"))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true));

    verify(service).updatePassword(any());
  }

  @Test
  void mapsCurrentPasswordFailureUsingLocalizedErrorContract() throws Exception {
    when(service.updatePassword(any())).thenThrow(new UserPasswordException(
        UserPasswordErrorCode.AUTH_CURRENT_PASSWORD_INVALID,
        "fallback"));

    mockMvc.perform(put("/api/auth/password")
            .header("Accept-Language", "en-US")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsBytes(new UserPasswordUpdateRequest(
                "old-password",
                "new-password",
                "new-password"))))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.error.code").value("AUTH_CURRENT_PASSWORD_INVALID"))
        .andExpect(jsonPath("$.error.messageKey").value("api.error.AUTH_CURRENT_PASSWORD_INVALID"))
        .andExpect(jsonPath("$.error.message").value("The current password is incorrect."));
  }

  @Test
  void mapsConcurrentModificationToConflict() throws Exception {
    when(service.updatePassword(any())).thenThrow(new UserPasswordException(
        UserPasswordErrorCode.AUTH_PASSWORD_CHANGED_CONCURRENTLY,
        "fallback"));

    mockMvc.perform(put("/api/auth/password")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsBytes(new UserPasswordUpdateRequest(
                "old-password",
                "new-password",
                "new-password"))))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.error.code").value("AUTH_PASSWORD_CHANGED_CONCURRENTLY"));
  }

  @Test
  void mapsInfrastructureFailureToServiceUnavailable() throws Exception {
    when(service.updatePassword(any())).thenThrow(new UserPasswordException(
        UserPasswordErrorCode.AUTH_PASSWORD_UPDATE_FAILED,
        "fallback"));

    mockMvc.perform(put("/api/auth/password")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsBytes(new UserPasswordUpdateRequest(
                "old-password",
                "new-password",
                "new-password"))))
        .andExpect(status().isServiceUnavailable())
        .andExpect(jsonPath("$.error.code").value("AUTH_PASSWORD_UPDATE_FAILED"));
  }
}
