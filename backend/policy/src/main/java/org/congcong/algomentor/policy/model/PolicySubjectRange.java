package org.congcong.algomentor.policy.model;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

/** 原样持久化到 JSONB 的策略适用范围。 */
public record PolicySubjectRange(boolean allSubject, List<PolicySubject> subjects) {

  public PolicySubjectRange {
    subjects = subjects == null ? List.of() : List.copyOf(subjects);
  }

  /** 校验互斥规则并按首次出现顺序移除重复主体。 */
  public PolicySubjectRange normalized() {
    if (allSubject && !subjects.isEmpty()) {
      throw new IllegalArgumentException("allSubject=true requires an empty subjects list");
    }
    if (!allSubject && subjects.isEmpty()) {
      throw new IllegalArgumentException("subjects must not be empty when allSubject=false");
    }
    LinkedHashSet<PolicySubject> unique = new LinkedHashSet<>(subjects);
    return new PolicySubjectRange(allSubject, new ArrayList<>(unique));
  }
}
