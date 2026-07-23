package org.congcong.algomentor.policy.model;

import java.util.Objects;

/** 一个用户或用户组主体引用。 */
public record PolicySubject(PolicySubjectType type, long id) {

  public PolicySubject {
    type = Objects.requireNonNull(type, "type must not be null");
    if (id < 1) {
      throw new IllegalArgumentException("subject id must be positive");
    }
  }
}
