package org.congcong.algomentor.api.practice.realtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class PracticeRealtimeCursorTest {

  @Test
  void v2CursorOnlyAcceptsInitialOrSequenceZeroIds() {
    assertThat(PracticeRealtimeCursor.normalizeV2After(null)).isEqualTo("0-0");
    assertThat(PracticeRealtimeCursor.normalizeV2After("12-0")).isEqualTo("12-0");
    assertThatThrownBy(() -> PracticeRealtimeCursor.normalizeV2After("12-1"))
        .isInstanceOf(PracticeRealtimeCursorInvalidException.class);
    assertThatThrownBy(() -> PracticeRealtimeCursor.normalizeV2After("1710000000000-9"))
        .isInstanceOf(PracticeRealtimeCursorInvalidException.class);
  }
}
