package org.congcong.algomentor.identity.group.service;

public record UserGroupMemberAddResult(
    long userId,
    UserGroupMemberAddStatus status
) {
}
