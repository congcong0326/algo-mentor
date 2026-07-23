package org.congcong.algomentor.identity.group.repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.congcong.algomentor.identity.group.model.UserGroup;
import org.congcong.algomentor.identity.group.model.UserGroupMemberPage;
import org.congcong.algomentor.identity.group.model.UserGroupMemberSearchQuery;
import org.congcong.algomentor.identity.group.model.UserGroupMembership;
import org.congcong.algomentor.identity.group.model.UserGroupMembershipSummary;
import org.congcong.algomentor.identity.group.model.UserGroupPage;
import org.congcong.algomentor.identity.group.model.UserGroupSearchQuery;
import org.congcong.algomentor.identity.group.model.UserGroupStatus;
import org.congcong.algomentor.identity.model.AuthUser;

public interface UserGroupRepository {

  UserGroupPage searchGroups(UserGroupSearchQuery query, Instant now);

  Optional<UserGroup> findGroupById(long groupId, Instant now);

  Optional<UserGroup> lockGroupById(long groupId, Instant now);

  UserGroup createGroup(String code, String name, String description, Instant now);

  boolean updateGroup(long groupId, String name, String description, UserGroupStatus status, Instant updatedAt);

  int deleteAllMemberships(long groupId);

  boolean markGroupDeleted(long groupId, long operatorUserId, Instant deletedAt);

  UserGroupMemberPage searchActiveMembers(long groupId, UserGroupMemberSearchQuery query, Instant now);

  Map<Long, AuthUser> findUsersByIds(Collection<Long> userIds);

  Map<Long, UserGroupMembership> findMemberships(long groupId, Collection<Long> userIds);

  void upsertMembership(long groupId, long userId, Instant expiresAt, Instant now);

  boolean removeMembership(long groupId, long userId);

  /** 仅返回当前 ACTIVE 用户组的成员关系，保留 joinedAt/expiresAt 供缓存读取时过滤。 */
  List<UserGroupMembership> findRelationMembershipsByUserId(long userId);

  /** 用户组状态变更或删除前取得当前未过期成员，供逐用户缓存失效。 */
  List<Long> findCurrentMembershipUserIds(long groupId, Instant now);

  Map<Long, List<UserGroupMembershipSummary>> findActiveMembershipsByUserIds(
      Collection<Long> userIds,
      Instant now);
}
