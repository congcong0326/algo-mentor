package org.congcong.algomentor.policy.repository.mybatis;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.congcong.algomentor.policy.model.GenericPolicy;
import org.congcong.algomentor.policy.repository.GenericPolicyDraft;
import org.congcong.algomentor.policy.repository.GenericPolicyPage;
import org.congcong.algomentor.policy.repository.GenericPolicyRepository;
import org.congcong.algomentor.policy.repository.GenericPolicySearchQuery;
import org.congcong.algomentor.policy.repository.GenericPolicyUpdate;
import org.congcong.algomentor.policy.repository.mybatis.model.GenericPolicyRow;

/** MyBatis 实现；JSONB 解析保持在 repository 边界。 */
public final class MyBatisGenericPolicyRepository implements GenericPolicyRepository {

  private final GenericPolicyMapper mapper;
  private final ObjectMapper objectMapper;

  public MyBatisGenericPolicyRepository(GenericPolicyMapper mapper, ObjectMapper objectMapper) {
    this.mapper = mapper;
    this.objectMapper = objectMapper;
  }

  @Override
  public void acquireTypeLock(String typeCode) {
    mapper.acquireTypeLock(typeCode);
  }

  @Override
  public List<GenericPolicy> findLiveByTypeForUpdate(String typeCode) {
    return mapper.findLiveByTypeForUpdate(typeCode).stream().map(this::toDomain).toList();
  }

  @Override
  public List<GenericPolicy> findEnabledByType(String typeCode) {
    return mapper.findEnabledByType(typeCode).stream().map(this::toDomain).toList();
  }

  @Override
  public Optional<GenericPolicy> findById(long policyId) {
    return Optional.ofNullable(mapper.findById(policyId)).map(this::toDomain);
  }

  @Override
  public Optional<GenericPolicy> findByIdForUpdate(long policyId) {
    return Optional.ofNullable(mapper.findByIdForUpdate(policyId)).map(this::toDomain);
  }

  @Override
  public GenericPolicyPage search(GenericPolicySearchQuery query) {
    String status = query.status() == null ? null : query.status().name();
    List<GenericPolicy> items = mapper.search(
            query.typeCode(), status, query.keyword(), query.pageSize(), query.offset())
        .stream()
        .map(this::toDomain)
        .toList();
    return new GenericPolicyPage(
        items,
        mapper.count(query.typeCode(), status, query.keyword()),
        query.page(),
        query.pageSize());
  }

  @Override
  public GenericPolicy insert(GenericPolicyDraft draft, int priority, Instant now) {
    GenericPolicyRow row = new GenericPolicyRow(
        null,
        draft.typeCode(),
        draft.name(),
        draft.description(),
        draft.status().name(),
        priority,
        objectMapper.valueToTree(draft.subjectRange()),
        draft.content(),
        1,
        draft.operatorUserId(),
        now,
        draft.operatorUserId(),
        now);
    mapper.insert(row);
    return toDomain(row);
  }

  @Override
  public boolean update(GenericPolicyUpdate update, Instant now) {
    return mapper.update(
        update.policyId(),
        update.version(),
        update.name(),
        update.description(),
        update.status().name(),
        objectMapper.valueToTree(update.subjectRange()),
        update.content(),
        update.operatorUserId(),
        now) == 1;
  }

  @Override
  public boolean markDeleted(long policyId, long version, long operatorUserId, Instant now) {
    return mapper.markDeleted(policyId, version, operatorUserId, now) == 1;
  }

  @Override
  public void moveLivePrioritiesToTemporaryRange(String typeCode) {
    mapper.moveLivePrioritiesToTemporaryRange(typeCode);
  }

  @Override
  public void updatePriority(long policyId, int priority, long operatorUserId, Instant now) {
    mapper.updatePriority(policyId, priority, operatorUserId, now);
  }

  @Override
  public void updatePriorityIfVersion(
      long policyId,
      long version,
      int priority,
      long operatorUserId,
      Instant now
  ) {
    if (mapper.updatePriorityIfVersion(policyId, version, priority, operatorUserId, now) != 1) {
      throw new IllegalStateException("Policy priority update lost optimistic lock for id=" + policyId);
    }
  }

  private GenericPolicy toDomain(GenericPolicyRow row) {
    try {
      return row.toDomain(objectMapper.treeToValue(
          row.getSubjectRange(), org.congcong.algomentor.policy.model.PolicySubjectRange.class));
    } catch (Exception exception) {
      throw new IllegalStateException("Failed to parse persisted generic policy subject_range", exception);
    }
  }
}
