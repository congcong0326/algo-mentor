package org.congcong.algomentor.agent.core.permission;

import java.time.Instant;
import org.congcong.algomentor.agent.core.AgentCancellationToken;

public interface AgentToolPermissionCoordinator {

  AgentToolPermissionAuthorization authorize(
      AgentToolPermissionCheck check,
      AgentToolPermissionDecisionPlan plan,
      AgentCancellationToken cancellationToken,
      EventPublisher eventPublisher
  );

  default AgentToolPermissionAuthorization authorize(
      AgentToolPermissionCheck check,
      AgentToolPermissionDecisionPlan plan,
      AgentCancellationToken cancellationToken
  ) {
    return authorize(check, plan, cancellationToken, EventPublisher.noop());
  }

  AgentToolPermissionDecisionResult decide(
      String permissionRequestId,
      AgentToolPermissionDecisionType decision,
      String reason,
      long userId
  );

  interface EventPublisher {

    void toolPermissionRequested(
        AgentToolPermissionRequest request,
        AgentToolPermissionDecisionPlan plan
    );

    void toolPermissionDecided(
        AgentToolPermissionRequest request,
        AgentToolPermissionDecision decision,
        AgentToolPermissionDecisionPlan plan
    );

    void toolPermissionTimedOut(
        AgentToolPermissionRequest request,
        String reason,
        Instant expiredAt,
        AgentToolPermissionDecisionPlan plan
    );

    static EventPublisher noop() {
      return new EventPublisher() {
        @Override
        public void toolPermissionRequested(
            AgentToolPermissionRequest request,
            AgentToolPermissionDecisionPlan plan
        ) {
        }

        @Override
        public void toolPermissionDecided(
            AgentToolPermissionRequest request,
            AgentToolPermissionDecision decision,
            AgentToolPermissionDecisionPlan plan
        ) {
        }

        @Override
        public void toolPermissionTimedOut(
            AgentToolPermissionRequest request,
            String reason,
            Instant expiredAt,
            AgentToolPermissionDecisionPlan plan
        ) {
        }
      };
    }
  }
}
