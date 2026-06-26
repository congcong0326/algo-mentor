package org.congcong.algomentor.auth.model;

public record PasswordLoginRequest(
    String email,
    String password
) {
}
