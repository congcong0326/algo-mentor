package org.congcong.algomentor.mentor.application.profile.recall;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.congcong.algomentor.agent.core.prompt.DefaultPromptRenderer;
import org.congcong.algomentor.agent.core.prompt.PromptAssemblyRequest;
import org.congcong.algomentor.agent.core.prompt.PromptProfile;
import org.congcong.algomentor.agent.core.prompt.PromptSensitivity;
import org.congcong.algomentor.agent.core.prompt.PromptTrustLevel;
import org.congcong.algomentor.mentor.application.practice.PracticeChatPromptConstants;
import org.congcong.algomentor.mentor.application.practice.PracticeChatPromptProfileResolver;
import org.congcong.algomentor.mentor.application.practice.TrustedProblemTag;
import org.congcong.algomentor.mentor.application.profile.LearnerProfileDimension;
import org.congcong.algomentor.mentor.application.profile.LearnerProfileEntry;
import org.congcong.algomentor.mentor.application.profile.LearnerProfileEntryKind;
import org.congcong.algomentor.mentor.application.profile.LearnerProfileEntryStatus;
import org.congcong.algomentor.mentor.application.profile.LearnerProfileIdentity;
import org.congcong.algomentor.mentor.application.profile.LearnerProfileOriginType;
import org.junit.jupiter.api.Test;

class LearnerProfileRecallPromptSectionProviderTest {

  @Test
  void rendersProfileInDeclaredTagThenGeneralOrderWithinEightHundredTokens() {
    LearnerProfileRecallSnapshot snapshot = new LearnerProfileRecallSnapshot(
        List.of(entry(LearnerProfileEntryKind.DECLARED_FACT, LearnerProfileDimension.GOALS_AND_INTENTS,
            null, "declared-marker-" + "x".repeat(4_000))),
        List.of(entry(LearnerProfileEntryKind.GENERAL_OBSERVATION,
            LearnerProfileDimension.PROBLEM_SOLVING_APPROACH, null, "general-marker")),
        List.of(new LearnerProfileRecallSnapshot.TagAssessment(
            new TrustedProblemTag(9L, "array", "Array", "数组"),
            entry(LearnerProfileEntryKind.TAG_ASSESSMENT, LearnerProfileDimension.TAG_MASTERY, 9L,
                "tag-marker"))));
    LearnerProfilePromptSectionProvider provider = new LearnerProfilePromptSectionProvider(800);
    PromptAssemblyRequest request = request(snapshot);
    PromptProfile profile = new PracticeChatPromptProfileResolver().resolve(request);

    var section = provider.sections(request, profile).get(0);
    String text = (String) section.variables().get("text");
    int renderedTokenEstimate = new DefaultPromptRenderer().render(section).tokenEstimate();

    assertThat(section.id()).isEqualTo(PracticeChatPromptConstants.SECTION_LEARNER_PROFILE);
    assertThat(section.priority()).isEqualTo(45);
    assertThat(section.trustLevel()).isEqualTo(PromptTrustLevel.MODEL_GENERATED);
    assertThat(section.sensitivity()).isEqualTo(PromptSensitivity.USER_CONTENT);
    assertThat(text).contains("declared-marker-").doesNotContain("tag-marker").doesNotContain("general-marker");
    assertThat(renderedTokenEstimate).isLessThanOrEqualTo(800);
    assertThat(section.sourceRef().attributes())
        .containsEntry(PracticeChatPromptConstants.METADATA_LEARNER_PROFILE_ENTRY_COUNT, 3)
        .containsEntry(PracticeChatPromptConstants.METADATA_LEARNER_PROFILE_TRIMMED, true);
  }

  @Test
  void omitsSectionForEmptySnapshot() {
    LearnerProfilePromptSectionProvider provider = new LearnerProfilePromptSectionProvider(800);
    PromptAssemblyRequest request = request(LearnerProfileRecallSnapshot.empty());

    assertThat(provider.sections(request, new PracticeChatPromptProfileResolver().resolve(request))).isEmpty();
  }

  private static PromptAssemblyRequest request(LearnerProfileRecallSnapshot snapshot) {
    return new PromptAssemblyRequest(
        PracticeChatPromptConstants.SCENARIO,
        PracticeChatPromptConstants.PROFILE_ID,
        8_000,
        Map.of(PracticeChatPromptConstants.VARIABLE_LEARNER_PROFILE_SNAPSHOT, snapshot),
        Map.of());
  }

  private static LearnerProfileEntry entry(
      LearnerProfileEntryKind kind,
      LearnerProfileDimension dimension,
      Long tagId,
      String content
  ) {
    Instant now = Instant.parse("2026-01-01T00:00:00Z");
    return new LearnerProfileEntry(
        1L,
        new LearnerProfileIdentity(7L, kind, dimension, tagId),
        1,
        LearnerProfileEntryStatus.ACTIVE,
        content,
        null,
        LearnerProfileOriginType.SYSTEM_DERIVED,
        null,
        null,
        null,
        now,
        null,
        now,
        now);
  }
}
