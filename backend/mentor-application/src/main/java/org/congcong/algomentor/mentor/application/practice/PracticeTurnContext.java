package org.congcong.algomentor.mentor.application.practice;

public record PracticeTurnContext(
    long userId,
    long planId,
    int phaseIndex,
    String problemSlug,
    long sessionId,
    long userMessageId,
    Long assistantMessageId,
    Long agentRunDbId,
    String problemFacts,
    String learningPlanFacts,
    String extractedCode,
    String originalMessage,
    String recentChatSummary,
    String locale,
    java.util.List<TrustedProblemTag> trustedProblemTags
) {

  public PracticeTurnContext(
      long userId, long planId, int phaseIndex, String problemSlug, long sessionId, long userMessageId,
      Long assistantMessageId, Long agentRunDbId, String problemFacts, String learningPlanFacts,
      String extractedCode, String originalMessage, String recentChatSummary, String locale) {
    this(userId, planId, phaseIndex, problemSlug, sessionId, userMessageId, assistantMessageId, agentRunDbId,
        problemFacts, learningPlanFacts, extractedCode, originalMessage, recentChatSummary, locale, java.util.List.of());
  }

  public PracticeTurnContext {
    requirePositive(userId, "user id");
    requirePositive(planId, "plan id");
    if (phaseIndex < 1) {
      throw new IllegalArgumentException("Practice turn phase index must be positive");
    }
    if (problemSlug == null || problemSlug.isBlank()) {
      throw new IllegalArgumentException("Practice turn problem slug must not be blank");
    }
    requirePositive(sessionId, "session id");
    requirePositive(userMessageId, "user message id");
    requirePositiveIfPresent(assistantMessageId, "assistant message id");
    requirePositiveIfPresent(agentRunDbId, "agent run database id");
    problemSlug = problemSlug.trim();
    problemFacts = blankToEmpty(problemFacts);
    learningPlanFacts = blankToEmpty(learningPlanFacts);
    extractedCode = blankToEmpty(extractedCode);
    originalMessage = blankToEmpty(originalMessage);
    recentChatSummary = blankToEmpty(recentChatSummary);
    locale = locale == null || locale.isBlank() ? "zh-CN" : locale.trim();
    trustedProblemTags = trustedProblemTags == null ? java.util.List.of() : trustedProblemTags.stream()
        .filter(java.util.Objects::nonNull).distinct().toList();
  }

  private static void requirePositive(long value, String fieldName) {
    if (value < 1) {
      throw new IllegalArgumentException("Practice turn " + fieldName + " must be positive");
    }
  }

  private static void requirePositiveIfPresent(Long value, String fieldName) {
    if (value != null && value < 1) {
      throw new IllegalArgumentException("Practice turn " + fieldName + " must be positive");
    }
  }

  private static String blankToEmpty(String value) {
    return value == null || value.isBlank() ? "" : value.trim();
  }
}
