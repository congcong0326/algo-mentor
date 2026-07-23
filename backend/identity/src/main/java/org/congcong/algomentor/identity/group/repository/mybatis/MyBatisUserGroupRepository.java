package org.congcong.algomentor.identity.group.repository.mybatis;

import java.time.Instant;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.congcong.algomentor.identity.group.model.UserGroup;
import org.congcong.algomentor.identity.group.model.UserGroupMemberPage;
import org.congcong.algomentor.identity.group.model.UserGroupMemberSearchQuery;
import org.congcong.algomentor.identity.group.model.UserGroupMembership;
import org.congcong.algomentor.identity.group.model.UserGroupMembershipSummary;
import org.congcong.algomentor.identity.group.model.UserGroupPage;
import org.congcong.algomentor.identity.group.model.UserGroupSearchQuery;
import org.congcong.algomentor.identity.group.model.UserGroupStatus;
import org.congcong.algomentor.identity.group.repository.UserGroupRepository;
import org.congcong.algomentor.identity.group.repository.mybatis.model.UserGroupMemberRow;
import org.congcong.algomentor.identity.group.repository.mybatis.model.UserGroupMembershipRow;
import org.congcong.algomentor.identity.group.repository.mybatis.model.UserGroupMembershipSummaryRow;
import org.congcong.algomentor.identity.group.repository.mybatis.model.UserGroupRow;
import org.congcong.algomentor.identity.model.AuthUser;
import org.congcong.algomentor.identity.repository.mybatis.model.AuthUserRow;

public class MyBatisUserGroupRepository implements UserGroupRepository {

  private final UserGroupMapper mapper;

  public MyBatisUserGroupRepository(UserGroupMapper mapper) {
    this.mapper = mapper;
  }

  @Override
  public UserGroupPage searchGroups(UserGroupSearchQuery query, Instant now) {
    List<String> statuses = query.effectiveStatuses().stream().map(UserGroupStatus::name).toList();
    List<UserGroup> groups = mapper.searchGroups(
            query.keyword(), statuses, now, query.pageSize(), query.offset())
        .stream()
        .map(UserGroupRow::toDomain)
        .toList();
    return new UserGroupPage(
        groups,
        mapper.countGroups(query.keyword(), statuses),
        query.page(),
        query.pageSize());
  }

  @Override
  public Optional<UserGroup> findGroupById(long groupId, Instant now) {
    return Optional.ofNullable(mapper.findGroupById(groupId, now)).map(UserGroupRow::toDomain);
  }

  @Override
  public Optional<UserGroup> lockGroupById(long groupId, Instant now) {
    return Optional.ofNullable(mapper.lockGroupById(groupId, now)).map(UserGroupRow::toDomain);
  }

  @Override
  public UserGroup createGroup(String code, String name, String description, Instant now) {
    UserGroupRow row = new UserGroupRow(
        null, code, name, description, UserGroupStatus.ACTIVE.name(), 0, now, now, null, null);
    mapper.insertGroup(row);
    return row.toDomain();
  }

  @Override
  public boolean updateGroup(
      long groupId,
      String name,
      String description,
      UserGroupStatus status,
      Instant updatedAt
  ) {
    return mapper.updateGroup(groupId, name, description, status.name(), updatedAt) == 1;
  }

  @Override
  public int deleteAllMemberships(long groupId) {
    return mapper.deleteAllMemberships(groupId);
  }

  @Override
  public boolean markGroupDeleted(long groupId, long operatorUserId, Instant deletedAt) {
    return mapper.markGroupDeleted(groupId, operatorUserId, deletedAt) == 1;
  }

  @Override
  public UserGroupMemberPage searchActiveMembers(
      long groupId,
      UserGroupMemberSearchQuery query,
      Instant now
  ) {
    return new UserGroupMemberPage(
        mapper.searchActiveMembers(groupId, query.keyword(), now, query.pageSize(), query.offset())
            .stream()
            .map(UserGroupMemberRow::toDomain)
            .toList(),
        mapper.countActiveMembers(groupId, query.keyword(), now),
        query.page(),
        query.pageSize());
  }

  @Override
  public Map<Long, AuthUser> findUsersByIds(Collection<Long> userIds) {
    List<Long> ids = normalizedIds(userIds);
    if (ids.isEmpty()) {
      return Map.of();
    }
    return mapper.findUsersByIds(ids).stream()
        .map(AuthUserRow::toDomain)
        .collect(Collectors.toMap(AuthUser::id, user -> user, (left, right) -> left, LinkedHashMap::new));
  }

  @Override
  public Map<Long, UserGroupMembership> findMemberships(long groupId, Collection<Long> userIds) {
    List<Long> ids = normalizedIds(userIds);
    if (ids.isEmpty()) {
      return Map.of();
    }
    return mapper.findMemberships(groupId, ids).stream()
        .map(UserGroupMembershipRow::toDomain)
        .collect(Collectors.toMap(
            UserGroupMembership::userId,
            membership -> membership,
            (left, right) -> left,
            LinkedHashMap::new));
  }

  @Override
  public void upsertMembership(long groupId, long userId, Instant expiresAt, Instant now) {
    mapper.upsertMembership(groupId, userId, expiresAt, now);
  }

  @Override
  public boolean removeMembership(long groupId, long userId) {
    return mapper.removeMembership(groupId, userId) == 1;
  }

  @Override
  public List<UserGroupMembership> findRelationMembershipsByUserId(long userId) {
    if (userId < 1) {
      return List.of();
    }
    return mapper.findRelationMembershipsByUserId(userId).stream()
        .map(UserGroupMembershipRow::toDomain)
        .toList();
  }

  @Override
  public List<Long> findCurrentMembershipUserIds(long groupId, Instant now) {
    if (groupId < 1) {
      return List.of();
    }
    return mapper.findCurrentMembershipUserIds(groupId, now).stream().distinct().toList();
  }

  @Override
  public Map<Long, List<UserGroupMembershipSummary>> findActiveMembershipsByUserIds(
      Collection<Long> userIds,
      Instant now
  ) {
    List<Long> ids = normalizedIds(userIds);
    if (ids.isEmpty()) {
      return Map.of();
    }
    return mapper.findActiveMembershipsByUserIds(ids, now).stream()
        .map(UserGroupMembershipSummaryRow::toDomain)
        .collect(Collectors.groupingBy(
            UserGroupMembershipSummary::userId,
            LinkedHashMap::new,
            Collectors.toList()));
  }

  private List<Long> normalizedIds(Collection<Long> userIds) {
    if (userIds == null || userIds.isEmpty()) {
      return List.of();
    }
    return userIds.stream().filter(id -> id != null && id > 0).distinct().toList();
  }
}
