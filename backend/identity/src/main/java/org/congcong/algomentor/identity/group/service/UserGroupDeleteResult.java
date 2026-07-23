package org.congcong.algomentor.identity.group.service;

public record UserGroupDeleteResult(
    long groupId,
    boolean deleted,
    int removedMembershipCount
) {
}
