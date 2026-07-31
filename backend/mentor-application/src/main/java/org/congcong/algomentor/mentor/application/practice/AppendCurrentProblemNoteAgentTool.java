package org.congcong.algomentor.mentor.application.practice;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.Map;
import java.util.Objects;
import org.congcong.algomentor.agent.core.AgentExecutionContext;
import org.congcong.algomentor.agent.core.AgentTool;
import org.congcong.algomentor.agent.core.runtime.model.AgentRuntimeMetadataKeys;
import org.congcong.algomentor.llm.core.tool.LlmToolSpec;
import org.congcong.algomentor.mentor.application.review.ReviewContractConstants;
import org.congcong.algomentor.mentor.application.review.ReviewException;
import org.congcong.algomentor.mentor.application.review.note.UserProblemNote;
import org.congcong.algomentor.mentor.application.review.note.UserProblemNoteAppendService;

/** 经用户确认后，只追加当前 Practice Chat 题目的 Markdown 笔记正文。 */
public final class AppendCurrentProblemNoteAgentTool implements AgentTool {

  private static final LlmToolSpec SPEC = new LlmToolSpec(
      AppendCurrentProblemNoteAgentToolContracts.TOOL_NAME,
      """
          Append Markdown to the active practice problem's user note. Use this only when the current user explicitly asks \
          to save or remember content as a problem note. Put the exact self-contained Markdown to append in \
          contentMarkdown. The system will show that exact content and require user confirmation before execution. \
          This tool only appends to the note body; it never overwrites existing notes or changes the structured outline.
          """.strip(),
      inputSchema(),
      true);

  private final PracticeSessionRepository sessionRepository;
  private final UserProblemNoteAppendService noteAppendService;

  public AppendCurrentProblemNoteAgentTool(
      PracticeSessionRepository sessionRepository,
      UserProblemNoteAppendService noteAppendService
  ) {
    this.sessionRepository = Objects.requireNonNull(sessionRepository, "sessionRepository must not be null");
    this.noteAppendService = Objects.requireNonNull(noteAppendService, "noteAppendService must not be null");
  }

  @Override
  public LlmToolSpec spec() {
    return SPEC;
  }

  @Override
  public JsonNode execute(JsonNode arguments, AgentExecutionContext context) {
    String contentMarkdown = AppendCurrentProblemNoteAgentToolContracts.contentMarkdown(arguments).orElse(null);
    if (contentMarkdown == null) {
      return failure(AppendCurrentProblemNoteAgentToolContracts.FAILURE_INVALID_ARGUMENTS);
    }

    TrustedPracticeContext trustedContext = trustedContext(context);
    if (trustedContext.failureCode() != null) {
      return failure(trustedContext.failureCode());
    }

    try {
      PracticeSession session = sessionRepository.findSessionForUser(
              trustedContext.sessionId(), trustedContext.userId())
          .orElse(null);
      if (session == null) {
        return failure(AppendCurrentProblemNoteAgentToolContracts.FAILURE_PRACTICE_SESSION_NOT_FOUND);
      }
      if (!matchesCurrentProblem(session, trustedContext)) {
        return failure(AppendCurrentProblemNoteAgentToolContracts.FAILURE_CURRENT_PROBLEM_MISMATCH);
      }

      UserProblemNote note = noteAppendService.appendToTrustedProblem(
          trustedContext.userId(), session.problemSlug(), contentMarkdown);
      ObjectNode result = JsonNodeFactory.instance.objectNode();
      result.put(AppendCurrentProblemNoteAgentToolContracts.FIELD_TYPE,
          AppendCurrentProblemNoteAgentToolContracts.RESULT_TYPE);
      result.put(AppendCurrentProblemNoteAgentToolContracts.FIELD_STATUS,
          AppendCurrentProblemNoteAgentToolContracts.STATUS_APPENDED);
      result.put(AppendCurrentProblemNoteAgentToolContracts.FIELD_PROBLEM_SLUG, session.problemSlug());
      result.put(AppendCurrentProblemNoteAgentToolContracts.FIELD_SESSION_ID, session.id());
      result.put(AppendCurrentProblemNoteAgentToolContracts.FIELD_REVISION, note.revision());
      result.put(AppendCurrentProblemNoteAgentToolContracts.FIELD_APPENDED_CHARS, contentMarkdown.length());
      result.put(AppendCurrentProblemNoteAgentToolContracts.FIELD_NOTE_CHARS, note.noteMarkdown().length());
      return result;
    } catch (ReviewException exception) {
      if ("PROBLEM_NOTE_TOO_LONG".equals(exception.code())) {
        return failure(AppendCurrentProblemNoteAgentToolContracts.FAILURE_NOTE_TOO_LONG);
      }
      return failure(AppendCurrentProblemNoteAgentToolContracts.FAILURE_INTERNAL);
    } catch (RuntimeException exception) {
      return failure(AppendCurrentProblemNoteAgentToolContracts.FAILURE_INTERNAL);
    }
  }

  private boolean matchesCurrentProblem(PracticeSession session, TrustedPracticeContext context) {
    return session.id() == context.sessionId()
        && session.userId() == context.userId()
        && session.planId() == context.planId()
        && session.phaseIndex() == context.phaseIndex()
        && session.problemSlug().equals(context.problemSlug());
  }

  private TrustedPracticeContext trustedContext(AgentExecutionContext context) {
    if (context == null) {
      return TrustedPracticeContext.failed(AppendCurrentProblemNoteAgentToolContracts.FAILURE_MISSING_METADATA);
    }
    Map<String, Object> metadata = context.requestMetadata();
    String scenario = nonBlank(metadata.get(PracticeChatPromptConstants.METADATA_SCENARIO));
    if (scenario == null) {
      return TrustedPracticeContext.failed(AppendCurrentProblemNoteAgentToolContracts.FAILURE_MISSING_METADATA);
    }
    if (!PracticeChatPromptConstants.SCENARIO.equals(scenario)) {
      return TrustedPracticeContext.failed(AppendCurrentProblemNoteAgentToolContracts.FAILURE_NOT_PRACTICE_CHAT);
    }
    Long userId = positiveLong(metadata.get(AgentRuntimeMetadataKeys.USER_ID));
    Long sessionId = positiveLong(metadata.get(PracticeChatPromptConstants.METADATA_PRACTICE_SESSION_ID));
    Long planId = positiveLong(metadata.get(PracticeChatPromptConstants.METADATA_PLAN_ID));
    Integer phaseIndex = positiveInteger(metadata.get(PracticeChatPromptConstants.METADATA_PHASE_INDEX));
    String problemSlug = nonBlank(metadata.get(PracticeChatPromptConstants.METADATA_PROBLEM_SLUG));
    if (userId == null || sessionId == null || planId == null || phaseIndex == null || problemSlug == null) {
      return TrustedPracticeContext.failed(AppendCurrentProblemNoteAgentToolContracts.FAILURE_MISSING_METADATA);
    }
    return new TrustedPracticeContext(userId, sessionId, planId, phaseIndex, problemSlug, null);
  }

  private Long positiveLong(Object value) {
    if (value instanceof Number number) {
      return number.longValue() > 0 ? number.longValue() : null;
    }
    if (value instanceof CharSequence text) {
      try {
        long parsed = Long.parseLong(text.toString().trim());
        return parsed > 0 ? parsed : null;
      } catch (NumberFormatException ignored) {
        return null;
      }
    }
    return null;
  }

  private Integer positiveInteger(Object value) {
    Long parsed = positiveLong(value);
    return parsed == null || parsed > Integer.MAX_VALUE ? null : parsed.intValue();
  }

  private String nonBlank(Object value) {
    return value == null || value.toString().isBlank() ? null : value.toString().trim();
  }

  private JsonNode failure(String failureCode) {
    ObjectNode result = JsonNodeFactory.instance.objectNode();
    result.put(AppendCurrentProblemNoteAgentToolContracts.FIELD_TYPE,
        AppendCurrentProblemNoteAgentToolContracts.RESULT_TYPE);
    result.put(AppendCurrentProblemNoteAgentToolContracts.FIELD_STATUS,
        AppendCurrentProblemNoteAgentToolContracts.STATUS_FAILED);
    result.put(AppendCurrentProblemNoteAgentToolContracts.FIELD_FAILURE_CODE, failureCode);
    result.put(AppendCurrentProblemNoteAgentToolContracts.FIELD_MESSAGE, failureMessage(failureCode));
    return result;
  }

  private String failureMessage(String failureCode) {
    return switch (failureCode) {
      case AppendCurrentProblemNoteAgentToolContracts.FAILURE_INVALID_ARGUMENTS ->
          "The note content is missing, blank, too long, or has unexpected fields.";
      case AppendCurrentProblemNoteAgentToolContracts.FAILURE_MISSING_METADATA ->
          "Trusted practice context is unavailable.";
      case AppendCurrentProblemNoteAgentToolContracts.FAILURE_NOT_PRACTICE_CHAT ->
          "This tool is only available in Practice Chat.";
      case AppendCurrentProblemNoteAgentToolContracts.FAILURE_PRACTICE_SESSION_NOT_FOUND ->
          "The current practice session is unavailable.";
      case AppendCurrentProblemNoteAgentToolContracts.FAILURE_CURRENT_PROBLEM_MISMATCH ->
          "The current practice problem context does not match the session.";
      case AppendCurrentProblemNoteAgentToolContracts.FAILURE_NOTE_TOO_LONG ->
          "Appending this content would exceed the problem note length limit.";
      default -> "The problem note could not be updated.";
    };
  }

  private static ObjectNode inputSchema() {
    ObjectNode schema = JsonNodeFactory.instance.objectNode();
    schema.put("type", "object");
    ObjectNode properties = schema.putObject("properties");
    ObjectNode content = properties.putObject(AppendCurrentProblemNoteAgentToolContracts.ARGUMENT_CONTENT_MARKDOWN);
    content.put("type", "string");
    content.put("minLength", 1);
    content.put("maxLength", ReviewContractConstants.NOTE_MARKDOWN_MAX_CHARS);
    schema.putArray("required").add(AppendCurrentProblemNoteAgentToolContracts.ARGUMENT_CONTENT_MARKDOWN);
    schema.put("additionalProperties", false);
    return schema;
  }

  private record TrustedPracticeContext(
      long userId,
      long sessionId,
      long planId,
      int phaseIndex,
      String problemSlug,
      String failureCode
  ) {
    private static TrustedPracticeContext failed(String failureCode) {
      return new TrustedPracticeContext(0, 0, 0, 0, "", failureCode);
    }
  }
}
