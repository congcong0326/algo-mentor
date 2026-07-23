package org.congcong.algomentor.policy.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.congcong.algomentor.cache.caffeine.BypassSharedCacheRegionFactory;
import org.congcong.algomentor.cache.coherence.LocalSharedCacheInvalidationCoordinator;
import org.congcong.algomentor.cache.invalidation.SpringCacheInvalidationExecutor;
import org.congcong.algomentor.cache.registry.CacheRegionRegistry;
import org.congcong.algomentor.identity.group.relation.UserRelationProvider;
import org.congcong.algomentor.identity.group.relation.UserRelations;
import org.congcong.algomentor.policy.cache.GenericPolicyCacheProperties;
import org.congcong.algomentor.policy.cache.GenericPolicyCompiler;
import org.congcong.algomentor.policy.cache.PolicySetCache;
import org.congcong.algomentor.policy.metrics.GenericPolicyMetrics;
import org.congcong.algomentor.policy.model.GenericPolicy;
import org.congcong.algomentor.policy.model.GenericPolicyStatus;
import org.congcong.algomentor.policy.model.PolicyMatchSource;
import org.congcong.algomentor.policy.model.PolicySubject;
import org.congcong.algomentor.policy.model.PolicySubjectRange;
import org.congcong.algomentor.policy.model.PolicySubjectType;
import org.congcong.algomentor.policy.repository.GenericPolicyDraft;
import org.congcong.algomentor.policy.repository.GenericPolicyPage;
import org.congcong.algomentor.policy.repository.GenericPolicyRepository;
import org.congcong.algomentor.policy.repository.GenericPolicySearchQuery;
import org.congcong.algomentor.policy.repository.GenericPolicyUpdate;
import org.congcong.algomentor.policy.type.GenericPolicyType;
import org.congcong.algomentor.policy.type.GenericPolicyTypeRegistry;
import org.junit.jupiter.api.Test;

class DefaultGenericPolicyQueryServiceTest {

  private final ObjectMapper objectMapper = new ObjectMapper();

  @Test
  void selectsLowestGlobalPriorityWithoutGivingUserScopeExtraWeight() throws Exception {
    GenericPolicyType<String> policyType = GenericPolicyType.of("demo", String.class);
    DefaultGenericPolicyQueryService service = service(
        policyType,
        List.of(
            policy(1, 1, new PolicySubjectRange(true, List.of()), "all"),
            policy(2, 2, new PolicySubjectRange(false,
                List.of(new PolicySubject(PolicySubjectType.USER, 42))), "user")),
        userId -> new UserRelations(userId, java.util.Set.of()));

    Optional<org.congcong.algomentor.policy.model.ResolvedPolicy<String>> resolved = service.resolve(policyType, 42);

    assertThat(resolved).hasValueSatisfying(policy -> {
      assertThat(policy.content()).isEqualTo("all");
      assertThat(policy.matchSource()).isEqualTo(PolicyMatchSource.ALL);
      assertThat(policy.priority()).isEqualTo(1);
    });
  }

  @Test
  void returnsGroupMatchAndNeverTreatsBrokenEnabledContentAsMiss() throws Exception {
    GenericPolicyType<String> policyType = GenericPolicyType.of("demo", String.class);
    DefaultGenericPolicyQueryService service = service(
        policyType,
        List.of(policy(3, 1, new PolicySubjectRange(false,
            List.of(new PolicySubject(PolicySubjectType.GROUP, 9))), "group")),
        userId -> new UserRelations(userId, java.util.Set.of(9L)));

    assertThat(service.resolve(policyType, 42)).hasValueSatisfying(policy -> {
      assertThat(policy.content()).isEqualTo("group");
      assertThat(policy.matchSource()).isEqualTo(PolicyMatchSource.GROUP);
      assertThat(policy.matchedSubjectId()).isEqualTo(9L);
    });

    DefaultGenericPolicyQueryService broken = service(
        policyType,
        List.of(policy(4, 1, new PolicySubjectRange(true, List.of()), objectMapper.readTree("{}"))),
        userId -> new UserRelations(userId, java.util.Set.of()));
    assertThatThrownBy(() -> broken.resolve(policyType, 42))
        .isInstanceOf(PolicyResolutionException.class)
        .hasCauseInstanceOf(PolicyCompilationException.class);
  }

  private DefaultGenericPolicyQueryService service(
      GenericPolicyType<String> policyType,
      List<GenericPolicy> policies,
      UserRelationProvider relationProvider
  ) {
    GenericPolicyTypeRegistry registry = new GenericPolicyTypeRegistry(List.of(policyType));
    GenericPolicyMetrics metrics = new GenericPolicyMetrics(null);
    PolicySetCache cache = new PolicySetCache(
        new BypassSharedCacheRegionFactory(new CacheRegionRegistry()),
        new LocalSharedCacheInvalidationCoordinator(new SpringCacheInvalidationExecutor()),
        new GenericPolicyCacheProperties());
    GenericPolicyCompiler compiler = new GenericPolicyCompiler(
        new EnabledPolicyRepository(policies), registry, objectMapper, metrics);
    return new DefaultGenericPolicyQueryService(registry, cache, compiler, relationProvider, metrics);
  }

  private GenericPolicy policy(long id, int priority, PolicySubjectRange range, Object content) {
    return new GenericPolicy(
        id,
        "demo",
        "policy-" + id,
        null,
        GenericPolicyStatus.ENABLED,
        priority,
        range,
        objectMapper.valueToTree(content),
        1,
        1,
        Instant.parse("2026-07-23T00:00:00Z"),
        1,
        Instant.parse("2026-07-23T00:00:00Z"));
  }

  private static final class EnabledPolicyRepository implements GenericPolicyRepository {

    private final List<GenericPolicy> enabledPolicies;

    private EnabledPolicyRepository(List<GenericPolicy> enabledPolicies) {
      this.enabledPolicies = enabledPolicies;
    }

    @Override
    public List<GenericPolicy> findEnabledByType(String typeCode) {
      return enabledPolicies;
    }

    @Override
    public void acquireTypeLock(String typeCode) {
      throw unsupported();
    }

    @Override
    public List<GenericPolicy> findLiveByTypeForUpdate(String typeCode) {
      throw unsupported();
    }

    @Override
    public Optional<GenericPolicy> findById(long policyId) {
      throw unsupported();
    }

    @Override
    public Optional<GenericPolicy> findByIdForUpdate(long policyId) {
      throw unsupported();
    }

    @Override
    public GenericPolicyPage search(GenericPolicySearchQuery query) {
      throw unsupported();
    }

    @Override
    public GenericPolicy insert(GenericPolicyDraft draft, int priority, Instant now) {
      throw unsupported();
    }

    @Override
    public boolean update(GenericPolicyUpdate update, Instant now) {
      throw unsupported();
    }

    @Override
    public boolean markDeleted(long policyId, long version, long operatorUserId, Instant now) {
      throw unsupported();
    }

    @Override
    public void moveLivePrioritiesToTemporaryRange(String typeCode) {
      throw unsupported();
    }

    @Override
    public void updatePriority(long policyId, int priority, long operatorUserId, Instant now) {
      throw unsupported();
    }

    @Override
    public void updatePriorityIfVersion(
        long policyId,
        long version,
        int priority,
        long operatorUserId,
        Instant now
    ) {
      throw unsupported();
    }

    private UnsupportedOperationException unsupported() {
      return new UnsupportedOperationException();
    }
  }
}
