package org.congcong.algomentor.mentor.application.profile.recall;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.congcong.algomentor.mentor.application.practice.PracticeChatPromptConstants;
import org.congcong.algomentor.mentor.application.practice.TrustedProblemTag;
import org.congcong.algomentor.mentor.application.practice.TrustedProblemTagCatalog;
import org.congcong.algomentor.mentor.application.profile.LearnerProfileDimension;
import org.congcong.algomentor.mentor.application.profile.LearnerProfileEntry;
import org.congcong.algomentor.mentor.application.profile.LearnerProfileEntryDraft;
import org.congcong.algomentor.mentor.application.profile.LearnerProfileEntryKind;
import org.congcong.algomentor.mentor.application.profile.LearnerProfileEntryStatus;
import org.congcong.algomentor.mentor.application.profile.LearnerProfileIdentity;
import org.congcong.algomentor.mentor.application.profile.LearnerProfileOriginType;
import org.congcong.algomentor.mentor.application.profile.LearnerProfileQueryService;
import org.congcong.algomentor.mentor.application.profile.LearnerProfileRepository;
import org.junit.jupiter.api.Test;

class LearnerProfileRecallServiceTest {

  @Test
  void enablesOnlyPracticeChatAndSkipsAllQueriesWhenDisabled() {
    RecordingRepository repository = new RecordingRepository();
    CountingTagCatalog catalog = new CountingTagCatalog();
    LearnerProfilePolicyResolver resolver = new LearnerProfilePolicyResolver(false, 800);
    LearnerProfileRecallService service = service(resolver, repository, catalog);

    assertThat(resolver.resolve("PRACTICE_CODE_REVIEW").enabled()).isFalse();
    assertThat(resolver.resolve("unknown").enabled()).isFalse();
    assertThat(service.recall(7L, PracticeChatPromptConstants.SCENARIO, "two-sum")).isEqualTo(
        LearnerProfileRecallSnapshot.empty());

    assertThat(repository.dimensionQueryCount).isZero();
    assertThat(repository.tagQueryCount).isZero();
    assertThat(catalog.queryCount).isZero();
  }

  @Test
  void recallsOnlyAllowedScopeInDeterministicPriorityOrder() {
    RecordingRepository repository = new RecordingRepository();
    repository.entries = List.of(
        entry(7L, LearnerProfileEntryKind.DECLARED_FACT,
            LearnerProfileDimension.LEARNING_AND_INTERACTION_PREFERENCES, null, "declared-preference"),
        entry(7L, LearnerProfileEntryKind.DECLARED_FACT,
            LearnerProfileDimension.GOALS_AND_INTENTS, null, "declared-goal"),
        entry(7L, LearnerProfileEntryKind.GENERAL_OBSERVATION,
            LearnerProfileDimension.IMPLEMENTATION_AND_ERROR_PATTERN, null, "general-error"),
        entry(7L, LearnerProfileEntryKind.GENERAL_OBSERVATION,
            LearnerProfileDimension.PROBLEM_SOLVING_APPROACH, null, "general-approach"),
        entry(7L, LearnerProfileEntryKind.TAG_ASSESSMENT,
            LearnerProfileDimension.TAG_MASTERY, 11L, "tag-eleven"),
        entry(7L, LearnerProfileEntryKind.TAG_ASSESSMENT,
            LearnerProfileDimension.TAG_MASTERY, 12L, "tag-twelve"),
        entry(7L, LearnerProfileEntryKind.GENERAL_OBSERVATION,
            LearnerProfileDimension.REVIEW_AND_GROWTH_PERFORMANCE, null, "must-not-recall"));
    CountingTagCatalog catalog = new CountingTagCatalog();
    catalog.tags = List.of(tag(12L, "dynamic-programming"), tag(11L, "hash-table"));

    LearnerProfileRecallSnapshot snapshot = service(
        new LearnerProfilePolicyResolver(true, 800), repository, catalog)
        .recall(7L, PracticeChatPromptConstants.SCENARIO, "two-sum");

    assertThat(snapshot.declaredFacts()).extracting(LearnerProfileEntry::contentText)
        .containsExactly("declared-goal", "declared-preference");
    assertThat(snapshot.currentProblemTagAssessments()).extracting(assessment -> assessment.tag().tagId())
        .containsExactly(12L, 11L);
    assertThat(snapshot.generalObservations()).extracting(LearnerProfileEntry::contentText)
        .containsExactly("general-approach", "general-error");
    assertThat(repository.dimensionQueryCount).isEqualTo(2);
    assertThat(repository.tagQueryCount).isEqualTo(1);
    assertThat(catalog.queryCount).isEqualTo(1);
  }

  @Test
  void degradesToEmptySnapshotWhenRecallInfrastructureFails() {
    RecordingRepository repository = new RecordingRepository();
    TrustedProblemTagCatalog failingCatalog = slug -> {
      throw new IllegalStateException("database unavailable");
    };

    LearnerProfileRecallSnapshot snapshot = service(
        new LearnerProfilePolicyResolver(true, 800), repository, failingCatalog)
        .recall(7L, PracticeChatPromptConstants.SCENARIO, "two-sum");

    assertThat(snapshot).isEqualTo(LearnerProfileRecallSnapshot.empty());
  }

  private LearnerProfileRecallService service(
      LearnerProfilePolicyResolver resolver,
      LearnerProfileRepository repository,
      TrustedProblemTagCatalog catalog
  ) {
    return new LearnerProfileRecallService(resolver, new LearnerProfileQueryService(repository), catalog);
  }

  private static TrustedProblemTag tag(long id, String value) {
    return new TrustedProblemTag(id, value, value, value);
  }

  private static LearnerProfileEntry entry(
      long userId,
      LearnerProfileEntryKind kind,
      LearnerProfileDimension dimension,
      Long tagId,
      String content
  ) {
    Instant now = Instant.parse("2026-01-01T00:00:00Z");
    return new LearnerProfileEntry(
        Math.abs(content.hashCode()) + 1L,
        new LearnerProfileIdentity(userId, kind, dimension, tagId),
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

  private static final class CountingTagCatalog implements TrustedProblemTagCatalog {
    private List<TrustedProblemTag> tags = List.of();
    private int queryCount;

    @Override
    public List<TrustedProblemTag> findByProblemSlug(String problemSlug) {
      queryCount++;
      return tags;
    }
  }

  private static final class RecordingRepository implements LearnerProfileRepository {
    private List<LearnerProfileEntry> entries = List.of();
    private int dimensionQueryCount;
    private int tagQueryCount;

    @Override
    public Optional<LearnerProfileEntry> findCurrent(LearnerProfileIdentity identity) {
      return Optional.empty();
    }

    @Override
    public void lockUser(long userId) {
      throw new UnsupportedOperationException();
    }

    @Override
    public Optional<LearnerProfileEntry> findCurrentForUpdate(LearnerProfileIdentity identity) {
      throw new UnsupportedOperationException();
    }

    @Override
    public List<LearnerProfileEntry> findHistory(LearnerProfileIdentity identity) {
      throw new UnsupportedOperationException();
    }

    @Override
    public List<LearnerProfileEntry> findCurrentByDimensions(
        long userId,
        LearnerProfileEntryKind entryKind,
        Collection<LearnerProfileDimension> dimensions
    ) {
      dimensionQueryCount++;
      return entries.stream()
          .filter(entry -> entry.identity().userId() == userId)
          .filter(entry -> entry.identity().entryKind() == entryKind)
          .filter(entry -> dimensions.contains(entry.identity().dimension()))
          .toList();
    }

    @Override
    public List<LearnerProfileEntry> findCurrentByTagIds(long userId, Collection<Long> tagIds) {
      tagQueryCount++;
      return entries.stream()
          .filter(entry -> entry.identity().userId() == userId)
          .filter(entry -> entry.identity().entryKind() == LearnerProfileEntryKind.TAG_ASSESSMENT)
          .filter(entry -> tagIds.contains(entry.identity().tagId()))
          .toList();
    }

    @Override
    public LearnerProfileEntry insert(LearnerProfileEntryDraft draft) {
      throw new UnsupportedOperationException();
    }

    @Override
    public void markInactive(long entryId, LearnerProfileEntryStatus status, Instant validTo) {
      throw new UnsupportedOperationException();
    }

    @Override
    public void deleteIdentity(LearnerProfileIdentity identity) {
      throw new UnsupportedOperationException();
    }
  }
}
