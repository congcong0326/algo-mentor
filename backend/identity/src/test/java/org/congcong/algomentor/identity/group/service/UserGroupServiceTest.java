package org.congcong.algomentor.identity.group.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.congcong.algomentor.common.admin.audit.AdminAuditAction;
import org.congcong.algomentor.common.admin.audit.AdminOperationAuditEvent;
import org.congcong.algomentor.common.admin.audit.AdminOperationAuditRecorder;
import org.congcong.algomentor.identity.group.model.UserGroup;
import org.congcong.algomentor.identity.group.model.UserGroupMembership;
import org.congcong.algomentor.identity.group.model.UserGroupStatus;
import org.congcong.algomentor.identity.group.repository.UserGroupRepository;
import org.congcong.algomentor.identity.model.AuthUser;
import org.congcong.algomentor.identity.model.AuthUserStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DuplicateKeyException;

class UserGroupServiceTest {

  private static final Instant NOW = Instant.parse("2026-07-23T05:00:00Z");
  private UserGroupRepository repository;
  private AdminOperationAuditRecorder auditRecorder;
  private UserGroupService service;

  @BeforeEach
  void setUp() {
    repository = mock(UserGroupRepository.class);
    auditRecorder = mock(AdminOperationAuditRecorder.class);
    service = new UserGroupService(
        repository,
        auditRecorder,
        Clock.fixed(NOW, ZoneOffset.UTC));
  }

  @Test
  void createNormalizesCodeAndText() {
    when(repository.createGroup("PRO_MEMBER", "专业会员", "说明", NOW))
        .thenReturn(group(8L, "PRO_MEMBER", UserGroupStatus.ACTIVE));

    UserGroup result = service.createGroup(" pro_member ", " 专业会员 ", " 说明 ", 99L);

    assertThat(result.id()).isEqualTo(8L);
    verify(repository).createGroup("PRO_MEMBER", "专业会员", "说明", NOW);
    ArgumentCaptor<AdminOperationAuditEvent> event = ArgumentCaptor.forClass(AdminOperationAuditEvent.class);
    verify(auditRecorder).record(event.capture());
    assertThat(event.getValue().action()).isEqualTo(AdminAuditAction.USER_GROUP_CREATE);
  }

  @Test
  void createMapsDeletedCodeReuseToStableConflict() {
    when(repository.createGroup(eq("PRO"), any(), any(), eq(NOW)))
        .thenThrow(new DuplicateKeyException("duplicate"));

    assertThatThrownBy(() -> service.createGroup("PRO", "专业会员", null, 99L))
        .isInstanceOf(UserGroupManagementException.class)
        .extracting(exception -> ((UserGroupManagementException) exception).code())
        .isEqualTo(UserGroupErrorCode.USER_GROUP_CODE_CONFLICT);
  }

  @Test
  void activeGroupCannotBeDeleted() {
    when(repository.lockGroupById(8L, NOW)).thenReturn(Optional.of(group(8L, "PRO", UserGroupStatus.ACTIVE)));

    assertThatThrownBy(() -> service.deleteGroup(8L, 99L))
        .isInstanceOf(UserGroupManagementException.class)
        .extracting(exception -> ((UserGroupManagementException) exception).code())
        .isEqualTo(UserGroupErrorCode.USER_GROUP_DELETE_REQUIRES_DISABLED);

    verify(repository, never()).deleteAllMemberships(8L);
  }

  @Test
  void disabledGroupDeleteClearsMembershipsAndWritesDeleteAudit() {
    when(repository.lockGroupById(8L, NOW)).thenReturn(Optional.of(group(8L, "PRO", UserGroupStatus.DISABLED)));
    when(repository.deleteAllMemberships(8L)).thenReturn(4);
    when(repository.markGroupDeleted(8L, 99L, NOW)).thenReturn(true);

    UserGroupDeleteResult result = service.deleteGroup(8L, 99L);

    assertThat(result).isEqualTo(new UserGroupDeleteResult(8L, true, 4));
    verify(repository).deleteAllMemberships(8L);
    verify(repository).markGroupDeleted(8L, 99L, NOW);
    ArgumentCaptor<AdminOperationAuditEvent> event = ArgumentCaptor.forClass(AdminOperationAuditEvent.class);
    verify(auditRecorder).record(event.capture());
    assertThat(event.getValue().action()).isEqualTo(AdminAuditAction.USER_GROUP_DELETE);
  }

  @Test
  void repeatedDeleteIsIdempotentAndDoesNotWriteAudit() {
    when(repository.lockGroupById(8L, NOW)).thenReturn(Optional.of(group(8L, "PRO", UserGroupStatus.DELETED)));

    assertThat(service.deleteGroup(8L, 99L)).isEqualTo(new UserGroupDeleteResult(8L, false, 0));
    verifyNoInteractions(auditRecorder);
  }

  @Test
  void addMembersDeduplicatesAndReturnsPartialBusinessResults() {
    when(repository.lockGroupById(8L, NOW)).thenReturn(Optional.of(group(8L, "PRO", UserGroupStatus.ACTIVE)));
    when(repository.findUsersByIds(List.of(42L, 43L, 44L))).thenReturn(Map.of(
        42L, user(42L, AuthUserStatus.ACTIVE),
        43L, user(43L, AuthUserStatus.DELETED)));
    when(repository.findMemberships(8L, List.of(42L, 43L, 44L))).thenReturn(Map.of(
        42L, new UserGroupMembership(42L, 8L, NOW.minusSeconds(60), null, NOW, NOW)));

    UserGroupMemberBatchResult result = service.addMembers(
        8L,
        List.of(42L, 42L, 43L, 44L),
        NOW.plusSeconds(3600),
        99L);

    assertThat(result.addedCount()).isZero();
    assertThat(result.updatedCount()).isEqualTo(1);
    assertThat(result.failedCount()).isEqualTo(2);
    assertThat(result.results()).extracting(UserGroupMemberAddResult::status)
        .containsExactly(
            UserGroupMemberAddStatus.UPDATED,
            UserGroupMemberAddStatus.USER_DELETED,
            UserGroupMemberAddStatus.USER_NOT_FOUND);
    verify(repository).upsertMembership(8L, 42L, NOW.plusSeconds(3600), NOW);
    verify(repository, never()).upsertMembership(eq(8L), eq(43L), any(), any());
  }

  @Test
  void invalidExpiryReturnsPerUserFailuresWithoutWrites() {
    when(repository.lockGroupById(8L, NOW)).thenReturn(Optional.of(group(8L, "PRO", UserGroupStatus.ACTIVE)));

    UserGroupMemberBatchResult result = service.addMembers(8L, List.of(42L, 43L), NOW, 99L);

    assertThat(result.failedCount()).isEqualTo(2);
    assertThat(result.results()).allMatch(item -> item.status() == UserGroupMemberAddStatus.INVALID_EXPIRY);
    verify(repository, never()).findUsersByIds(any());
    verify(repository, never()).upsertMembership(any(Long.class), any(Long.class), any(), any());
  }

  @Test
  void removeMissingMembershipIsIdempotent() {
    when(repository.lockGroupById(8L, NOW)).thenReturn(Optional.of(group(8L, "PRO", UserGroupStatus.DISABLED)));
    when(repository.removeMembership(8L, 42L)).thenReturn(false);

    assertThat(service.removeMember(8L, 42L, 99L))
        .isEqualTo(new UserGroupMemberRemovalResult(8L, 42L, false));
  }

  private UserGroup group(long id, String code, UserGroupStatus status) {
    return new UserGroup(id, code, "专业会员", null, status, 0, NOW, NOW, null, null);
  }

  private AuthUser user(long id, AuthUserStatus status) {
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
        status == AuthUserStatus.DELETED ? 99L : null);
  }
}
