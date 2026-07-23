package org.congcong.algomentor.policy.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.util.List;
import org.junit.jupiter.api.Test;

class PolicySubjectRangeTest {

  @Test
  void removesDuplicateSubjectsWithoutChangingFirstOccurrenceOrder() {
    PolicySubject user = new PolicySubject(PolicySubjectType.USER, 7);
    PolicySubject group = new PolicySubject(PolicySubjectType.GROUP, 3);

    PolicySubjectRange normalized = new PolicySubjectRange(false, List.of(user, group, user)).normalized();

    assertThat(normalized.subjects()).containsExactly(user, group);
  }

  @Test
  void enforcesAllSubjectAndSubjectsMutualExclusion() {
    assertThatIllegalArgumentException().isThrownBy(() -> new PolicySubjectRange(
        true, List.of(new PolicySubject(PolicySubjectType.USER, 1))).normalized());
    assertThatIllegalArgumentException().isThrownBy(() -> new PolicySubjectRange(false, List.of()).normalized());
  }
}
