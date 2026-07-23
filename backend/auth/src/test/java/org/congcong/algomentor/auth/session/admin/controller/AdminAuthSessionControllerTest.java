package org.congcong.algomentor.auth.session.admin.controller;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import org.congcong.algomentor.auth.session.admin.controller.model.AdminAuthSessionListQuery;
import org.congcong.algomentor.auth.session.admin.model.AuthSessionActivity;
import org.congcong.algomentor.auth.session.admin.model.AuthSessionAdminPage;
import org.congcong.algomentor.auth.session.admin.model.AuthSessionAdminRecord;
import org.congcong.algomentor.auth.session.admin.model.AuthSessionAdminSummary;
import org.congcong.algomentor.auth.session.admin.service.AuthSessionAdminErrorCode;
import org.congcong.algomentor.auth.session.admin.service.AuthSessionAdminException;
import org.congcong.algomentor.auth.session.admin.service.AuthSessionAdminService;
import org.congcong.algomentor.common.api.ApiErrorMessageResolver;
import org.congcong.algomentor.common.api.ApiErrorResponseFactory;
import org.congcong.algomentor.identity.model.AuthUserStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class AdminAuthSessionControllerTest {

  private static final String SESSION_REF = "0f5cdb19-97f8-4e52-9ca8-218bfa8b3d44";

  private AuthSessionAdminService service;
  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    service = mock(AuthSessionAdminService.class);
    mockMvc = MockMvcBuilders.standaloneSetup(new AdminAuthSessionController(service))
        .setControllerAdvice(new AdminAuthSessionExceptionHandler(
            new ApiErrorResponseFactory(new ApiErrorMessageResolver())))
        .build();
  }

  @Test
  void forwardsNormalizedListParametersAndCurrentRequestSession() throws Exception {
    Instant now = Instant.parse("2026-07-23T09:26:00Z");
    when(service.list(eq(new AdminAuthSessionListQuery(2, 10, "alice", "IDLE")), eq("current-session")))
        .thenReturn(new AuthSessionAdminPage(
            List.of(new AuthSessionAdminRecord(
                SESSION_REF,
                "session-internal-only",
                42,
                "alice@example.com",
                "Alice",
                AuthUserStatus.ACTIVE,
                now.minusSeconds(60),
                now.minusSeconds(30),
                now.plusSeconds(60),
                AuthSessionActivity.ACTIVE,
                true)),
            1,
            2,
            10,
            new AuthSessionAdminSummary(1, 1, 1),
            now));

    mockMvc.perform(get("/api/admin/auth-sessions")
            .param("page", "2")
            .param("pageSize", "10")
            .param("keyword", "alice")
            .param("activity", "IDLE")
            .session(new MockHttpSession(null, "current-session")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.items[0].sessionRef").value(SESSION_REF))
        .andExpect(jsonPath("$.data.items[0].current").value(true))
        .andExpect(jsonPath("$.data.items[0].sessionId").doesNotExist());

    verify(service).list(new AdminAuthSessionListQuery(2, 10, "alice", "IDLE"), "current-session");
  }

  @Test
  void mapsCurrentSessionRevocationRejectionToConflict() throws Exception {
    when(service.revoke(eq(SESSION_REF), eq("current-session"), eq(7L)))
        .thenThrow(new AuthSessionAdminException(
            AuthSessionAdminErrorCode.AUTH_SESSION_CURRENT_REVOKE_FORBIDDEN,
            "不能在会话监控中下线当前会话，请使用退出登录。"));

    mockMvc.perform(delete("/api/admin/auth-sessions/{sessionRef}", SESSION_REF)
            .principal(new TestingAuthenticationToken("7", null))
            .session(new MockHttpSession(null, "current-session")))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.error.code").value("AUTH_SESSION_CURRENT_REVOKE_FORBIDDEN"))
        .andExpect(jsonPath("$.error.messageKey")
            .value(AdminAuthSessionApiContractConstants.CURRENT_REVOKE_FORBIDDEN_MESSAGE_KEY));
  }
}
