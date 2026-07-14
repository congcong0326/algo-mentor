package org.congcong.algomentor.auth.passwordreset;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class TemporaryPasswordGeneratorTest {

  @Test
  void generatesTwentyCharacterPasswordsWithEveryRequiredClass() {
    TemporaryPasswordGenerator generator = new TemporaryPasswordGenerator();

    for (int index = 0; index < 100; index++) {
      String password = generator.generate();
      assertThat(password).hasSize(TemporaryPasswordGenerator.DEFAULT_LENGTH);
      assertThat(password).matches(".*[A-Z].*");
      assertThat(password).matches(".*[a-z].*");
      assertThat(password).matches(".*[2-9].*");
      assertThat(password).matches(".*[!@#$%*\\-_+?].*");
      assertThat(password).doesNotContain("0", "1", "I", "O", "l", "o", "i");
    }
  }
}
