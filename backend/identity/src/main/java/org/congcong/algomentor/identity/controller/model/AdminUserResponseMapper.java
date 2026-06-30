package org.congcong.algomentor.identity.controller.model;

import java.util.List;
import org.congcong.algomentor.identity.model.AuthRole;
import org.congcong.algomentor.identity.model.AuthUser;
import org.congcong.algomentor.identity.model.IdentityUserPage;
import org.congcong.algomentor.identity.repository.IdentityUserRepository;

public class AdminUserResponseMapper {

  private final IdentityUserRepository repository;

  public AdminUserResponseMapper(IdentityUserRepository repository) {
    this.repository = repository;
  }

  public AdminUserPageResponse toPageResponse(IdentityUserPage page) {
    List<AdminUserSummaryResponse> items = page.items()
        .stream()
        .map(this::toSummaryResponse)
        .toList();
    return new AdminUserPageResponse(items, page.total(), page.page(), page.pageSize());
  }

  public AdminUserSummaryResponse toSummaryResponse(AuthUser user) {
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
        user.lastLoginAt());
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
        user.deletedBy());
  }
}
