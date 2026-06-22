package org.congcong.algomentor.auth.security;

import java.util.Optional;

public interface CurrentUserIdProvider {

  Optional<AuthenticatedUserPrincipal> currentUser();
}
