package org.congcong.algomentor.auth.security;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;

public class AuthenticatedDaoAuthenticationProvider extends DaoAuthenticationProvider {

  public AuthenticatedDaoAuthenticationProvider(
      PasswordEncoder passwordEncoder,
      PasswordUserDetailsService userDetailsService
  ) {
    super(passwordEncoder);
    setUserDetailsService(userDetailsService);
  }

  @Override
  protected Authentication createSuccessAuthentication(
      Object principal,
      Authentication authentication,
      UserDetails user
  ) {
    if (user instanceof AuthenticatedUserDetails authenticatedUserDetails) {
      return UsernamePasswordAuthenticationToken.authenticated(
          authenticatedUserDetails.principal(),
          authentication.getCredentials(),
          authenticatedUserDetails.getAuthorities());
    }
    return super.createSuccessAuthentication(principal, authentication, user);
  }
}
