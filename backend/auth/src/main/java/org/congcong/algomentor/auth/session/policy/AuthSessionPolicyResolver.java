package org.congcong.algomentor.auth.session.policy;

import java.util.Optional;
import org.congcong.algomentor.policy.model.ResolvedPolicy;
import org.congcong.algomentor.policy.service.GenericPolicyQueryService;
import org.congcong.algomentor.policy.type.GenericPolicyType;

/** 将通用策略底座的单条命中结果解析为认证模块会话策略快照。 */
public class AuthSessionPolicyResolver {

  private final GenericPolicyQueryService policyQueryService;
  private final GenericPolicyType<UserSessionPolicy> policyType;
  private final AuthSessionPolicyMetrics metrics;

  public AuthSessionPolicyResolver(
      GenericPolicyQueryService policyQueryService,
      GenericPolicyType<UserSessionPolicy> policyType,
      AuthSessionPolicyMetrics metrics
  ) {
    this.policyQueryService = policyQueryService;
    this.policyType = policyType;
    this.metrics = metrics;
  }

  public ResolvedUserSessionPolicy resolve(long userId) {
    if (userId < 1) {
      throw unavailable(new IllegalArgumentException("userId must be positive."));
    }
    try {
      Optional<ResolvedPolicy<UserSessionPolicy>> resolved = policyQueryService.resolve(policyType, userId);
      if (resolved.isEmpty()) {
        metrics.recordResolution(AuthSessionPolicyResolutionSource.DEFAULT, true);
        return ResolvedUserSessionPolicy.defaults();
      }
      ResolvedPolicy<UserSessionPolicy> match = resolved.get();
      UserSessionPolicy content = match.content();
      UserSessionPolicyConstraints.validate(content.maxSessions(), content.absoluteTimeoutSeconds());
      metrics.recordResolution(AuthSessionPolicyResolutionSource.POLICY, true);
      return new ResolvedUserSessionPolicy(content, match.policyId(), match.version());
    } catch (AuthSessionPolicyException exception) {
      throw exception;
    } catch (RuntimeException exception) {
      throw unavailable(exception);
    }
  }

  private AuthSessionPolicyException unavailable(Throwable cause) {
    metrics.recordResolution(AuthSessionPolicyResolutionSource.POLICY, false);
    metrics.recordFailure(AuthSessionPolicyFailureOperation.RESOLVE);
    return new AuthSessionPolicyException(
        AuthSessionPolicyErrorCode.AUTH_SESSION_POLICY_UNAVAILABLE,
        "用户会话策略暂不可用，请稍后重试。",
        cause);
  }
}
