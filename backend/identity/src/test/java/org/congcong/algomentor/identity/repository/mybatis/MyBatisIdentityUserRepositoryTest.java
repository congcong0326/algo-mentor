package org.congcong.algomentor.identity.repository.mybatis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;
import org.congcong.algomentor.identity.model.AuthRole;
import org.congcong.algomentor.identity.model.AuthUserStatus;
import org.congcong.algomentor.identity.model.IdentityUserPage;
import org.congcong.algomentor.identity.model.IdentityUserSearchQuery;
import org.congcong.algomentor.identity.repository.mybatis.model.AuthUserRow;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class MyBatisIdentityUserRepositoryTest {

  private static final Instant NOW = Instant.parse("2026-06-30T00:00:00Z");

  private FakeIdentityUserMapper mapper;
  private MyBatisIdentityUserRepository repository;

  @BeforeEach
  void setUp() {
    mapper = new FakeIdentityUserMapper();
    repository = new MyBatisIdentityUserRepository(mapper);
  }

  @Test
  void searchUsersDefaultsToActiveAndDisabledStatuses() {
    IdentityUserPage page = repository.searchUsers(new IdentityUserSearchQuery(1, 20, "user", null));

    assertThat(mapper.lastStatuses).containsExactly("ACTIVE", "DISABLED");
    assertThat(mapper.lastKeyword).isEqualTo("user");
    assertThat(page.items()).hasSize(1);
    assertThat(page.total()).isEqualTo(1);
  }

  @Test
  void softDeleteReturnsFalseWhenMapperDoesNotUpdateAnyRow() {
    mapper.updatedRows = 0;

    assertThat(repository.softDeleteUser(
        42L,
        1L,
        AuthUserStatus.ACTIVE,
        NOW)).isFalse();
  }

  @Test
  void updateLastLoginAtThrowsWhenMapperDoesNotUpdateAnyRow() {
    mapper.updatedRows = 0;

    assertThatThrownBy(() -> repository.updateLastLoginAt(42L, NOW))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("Cannot update identity user login time")
        .hasMessageContaining("42");
  }

  private static final class FakeIdentityUserMapper implements IdentityUserMapper {

    private List<String> lastStatuses = List.of();
    private String lastKeyword;
    private int updatedRows = 1;

    @Override
    public AuthUserRow findUserById(long userId) {
      return userRow(userId);
    }

    @Override
    public AuthUserRow findUserByEmailNormalized(String emailNormalized) {
      return userRow(1L);
    }

    @Override
    public int insertUser(AuthUserRow user) {
      user.setId(1L);
      return 1;
    }

    @Override
    public int insertUserRole(long userId, String role, Instant createdAt) {
      return 1;
    }

    @Override
    public List<String> findRoles(long userId) {
      return List.of(AuthRole.USER.name());
    }

    @Override
    public int updateLastLoginAt(long userId, Instant lastLoginAt) {
      return updatedRows;
    }

    @Override
    public List<AuthUserRow> searchUsers(String keyword, List<String> statuses, int limit, int offset) {
      lastKeyword = keyword;
      lastStatuses = statuses;
      return List.of(userRow(1L));
    }

    @Override
    public long countUsers(String keyword, List<String> statuses) {
      return 1;
    }

    @Override
    public int updateUserStatus(long userId, String expectedStatus, String status, Instant updatedAt) {
      return updatedRows;
    }

    @Override
    public int softDeleteUser(long userId, long operatorUserId, String expectedStatus, Instant deletedAt) {
      return updatedRows;
    }

    private AuthUserRow userRow(long userId) {
      return new AuthUserRow(
          userId,
          "user@example.com",
          "user@example.com",
          "User",
          null,
          AuthUserStatus.ACTIVE.name(),
          NOW,
          NOW,
          null,
          null,
          null);
    }
  }
}
