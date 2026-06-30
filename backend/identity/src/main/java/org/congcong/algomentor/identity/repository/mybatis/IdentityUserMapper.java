package org.congcong.algomentor.identity.repository.mybatis;

import java.time.Instant;
import java.util.List;
import org.apache.ibatis.annotations.Param;
import org.congcong.algomentor.identity.repository.mybatis.model.AuthUserRow;

public interface IdentityUserMapper {

  AuthUserRow findUserById(@Param("userId") long userId);

  AuthUserRow findUserByEmailNormalized(@Param("emailNormalized") String emailNormalized);

  int insertUser(AuthUserRow user);

  int insertUserRole(@Param("userId") long userId, @Param("role") String role, @Param("createdAt") Instant createdAt);

  List<String> findRoles(@Param("userId") long userId);

  int updateLastLoginAt(@Param("userId") long userId, @Param("lastLoginAt") Instant lastLoginAt);

  List<AuthUserRow> searchUsers(
      @Param("keyword") String keyword,
      @Param("statuses") List<String> statuses,
      @Param("limit") int limit,
      @Param("offset") int offset);

  long countUsers(@Param("keyword") String keyword, @Param("statuses") List<String> statuses);

  int updateUserStatus(
      @Param("userId") long userId,
      @Param("expectedStatus") String expectedStatus,
      @Param("status") String status,
      @Param("updatedAt") Instant updatedAt);

  int softDeleteUser(
      @Param("userId") long userId,
      @Param("operatorUserId") long operatorUserId,
      @Param("expectedStatus") String expectedStatus,
      @Param("deletedAt") Instant deletedAt);
}
