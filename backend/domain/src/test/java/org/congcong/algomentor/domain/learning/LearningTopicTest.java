package org.congcong.algomentor.domain.learning;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class LearningTopicTest {

  @Test
  void createsTopicFromNonBlankTitle() {
    LearningTopic topic = LearningTopic.of("binary search");

    assertThat(topic.title()).isEqualTo("binary search");
  }

  @Test
  void rejectsBlankTitle() {
    assertThatThrownBy(() -> LearningTopic.of(" "))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Learning topic title must not be blank");
  }
}
