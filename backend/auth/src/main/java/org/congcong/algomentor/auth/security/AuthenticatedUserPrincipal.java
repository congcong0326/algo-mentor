package org.congcong.algomentor.auth.security;

public record AuthenticatedUserPrincipal(Long userId) {

  public AuthenticatedUserPrincipal {
    if (userId == null || userId < 1) {
      throw new IllegalArgumentException("userId must be a positive number.");
    }
  }
}
