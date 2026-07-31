package org.congcong.algomentor.mentor.application.review.note;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.congcong.algomentor.mentor.application.review.ReviewContractConstants;
import org.congcong.algomentor.mentor.application.review.ReviewException;
import org.junit.jupiter.api.Test;

class UserProblemNoteAppendServiceTest {

  private static final Instant NOW = Instant.parse("2026-07-31T12:00:00Z");
  private final InMemoryRepository repository = new InMemoryRepository();
  private final UserProblemNoteAppendService service = new UserProblemNoteAppendService(
      repository, Clock.fixed(NOW, ZoneOffset.UTC));

  @Test
  void appendsMarkdownWithoutChangingTheStructuredOutline() {
    repository.notes.put("42:two-sum", new UserProblemNote(
        1L, 42L, "two-sum", outline(), "先查补数", 1, NOW, NOW));

    UserProblemNote updated = service.appendToTrustedProblem(42L, "two-sum", "  注意重复元素  ");

    assertThat(updated.revision()).isEqualTo(2);
    assertThat(updated.outline().coreIdea()).isEqualTo("哈希表");
    assertThat(updated.noteMarkdown()).isEqualTo("先查补数\n\n注意重复元素");
  }

  @Test
  void createsANoteWithAnEmptyOutlineWhenAppendingForTheFirstTime() {
    UserProblemNote created = service.appendToTrustedProblem(42L, "two-sum", "使用 Map 保存下标");

    assertThat(created.revision()).isEqualTo(1);
    assertThat(created.outline().hasContent()).isFalse();
    assertThat(created.noteMarkdown()).isEqualTo("使用 Map 保存下标");
  }

  @Test
  void rejectsBlankAppendContent() {
    assertThatThrownBy(() -> service.appendToTrustedProblem(42L, "two-sum", "  \n "))
        .isInstanceOfSatisfying(ReviewException.class, exception ->
            assertThat(exception.code()).isEqualTo("PROBLEM_NOTE_APPEND_CONTENT_REQUIRED"));
  }

  private ProblemSolutionOutlineV1 outline() {
    return new ProblemSolutionOutlineV1(
        1, "哈希表", java.util.List.of(), java.util.List.of(), "",
        java.util.List.of(), java.util.List.of(), "",
        ProblemComplexityValue.empty(), ProblemComplexityValue.empty(), "");
  }

  private static final class InMemoryRepository implements UserProblemNoteRepository {
    private final Map<String, UserProblemNote> notes = new HashMap<>();
    private long nextId = 2;

    @Override
    public Optional<UserProblemNote> find(long userId, String problemSlug) {
      return Optional.ofNullable(notes.get(key(userId, problemSlug)));
    }

    @Override
    public Optional<UserProblemNote> append(long userId, String problemSlug, ProblemSolutionOutlineV1 initialOutline,
        String contentMarkdown, Instant now) {
      String key = key(userId, problemSlug);
      UserProblemNote current = notes.get(key);
      String currentMarkdown = current == null ? "" : current.noteMarkdown();
      String separator = currentMarkdown.isEmpty() ? "" : ReviewContractConstants.NOTE_MARKDOWN_APPEND_SEPARATOR;
      String appended = currentMarkdown + separator + contentMarkdown;
      if (appended.length() > ReviewContractConstants.NOTE_MARKDOWN_MAX_CHARS) {
        return Optional.empty();
      }
      UserProblemNote saved = current == null
          ? new UserProblemNote(nextId++, userId, problemSlug, initialOutline, appended, 1, now, now)
          : new UserProblemNote(current.id(), userId, problemSlug, current.outline(), appended,
              current.revision() + 1, current.createdAt(), now);
      notes.put(key, saved);
      return Optional.of(saved);
    }

    @Override public Optional<UserProblemNote> insert(long userId, String problemSlug,
        ProblemSolutionOutlineV1 outline, String noteMarkdown, Instant now) {
      throw new UnsupportedOperationException();
    }
    @Override public Optional<UserProblemNote> update(long userId, String problemSlug,
        ProblemSolutionOutlineV1 outline, String noteMarkdown, long expectedRevision, Instant now) {
      throw new UnsupportedOperationException();
    }
    @Override public boolean delete(long userId, String problemSlug) { return false; }

    private String key(long userId, String problemSlug) {
      return userId + ":" + problemSlug;
    }
  }
}
