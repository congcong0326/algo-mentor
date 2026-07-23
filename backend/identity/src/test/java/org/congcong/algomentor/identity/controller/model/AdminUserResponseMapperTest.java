package org.congcong.algomentor.identity.controller.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import org.congcong.algomentor.identity.group.model.UserGroupMembershipSummary;
import org.congcong.algomentor.identity.group.repository.UserGroupRepository;
import org.congcong.algomentor.identity.model.AuthRole;
import org.congcong.algomentor.identity.model.AuthUser;
import org.congcong.algomentor.identity.model.AuthUserStatus;
import org.congcong.algomentor.identity.model.IdentityUserPage;
import org.congcong.algomentor.identity.repository.IdentityUserRepository;
import org.junit.jupiter.api.Test;

class AdminUserResponseMapperTest {

  private static final Instant NOW = Instant.parse("2026-07-23T05:00:00Z");

  @Test
  void pageLoadsGroupSummariesInOneBatch() {
    IdentityUserRepository userRepository = mock(IdentityUserRepository.class);
    UserGroupRepository groupRepository = mock(UserGroupRepository.class);
    when(userRepository.findRoles(1L)).thenReturn(List.of(AuthRole.USER));
    when(userRepository.findRoles(2L)).thenReturn(List.of(AuthRole.USER));
    when(groupRepository.findActiveMembershipsByUserIds(List.of(1L, 2L), NOW)).thenReturn(Map.of(
        1L, List.of(new UserGroupMembershipSummary(1L, 8L, "PRO", "专业会员", NOW, null))));
    AdminUserResponseMapper mapper = new AdminUserResponseMapper(
        userRepository,
        groupRepository,
        Clock.fixed(NOW, ZoneOffset.UTC));

    AdminUserPageResponse response = mapper.toPageResponse(new IdentityUserPage(
        List.of(user(1L), user(2L)), 2, 1, 20));

    assertThat(response.items().get(0).groups()).extracting(AdminUserGroupSummaryResponse::code)
        .containsExactly("PRO");
    assertThat(response.items().get(1).groups()).isEmpty();
    verify(groupRepository).findActiveMembershipsByUserIds(List.of(1L, 2L), NOW);
  }

  private AuthUser user(long id) {
    return new AuthUser(
        id,
        "user" + id + "@example.com",
        "user" + id + "@example.com",
        "User " + id,
        null,
        AuthUserStatus.ACTIVE,
        NOW,
        NOW,
        null,
        null,
        null);
  }
}
