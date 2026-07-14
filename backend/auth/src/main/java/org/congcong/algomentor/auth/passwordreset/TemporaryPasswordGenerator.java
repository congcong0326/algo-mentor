package org.congcong.algomentor.auth.passwordreset;

import java.security.SecureRandom;

public class TemporaryPasswordGenerator {

  public static final int DEFAULT_LENGTH = 20;
  private static final char[] UPPERCASE = "ABCDEFGHJKLMNPQRSTUVWXYZ".toCharArray();
  private static final char[] LOWERCASE = "abcdefghjkmnpqrstuvwxyz".toCharArray();
  private static final char[] DIGITS = "23456789".toCharArray();
  private static final char[] SYMBOLS = "!@#$%*-_+?".toCharArray();
  private static final char[] ALL = concat(UPPERCASE, LOWERCASE, DIGITS, SYMBOLS);

  private final SecureRandom secureRandom;

  public TemporaryPasswordGenerator() {
    this(new SecureRandom());
  }

  public TemporaryPasswordGenerator(SecureRandom secureRandom) {
    this.secureRandom = secureRandom;
  }

  public String generate() {
    char[] password = new char[DEFAULT_LENGTH];
    password[0] = pick(UPPERCASE);
    password[1] = pick(LOWERCASE);
    password[2] = pick(DIGITS);
    password[3] = pick(SYMBOLS);
    for (int index = 4; index < password.length; index++) {
      password[index] = pick(ALL);
    }
    for (int index = password.length - 1; index > 0; index--) {
      int replacement = secureRandom.nextInt(index + 1);
      char current = password[index];
      password[index] = password[replacement];
      password[replacement] = current;
    }
    return new String(password);
  }

  private char pick(char[] characters) {
    return characters[secureRandom.nextInt(characters.length)];
  }

  private static char[] concat(char[]... sources) {
    int length = 0;
    for (char[] source : sources) {
      length += source.length;
    }
    char[] result = new char[length];
    int offset = 0;
    for (char[] source : sources) {
      System.arraycopy(source, 0, result, offset, source.length);
      offset += source.length;
    }
    return result;
  }
}
