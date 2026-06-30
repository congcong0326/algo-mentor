package org.congcong.algomentor.identity.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.congcong.algomentor.identity.event.IdentityEventPublisher;
import org.congcong.algomentor.identity.event.IdentityUserStatusChangedEvent;
import org.congcong.algomentor.identity.model.AuthRole;
import org.congcong.algomentor.identity.model.AuthUser;
import org.congcong.algomentor.identity.model.AuthUserStatus;
import org.congcong.algomentor.identity.model.IdentityUserPage;
import org.congcong.algomentor.identity.model.IdentityUserSearchQuery;
import org.congcong.algomentor.identity.repository.IdentityUserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class IdentityUserServiceTest {

  private static final Instant NOW = Instant.parse("2026-06-30T00:00:00Z");

  private FakeIdentityUserRepository repository;
  private RecordingIdentityEventPublisher publisher;
  private IdentityUserService service;

  @BeforeEach
  void setUp() {
    repository = new FakeIdentityUserRepository();
    publisher = new RecordingIdentityEventPublisher();
    service = new IdentityUserService(repository, publisher, Clock.fixed(NOW, ZoneOffset.UTC));
  }

  @Test
  void disableChangesActiveUserToDisabledAndPublishesEvent() {
    repository.save(user(42L, AuthUserStatus.ACTIVE));

    AuthUser updated = service.updateStatus(42L, 7L, AuthUserStatus.DISABLED);

    assertThat(updated.status()).isEqualTo(AuthUserStatus.DISABLED);
    assertThat(publisher.events).containsExactly(new IdentityUserStatusChangedEvent(
        42L,
        AuthUserStatus.ACTIVE,
        AuthUserStatus.DISABLED,
        7L,
        NOW));
  }

  @Test
  void restoreChangesDisabledUserToActiveAndPublishesEvent() {
    repository.save(user(42L, AuthUserStatus.DISABLED));

    AuthUser updated = service.updateStatus(42L, 7L, AuthUserStatus.ACTIVE);

    assertThat(updated.status()).isEqualTo(AuthUserStatus.ACTIVE);
    assertThat(publisher.events).containsExactly(new IdentityUserStatusChangedEvent(
        42L,
        AuthUserStatus.DISABLED,
        AuthUserStatus.ACTIVE,
        7L,
        NOW));
  }

  @Test
  void statusChangeEventUsesRequestedStatusWhenReloadSeesLaterConcurrentState() {
    repository.save(user(42L, AuthUserStatus.ACTIVE));
    repository.statusAfterSuccessfulUpdateReload = AuthUserStatus.ACTIVE;

    AuthUser updated = service.updateStatus(42L, 7L, AuthUserStatus.DISABLED);

    assertThat(updated.status()).isEqualTo(AuthUserStatus.ACTIVE);
    assertThat(publisher.events).containsExactly(new IdentityUserStatusChangedEvent(
        42L,
        AuthUserStatus.ACTIVE,
        AuthUserStatus.DISABLED,
        7L,
        NOW));
  }

  @Test
  void softDeleteActiveUserMarksDeletedAndPublishesEvent() {
    repository.save(user(42L, AuthUserStatus.ACTIVE));

    AuthUser updated = service.softDelete(42L, 7L);

    assertThat(updated.status()).isEqualTo(AuthUserStatus.DELETED);
    assertThat(updated.deletedAt()).isEqualTo(NOW);
    assertThat(updated.deletedBy()).isEqualTo(7L);
    assertThat(publisher.events).containsExactly(new IdentityUserStatusChangedEvent(
        42L,
        AuthUserStatus.ACTIVE,
        AuthUserStatus.DELETED,
        7L,
        NOW));
  }

  @Test
  void defaultSearchExcludesDeletedUsers() {
    repository.save(user(1L, AuthUserStatus.ACTIVE));
    repository.save(user(2L, AuthUserStatus.DISABLED));
    repository.save(user(3L, AuthUserStatus.DELETED));

    IdentityUserPage page = service.searchUsers(new IdentityUserSearchQuery(1, 20, "", null));

    assertThat(page.items())
        .extracting(AuthUser::id)
        .containsExactly(1L, 2L);
  }

  @Test
  void selfOperationIsRejected() {
    repository.save(user(42L, AuthUserStatus.ACTIVE));

    assertThatThrownBy(() -> service.updateStatus(42L, 42L, AuthUserStatus.DISABLED))
        .isInstanceOf(IdentityUserManagementException.class)
        .extracting("code")
        .isEqualTo(IdentityUserErrorCode.USER_SELF_OPERATION_FORBIDDEN);
  }

  @Test
  void deletedUserCannotBeDisabledRestoredOrDeletedAgain() {
    repository.save(user(42L, AuthUserStatus.DELETED));

    assertThatThrownBy(() -> service.updateStatus(42L, 7L, AuthUserStatus.DISABLED))
        .isInstanceOf(IdentityUserManagementException.class)
        .extracting("code")
        .isEqualTo(IdentityUserErrorCode.USER_STATUS_CONFLICT);
    assertThatThrownBy(() -> service.updateStatus(42L, 7L, AuthUserStatus.ACTIVE))
        .isInstanceOf(IdentityUserManagementException.class)
        .extracting("code")
        .isEqualTo(IdentityUserErrorCode.USER_STATUS_CONFLICT);
    assertThatThrownBy(() -> service.softDelete(42L, 7L))
        .isInstanceOf(IdentityUserManagementException.class)
        .extracting("code")
        .isEqualTo(IdentityUserErrorCode.USER_STATUS_CONFLICT);
    assertThat(publisher.events).isEmpty();
  }

  @Test
  void missingUserReturnsNotFound() {
    assertThatThrownBy(() -> service.getUser(42L))
        .isInstanceOf(IdentityUserManagementException.class)
        .extracting("code")
        .isEqualTo(IdentityUserErrorCode.USER_NOT_FOUND);
  }

  @Test
  void invalidRequestedStatusIsRejected() {
    repository.save(user(42L, AuthUserStatus.ACTIVE));

    assertThatThrownBy(() -> service.updateStatus(42L, 7L, null))
        .isInstanceOf(IdentityUserManagementException.class)
        .extracting("code")
        .isEqualTo(IdentityUserErrorCode.USER_STATUS_INVALID);
    assertThatThrownBy(() -> service.updateStatus(42L, 7L, AuthUserStatus.DELETED))
        .isInstanceOf(IdentityUserManagementException.class)
        .extracting("code")
        .isEqualTo(IdentityUserErrorCode.USER_STATUS_CONFLICT);
  }

  @Test
  void eventPublisherFailureDoesNotRevertIdentityState() {
    repository.save(user(42L, AuthUserStatus.ACTIVE));
    service = new IdentityUserService(
        repository,
        event -> {
          throw new IllegalStateException("listener failed");
        },
        Clock.fixed(NOW, ZoneOffset.UTC));

    AuthUser updated = service.updateStatus(42L, 7L, AuthUserStatus.DISABLED);

    assertThat(updated.status()).isEqualTo(AuthUserStatus.DISABLED);
    assertThat(repository.findUserById(42L))
        .get()
        .extracting(AuthUser::status)
        .isEqualTo(AuthUserStatus.DISABLED);
  }

  private static AuthUser user(long id, AuthUserStatus status) {
    return new AuthUser(
        id,
        "user" + id + "@example.com",
        "user" + id + "@example.com",
        "User " + id,
        null,
        status,
        NOW,
        NOW,
        null,
        status == AuthUserStatus.DELETED ? NOW : null,
        null);
  }

  private static final class RecordingIdentityEventPublisher implements IdentityEventPublisher {

    private final List<IdentityUserStatusChangedEvent> events = new ArrayList<>();

    @Override
    public void publish(IdentityUserStatusChangedEvent event) {
      events.add(event);
    }
  }

  private static final class FakeIdentityUserRepository implements IdentityUserRepository {

    private final Map<Long, AuthUser> users = new LinkedHashMap<>();
    private AuthUserStatus statusAfterSuccessfulUpdateReload;
    private boolean useStatusAfterSuccessfulUpdateReload;

    void save(AuthUser user) {
      users.put(user.id(), user);
    }

    @Override
    public Optional<AuthUser> findUserById(long userId) {
      AuthUser user = users.get(userId);
      if (!useStatusAfterSuccessfulUpdateReload || user == null) {
        return Optional.ofNullable(user);
      }
      useStatusAfterSuccessfulUpdateReload = false;
      AuthUserStatus reloadStatus = statusAfterSuccessfulUpdateReload;
      return Optional.of(new AuthUser(
          user.id(),
          user.email(),
          user.emailNormalized(),
          user.displayName(),
          user.avatarUrl(),
          reloadStatus,
          user.createdAt(),
          user.updatedAt(),
          user.lastLoginAt(),
          user.deletedAt(),
          user.deletedBy()));
    }

    @Override
    public Optional<AuthUser> findUserByEmailNormalized(String emailNormalized) {
      return users.values()
          .stream()
          .filter(user -> user.emailNormalized().equals(emailNormalized))
          .findFirst();
    }

    @Override
    public AuthUser createUser(
        String email,
        String emailNormalized,
        String displayName,
        String avatarUrl,
        AuthUserStatus status,
        Instant now) {
      AuthUser user = new AuthUser(
          (long) users.size() + 1,
          email,
          emailNormalized,
          displayName,
          avatarUrl,
          status,
          now,
          now,
          null,
          null,
          null);
      save(user);
      return user;
    }

    @Override
    public void addRole(long userId, AuthRole role) {
    }

    @Override
    public List<AuthRole> findRoles(long userId) {
      return List.of(AuthRole.USER);
    }

    @Override
    public AuthUser updateLastLoginAt(long userId, Instant lastLoginAt) {
      AuthUser user = users.get(userId);
      AuthUser updated = new AuthUser(
          user.id(),
          user.email(),
          user.emailNormalized(),
          user.displayName(),
          user.avatarUrl(),
          user.status(),
          user.createdAt(),
          lastLoginAt,
          lastLoginAt,
          user.deletedAt(),
          user.deletedBy());
      save(updated);
      return updated;
    }

    @Override
    public IdentityUserPage searchUsers(IdentityUserSearchQuery query) {
      EnumSet<AuthUserStatus> statuses = EnumSet.copyOf(query.effectiveStatuses());
      List<AuthUser> items = users.values()
          .stream()
          .filter(user -> statuses.contains(user.status()))
          .toList();
      return new IdentityUserPage(items, items.size(), query.page(), query.pageSize());
    }

    @Override
    public boolean updateUserStatus(
        long userId,
        AuthUserStatus expectedStatus,
        AuthUserStatus status,
        Instant updatedAt) {
      AuthUser user = users.get(userId);
      if (user == null || user.status() != expectedStatus) {
        return false;
      }
      save(new AuthUser(
          user.id(),
          user.email(),
          user.emailNormalized(),
          user.displayName(),
          user.avatarUrl(),
          status,
          user.createdAt(),
          updatedAt,
          user.lastLoginAt(),
          user.deletedAt(),
          user.deletedBy()));
      useStatusAfterSuccessfulUpdateReload = statusAfterSuccessfulUpdateReload != null;
      return true;
    }

    @Override
    public boolean softDeleteUser(
        long userId,
        long operatorUserId,
        AuthUserStatus expectedStatus,
        Instant deletedAt) {
      AuthUser user = users.get(userId);
      if (user == null || user.status() != expectedStatus) {
        return false;
      }
      save(new AuthUser(
          user.id(),
          user.email(),
          user.emailNormalized(),
          user.displayName(),
          user.avatarUrl(),
          AuthUserStatus.DELETED,
          user.createdAt(),
          deletedAt,
          user.lastLoginAt(),
          deletedAt,
          operatorUserId));
      return true;
    }
  }
}
