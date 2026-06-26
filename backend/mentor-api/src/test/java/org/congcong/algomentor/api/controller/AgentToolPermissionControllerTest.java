package org.congcong.algomentor.api.controller;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.congcong.algomentor.agent.core.permission.AgentToolPermissionCoordinator;
import org.congcong.algomentor.agent.core.permission.AgentToolPermissionDecision;
import org.congcong.algomentor.agent.core.permission.AgentToolPermissionDecisionResult;
import org.congcong.algomentor.agent.core.permission.AgentToolPermissionDecisionType;
import org.congcong.algomentor.agent.core.permission.AgentToolPermissionException;
import org.congcong.algomentor.agent.core.permission.AgentToolPermissionRequest;
import org.congcong.algomentor.auth.model.AuthUserStatus;
import org.congcong.algomentor.auth.security.AuthenticatedUserPrincipal;
import org.congcong.algomentor.auth.security.CurrentUserIdProvider;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = AgentToolPermissionController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(AgentToolPermissionExceptionHandler.class)
class AgentToolPermissionControllerTest {

  private static final String DECISION_PATH = "/api/agent/tool-permissions/perm-1/decision";

  @Autowired
  private MockMvc mockMvc;

  @MockBean
  private AgentToolPermissionCoordinator coordinator;

  @MockBean
  private CurrentUserIdProvider currentUserIdProvider;

  @Test
  void ownerCanAllowPermissionRequest() throws Exception {
    when(currentUserIdProvider.currentUser()).thenReturn(Optional.of(currentUser()));
    when(coordinator.decide(eq("perm-1"), eq(AgentToolPermissionDecisionType.ALLOW), eq("user_confirmed"), eq(42L)))
        .thenReturn(decisionResult(AgentToolPermissionDecisionType.ALLOW, "user_confirmed", true));

    mockMvc.perform(post(DECISION_PATH)
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {
                  "decision": "ALLOW",
                  "reason": "user_confirmed"
                }
                """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.permissionRequestId").value("perm-1"))
        .andExpect(jsonPath("$.data.decision").value("ALLOW"))
        .andExpect(jsonPath("$.data.accepted").value(true));

    verify(coordinator).decide("perm-1", AgentToolPermissionDecisionType.ALLOW, "user_confirmed", 42L);
  }

  @Test
  void ownerCanDenyPermissionRequest() throws Exception {
    when(currentUserIdProvider.currentUser()).thenReturn(Optional.of(currentUser()));
    when(coordinator.decide(eq("perm-1"), eq(AgentToolPermissionDecisionType.DENY), eq("user_rejected"), eq(42L)))
        .thenReturn(decisionResult(AgentToolPermissionDecisionType.DENY, "user_rejected", true));

    mockMvc.perform(post(DECISION_PATH)
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {
                  "decision": "DENY",
                  "reason": "user_rejected"
                }
                """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.permissionRequestId").value("perm-1"))
        .andExpect(jsonPath("$.data.decision").value("DENY"))
        .andExpect(jsonPath("$.data.accepted").value(true));

    verify(coordinator).decide("perm-1", AgentToolPermissionDecisionType.DENY, "user_rejected", 42L);
  }

  @Test
  void unauthenticatedRequestReturns401() throws Exception {
    when(currentUserIdProvider.currentUser()).thenReturn(Optional.empty());

    mockMvc.perform(post(DECISION_PATH)
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {
                  "decision": "ALLOW",
                  "reason": "user_confirmed"
                }
                """))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.error.code").value("AUTH_UNAUTHENTICATED"));

    verifyNoInteractions(coordinator);
  }

  @Test
  void nonOwnerDecisionReturns403() throws Exception {
    when(currentUserIdProvider.currentUser()).thenReturn(Optional.of(currentUser()));
    when(coordinator.decide(eq("perm-1"), eq(AgentToolPermissionDecisionType.DENY), eq("user_rejected"), eq(42L)))
        .thenThrow(permissionException(AgentToolPermissionException.Code.FORBIDDEN));

    mockMvc.perform(post(DECISION_PATH)
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {
                  "decision": "DENY",
                  "reason": "user_rejected"
                }
                """))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
  }

  @Test
  void missingPermissionRequestReturns404() throws Exception {
    when(currentUserIdProvider.currentUser()).thenReturn(Optional.of(currentUser()));
    when(coordinator.decide(eq("perm-1"), eq(AgentToolPermissionDecisionType.ALLOW), eq("user_confirmed"), eq(42L)))
        .thenThrow(permissionException(AgentToolPermissionException.Code.NOT_FOUND));

    mockMvc.perform(post(DECISION_PATH)
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {
                  "decision": "ALLOW",
                  "reason": "user_confirmed"
                }
                """))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.error.code").value("NOT_FOUND"));
  }

  @Test
  void alreadyDecidedPermissionRequestReturns409() throws Exception {
    when(currentUserIdProvider.currentUser()).thenReturn(Optional.of(currentUser()));
    when(coordinator.decide(eq("perm-1"), eq(AgentToolPermissionDecisionType.ALLOW), eq("user_confirmed"), eq(42L)))
        .thenThrow(permissionException(AgentToolPermissionException.Code.ALREADY_DECIDED));

    mockMvc.perform(post(DECISION_PATH)
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {
                  "decision": "ALLOW",
                  "reason": "user_confirmed"
                }
                """))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.error.code").value("ALREADY_DECIDED"));
  }

  @Test
  void expiredPermissionRequestReturns409() throws Exception {
    when(currentUserIdProvider.currentUser()).thenReturn(Optional.of(currentUser()));
    when(coordinator.decide(eq("perm-1"), eq(AgentToolPermissionDecisionType.ALLOW), eq("user_confirmed"), eq(42L)))
        .thenThrow(permissionException(AgentToolPermissionException.Code.EXPIRED));

    mockMvc.perform(post(DECISION_PATH)
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {
                  "decision": "ALLOW",
                  "reason": "user_confirmed"
                }
                """))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.error.code").value("EXPIRED"));
  }

  @Test
  void invalidDecisionReturns400WithoutCallingCoordinator() throws Exception {
    when(currentUserIdProvider.currentUser()).thenReturn(Optional.of(currentUser()));

    mockMvc.perform(post(DECISION_PATH)
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {
                  "decision": "ASK",
                  "reason": "user_confirmed"
                }
                """))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("INVALID_DECISION"));

    verifyNoInteractions(coordinator);
  }

  @Test
  void requestBodyDoesNotAcceptUserId() throws Exception {
    when(currentUserIdProvider.currentUser()).thenReturn(Optional.of(currentUser()));

    mockMvc.perform(post(DECISION_PATH)
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {
                  "decision": "ALLOW",
                  "reason": "user_confirmed",
                  "userId": 999
                }
                """))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("AGENT_TOOL_PERMISSION_REQUEST_INVALID"));

    verifyNoInteractions(coordinator);
  }

  private AuthenticatedUserPrincipal currentUser() {
    return new AuthenticatedUserPrincipal(
        42L,
        "learner@example.com",
        "Learner",
        null,
        List.of(),
        AuthUserStatus.ACTIVE);
  }

  private AgentToolPermissionDecisionResult decisionResult(
      AgentToolPermissionDecisionType type,
      String reason,
      boolean accepted
  ) {
    return new AgentToolPermissionDecisionResult(
        permissionRequest(),
        new AgentToolPermissionDecision(
            "perm-1",
            type,
            reason,
            42L,
            Instant.parse("2026-06-26T00:00:00Z")),
        accepted);
  }

  private AgentToolPermissionRequest permissionRequest() {
    return new AgentToolPermissionRequest(
        "perm-1",
        "run-1",
        1,
        "call-1",
        "submit_practice_code_review",
        "提交代码 Review",
        "模型请求执行正式 Review",
        Map.of("effect", "save_review"),
        Instant.parse("2026-06-26T00:00:00Z"),
        Instant.parse("2026-06-26T00:01:00Z"));
  }

  private AgentToolPermissionException permissionException(AgentToolPermissionException.Code code) {
    return new AgentToolPermissionException(code, "permission error");
  }
}
