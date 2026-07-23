package org.congcong.algomentor.auth.session.policy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Optional;
import org.congcong.algomentor.policy.model.PolicyMatchSource;
import org.congcong.algomentor.policy.model.ResolvedPolicy;
import org.congcong.algomentor.policy.service.GenericPolicyQueryService;
import org.congcong.algomentor.policy.type.GenericPolicyType;
import org.junit.jupiter.api.Test;

class AuthSessionPolicyResolverTest {

  private final GenericPolicyType<UserSessionPolicy> policyType =
      GenericPolicyType.of(AuthSessionPolicyConstants.TYPE_CODE, UserSessionPolicy.class);

  @Test
  void usesCodeDefaultsOnlyWhenNoPolicyMatches() {
    GenericPolicyQueryService queryService = mock(GenericPolicyQueryService.class);
    when(queryService.resolve(policyType, 42L)).thenReturn(Optional.empty());
    CapturingMetrics metrics = new CapturingMetrics();

    ResolvedUserSessionPolicy resolved = new AuthSessionPolicyResolver(queryService, policyType, metrics)
        .resolve(42L);

    assertThat(resolved).isEqualTo(ResolvedUserSessionPolicy.defaults());
    assertThat(metrics.defaultSuccesses).isEqualTo(1);
    assertThat(metrics.failures).isEmpty();
  }

  @Test
  void keepsPolicyIdentityAndVersionInResolvedSnapshot() {
    GenericPolicyQueryService queryService = mock(GenericPolicyQueryService.class);
    UserSessionPolicy content = new UserSessionPolicy(5, 7_200L);
    when(queryService.resolve(policyType, 42L)).thenReturn(Optional.of(new ResolvedPolicy<>(
        7L,
        AuthSessionPolicyConstants.TYPE_CODE,
        "internal-admins",
        2,
        content,
        PolicyMatchSource.GROUP,
        9L,
        3L)));

    ResolvedUserSessionPolicy resolved = new AuthSessionPolicyResolver(
        queryService, policyType, new CapturingMetrics()).resolve(42L);

    assertThat(resolved.content()).isEqualTo(content);
    assertThat(resolved.policyId()).isEqualTo(7L);
    assertThat(resolved.policyVersion()).isEqualTo(3L);
  }

  @Test
  void doesNotSilentlyFallBackWhenGenericPolicyResolutionFails() {
    GenericPolicyQueryService queryService = mock(GenericPolicyQueryService.class);
    when(queryService.resolve(policyType, 42L)).thenThrow(new IllegalStateException("cache unavailable"));
    CapturingMetrics metrics = new CapturingMetrics();

    assertThatThrownBy(() -> new AuthSessionPolicyResolver(queryService, policyType, metrics).resolve(42L))
        .isInstanceOf(AuthSessionPolicyException.class)
        .extracting(exception -> ((AuthSessionPolicyException) exception).code())
        .isEqualTo(AuthSessionPolicyErrorCode.AUTH_SESSION_POLICY_UNAVAILABLE);
    assertThat(metrics.policyFailures).isEqualTo(1);
    assertThat(metrics.failures).containsExactly(AuthSessionPolicyFailureOperation.RESOLVE);
  }

  private static final class CapturingMetrics implements AuthSessionPolicyMetrics {

    private int defaultSuccesses;
    private int policyFailures;
    private final java.util.List<AuthSessionPolicyFailureOperation> failures = new java.util.ArrayList<>();

    @Override
    public void recordResolution(AuthSessionPolicyResolutionSource source, boolean success) {
      if (source == AuthSessionPolicyResolutionSource.DEFAULT && success) {
        defaultSuccesses++;
      }
      if (source == AuthSessionPolicyResolutionSource.POLICY && !success) {
        policyFailures++;
      }
    }

    @Override
    public void recordEvictions(int count) {
    }

    @Override
    public void recordAbsoluteExpiration() {
    }

    @Override
    public void recordFailure(AuthSessionPolicyFailureOperation operation) {
      failures.add(operation);
    }
  }
}
