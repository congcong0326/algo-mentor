package org.congcong.algomentor.api.profile.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import org.congcong.algomentor.api.profile.mapper.LearnerProfileMapper;
import org.congcong.algomentor.api.profile.mapper.model.LearnerProfileViewRow;
import org.junit.jupiter.api.Test;

class LearnerProfileViewServiceTest {

  @Test
  void groupsCurrentEntriesAndKeepsOnlySafeDisplayMetadata() {
    LearnerProfileMapper mapper = mock(LearnerProfileMapper.class);
    Instant earlier = Instant.parse("2026-07-19T12:00:00Z");
    Instant latest = Instant.parse("2026-07-20T12:00:00Z");
    when(mapper.findCurrentForDisplay(42L)).thenReturn(List.of(
        row(1L, "DECLARED_FACT", "GOALS_AND_INTENTS", null, null, null, null, 2,
            "准备后端面试。", "USER_EXPLICIT", earlier),
        row(2L, "GENERAL_OBSERVATION", "PROBLEM_SOLVING_APPROACH", null, null, null, null, 1,
            "能够先拆解状态。", "SYSTEM_DERIVED", latest),
        row(3L, "TAG_ASSESSMENT", "TAG_MASTERY", 7L, "binary-search", "Binary Search", "二分查找", 1,
            "循环不变量仍需巩固。", "SYSTEM_DERIVED", earlier)));

    var response = new LearnerProfileViewService(mapper).getProfile(42L);

    assertThat(response.declaredFacts()).extracting(entry -> entry.dimension())
        .containsExactly("GOALS_AND_INTENTS");
    assertThat(response.generalObservations()).extracting(entry -> entry.contentText())
        .containsExactly("能够先拆解状态。");
    assertThat(response.tagAssessments()).singleElement().satisfies(entry -> {
      assertThat(entry.tag().value()).isEqualTo("binary-search");
      assertThat(entry.tag().labelEn()).isEqualTo("Binary Search");
      assertThat(entry.tag().labelZh()).isEqualTo("二分查找");
    });
    assertThat(response.updatedAt()).isEqualTo(latest);
  }

  private LearnerProfileViewRow row(
      long id,
      String kind,
      String dimension,
      Long tagId,
      String tagValue,
      String tagLabelEn,
      String tagLabelZh,
      int revisionNo,
      String contentText,
      String originType,
      Instant updatedAt
  ) {
    return new LearnerProfileViewRow(
        id,
        kind,
        dimension,
        tagId,
        tagValue,
        tagLabelEn,
        tagLabelZh,
        revisionNo,
        contentText,
        originType,
        updatedAt);
  }
}
