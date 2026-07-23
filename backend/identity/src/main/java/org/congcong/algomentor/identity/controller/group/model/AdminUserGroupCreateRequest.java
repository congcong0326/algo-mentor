package org.congcong.algomentor.identity.controller.group.model;

public record AdminUserGroupCreateRequest(
    String code,
    String name,
    String description
) {
}
