package org.congcong.algomentor.policy.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.congcong.algomentor.common.admin.audit.AdminAuditAction;
import org.congcong.algomentor.common.admin.audit.AdminAuditMetadataKey;
import org.congcong.algomentor.common.admin.audit.AdminAuditTargetType;
import org.congcong.algomentor.common.admin.audit.AdminOperationAuditEvent;
import org.congcong.algomentor.common.admin.audit.AdminOperationAuditRecorder;
import org.congcong.algomentor.identity.group.repository.UserGroupRepository;
import org.congcong.algomentor.identity.model.AuthUserStatus;
import org.congcong.algomentor.identity.repository.IdentityUserRepository;
import org.congcong.algomentor.policy.cache.PolicySetCache;
import org.congcong.algomentor.policy.metrics.GenericPolicyMetrics;
import org.congcong.algomentor.policy.model.GenericPolicy;
import org.congcong.algomentor.policy.model.GenericPolicyStatus;
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
import org.springframework.transaction.annotation.Transactional;

/** 策略管理写入、并发控制、范围校验与运行时缓存失效的应用服务。 */
public class GenericPolicyManagementService {

  private final GenericPolicyRepository repository;
  private final GenericPolicyTypeRegistry typeRegistry;
  private final IdentityUserRepository identityUserRepository;
  private final UserGroupRepository userGroupRepository;
  private final PolicySetCache policySetCache;
  private final ObjectMapper objectMapper;
  private final AdminOperationAuditRecorder auditRecorder;
  private final GenericPolicyMetrics metrics;
  private final Clock clock;

  public GenericPolicyManagementService(
      GenericPolicyRepository repository,
      GenericPolicyTypeRegistry typeRegistry,
      IdentityUserRepository identityUserRepository,
      UserGroupRepository userGroupRepository,
      PolicySetCache policySetCache,
      ObjectMapper objectMapper,
      AdminOperationAuditRecorder auditRecorder,
      GenericPolicyMetrics metrics,
      Clock clock
  ) {
    this.repository = Objects.requireNonNull(repository, "repository must not be null");
    this.typeRegistry = Objects.requireNonNull(typeRegistry, "typeRegistry must not be null");
    this.identityUserRepository = Objects.requireNonNull(identityUserRepository, "identityUserRepository must not be null");
    this.userGroupRepository = Objects.requireNonNull(userGroupRepository, "userGroupRepository must not be null");
    this.policySetCache = Objects.requireNonNull(policySetCache, "policySetCache must not be null");
    this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
    this.auditRecorder = Objects.requireNonNull(auditRecorder, "auditRecorder must not be null");
    this.metrics = Objects.requireNonNull(metrics, "metrics must not be null");
    this.clock = Objects.requireNonNull(clock, "clock must not be null");
  }

  public GenericPolicyPage search(GenericPolicySearchQuery query) {
    Objects.requireNonNull(query, "query must not be null");
    String typeCode = requireRegisteredTypeCode(query.typeCode());
    if (query.status() == GenericPolicyStatus.DELETED) {
      throw invalidRequest("管理列表不支持 DELETED 状态。");
    }
    return repository.search(new GenericPolicySearchQuery(
        typeCode, query.status(), normalizeOptional(query.keyword()), query.page(), query.pageSize()));
  }

  public GenericPolicy get(long policyId) {
    requirePositive(policyId, "策略 ID 必须为正整数。");
    GenericPolicy policy = repository.findById(policyId).orElseThrow(() -> notFound(policyId));
    if (policy.status() == GenericPolicyStatus.DELETED) {
      throw notFound(policyId);
    }
    return policy;
  }

  @Transactional
  public GenericPolicy create(GenericPolicyCreateCommand command) {
    Objects.requireNonNull(command, "command must not be null");
    String typeCode = requireRegisteredTypeCode(command.typeCode());
    requirePositive(command.operatorUserId(), "操作人 ID 必须为正整数。");
    try {
      GenericPolicyStatus status = command.status() == null ? GenericPolicyStatus.DISABLED : command.status();
      rejectDeletedStatus(status);
      String name = normalizeName(command.name());
      String description = normalizeDescription(command.description());
      PolicySubjectRange subjectRange = validateSubjectRange(command.subjectRange());
      JsonNode content = validateContent(typeRegistry.require(typeCode), command.content());

      repository.acquireTypeLock(typeCode);
      List<GenericPolicy> livePolicies = repository.findLiveByTypeForUpdate(typeCode);
      if (livePolicies.size() >= GenericPolicyConstraints.MAX_POLICIES_PER_TYPE) {
        throw new GenericPolicyException(
            GenericPolicyErrorCode.POLICY_LIMIT_EXCEEDED,
            "同一策略类型最多保留 " + GenericPolicyConstraints.MAX_POLICIES_PER_TYPE + " 条未删除策略。");
      }
      int priority = livePolicies.isEmpty() ? 1 : livePolicies.get(livePolicies.size() - 1).priority() + 1;
      GenericPolicy created = repository.insert(
          new GenericPolicyDraft(
              typeCode, name, description, status, subjectRange, content, command.operatorUserId()),
          priority,
          Instant.now(clock));
      invalidate(typeCode);
      auditSuccess(command.operatorUserId(), AdminAuditAction.GENERIC_POLICY_CREATE, created);
      metrics.recordAdminWrite("create", "success");
      return created;
    } catch (GenericPolicyException exception) {
      auditFailure(command.operatorUserId(), AdminAuditAction.GENERIC_POLICY_CREATE, null, exception.code());
      metrics.recordAdminWrite("create", "failure");
      throw exception;
    }
  }

  @Transactional
  public GenericPolicy update(long policyId, GenericPolicyUpdateCommand command) {
    requirePositive(policyId, "策略 ID 必须为正整数。");
    Objects.requireNonNull(command, "command must not be null");
    requirePositive(command.operatorUserId(), "操作人 ID 必须为正整数。");
    requirePositive(command.version(), "策略版本必须为正整数。");
    try {
      GenericPolicy current = repository.findByIdForUpdate(policyId).orElseThrow(() -> notFound(policyId));
      if (current.status() == GenericPolicyStatus.DELETED) {
        throw notFound(policyId);
      }
      String typeCode = requireRegisteredTypeCode(current.typeCode());
      GenericPolicyStatus status = Objects.requireNonNull(command.status(), "status must not be null");
      rejectDeletedStatus(status);
      String name = normalizeName(command.name());
      String description = normalizeDescription(command.description());
      PolicySubjectRange subjectRange = validateSubjectRange(command.subjectRange());
      JsonNode content = validateContent(typeRegistry.require(typeCode), command.content());
      boolean updated = repository.update(
          new GenericPolicyUpdate(
              policyId,
              command.version(),
              name,
              description,
              status,
              subjectRange,
              content,
              command.operatorUserId()),
          Instant.now(clock));
      if (!updated) {
        throw new GenericPolicyException(
            GenericPolicyErrorCode.POLICY_VERSION_CONFLICT,
            "策略已被其他管理员修改，请刷新后重试。");
      }
      invalidate(typeCode);
      GenericPolicy updatedPolicy = repository.findById(policyId).orElseThrow(() -> notFound(policyId));
      auditSuccess(command.operatorUserId(), AdminAuditAction.GENERIC_POLICY_UPDATE, updatedPolicy);
      metrics.recordAdminWrite("update", "success");
      return updatedPolicy;
    } catch (GenericPolicyException exception) {
      auditFailure(command.operatorUserId(), AdminAuditAction.GENERIC_POLICY_UPDATE, policyId, exception.code());
      metrics.recordAdminWrite("update", "failure");
      throw exception;
    }
  }

  @Transactional
  public boolean delete(long policyId, long version, long operatorUserId) {
    requirePositive(policyId, "策略 ID 必须为正整数。");
    requirePositive(version, "策略版本必须为正整数。");
    requirePositive(operatorUserId, "操作人 ID 必须为正整数。");
    try {
      GenericPolicy snapshot = repository.findById(policyId).orElseThrow(() -> notFound(policyId));
      if (snapshot.status() == GenericPolicyStatus.DELETED) {
        return false;
      }
      String typeCode = requireRegisteredTypeCode(snapshot.typeCode());
      repository.acquireTypeLock(typeCode);
      GenericPolicy current = repository.findByIdForUpdate(policyId).orElseThrow(() -> notFound(policyId));
      if (current.status() == GenericPolicyStatus.DELETED) {
        return false;
      }
      if (!typeCode.equals(current.typeCode())) {
        throw new GenericPolicyException(
            GenericPolicyErrorCode.POLICY_VERSION_CONFLICT,
            "策略类型已变化，请刷新后重试。");
      }
      if (!repository.markDeleted(policyId, version, operatorUserId, Instant.now(clock))) {
        throw new GenericPolicyException(
            GenericPolicyErrorCode.POLICY_VERSION_CONFLICT,
            "策略已被其他管理员修改，请刷新后重试。");
      }
      renumberLivePolicies(typeCode, operatorUserId);
      invalidate(typeCode);
      auditSuccess(operatorUserId, AdminAuditAction.GENERIC_POLICY_DELETE, current);
      metrics.recordAdminWrite("delete", "success");
      return true;
    } catch (GenericPolicyException exception) {
      auditFailure(operatorUserId, AdminAuditAction.GENERIC_POLICY_DELETE, policyId, exception.code());
      metrics.recordAdminWrite("delete", "failure");
      throw exception;
    }
  }

  @Transactional
  public void reorder(String requestedTypeCode, GenericPolicyOrderCommand command) {
    Objects.requireNonNull(command, "command must not be null");
    requirePositive(command.operatorUserId(), "操作人 ID 必须为正整数。");
    String typeCode = requireRegisteredTypeCode(requestedTypeCode);
    try {
      repository.acquireTypeLock(typeCode);
      List<GenericPolicy> current = repository.findLiveByTypeForUpdate(typeCode);
      validateOrder(current, command);
      repository.moveLivePrioritiesToTemporaryRange(typeCode);
      Instant now = Instant.now(clock);
      for (int index = 0; index < command.policyIds().size(); index++) {
        long policyId = command.policyIds().get(index);
        try {
          repository.updatePriorityIfVersion(
              policyId, command.versions().get(policyId), index + 1, command.operatorUserId(), now);
        } catch (IllegalStateException exception) {
          throw new GenericPolicyException(
              GenericPolicyErrorCode.POLICY_ORDER_CONFLICT,
              "策略排序发生并发冲突，请刷新后重试。",
              exception);
        }
      }
      invalidate(typeCode);
      auditReorderSuccess(command.operatorUserId(), typeCode, current.size());
      metrics.recordAdminWrite("reorder", "success");
    } catch (GenericPolicyException exception) {
      auditReorderFailure(command.operatorUserId(), typeCode, exception.code());
      metrics.recordAdminWrite("reorder", "failure");
      throw exception;
    }
  }

  private void validateOrder(List<GenericPolicy> current, GenericPolicyOrderCommand command) {
    if (current.size() != command.policyIds().size()
        || current.size() != command.versions().size()) {
      throw new GenericPolicyException(
          GenericPolicyErrorCode.POLICY_ORDER_CONFLICT,
          "排序请求必须包含该类型全部未删除策略及其版本。");
    }
    Map<Long, GenericPolicy> byId = new HashMap<>();
    for (GenericPolicy policy : current) {
      byId.put(policy.id(), policy);
    }
    Set<Long> seen = new HashSet<>();
    for (Long policyId : command.policyIds()) {
      if (policyId == null || !seen.add(policyId)) {
        throw new GenericPolicyException(GenericPolicyErrorCode.POLICY_ORDER_CONFLICT, "排序策略 ID 不能重复或为空。");
      }
      GenericPolicy policy = byId.get(policyId);
      Long version = command.versions().get(policyId);
      if (policy == null || version == null || version < 1 || version != policy.version()) {
        throw new GenericPolicyException(
            GenericPolicyErrorCode.POLICY_ORDER_CONFLICT,
            "排序请求包含不存在、跨类型或版本已变化的策略。");
      }
    }
    if (!byId.keySet().equals(command.versions().keySet())) {
      throw new GenericPolicyException(
          GenericPolicyErrorCode.POLICY_ORDER_CONFLICT,
          "排序版本映射必须恰好对应全部未删除策略。");
    }
  }

  private void renumberLivePolicies(String typeCode, long operatorUserId) {
    List<GenericPolicy> remaining = repository.findLiveByTypeForUpdate(typeCode);
    repository.moveLivePrioritiesToTemporaryRange(typeCode);
    Instant now = Instant.now(clock);
    for (int index = 0; index < remaining.size(); index++) {
      repository.updatePriority(remaining.get(index).id(), index + 1, operatorUserId, now);
    }
  }

  private String requireRegisteredTypeCode(String typeCode) {
    return typeRegistry.require(typeCode).typeCode();
  }

  private PolicySubjectRange validateSubjectRange(PolicySubjectRange requestedRange) {
    if (requestedRange == null) {
      throw new GenericPolicyException(GenericPolicyErrorCode.POLICY_INVALID_SUBJECT_RANGE, "策略适用范围不能为空。");
    }
    final PolicySubjectRange range;
    try {
      range = requestedRange.normalized();
    } catch (IllegalArgumentException exception) {
      throw new GenericPolicyException(
          GenericPolicyErrorCode.POLICY_INVALID_SUBJECT_RANGE,
          "策略适用范围必须在全部用户与明确主体之间二选一。",
          exception);
    }
    if (range.subjects().size() > GenericPolicyConstraints.MAX_SUBJECTS_PER_POLICY) {
      throw new GenericPolicyException(
          GenericPolicyErrorCode.POLICY_INVALID_SUBJECT_RANGE,
          "单条策略最多包含 " + GenericPolicyConstraints.MAX_SUBJECTS_PER_POLICY + " 个主体。");
    }
    Instant now = Instant.now(clock);
    for (PolicySubject subject : range.subjects()) {
      if (subject.type() == PolicySubjectType.USER) {
        boolean valid = identityUserRepository.findUserById(subject.id())
            .map(user -> user.status() != AuthUserStatus.DELETED)
            .orElse(false);
        if (!valid) {
          throw new GenericPolicyException(
              GenericPolicyErrorCode.POLICY_SUBJECT_NOT_FOUND,
              "策略指定的用户主体不存在或已删除：" + subject.id());
        }
      } else if (subject.type() == PolicySubjectType.GROUP
          && userGroupRepository.findGroupById(subject.id(), now).isEmpty()) {
        throw new GenericPolicyException(
            GenericPolicyErrorCode.POLICY_SUBJECT_NOT_FOUND,
            "策略指定的用户组主体不存在或已删除：" + subject.id());
      }
    }
    return range;
  }

  private JsonNode validateContent(GenericPolicyType<?> policyType, JsonNode content) {
    if (content == null) {
      throw invalidRequest("策略内容必须是合法 JSON，允许 JSON null。");
    }
    try {
      if (objectMapper.writeValueAsBytes(content).length > GenericPolicyConstraints.MAX_CONTENT_BYTES) {
        throw new GenericPolicyException(
            GenericPolicyErrorCode.POLICY_CONTENT_TOO_LARGE,
            "策略内容不能超过 " + GenericPolicyConstraints.MAX_CONTENT_BYTES + " 字节。");
      }
      policyType.deserialize(objectMapper, content);
      return content.deepCopy();
    } catch (GenericPolicyException exception) {
      throw exception;
    } catch (IllegalArgumentException | JsonProcessingException exception) {
      throw new GenericPolicyException(
          GenericPolicyErrorCode.POLICY_CONTENT_COMPILE_FAILED,
          "策略内容无法转换为该类型注册的 Java 对象。",
          exception);
    }
  }

  private void invalidate(String typeCode) {
    try {
      policySetCache.invalidate(typeCode);
      metrics.recordCacheInvalidation(typeCode, "success");
    } catch (RuntimeException exception) {
      metrics.recordCacheInvalidation(typeCode, "failure");
      throw exception;
    }
  }

  private String normalizeName(String value) {
    String normalized = value == null ? "" : value.trim();
    if (normalized.isEmpty() || normalized.length() > GenericPolicyConstraints.MAX_NAME_LENGTH) {
      throw invalidRequest("策略名称不能为空且不能超过 " + GenericPolicyConstraints.MAX_NAME_LENGTH + " 个字符。");
    }
    return normalized;
  }

  private String normalizeDescription(String value) {
    if (value == null || value.isBlank()) {
      return null;
    }
    String normalized = value.trim();
    if (normalized.length() > GenericPolicyConstraints.MAX_DESCRIPTION_LENGTH) {
      throw invalidRequest("策略说明不能超过 " + GenericPolicyConstraints.MAX_DESCRIPTION_LENGTH + " 个字符。");
    }
    return normalized;
  }

  private String normalizeOptional(String value) {
    return value == null || value.isBlank() ? null : value.trim();
  }

  private void rejectDeletedStatus(GenericPolicyStatus status) {
    if (status == GenericPolicyStatus.DELETED) {
      throw invalidRequest("不能通过管理写接口指定 DELETED 状态。");
    }
  }

  private GenericPolicyException notFound(long policyId) {
    return new GenericPolicyException(GenericPolicyErrorCode.POLICY_NOT_FOUND, "策略不存在：" + policyId);
  }

  private GenericPolicyException invalidRequest(String message) {
    return new GenericPolicyException(GenericPolicyErrorCode.POLICY_INVALID_REQUEST, message);
  }

  private void requirePositive(long value, String message) {
    if (value < 1) {
      throw invalidRequest(message);
    }
  }

  private void auditSuccess(long operatorUserId, AdminAuditAction action, GenericPolicy policy) {
    auditRecorder.record(AdminOperationAuditEvent.success(
        operatorUserId,
        action,
        AdminAuditTargetType.GENERIC_POLICY,
        Long.toString(policy.id()),
        Map.of(
            AdminAuditMetadataKey.POLICY_TYPE_CODE, policy.typeCode(),
            AdminAuditMetadataKey.POLICY_STATUS, policy.status().name(),
            AdminAuditMetadataKey.SUBJECT_COUNT, policy.subjectRange().subjects().size())));
  }

  private void auditFailure(
      long operatorUserId,
      AdminAuditAction action,
      Long policyId,
      GenericPolicyErrorCode errorCode
  ) {
    if (operatorUserId > 0) {
      auditRecorder.record(AdminOperationAuditEvent.failure(
          operatorUserId,
          action,
          AdminAuditTargetType.GENERIC_POLICY,
          policyId == null || policyId < 1 ? null : Long.toString(policyId),
          errorCode.name()));
    }
  }

  private void auditReorderSuccess(long operatorUserId, String typeCode, int policyCount) {
    auditRecorder.record(AdminOperationAuditEvent.success(
        operatorUserId,
        AdminAuditAction.GENERIC_POLICY_REORDER,
        AdminAuditTargetType.GENERIC_POLICY,
        typeCode,
        Map.of(
            AdminAuditMetadataKey.POLICY_TYPE_CODE, typeCode,
            AdminAuditMetadataKey.SUBJECT_COUNT, policyCount)));
  }

  private void auditReorderFailure(long operatorUserId, String typeCode, GenericPolicyErrorCode errorCode) {
    auditRecorder.record(AdminOperationAuditEvent.failure(
        operatorUserId,
        AdminAuditAction.GENERIC_POLICY_REORDER,
        AdminAuditTargetType.GENERIC_POLICY,
        typeCode,
        errorCode.name()));
  }
}
