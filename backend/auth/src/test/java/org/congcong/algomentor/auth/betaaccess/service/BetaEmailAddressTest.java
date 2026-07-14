package org.congcong.algomentor.auth.betaaccess.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class BetaEmailAddressTest {

  @Test
  void normalizesWithTrimAndLocaleIndependentLowercase() {
    assertThat(BetaEmailAddress.normalize("  USER@Example.COM  ")).isEqualTo("user@example.com");
  }

  @Test
  void validatesLengthWhitespaceAndSingleAtSign() {
    assertThat(BetaEmailAddress.isValid("user@example.com")).isTrue();
    assertThat(BetaEmailAddress.isValid(" user@example.com ")).isTrue();
    assertThat(BetaEmailAddress.isValid("user name@example.com")).isFalse();
    assertThat(BetaEmailAddress.isValid("user@@example.com")).isFalse();
    assertThat(BetaEmailAddress.isValid("@example.com")).isFalse();
    assertThat(BetaEmailAddress.isValid("user@")).isFalse();
    assertThat(BetaEmailAddress.isValid("a".repeat(310) + "@example.com")).isFalse();
  }
}
