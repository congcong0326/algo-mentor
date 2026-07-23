package org.congcong.algomentor.identity.group.service;

import java.util.regex.Pattern;

public final class UserGroupConstraints {

  public static final int MAX_BATCH_MEMBER_COUNT = 100;
  public static final int MAX_NAME_LENGTH = 120;
  public static final int MAX_DESCRIPTION_LENGTH = 500;
  public static final Pattern CODE_PATTERN = Pattern.compile("^[A-Z][A-Z0-9_]{0,63}$");

  private UserGroupConstraints() {
  }
}
