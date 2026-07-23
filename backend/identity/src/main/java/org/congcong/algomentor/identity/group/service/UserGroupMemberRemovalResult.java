package org.congcong.algomentor.identity.group.service;

public record UserGroupMemberRemovalResult(
    long groupId,
    long userId,
    boolean removed
) {
}
