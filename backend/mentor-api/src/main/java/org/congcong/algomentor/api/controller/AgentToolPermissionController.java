package org.congcong.algomentor.api.controller;

import java.util.Locale;
import org.congcong.algomentor.agent.core.permission.AgentToolPermissionCoordinator;
import org.congcong.algomentor.agent.core.permission.AgentToolPermissionDecisionResult;
import org.congcong.algomentor.agent.core.permission.AgentToolPermissionDecisionType;
import org.congcong.algomentor.agent.core.permission.AgentToolPermissionException;
import org.congcong.algomentor.api.agent.model.AgentToolPermissionDecisionRequest;
import org.congcong.algomentor.api.agent.model.AgentToolPermissionDecisionResponse;
import org.congcong.algomentor.api.config.ApiContractConstants;
import org.congcong.algomentor.auth.security.AuthenticatedUserPrincipal;
import org.congcong.algomentor.auth.security.CurrentUserIdProvider;
import org.congcong.algomentor.common.api.ApiResponse;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AgentToolPermissionController {

  private final AgentToolPermissionCoordinator coordinator;
  private final CurrentUserIdProvider currentUserIdProvider;

  public AgentToolPermissionController(
      AgentToolPermissionCoordinator coordinator,
      CurrentUserIdProvider currentUserIdProvider
  ) {
    this.coordinator = coordinator;
    this.currentUserIdProvider = currentUserIdProvider;
  }

  @PostMapping(ApiContractConstants.AGENT_TOOL_PERMISSION_DECISION_PATH)
  public ApiResponse<AgentToolPermissionDecisionResponse> decide(
      @PathVariable String permissionRequestId,
      @RequestBody AgentToolPermissionDecisionRequest request
  ) {
    long currentUserId = requireCurrentUserId();
    AgentToolPermissionDecisionType decision = parseDecision(request);
    AgentToolPermissionDecisionResult result = coordinator.decide(
        permissionRequestId,
        decision,
        request.reason(),
        currentUserId);
    return ApiResponse.success(AgentToolPermissionDecisionResponse.fromResult(result));
  }

  private long requireCurrentUserId() {
    return currentUserIdProvider.currentUser()
        .map(AuthenticatedUserPrincipal::userId)
        .orElseThrow(() -> new AgentToolPermissionUnauthenticatedException("当前请求未登录或无法解析当前用户。"));
  }

  private AgentToolPermissionDecisionType parseDecision(AgentToolPermissionDecisionRequest request) {
    if (request == null || request.decision() == null || request.decision().isBlank()) {
      throw new AgentToolPermissionException(
          AgentToolPermissionException.Code.INVALID_DECISION,
          "工具权限决策不能为空。");
    }
    try {
      return AgentToolPermissionDecisionType.valueOf(request.decision().trim().toUpperCase(Locale.ROOT));
    } catch (IllegalArgumentException exception) {
      throw new AgentToolPermissionException(
          AgentToolPermissionException.Code.INVALID_DECISION,
          "工具权限决策只支持 ALLOW 或 DENY。");
    }
  }
}
