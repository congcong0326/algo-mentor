package org.congcong.algomentor.auth.model;

public record CurrentUserResponse(Long id) {

  public CurrentUserResponse {
    if (id == null || id < 1) {
      throw new IllegalArgumentException("id must be a positive number.");
    }
  }
}
