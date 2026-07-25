package org.congcong.algomentor.mentor.application.review.note;

import java.time.Instant;
import java.util.Optional;

public interface UserProblemNoteRepository {

  Optional<UserProblemNote> find(long userId, String problemSlug);

  Optional<UserProblemNote> insert(
      long userId,
      String problemSlug,
      ProblemSolutionOutlineV1 outline,
      String noteMarkdown,
      Instant now
  );

  Optional<UserProblemNote> update(
      long userId,
      String problemSlug,
      ProblemSolutionOutlineV1 outline,
      String noteMarkdown,
      long expectedRevision,
      Instant now
  );

  boolean delete(long userId, String problemSlug);
}
