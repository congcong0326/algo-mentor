package org.congcong.algomentor.auth.model;

public record PasswordRegisterRequest(
    String email,
    String password,
    String displayName
) {
}
