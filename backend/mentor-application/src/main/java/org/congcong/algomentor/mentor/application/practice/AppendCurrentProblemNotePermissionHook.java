package org.congcong.algomentor.mentor.application.practice;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.congcong.algomentor.agent.core.permission.AgentToolPermissionCheck;
import org.congcong.algomentor.agent.core.permission.AgentToolPermissionDecisionPlan;
import org.congcong.algomentor.agent.core.permission.AgentToolPermissionHook;
import org.congcong.algomentor.agent.core.permission.ToolNamePermissionHook;
import org.congcong.algomentor.agent.core.runtime.model.AgentRuntimeMetadataKeys;

/** 在追加题目笔记前展示本次确切 Markdown，并等待用户确认。 */
public final class AppendCurrentProblemNotePermissionHook implements AgentToolPermissionHook {

  public static final int DEFAULT_ORDER = ToolNamePermissionHook.DEFAULT_ORDER - 50;
  public static final String POLICY_SOURCE = "append-current-problem-note-hook";
  public static final String DISPLAY_NAME = "追加题目笔记";
  public static final String REASON = "模型请求把以下内容追加到当前题目的笔记。";
  public static final String INVALID_ARGUMENT_REASON = "待追加的题目笔记内容无效。";

  private static final List<String> APPEND_EFFECTS = List.of(
      "只追加到当前题目的笔记正文",
      "不会覆盖已有笔记或修改结构化提纲");

  private final PracticeSessionRepository sessionRepository;

  public AppendCurrentProblemNotePermissionHook(PracticeSessionRepository sessionRepository) {
    this.sessionRepository = Objects.requireNonNull(sessionRepository, "sessionRepository must not be null");
  }

  @Override
  public int order() {
    return DEFAULT_ORDER;
  }

  @Override
  public AgentToolPermissionDecisionPlan evaluate(AgentToolPermissionCheck check) {
    if (check == null) {
      throw new IllegalArgumentException("Agent tool permission check must not be null");
    }
    if (!AppendCurrentProblemNoteAgentToolContracts.TOOL_NAME.equals(check.toolCall().name())) {
      return AgentToolPermissionDecisionPlan.passthrough();
    }

    String contentMarkdown = AppendCurrentProblemNoteAgentToolContracts
        .contentMarkdown(check.toolCall().arguments())
        .orElse(null);
    if (contentMarkdown == null) {
      return AgentToolPermissionDecisionPlan.deny(INVALID_ARGUMENT_REASON, POLICY_SOURCE);
    }
    return AgentToolPermissionDecisionPlan.ask(
        DISPLAY_NAME,
        REASON,
        preview(contentMarkdown, check.trustedMetadata()),
        POLICY_SOURCE);
  }

  private Map<String, Object> preview(String contentMarkdown, Map<String, Object> trustedMetadata) {
    LinkedHashMap<String, Object> preview = new LinkedHashMap<>();
    preview.put(AppendCurrentProblemNoteAgentToolContracts.PREVIEW_NOTE_MARKDOWN, contentMarkdown);
    preview.put(AppendCurrentProblemNoteAgentToolContracts.PREVIEW_APPENDED_CHARS, contentMarkdown.length());
    preview.put(AppendCurrentProblemNoteAgentToolContracts.PREVIEW_EFFECTS, APPEND_EFFECTS);

    Long userId = positiveLong(trustedMetadata.get(AgentRuntimeMetadataKeys.USER_ID));
    Long sessionId = positiveLong(
        trustedMetadata.get(PracticeChatPromptConstants.METADATA_PRACTICE_SESSION_ID));
    if (userId == null || sessionId == null) {
      preview.put(AppendCurrentProblemNoteAgentToolContracts.PREVIEW_CONTEXT_AVAILABLE, false);
      return preview;
    }

    try {
      PracticeSession session = sessionRepository.findSessionForUser(sessionId, userId).orElse(null);
      if (session == null) {
        preview.put(AppendCurrentProblemNoteAgentToolContracts.PREVIEW_CONTEXT_AVAILABLE, false);
        return preview;
      }
      preview.put(AppendCurrentProblemNoteAgentToolContracts.PREVIEW_PROBLEM_SLUG, session.problemSlug());
      preview.put(AppendCurrentProblemNoteAgentToolContracts.PREVIEW_PROBLEM_TITLE, session.problemSlug());
      preview.put(AppendCurrentProblemNoteAgentToolContracts.PREVIEW_CONTEXT_AVAILABLE, true);
      return preview;
    } catch (RuntimeException ignored) {
      preview.put(AppendCurrentProblemNoteAgentToolContracts.PREVIEW_CONTEXT_AVAILABLE, false);
      return preview;
    }
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
}
