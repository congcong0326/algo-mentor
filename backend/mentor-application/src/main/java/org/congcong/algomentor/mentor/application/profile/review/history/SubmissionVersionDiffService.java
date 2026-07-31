package org.congcong.algomentor.mentor.application.profile.review.history;

import com.github.difflib.DiffUtils;
import com.github.difflib.UnifiedDiffUtils;
import com.github.difflib.patch.Patch;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** 仅由规范化代码生成有界 unified diff 的确定性服务。 */
public final class SubmissionVersionDiffService {

  public static final int MAX_DIFF_CHARS = 8_000;
  private static final int CONTEXT_LINES = 3;
  private static final String FROM_FILE_PREFIX = "submission-v";
  private static final String TO_FILE_PREFIX = "submission-v";

  public SubmissionVersionDiff diff(CodeReviewSubmissionVersion from, CodeReviewSubmissionVersion to) {
    if (from == null || to == null || !from.problemSlug().equals(to.problemSlug())
        || from.versionNo() >= to.versionNo()) {
      throw new IllegalArgumentException("Submission diff requires two ordered versions of the same problem");
    }
    List<String> fromLines = lines(from.normalizedCode());
    List<String> toLines = lines(to.normalizedCode());
    Patch<String> patch = DiffUtils.diff(fromLines, toLines);
    List<String> unified = UnifiedDiffUtils.generateUnifiedDiff(
        filename(FROM_FILE_PREFIX, from), filename(TO_FILE_PREFIX, to), fromLines, patch, CONTEXT_LINES);
    return limitToCompleteHunks(unified);
  }

  private static List<String> lines(String source) {
    String normalized = source.replace("\r\n", "\n").replace('\r', '\n');
    return Arrays.asList(normalized.split("\n", -1));
  }

  private static String filename(String prefix, CodeReviewSubmissionVersion submission) {
    return prefix + submission.versionNo() + "-" + submission.reviewId();
  }

  private static SubmissionVersionDiff limitToCompleteHunks(List<String> unified) {
    if (unified == null || unified.isEmpty()) {
      return new SubmissionVersionDiff("", false);
    }
    String complete = String.join("\n", unified);
    if (complete.length() <= MAX_DIFF_CHARS) {
      return new SubmissionVersionDiff(complete, false);
    }

    List<String> retained = new ArrayList<>();
    int index = 0;
    while (index < unified.size() && !unified.get(index).startsWith("@@")) {
      retained.add(unified.get(index++));
    }
    while (index < unified.size()) {
      int hunkStart = index;
      index++;
      while (index < unified.size() && !unified.get(index).startsWith("@@")) {
        index++;
      }
      List<String> candidate = new ArrayList<>(retained);
      candidate.addAll(unified.subList(hunkStart, index));
      if (String.join("\n", candidate).length() > MAX_DIFF_CHARS) {
        break;
      }
      retained = candidate;
    }
    return new SubmissionVersionDiff(String.join("\n", retained), true);
  }
}
