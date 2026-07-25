package org.congcong.algomentor.mentor.application.review.note;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.congcong.algomentor.mentor.application.review.ReviewException;
import org.congcong.algomentor.mentor.application.review.catalog.ReviewProblemCatalog;
import org.congcong.algomentor.mentor.application.review.catalog.ReviewProblemSnapshot;
import org.junit.jupiter.api.Test;

class UserProblemNoteServiceTest {

  private static final Instant NOW = Instant.parse("2026-07-24T08:00:00Z");
  private final InMemoryRepository repository = new InMemoryRepository();
  private final ReviewProblemCatalog catalog = slug -> "two-sum".equals(slug)
      ? Optional.of(new ReviewProblemSnapshot(slug, "两数之和", "EASY", "", ""))
      : Optional.empty();
  private final UserProblemNoteService service = new UserProblemNoteService(
      repository,
      catalog,
      Clock.fixed(NOW, ZoneOffset.UTC));

  @Test
  void returnsAnEmptyNoteWithoutRequiringAReviewCard() {
    UserProblemNote note = service.get(42L, "two-sum");

    assertThat(note.exists()).isFalse();
    assertThat(note.revision()).isZero();
    assertThat(note.hasContent()).isFalse();
  }

  @Test
  void createsUpdatesAndDeletesByRevision() {
    UserProblemNote created = service.upsert(42L, "two-sum", outline("哈希表"), "先查再写入", 0);
    UserProblemNote updated = service.upsert(42L, "two-sum", outline("补数"), "避免同一元素复用", 1);

    assertThat(created.revision()).isEqualTo(1);
    assertThat(updated.revision()).isEqualTo(2);
    assertThat(updated.outline().coreIdea()).isEqualTo("补数");
    assertThat(updated.noteMarkdown()).isEqualTo("避免同一元素复用");

    service.delete(42L, "two-sum");
    assertThat(service.get(42L, "two-sum").exists()).isFalse();
  }

  @Test
  void rejectsAStaleRevisionWithoutOverwritingTheSavedNote() {
    service.upsert(42L, "two-sum", outline("哈希表"), "初始笔记", 0);

    assertThatThrownBy(() -> service.upsert(42L, "two-sum", outline("覆盖"), "旧页面内容", 7))
        .isInstanceOfSatisfying(ReviewException.class, exception ->
            assertThat(exception.code()).isEqualTo("PROBLEM_NOTE_REVISION_CONFLICT"));
    assertThat(service.get(42L, "two-sum").noteMarkdown()).isEqualTo("初始笔记");
  }

  @Test
  void readsLegacyOutlineJsonWithoutCategoryNotes() throws Exception {
    ProblemSolutionOutlineV1 outline = new ObjectMapper().readValue("""
        {
          "schemaVersion": 1,
          "coreIdea": "哈希表查找补数",
          "dataStructures": ["HASH_MAP"],
          "customDataStructures": [],
          "algorithms": [],
          "customAlgorithms": [],
          "timeComplexity": {"key": "O_N", "customText": null},
          "spaceComplexity": {"key": "O_N", "customText": null},
          "edgeCases": "不能重复使用同一元素"
        }
        """, ProblemSolutionOutlineV1.class);

    assertThat(outline.dataStructureNotes()).isEmpty();
    assertThat(outline.algorithmNotes()).isEmpty();
    assertThat(outline.hasContent()).isTrue();
  }

  private ProblemSolutionOutlineV1 outline(String coreIdea) {
    return new ProblemSolutionOutlineV1(
        1,
        coreIdea,
        java.util.List.of(ProblemDataStructureKey.HASH_MAP),
        java.util.List.of(),
        "保存已访问元素",
        java.util.List.of(),
        java.util.List.of(),
        "",
        ProblemComplexityValue.empty(),
        ProblemComplexityValue.empty(),
        "");
  }

  private static final class InMemoryRepository implements UserProblemNoteRepository {
    private final Map<String, UserProblemNote> notes = new HashMap<>();
    private long nextId = 1;

    @Override
    public Optional<UserProblemNote> find(long userId, String problemSlug) {
      return Optional.ofNullable(notes.get(key(userId, problemSlug)));
    }

    @Override
    public Optional<UserProblemNote> insert(
        long userId,
        String problemSlug,
        ProblemSolutionOutlineV1 outline,
        String noteMarkdown,
        Instant now
    ) {
      String key = key(userId, problemSlug);
      if (notes.containsKey(key)) {
        return Optional.empty();
      }
      UserProblemNote note = new UserProblemNote(
          nextId++, userId, problemSlug, outline, noteMarkdown, 1, now, now);
      notes.put(key, note);
      return Optional.of(note);
    }

    @Override
    public Optional<UserProblemNote> update(
        long userId,
        String problemSlug,
        ProblemSolutionOutlineV1 outline,
        String noteMarkdown,
        long expectedRevision,
        Instant now
    ) {
      String key = key(userId, problemSlug);
      UserProblemNote current = notes.get(key);
      if (current == null || current.revision() != expectedRevision) {
        return Optional.empty();
      }
      UserProblemNote updated = new UserProblemNote(
          current.id(),
          userId,
          problemSlug,
          outline,
          noteMarkdown,
          current.revision() + 1,
          current.createdAt(),
          now);
      notes.put(key, updated);
      return Optional.of(updated);
    }

    @Override
    public boolean delete(long userId, String problemSlug) {
      return notes.remove(key(userId, problemSlug)) != null;
    }

    private String key(long userId, String problemSlug) {
      return userId + ":" + problemSlug;
    }
  }
}
