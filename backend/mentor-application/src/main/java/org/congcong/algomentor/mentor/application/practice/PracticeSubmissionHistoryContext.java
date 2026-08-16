package org.congcong.algomentor.mentor.application.practice;

import java.util.List;

/** 单次 Practice Chat Prompt 组装使用的用户级历史提交索引。 */
public record PracticeSubmissionHistoryContext(
    List<PracticeSubmissionHistoryEntry> submittedProblems,
    List<PracticeSubmissionHistoryEntry> relatedSubmittedProblems,
    List<PracticeSubmissionHistoryScopeInput> scopeInputs
) {

  public static final int RECENT_SUBMITTED_PROBLEM_LIMIT = 5;
  public static final int RELATED_SUBMITTED_PROBLEM_LIMIT = 3;

  public PracticeSubmissionHistoryContext {
    submittedProblems = submittedProblems == null ? List.of() : List.copyOf(submittedProblems);
    relatedSubmittedProblems = relatedSubmittedProblems == null ? List.of() : List.copyOf(relatedSubmittedProblems);
    scopeInputs = scopeInputs == null ? List.of() : List.copyOf(scopeInputs);
    if (submittedProblems.size() > RECENT_SUBMITTED_PROBLEM_LIMIT
        || relatedSubmittedProblems.size() > RELATED_SUBMITTED_PROBLEM_LIMIT) {
      throw new IllegalArgumentException("Practice submission history context exceeds its prompt index limits");
    }
  }

  public PracticeSubmissionHistoryContext(
      List<PracticeSubmissionHistoryEntry> submittedProblems,
      List<PracticeSubmissionHistoryEntry> relatedSubmittedProblems
  ) {
    this(submittedProblems, relatedSubmittedProblems, List.of());
  }

  public static PracticeSubmissionHistoryContext empty() {
    return new PracticeSubmissionHistoryContext(List.of(), List.of(), List.of());
  }
}
