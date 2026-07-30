package org.congcong.algomentor.mentor.application.practice;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PracticeCoachStyleTest {

  @Test
  void fromNullReturnsDefault() {
    assertThat(PracticeCoachStyle.from(null)).isEqualTo(PracticeCoachStyle.GUIDED);
  }

  @Test
  void fromBlankReturnsDefault() {
    assertThat(PracticeCoachStyle.from("")).isEqualTo(PracticeCoachStyle.GUIDED);
    assertThat(PracticeCoachStyle.from("   ")).isEqualTo(PracticeCoachStyle.GUIDED);
  }

  @Test
  void fromKnownGuided() {
    assertThat(PracticeCoachStyle.from("GUIDED")).isEqualTo(PracticeCoachStyle.GUIDED);
  }

  @Test
  void fromKnownDirect() {
    assertThat(PracticeCoachStyle.from("DIRECT")).isEqualTo(PracticeCoachStyle.DIRECT);
  }

  @Test
  void fromTrimmedKnown() {
    assertThat(PracticeCoachStyle.from("  DIRECT  ")).isEqualTo(PracticeCoachStyle.DIRECT);
  }

  @Test
  void fromLegacyValueReturnsDefault() {
    assertThat(PracticeCoachStyle.from("SOCRATIC_GUIDE")).isEqualTo(PracticeCoachStyle.GUIDED);
  }

  @Test
  void fromEnumInstance() {
    assertThat(PracticeCoachStyle.from(PracticeCoachStyle.DIRECT)).isEqualTo(PracticeCoachStyle.DIRECT);
  }

  @Test
  void defaultStyleIsGuided() {
    assertThat(PracticeCoachStyle.defaultStyle()).isEqualTo(PracticeCoachStyle.GUIDED);
  }

  @Test
  void guidedInstructionContainsLayeredKeywords() {
    assertThat(PracticeCoachStyle.GUIDED.instruction())
        .contains("L1", "L4", "分层提示协议", "优先级最高");
  }

  @Test
  void directInstructionContainsIntuitionKeyword() {
    assertThat(PracticeCoachStyle.DIRECT.instruction())
        .contains("直觉", "完整推导");
  }
}
