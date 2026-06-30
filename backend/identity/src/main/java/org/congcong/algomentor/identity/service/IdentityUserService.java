package org.congcong.algomentor.identity.service;

import java.time.Clock;
import java.time.Instant;
import org.congcong.algomentor.identity.event.IdentityEventPublisher;
import org.congcong.algomentor.identity.event.IdentityUserStatusChangedEvent;
import org.congcong.algomentor.identity.model.AuthUser;
import org.congcong.algomentor.identity.model.AuthUserStatus;
import org.congcong.algomentor.identity.model.IdentityUserPage;
import org.congcong.algomentor.identity.model.IdentityUserSearchQuery;
import org.congcong.algomentor.identity.repository.IdentityUserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class IdentityUserService {

  private static final Logger log = LoggerFactory.getLogger(IdentityUserService.class);

  private final IdentityUserRepository repository;
  private final IdentityEventPublisher eventPublisher;
  private final Clock clock;

  public IdentityUserService(
      IdentityUserRepository repository,
      IdentityEventPublisher eventPublisher,
      Clock clock
  ) {
    this.repository = repository;
    this.eventPublisher = eventPublisher;
    this.clock = clock;
  }

  public IdentityUserPage searchUsers(IdentityUserSearchQuery query) {
    return repository.searchUsers(query);
  }

  public AuthUser getUser(long userId) {
    return repository.findUserById(userId)
        .orElseThrow(() -> new IdentityUserManagementException(
            IdentityUserErrorCode.USER_NOT_FOUND,
            "Identity user not found: " + userId));
  }

  public AuthUser updateStatus(long userId, long operatorUserId, AuthUserStatus requestedStatus) {
    rejectSelfOperation(userId, operatorUserId);
    if (requestedStatus == null) {
      throw new IdentityUserManagementException(
          IdentityUserErrorCode.USER_STATUS_INVALID,
          "Identity user requested status is required.");
    }
    if (requestedStatus == AuthUserStatus.DELETED) {
      throw new IdentityUserManagementException(
          IdentityUserErrorCode.USER_STATUS_CONFLICT,
          "Identity user cannot be changed to deleted by status update.");
    }

    AuthUser current = getUser(userId);
    AuthUserStatus previousStatus = current.status();
    if (previousStatus == AuthUserStatus.DELETED || previousStatus == requestedStatus) {
      throw new IdentityUserManagementException(
          IdentityUserErrorCode.USER_STATUS_CONFLICT,
          "Identity user status transition is not allowed: " + userId);
    }

    Instant now = Instant.now(clock);
    if (!repository.updateUserStatus(userId, previousStatus, requestedStatus, now)) {
      throw new IdentityUserManagementException(
          IdentityUserErrorCode.USER_STATUS_CONFLICT,
          "Identity user status was changed concurrently: " + userId);
    }

    AuthUser updated = getUser(userId);
    publishStatusChanged(userId, previousStatus, requestedStatus, operatorUserId, now);
    return updated;
  }

  public AuthUser softDelete(long userId, long operatorUserId) {
    rejectSelfOperation(userId, operatorUserId);
    AuthUser current = getUser(userId);
    AuthUserStatus previousStatus = current.status();
    if (previousStatus == AuthUserStatus.DELETED) {
      throw new IdentityUserManagementException(
          IdentityUserErrorCode.USER_STATUS_CONFLICT,
          "Identity user has already been deleted: " + userId);
    }

    Instant now = Instant.now(clock);
    if (!repository.softDeleteUser(userId, operatorUserId, previousStatus, now)) {
      throw new IdentityUserManagementException(
          IdentityUserErrorCode.USER_STATUS_CONFLICT,
          "Identity user status was changed concurrently: " + userId);
    }

    AuthUser updated = getUser(userId);
    publishStatusChanged(userId, previousStatus, AuthUserStatus.DELETED, operatorUserId, now);
    return updated;
  }

  private void rejectSelfOperation(long userId, long operatorUserId) {
    if (userId == operatorUserId) {
      throw new IdentityUserManagementException(
          IdentityUserErrorCode.USER_SELF_OPERATION_FORBIDDEN,
          "Identity user cannot operate on self: " + userId);
    }
  }

  private void publishStatusChanged(
      long userId,
      AuthUserStatus previousStatus,
      AuthUserStatus currentStatus,
      long operatorUserId,
      Instant occurredAt
  ) {
    try {
      eventPublisher.publish(new IdentityUserStatusChangedEvent(
          userId,
          previousStatus,
          currentStatus,
          operatorUserId,
          occurredAt));
    } catch (RuntimeException ex) {
      log.warn("Failed to publish identity user status changed event: userId={}", userId, ex);
    }
  }
}
