package org.congcong.algomentor.identity.group.service;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.congcong.algomentor.common.admin.audit.AdminAuditAction;
import org.congcong.algomentor.common.admin.audit.AdminAuditMetadataKey;
import org.congcong.algomentor.common.admin.audit.AdminAuditTargetType;
import org.congcong.algomentor.common.admin.audit.AdminOperationAuditEvent;
import org.congcong.algomentor.common.admin.audit.AdminOperationAuditRecorder;
import org.congcong.algomentor.identity.group.model.UserGroup;
import org.congcong.algomentor.identity.group.model.UserGroupMemberPage;
import org.congcong.algomentor.identity.group.model.UserGroupMemberSearchQuery;
import org.congcong.algomentor.identity.group.model.UserGroupMembership;
import org.congcong.algomentor.identity.group.model.UserGroupPage;
import org.congcong.algomentor.identity.group.model.UserGroupSearchQuery;
import org.congcong.algomentor.identity.group.model.UserGroupStatus;
import org.congcong.algomentor.identity.group.repository.UserGroupRepository;
import org.congcong.algomentor.identity.group.relation.NoopUserRelationCacheInvalidator;
import org.congcong.algomentor.identity.group.relation.UserRelationCacheInvalidator;
import org.congcong.algomentor.identity.model.AuthUser;
import org.congcong.algomentor.identity.model.AuthUserStatus;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.transaction.annotation.Transactional;

public class UserGroupService {

  private final UserGroupRepository repository;
  private final AdminOperationAuditRecorder auditRecorder;
  private final UserRelationCacheInvalidator userRelationCacheInvalidator;
  private final Clock clock;

  public UserGroupService(
      UserGroupRepository repository,
      AdminOperationAuditRecorder auditRecorder,
      Clock clock
  ) {
    this(repository, auditRecorder, new NoopUserRelationCacheInvalidator(), clock);
  }

  public UserGroupService(
      UserGroupRepository repository,
      AdminOperationAuditRecorder auditRecorder,
      UserRelationCacheInvalidator userRelationCacheInvalidator,
      Clock clock
  ) {
    this.repository = repository;
    this.auditRecorder = auditRecorder;
    this.userRelationCacheInvalidator = userRelationCacheInvalidator;
    this.clock = clock;
  }

  public UserGroupPage searchGroups(UserGroupSearchQuery query) {
    rejectDeletedStatus(query.status());
    return repository.searchGroups(query, Instant.now(clock));
  }

  public UserGroup getGroup(long groupId) {
    return repository.findGroupById(groupId, Instant.now(clock))
        .orElseThrow(() -> notFound(groupId));
  }

  public UserGroupMemberPage searchMembers(long groupId, UserGroupMemberSearchQuery query) {
    getGroup(groupId);
    return repository.searchActiveMembers(groupId, query, Instant.now(clock));
  }

  @Transactional
  public UserGroup createGroup(
      String code,
      String name,
      String description,
      long operatorUserId
  ) {
    try {
      String normalizedCode = normalizeCode(code);
      String normalizedName = normalizeName(name);
      String normalizedDescription = normalizeDescription(description);
      UserGroup created = repository.createGroup(
          normalizedCode,
          normalizedName,
          normalizedDescription,
          Instant.now(clock));
      auditRecorder.record(AdminOperationAuditEvent.success(
          operatorUserId,
          AdminAuditAction.USER_GROUP_CREATE,
          AdminAuditTargetType.USER_GROUP,
          Long.toString(created.id()),
          Map.of(AdminAuditMetadataKey.GROUP_CODE, created.code())));
      return created;
    } catch (DuplicateKeyException exception) {
      UserGroupManagementException conflict = new UserGroupManagementException(
          UserGroupErrorCode.USER_GROUP_CODE_CONFLICT,
          "用户组编码已存在，且删除后的编码也不能复用。",
          exception);
      recordFailure(operatorUserId, AdminAuditAction.USER_GROUP_CREATE, null, conflict.code());
      throw conflict;
    } catch (UserGroupManagementException exception) {
      recordFailure(operatorUserId, AdminAuditAction.USER_GROUP_CREATE, null, exception.code());
      throw exception;
    }
  }

  @Transactional
  public UserGroup updateGroup(
      long groupId,
      String name,
      String description,
      UserGroupStatus status,
      long operatorUserId
  ) {
    try {
      UserGroup current = lockExistingGroup(groupId);
      rejectDeletedStatus(status);
      if (status == null) {
        throw invalidRequest("用户组状态不能为空。");
      }
      String normalizedName = normalizeName(name);
      String normalizedDescription = normalizeDescription(description);
      Instant now = Instant.now(clock);
      List<Long> affectedUserIds = current.status() == status
          ? List.of()
          : repository.findCurrentMembershipUserIds(groupId, now);
      if (!repository.updateGroup(groupId, normalizedName, normalizedDescription, status, now)) {
        throw notFound(groupId);
      }
      invalidateRelations(affectedUserIds, "group_status");
      UserGroup updated = repository.findGroupById(groupId, now).orElseThrow(() -> notFound(groupId));
      auditRecorder.record(AdminOperationAuditEvent.success(
          operatorUserId,
          AdminAuditAction.USER_GROUP_UPDATE,
          AdminAuditTargetType.USER_GROUP,
          Long.toString(groupId),
          Map.of(AdminAuditMetadataKey.GROUP_CODE, current.code())));
      return updated;
    } catch (UserGroupManagementException exception) {
      recordFailure(operatorUserId, AdminAuditAction.USER_GROUP_UPDATE, groupId, exception.code());
      throw exception;
    }
  }

  @Transactional
  public UserGroupDeleteResult deleteGroup(long groupId, long operatorUserId) {
    try {
      UserGroup current = repository.lockGroupById(groupId, Instant.now(clock))
          .orElseThrow(() -> notFound(groupId));
      if (current.status() == UserGroupStatus.DELETED) {
        return new UserGroupDeleteResult(groupId, false, 0);
      }
      if (current.status() != UserGroupStatus.DISABLED) {
        throw new UserGroupManagementException(
            UserGroupErrorCode.USER_GROUP_DELETE_REQUIRES_DISABLED,
            "用户组必须先停用后才能删除。");
      }
      Instant now = Instant.now(clock);
      List<Long> affectedUserIds = repository.findCurrentMembershipUserIds(groupId, now);
      int removed = repository.deleteAllMemberships(groupId);
      if (!repository.markGroupDeleted(groupId, operatorUserId, now)) {
        throw new UserGroupManagementException(
            UserGroupErrorCode.USER_GROUP_DELETE_REQUIRES_DISABLED,
            "用户组状态已变化，请刷新后重试。");
      }
      invalidateRelations(affectedUserIds, "group_deleted");
      auditRecorder.record(AdminOperationAuditEvent.success(
          operatorUserId,
          AdminAuditAction.USER_GROUP_DELETE,
          AdminAuditTargetType.USER_GROUP,
          Long.toString(groupId),
          Map.of(
              AdminAuditMetadataKey.GROUP_CODE, current.code(),
              AdminAuditMetadataKey.REMOVED_MEMBERSHIP_COUNT, removed)));
      return new UserGroupDeleteResult(groupId, true, removed);
    } catch (UserGroupManagementException exception) {
      recordFailure(operatorUserId, AdminAuditAction.USER_GROUP_DELETE, groupId, exception.code());
      throw exception;
    }
  }

  @Transactional
  public UserGroupMemberBatchResult addMembers(
      long groupId,
      List<Long> requestedUserIds,
      Instant expiresAt,
      long operatorUserId
  ) {
    try {
      UserGroup group = lockExistingGroup(groupId);
      if (group.status() == UserGroupStatus.DISABLED) {
        throw new UserGroupManagementException(
            UserGroupErrorCode.USER_GROUP_DISABLED,
            "停用的用户组不能新增成员。");
      }
      List<Long> userIds = normalizeUserIds(requestedUserIds);
      Instant now = Instant.now(clock);
      if (expiresAt != null && !expiresAt.isAfter(now)) {
        List<UserGroupMemberAddResult> results = userIds.stream()
            .map(userId -> new UserGroupMemberAddResult(userId, UserGroupMemberAddStatus.INVALID_EXPIRY))
            .toList();
        UserGroupMemberBatchResult batch = UserGroupMemberBatchResult.from(results);
        recordMemberAddSuccess(operatorUserId, groupId, userIds.size(), batch);
        return batch;
      }

      Map<Long, AuthUser> users = repository.findUsersByIds(userIds);
      Map<Long, UserGroupMembership> memberships = repository.findMemberships(groupId, userIds);
      List<UserGroupMemberAddResult> results = new ArrayList<>(userIds.size());
      List<Long> affectedUserIds = new ArrayList<>();
      for (Long userId : userIds) {
        AuthUser user = users.get(userId);
        if (user == null) {
          results.add(new UserGroupMemberAddResult(userId, UserGroupMemberAddStatus.USER_NOT_FOUND));
          continue;
        }
        if (user.status() == AuthUserStatus.DELETED) {
          results.add(new UserGroupMemberAddResult(userId, UserGroupMemberAddStatus.USER_DELETED));
          continue;
        }
        boolean existed = memberships.containsKey(userId);
        repository.upsertMembership(groupId, userId, expiresAt, now);
        affectedUserIds.add(userId);
        results.add(new UserGroupMemberAddResult(
            userId,
            existed ? UserGroupMemberAddStatus.UPDATED : UserGroupMemberAddStatus.ADDED));
      }
      UserGroupMemberBatchResult batch = UserGroupMemberBatchResult.from(results);
      invalidateRelations(affectedUserIds, "membership_upsert");
      recordMemberAddSuccess(operatorUserId, groupId, userIds.size(), batch);
      return batch;
    } catch (UserGroupManagementException exception) {
      recordFailure(operatorUserId, AdminAuditAction.USER_GROUP_MEMBER_ADD, groupId, exception.code());
      throw exception;
    }
  }

  @Transactional
  public UserGroupMemberRemovalResult removeMember(long groupId, long userId, long operatorUserId) {
    try {
      lockExistingGroup(groupId);
      boolean removed = repository.removeMembership(groupId, userId);
      if (removed) {
        invalidateRelations(List.of(userId), "membership_remove");
      }
      auditRecorder.record(AdminOperationAuditEvent.success(
          operatorUserId,
          AdminAuditAction.USER_GROUP_MEMBER_REMOVE,
          AdminAuditTargetType.USER_GROUP,
          Long.toString(groupId),
          Map.of(
              AdminAuditMetadataKey.USER_ID, userId,
              AdminAuditMetadataKey.REMOVED, removed)));
      return new UserGroupMemberRemovalResult(groupId, userId, removed);
    } catch (UserGroupManagementException exception) {
      recordFailure(operatorUserId, AdminAuditAction.USER_GROUP_MEMBER_REMOVE, groupId, exception.code());
      throw exception;
    }
  }

  private UserGroup lockExistingGroup(long groupId) {
    UserGroup group = repository.lockGroupById(groupId, Instant.now(clock))
        .orElseThrow(() -> notFound(groupId));
    if (group.status() == UserGroupStatus.DELETED) {
      throw notFound(groupId);
    }
    return group;
  }

  private List<Long> normalizeUserIds(List<Long> requestedUserIds) {
    if (requestedUserIds == null || requestedUserIds.isEmpty()) {
      throw invalidRequest("至少需要选择一个用户。");
    }
    LinkedHashSet<Long> unique = new LinkedHashSet<>();
    for (Long userId : requestedUserIds) {
      if (userId == null || userId < 1) {
        throw invalidRequest("用户 ID 必须为正整数。");
      }
      unique.add(userId);
    }
    if (unique.size() > UserGroupConstraints.MAX_BATCH_MEMBER_COUNT) {
      throw new UserGroupManagementException(
          UserGroupErrorCode.USER_GROUP_BATCH_LIMIT_EXCEEDED,
          "单次最多添加 " + UserGroupConstraints.MAX_BATCH_MEMBER_COUNT + " 个用户。");
    }
    return List.copyOf(unique);
  }

  private String normalizeCode(String code) {
    String normalized = code == null ? "" : code.trim().toUpperCase(Locale.ROOT);
    if (!UserGroupConstraints.CODE_PATTERN.matcher(normalized).matches()) {
      throw new UserGroupManagementException(
          UserGroupErrorCode.USER_GROUP_INVALID_CODE,
          "用户组编码必须以大写字母开头，且只能包含大写字母、数字和下划线。");
    }
    return normalized;
  }

  private String normalizeName(String name) {
    String normalized = name == null ? "" : name.trim();
    if (normalized.isEmpty() || normalized.length() > UserGroupConstraints.MAX_NAME_LENGTH) {
      throw invalidRequest("用户组名称不能为空且不能超过 120 个字符。");
    }
    return normalized;
  }

  private String normalizeDescription(String description) {
    if (description == null || description.isBlank()) {
      return null;
    }
    String normalized = description.trim();
    if (normalized.length() > UserGroupConstraints.MAX_DESCRIPTION_LENGTH) {
      throw invalidRequest("用户组说明不能超过 500 个字符。");
    }
    return normalized;
  }

  private void rejectDeletedStatus(UserGroupStatus status) {
    if (status == UserGroupStatus.DELETED) {
      throw invalidRequest("不能通过查询或编辑接口使用 DELETED 状态。");
    }
  }

  private UserGroupManagementException notFound(long groupId) {
    return new UserGroupManagementException(
        UserGroupErrorCode.USER_GROUP_NOT_FOUND,
        "用户组不存在：" + groupId);
  }

  private UserGroupManagementException invalidRequest(String message) {
    return new UserGroupManagementException(UserGroupErrorCode.USER_GROUP_INVALID_REQUEST, message);
  }

  private void recordMemberAddSuccess(
      long operatorUserId,
      long groupId,
      int userCount,
      UserGroupMemberBatchResult result
  ) {
    auditRecorder.record(AdminOperationAuditEvent.success(
        operatorUserId,
        AdminAuditAction.USER_GROUP_MEMBER_ADD,
        AdminAuditTargetType.USER_GROUP,
        Long.toString(groupId),
        Map.of(
            AdminAuditMetadataKey.USER_COUNT, userCount,
            AdminAuditMetadataKey.ADDED_COUNT, result.addedCount(),
            AdminAuditMetadataKey.UPDATED_COUNT, result.updatedCount(),
            AdminAuditMetadataKey.FAILED_COUNT, result.failedCount())));
  }

  private void recordFailure(
      long operatorUserId,
      AdminAuditAction action,
      Long groupId,
      UserGroupErrorCode errorCode
  ) {
    auditRecorder.record(AdminOperationAuditEvent.failure(
        operatorUserId,
        action,
        AdminAuditTargetType.USER_GROUP,
        groupId == null || groupId < 1 ? null : Long.toString(groupId),
        errorCode.name()));
  }

  private void invalidateRelations(List<Long> userIds, String reason) {
    for (Long userId : userIds) {
      if (userId != null && userId > 0) {
        userRelationCacheInvalidator.invalidate(userId, reason);
      }
    }
  }
}
