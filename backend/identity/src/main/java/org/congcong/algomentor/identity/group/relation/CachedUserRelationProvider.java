package org.congcong.algomentor.identity.group.relation;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.congcong.algomentor.identity.group.repository.UserGroupRepository;
import org.congcong.algomentor.identity.model.AuthUserStatus;
import org.congcong.algomentor.identity.repository.IdentityUserRepository;

/** 从身份事实加载关系并在每次读取时应用成员有效期的用户关系提供者。 */
public final class CachedUserRelationProvider implements UserRelationProvider {

  private final IdentityUserRepository identityUserRepository;
  private final UserGroupRepository userGroupRepository;
  private final UserRelationCache cache;
  private final Clock clock;

  public CachedUserRelationProvider(
      IdentityUserRepository identityUserRepository,
      UserGroupRepository userGroupRepository,
      UserRelationCache cache,
      Clock clock
  ) {
    this.identityUserRepository = Objects.requireNonNull(identityUserRepository, "identityUserRepository must not be null");
    this.userGroupRepository = Objects.requireNonNull(userGroupRepository, "userGroupRepository must not be null");
    this.cache = Objects.requireNonNull(cache, "cache must not be null");
    this.clock = Objects.requireNonNull(clock, "clock must not be null");
  }

  @Override
  public UserRelations getRelations(long userId) {
    if (userId < 1) {
      throw new IllegalArgumentException("userId must be positive");
    }
    CachedUserRelations cached = cache.get(userId, () -> load(userId));
    Instant now = Instant.now(clock);
    Set<Long> activeGroupIds = cached.memberships().stream()
        .filter(membership -> membership.activeAt(now))
        .map(CachedGroupMembership::groupId)
        .collect(java.util.stream.Collectors.toUnmodifiableSet());
    return new UserRelations(userId, activeGroupIds);
  }

  private CachedUserRelations load(long userId) {
    boolean exists = identityUserRepository.findUserById(userId)
        .map(user -> user.status() != AuthUserStatus.DELETED)
        .orElse(false);
    if (!exists) {
      throw new IllegalStateException("Identity user does not exist or has been deleted: " + userId);
    }
    List<CachedGroupMembership> memberships = userGroupRepository.findRelationMembershipsByUserId(userId)
        .stream()
        .map(membership -> new CachedGroupMembership(
            membership.groupId(), membership.joinedAt(), membership.expiresAt()))
        .toList();
    return new CachedUserRelations(userId, memberships);
  }
}
