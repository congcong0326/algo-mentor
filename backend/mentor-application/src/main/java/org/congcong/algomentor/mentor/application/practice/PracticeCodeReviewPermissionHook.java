package org.congcong.algomentor.mentor.application.practice;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;
import org.congcong.algomentor.agent.core.permission.AgentToolPermissionCheck;
import org.congcong.algomentor.agent.core.permission.AgentToolPermissionDecisionPlan;
import org.congcong.algomentor.agent.core.permission.AgentToolPermissionHook;
import org.congcong.algomentor.agent.core.permission.ToolNamePermissionHook;
import org.congcong.algomentor.agent.core.runtime.model.AgentMessage;
import org.congcong.algomentor.agent.core.runtime.model.AgentRuntimeMetadataKeys;
import org.congcong.algomentor.agent.core.runtime.model.AgentTurnMessages;
import org.congcong.algomentor.agent.core.runtime.repository.AgentTurnMessageLookupRepository;

public final class PracticeCodeReviewPermissionHook implements AgentToolPermissionHook {

  public static final int DEFAULT_ORDER = ToolNamePermissionHook.DEFAULT_ORDER - 50;
  public static final String POLICY_SOURCE = "practice-code-review-hook";
  public static final String DISPLAY_NAME = "提交代码 Review";
  public static final String REASON = "模型请求执行一次正式代码 Review。";

  private static final int CODE_PREVIEW_MAX_LENGTH = 500;
  private static final List<String> REVIEW_EFFECTS = List.of(
      "将生成正式 Review 记录",
      "可能影响题目完成状态");
  private static final Pattern CODE_FENCE_LANGUAGE_PATTERN =
      Pattern.compile("(?m)^```\\s*([A-Za-z0-9_+#.-]+)");
  private static final Pattern JWT_LIKE_TOKEN_PATTERN =
      Pattern.compile("\\b[A-Za-z0-9_-]{12,}\\.[A-Za-z0-9_-]{12,}\\.[A-Za-z0-9_-]{12,}\\b");

  private final PracticeSessionRepository sessionRepository;
  private final AgentTurnMessageLookupRepository turnMessageLookupRepository;

  public PracticeCodeReviewPermissionHook(
      PracticeSessionRepository sessionRepository,
      AgentTurnMessageLookupRepository turnMessageLookupRepository
  ) {
    this.sessionRepository = Objects.requireNonNull(sessionRepository, "sessionRepository must not be null");
    this.turnMessageLookupRepository = Objects.requireNonNull(
        turnMessageLookupRepository,
        "turnMessageLookupRepository must not be null");
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
    if (!PracticeCodeReviewAgentToolNames.SUBMIT_PRACTICE_CODE_REVIEW.equals(check.toolCall().name())) {
      return AgentToolPermissionDecisionPlan.passthrough();
    }
    return AgentToolPermissionDecisionPlan.ask(
        DISPLAY_NAME,
        REASON,
        previewFromTrustedContext(check.trustedMetadata()),
        POLICY_SOURCE);
  }

  private Map<String, Object> previewFromTrustedContext(Map<String, Object> trustedMetadata) {
    Long userId = positiveLongOrNull(trustedMetadata.get(AgentRuntimeMetadataKeys.USER_ID));
    Long sessionId = positiveLongOrNull(
        trustedMetadata.get(PracticeChatPromptConstants.METADATA_PRACTICE_SESSION_ID));
    Long runDbId = positiveLongOrNull(trustedMetadata.get(AgentRuntimeMetadataKeys.RUN_DB_ID));
    if (userId == null || sessionId == null || runDbId == null) {
      return fallbackPreview();
    }

    try {
      PracticeSession session = sessionRepository.findSessionForUser(sessionId, userId).orElse(null);
      AgentTurnMessages turnMessages = turnMessageLookupRepository.findByRunId(runDbId).orElse(null);
      if (session == null || turnMessages == null) {
        return fallbackPreview();
      }
      return previewFromSessionAndMessage(session, turnMessages.userMessage());
    } catch (RuntimeException ignored) {
      return fallbackPreview();
    }
  }

  private Map<String, Object> previewFromSessionAndMessage(PracticeSession session, AgentMessage userMessage) {
    String content = userMessage.content();
    LinkedHashMap<String, Object> preview = new LinkedHashMap<>();
    preview.put(PracticeCodeReviewAgentToolNames.PREVIEW_PROBLEM_SLUG, session.problemSlug());
    preview.put(PracticeCodeReviewAgentToolNames.PREVIEW_PROBLEM_TITLE, session.problemSlug());
    preview.put(PracticeCodeReviewAgentToolNames.PREVIEW_LANGUAGE_HINT, languageHint(content));
    preview.put(PracticeCodeReviewAgentToolNames.PREVIEW_CODE_LENGTH, content.length());
    preview.put(PracticeCodeReviewAgentToolNames.PREVIEW_CODE_PREVIEW, codePreview(content));
    preview.put(PracticeCodeReviewAgentToolNames.PREVIEW_EFFECTS, REVIEW_EFFECTS);
    preview.put(PracticeCodeReviewAgentToolNames.PREVIEW_CONTEXT_AVAILABLE, true);
    return preview;
  }

  private Map<String, Object> fallbackPreview() {
    return Map.of(
        PracticeCodeReviewAgentToolNames.PREVIEW_EFFECTS,
        REVIEW_EFFECTS,
        PracticeCodeReviewAgentToolNames.PREVIEW_CONTEXT_AVAILABLE,
        false);
  }

  private String codePreview(String content) {
    String preview = redactSensitiveContent(content).trim();
    if (preview.length() <= CODE_PREVIEW_MAX_LENGTH) {
      return preview;
    }
    return preview.substring(0, CODE_PREVIEW_MAX_LENGTH);
  }

  private String redactSensitiveContent(String content) {
    String jwtRedacted = JWT_LIKE_TOKEN_PATTERN.matcher(content).replaceAll("[REDACTED_SECRET]");
    String[] lines = jwtRedacted.split("\\R", -1);
    StringBuilder redacted = new StringBuilder(jwtRedacted.length());
    for (int i = 0; i < lines.length; i++) {
      if (i > 0) {
        redacted.append('\n');
      }
      String line = lines[i];
      redacted.append(containsSensitiveMarker(line) ? "[REDACTED_SECRET]" : line);
    }
    return redacted.toString();
  }

  private boolean containsSensitiveMarker(String line) {
    String lower = line.toLowerCase(Locale.ROOT);
    return lower.contains("authorization")
        || lower.contains("cookie")
        || lower.contains("api_key")
        || lower.contains("api-key")
        || lower.contains("api key")
        || lower.contains("apikey")
        || lower.contains("jwt")
        || lower.contains("bearer ");
  }

  private String languageHint(String content) {
    java.util.regex.Matcher matcher = CODE_FENCE_LANGUAGE_PATTERN.matcher(content);
    if (matcher.find()) {
      String language = matcher.group(1);
      if (language != null && !language.isBlank()) {
        return language.toLowerCase(Locale.ROOT);
      }
    }

    String lower = content.toLowerCase(Locale.ROOT);
    if (lower.contains("class solution") || lower.contains("public static") || lower.contains("public int")) {
      return "java";
    }
    if (lower.contains("#include") || lower.contains("std::")) {
      return "cpp";
    }
    if (lower.contains("def ") || lower.contains("self")) {
      return "python";
    }
    if (lower.contains("function ") || lower.contains("const ")) {
      return "javascript";
    }
    return "unknown";
  }

  private Long positiveLongOrNull(Object value) {
    Long parsed = parseLong(value);
    return parsed == null || parsed < 1 ? null : parsed;
  }

  private Long parseLong(Object value) {
    if (value instanceof Number number) {
      return number.longValue();
    }
    if (value instanceof CharSequence text) {
      try {
        return Long.parseLong(text.toString().trim());
      } catch (NumberFormatException ignored) {
        return null;
      }
    }
    return null;
  }
}
