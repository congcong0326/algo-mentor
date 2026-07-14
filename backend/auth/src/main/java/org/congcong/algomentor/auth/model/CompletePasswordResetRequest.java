package org.congcong.algomentor.auth.model;

public record CompletePasswordResetRequest(String newPassword, String confirmPassword) {
}
