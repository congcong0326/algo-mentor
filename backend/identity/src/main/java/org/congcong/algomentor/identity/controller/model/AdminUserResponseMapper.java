package org.congcong.algomentor.identity.controller.model;

import java.time.Clock;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import org.congcong.algomentor.identity.model.AuthRole;
import org.congcong.algomentor.identity.model.AuthUser;
import org.congcong.algomentor.identity.model.IdentityUserPage;
import org.congcong.algomentor.identity.group.model.UserGroupMembershipSummary;
import org.congcong.algomentor.identity.group.repository.UserGroupRepository;
import org.congcong.algomentor.identity.repository.IdentityUserRepository;

public class AdminUserResponseMapper {

  private final IdentityUserRepository repository;
  private final UserGroupRepository userGroupRepository;
  private final Clock clock;

  public AdminUserResponseMapper(IdentityUserRepository repository) {
    this(repository, null, Clock.systemUTC());
  }

  public AdminUserResponseMapper(
      IdentityUserRepository repository,
      UserGroupRepository userGroupRepository,
      Clock clock
  ) {
    this.repository = repository;
    this.userGroupRepository = userGroupRepository;
    this.clock = clock;
  }

  public AdminUserPageResponse toPageResponse(IdentityUserPage page) {
    Map<Long, List<UserGroupMembershipSummary>> memberships = activeMemberships(
        page.items().stream().map(AuthUser::id).toList());
    List<AdminUserSummaryResponse> items = page.items()
        .stream()
        .map(user -> toSummaryResponse(user, memberships.getOrDefault(user.id(), List.of())))
        .toList();
    return new AdminUserPageResponse(items, page.total(), page.page(), page.pageSize());
  }

  public AdminUserSummaryResponse toSummaryResponse(AuthUser user) {
    return toSummaryResponse(user, activeMemberships(List.of(user.id())).getOrDefault(user.id(), List.of()));
  }

  private AdminUserSummaryResponse toSummaryResponse(
      AuthUser user,
      List<UserGroupMembershipSummary> memberships
  ) {
    List<AuthRole> roles = repository.findRoles(user.id());
    return new AdminUserSummaryResponse(
        user.id(),
        user.email(),
        user.displayName(),
        user.avatarUrl(),
        user.status(),
        roles,
        user.createdAt(),
        user.updatedAt(),
        user.lastLoginAt(),
        memberships.stream()
            .map(membership -> new AdminUserGroupSummaryResponse(
                membership.groupId(), membership.code(), membership.name()))
            .toList());
  }

  public AdminUserDetailResponse toDetailResponse(AuthUser user) {
    List<AuthRole> roles = repository.findRoles(user.id());
    return new AdminUserDetailResponse(
        user.id(),
        user.email(),
        user.emailNormalized(),
        user.displayName(),
        user.avatarUrl(),
        user.status(),
        roles,
        user.createdAt(),
        user.updatedAt(),
        user.lastLoginAt(),
        user.deletedAt(),
        user.deletedBy(),
        activeMemberships(List.of(user.id())).getOrDefault(user.id(), List.of()).stream()
            .map(membership -> new AdminUserGroupMembershipResponse(
                membership.groupId(),
                membership.code(),
                membership.name(),
                membership.joinedAt(),
                membership.expiresAt()))
            .toList());
  }

  private Map<Long, List<UserGroupMembershipSummary>> activeMemberships(Collection<Long> userIds) {
    if (userGroupRepository == null || userIds == null || userIds.isEmpty()) {
      return Map.of();
    }
    Instant now = Instant.now(clock);
    return userGroupRepository.findActiveMembershipsByUserIds(userIds, now);
  }
}
