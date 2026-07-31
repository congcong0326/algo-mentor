package org.congcong.algomentor.mentor.application.practice;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Optional;
import org.congcong.algomentor.mentor.application.review.ReviewContractConstants;

/** Practice Chat 追加当前题目笔记 Tool 的稳定参数、结果与确认预览契约。 */
public final class AppendCurrentProblemNoteAgentToolContracts {

  public static final String TOOL_NAME = "append_current_problem_note";
  public static final String ARGUMENT_CONTENT_MARKDOWN = "contentMarkdown";

  public static final String RESULT_TYPE = "current_problem_note_appended";
  public static final String STATUS_APPENDED = "APPENDED";
  public static final String STATUS_FAILED = "FAILED";

  public static final String FIELD_TYPE = "type";
  public static final String FIELD_STATUS = "status";
  public static final String FIELD_FAILURE_CODE = "failureCode";
  public static final String FIELD_MESSAGE = "message";
  public static final String FIELD_PROBLEM_SLUG = "problemSlug";
  public static final String FIELD_SESSION_ID = "sessionId";
  public static final String FIELD_REVISION = "revision";
  public static final String FIELD_APPENDED_CHARS = "appendedChars";
  public static final String FIELD_NOTE_CHARS = "noteChars";

  public static final String PREVIEW_PROBLEM_SLUG = "problemSlug";
  public static final String PREVIEW_PROBLEM_TITLE = "problemTitle";
  public static final String PREVIEW_NOTE_MARKDOWN = "noteMarkdown";
  public static final String PREVIEW_APPENDED_CHARS = "appendedChars";
  public static final String PREVIEW_EFFECTS = "effects";
  public static final String PREVIEW_CONTEXT_AVAILABLE = "contextAvailable";

  public static final String FAILURE_INVALID_ARGUMENTS = "INVALID_ARGUMENTS";
  public static final String FAILURE_MISSING_METADATA = "MISSING_TRUSTED_METADATA";
  public static final String FAILURE_NOT_PRACTICE_CHAT = "NOT_PRACTICE_CHAT";
  public static final String FAILURE_PRACTICE_SESSION_NOT_FOUND = "PRACTICE_SESSION_NOT_FOUND";
  public static final String FAILURE_CURRENT_PROBLEM_MISMATCH = "CURRENT_PROBLEM_MISMATCH";
  public static final String FAILURE_NOTE_TOO_LONG = "PROBLEM_NOTE_TOO_LONG";
  public static final String FAILURE_INTERNAL = "INTERNAL_ERROR";

  private AppendCurrentProblemNoteAgentToolContracts() {
  }

  public static Optional<String> contentMarkdown(JsonNode arguments) {
    if (arguments == null || !arguments.isObject() || arguments.size() != 1) {
      return Optional.empty();
    }
    JsonNode value = arguments.get(ARGUMENT_CONTENT_MARKDOWN);
    if (value == null || !value.isTextual()) {
      return Optional.empty();
    }
    String normalized = value.textValue().strip();
    if (normalized.isEmpty() || normalized.length() > ReviewContractConstants.NOTE_MARKDOWN_MAX_CHARS) {
      return Optional.empty();
    }
    return Optional.of(normalized);
  }
}
