package org.congcong.algomentor.policy.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.congcong.algomentor.policy.model.GenericPolicy;
import org.congcong.algomentor.policy.model.GenericPolicyStatus;

/** PostgreSQL 中通用策略单表的访问边界。 */
public interface GenericPolicyRepository {

  void acquireTypeLock(String typeCode);

  List<GenericPolicy> findLiveByTypeForUpdate(String typeCode);

  List<GenericPolicy> findEnabledByType(String typeCode);

  Optional<GenericPolicy> findById(long policyId);

  Optional<GenericPolicy> findByIdForUpdate(long policyId);

  GenericPolicyPage search(GenericPolicySearchQuery query);

  GenericPolicy insert(GenericPolicyDraft draft, int priority, Instant now);

  boolean update(GenericPolicyUpdate update, Instant now);

  boolean markDeleted(long policyId, long version, long operatorUserId, Instant now);

  void moveLivePrioritiesToTemporaryRange(String typeCode);

  void updatePriority(long policyId, int priority, long operatorUserId, Instant now);

  void updatePriorityIfVersion(long policyId, long version, int priority, long operatorUserId, Instant now);
}
