package org.congcong.algomentor.identity.group.repository.mybatis;

import java.time.Instant;
import java.util.List;
import org.apache.ibatis.annotations.Param;
import org.congcong.algomentor.identity.group.repository.mybatis.model.UserGroupMemberRow;
import org.congcong.algomentor.identity.group.repository.mybatis.model.UserGroupMembershipRow;
import org.congcong.algomentor.identity.group.repository.mybatis.model.UserGroupMembershipSummaryRow;
import org.congcong.algomentor.identity.group.repository.mybatis.model.UserGroupRow;
import org.congcong.algomentor.identity.repository.mybatis.model.AuthUserRow;

public interface UserGroupMapper {

  List<UserGroupRow> searchGroups(
      @Param("keyword") String keyword,
      @Param("statuses") List<String> statuses,
      @Param("now") Instant now,
      @Param("limit") int limit,
      @Param("offset") int offset);

  long countGroups(@Param("keyword") String keyword, @Param("statuses") List<String> statuses);

  UserGroupRow findGroupById(@Param("groupId") long groupId, @Param("now") Instant now);

  UserGroupRow lockGroupById(@Param("groupId") long groupId, @Param("now") Instant now);

  int insertGroup(UserGroupRow row);

  int updateGroup(
      @Param("groupId") long groupId,
      @Param("name") String name,
      @Param("description") String description,
      @Param("status") String status,
      @Param("updatedAt") Instant updatedAt);

  int deleteAllMemberships(@Param("groupId") long groupId);

  int markGroupDeleted(
      @Param("groupId") long groupId,
      @Param("operatorUserId") long operatorUserId,
      @Param("deletedAt") Instant deletedAt);

  List<UserGroupMemberRow> searchActiveMembers(
      @Param("groupId") long groupId,
      @Param("keyword") String keyword,
      @Param("now") Instant now,
      @Param("limit") int limit,
      @Param("offset") int offset);

  long countActiveMembers(
      @Param("groupId") long groupId,
      @Param("keyword") String keyword,
      @Param("now") Instant now);

  List<AuthUserRow> findUsersByIds(@Param("userIds") List<Long> userIds);

  List<UserGroupMembershipRow> findMemberships(
      @Param("groupId") long groupId,
      @Param("userIds") List<Long> userIds);

  int upsertMembership(
      @Param("groupId") long groupId,
      @Param("userId") long userId,
      @Param("expiresAt") Instant expiresAt,
      @Param("now") Instant now);

  int removeMembership(@Param("groupId") long groupId, @Param("userId") long userId);

  List<UserGroupMembershipSummaryRow> findActiveMembershipsByUserIds(
      @Param("userIds") List<Long> userIds,
      @Param("now") Instant now);
}
