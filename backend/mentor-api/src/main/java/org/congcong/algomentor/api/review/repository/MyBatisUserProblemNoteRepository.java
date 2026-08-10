package org.congcong.algomentor.api.review.repository;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.Optional;
import org.congcong.algomentor.api.review.mapper.UserProblemNoteMapper;
import org.congcong.algomentor.api.review.mapper.model.UserProblemNoteRow;
import org.congcong.algomentor.api.review.mapper.model.UserProblemNoteSummaryRow;
import org.congcong.algomentor.mentor.application.review.note.ProblemSolutionOutlineV1;
import org.congcong.algomentor.mentor.application.review.note.UserProblemNote;
import org.congcong.algomentor.mentor.application.review.note.UserProblemNoteRepository;
import org.congcong.algomentor.mentor.application.review.note.UserProblemNoteSummary;

public class MyBatisUserProblemNoteRepository implements UserProblemNoteRepository {

  private final UserProblemNoteMapper mapper;
  private final ObjectMapper objectMapper;

  public MyBatisUserProblemNoteRepository(UserProblemNoteMapper mapper, ObjectMapper objectMapper) {
    this.mapper = mapper;
    this.objectMapper = objectMapper;
  }

  @Override
  public Optional<UserProblemNoteSummary> findSummary(long userId, String problemSlug) {
    return Optional.ofNullable(mapper.findSummary(userId, problemSlug)).map(this::toSummary);
  }

  @Override
  public Optional<UserProblemNote> find(long userId, String problemSlug) {
    return Optional.ofNullable(mapper.find(userId, problemSlug)).map(this::toNote);
  }

  @Override
  public Optional<UserProblemNote> insert(
      long userId,
      String problemSlug,
      ProblemSolutionOutlineV1 outline,
      String noteMarkdown,
      Instant now
  ) {
    return Optional.ofNullable(mapper.insert(
        userId,
        problemSlug,
        objectMapper.valueToTree(outline),
        noteMarkdown,
        now)).map(this::toNote);
  }

  @Override
  public Optional<UserProblemNote> update(
      long userId,
      String problemSlug,
      ProblemSolutionOutlineV1 outline,
      long expectedRevision,
      Instant now
  ) {
    return Optional.ofNullable(mapper.update(
        userId,
        problemSlug,
        objectMapper.valueToTree(outline),
        expectedRevision,
        now)).map(this::toNote);
  }

  @Override
  public Optional<UserProblemNote> replaceCoachSummary(
      long userId,
      String problemSlug,
      ProblemSolutionOutlineV1 initialOutline,
      String summaryMarkdown,
      long expectedCoachSummaryRevision,
      Instant now
  ) {
    UserProblemNoteRow row = expectedCoachSummaryRevision == 0
        ? mapper.replaceCoachSummaryAtZero(
            userId,
            problemSlug,
            objectMapper.valueToTree(initialOutline),
            summaryMarkdown,
            now)
        : mapper.replaceCoachSummary(
            userId,
            problemSlug,
            summaryMarkdown,
            expectedCoachSummaryRevision,
            now);
    return Optional.ofNullable(row).map(this::toNote);
  }

  @Override
  public boolean delete(long userId, String problemSlug) {
    return mapper.delete(userId, problemSlug) > 0;
  }

  private UserProblemNote toNote(UserProblemNoteRow row) {
    return new UserProblemNote(
        row.id(),
        row.userId(),
        row.problemSlug(),
        objectMapper.convertValue(row.outlineJson(), ProblemSolutionOutlineV1.class),
        row.noteMarkdown(),
        row.revision(),
        row.coachSummaryRevision(),
        row.createdAt(),
        row.coachSummaryUpdatedAt(),
        row.updatedAt());
  }

  private UserProblemNoteSummary toSummary(UserProblemNoteSummaryRow row) {
    return new UserProblemNoteSummary(
        row.id(),
        row.userId(),
        row.problemSlug(),
        objectMapper.convertValue(row.outlineJson(), ProblemSolutionOutlineV1.class),
        row.hasNoteMarkdown(),
        row.revision(),
        row.coachSummaryRevision(),
        row.createdAt(),
        row.coachSummaryUpdatedAt(),
        row.updatedAt());
  }
}
