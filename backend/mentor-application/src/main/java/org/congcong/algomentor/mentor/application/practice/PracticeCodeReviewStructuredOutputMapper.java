package org.congcong.algomentor.mentor.application.practice;

import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class PracticeCodeReviewStructuredOutputMapper {

  private static final BigDecimal CORRECTNESS_BLOCKING_THRESHOLD = new BigDecimal("2.0");
  private static final BigDecimal CORRECTNESS_BLOCKING_TOTAL_CAP = new BigDecimal("5.0");

  public PracticeReviewResult map(PracticeTurnContext context, JsonNode structuredOutput) {
    if (context == null || structuredOutput == null || !structuredOutput.isObject()) {
      return invalid();
    }
    try {
      if (!requiredBoolean(structuredOutput, "isCodeSubmission")) {
        return PracticeReviewResult.notCodeLike();
      }
      if (!requiredBoolean(structuredOutput, "belongsToCurrentProblem")
          || !requiredBoolean(structuredOutput, "isCompleteLeetCodeSolution")) {
        return PracticeReviewResult.notCompleteSubmission();
      }

      String rawCode = textValue(structuredOutput, "rawCode");
      String normalizedCode = textValue(structuredOutput, "normalizedCode");
      if (rawCode.isBlank() || normalizedCode.isBlank()) {
        return invalid();
      }

      JsonNode scores = requiredObject(structuredOutput, "scores");
      BigDecimal correctness = score(scores, "correctness", new BigDecimal("4"));
      BigDecimal complexity = score(scores, "complexity", new BigDecimal("2"));
      BigDecimal edgeCases = score(scores, "edgeCases", new BigDecimal("2"));
      BigDecimal codeQuality = score(scores, "codeQuality", BigDecimal.ONE);
      BigDecimal problemFit = score(scores, "problemFit", BigDecimal.ONE);

      BigDecimal total = correctness
          .add(complexity)
          .add(edgeCases)
          .add(codeQuality)
          .add(problemFit);
      List<PracticeCodeReviewEvidence> evidence = new ArrayList<>(evidence(structuredOutput.path("evidence")));
      if (correctness.compareTo(CORRECTNESS_BLOCKING_THRESHOLD) <= 0
          && total.compareTo(CORRECTNESS_BLOCKING_TOTAL_CAP) > 0) {
        total = CORRECTNESS_BLOCKING_TOTAL_CAP;
        evidence.add(new PracticeCodeReviewEvidence(
            PracticeCodeReviewConstants.EVIDENCE_CORRECTNESS_BLOCKING_CAP,
            "correctness <= 2 caps total score at 5.0"));
      }

      PracticeCodeReviewScore normalizedScore = new PracticeCodeReviewScore(
          correctness,
          complexity,
          edgeCases,
          codeQuality,
          problemFit,
          total);
      PracticeCodeReviewDraft draft = new PracticeCodeReviewDraft(
          context.userId(),
          context.planId(),
          context.phaseIndex(),
          context.problemSlug(),
          context.sessionId(),
          context.userMessageId(),
          context.assistantMessageId(),
          context.agentRunDbId(),
          rawCode,
          normalizedCode,
          textValue(structuredOutput, "language"),
          evidence,
          textValue(structuredOutput, "contextSummary"),
          normalizedScore,
          total.compareTo(PracticeCodeReviewConstants.PASS_SCORE) >= 0,
          stringList(structuredOutput.path("deductionReasons")),
          stringList(structuredOutput.path("improvementSuggestions")),
          textValue(structuredOutput, "reviewMarkdown"),
          affectedTagIds(context, structuredOutput.path(PracticeCodeReviewConstants.JSON_AFFECTED_TAG_IDS)));
      return PracticeReviewResult.reviewed(draft);
    } catch (IllegalArgumentException exception) {
      return invalid();
    }
  }

  private PracticeReviewResult invalid() {
    return PracticeReviewResult.failed(PracticeReviewResult.INVALID_STRUCTURED_OUTPUT);
  }

  private boolean requiredBoolean(JsonNode node, String field) {
    JsonNode value = node.path(field);
    if (!value.isBoolean()) {
      throw new IllegalArgumentException("Practice review structured output " + field + " must be boolean");
    }
    return value.booleanValue();
  }

  private JsonNode requiredObject(JsonNode node, String field) {
    JsonNode value = node.path(field);
    if (!value.isObject()) {
      throw new IllegalArgumentException("Practice review structured output " + field + " must be an object");
    }
    return value;
  }

  private BigDecimal score(JsonNode node, String field, BigDecimal maximum) {
    JsonNode value = node.path(field);
    if (!value.isNumber()) {
      throw new IllegalArgumentException("Practice review score " + field + " must be numeric");
    }
    BigDecimal score = value.decimalValue();
    if (score.compareTo(BigDecimal.ZERO) < 0 || score.compareTo(maximum) > 0) {
      throw new IllegalArgumentException("Practice review score " + field + " is outside allowed range");
    }
    return score;
  }

  private List<PracticeCodeReviewEvidence> evidence(JsonNode node) {
    if (!node.isArray()) {
      return List.of();
    }
    List<PracticeCodeReviewEvidence> evidence = new ArrayList<>();
    for (JsonNode item : node) {
      String type = textValue(item, "type");
      String value = textValue(item, "value");
      if (!type.isBlank() && !value.isBlank()) {
        evidence.add(new PracticeCodeReviewEvidence(type, value));
      }
    }
    return evidence;
  }

  private List<String> stringList(JsonNode node) {
    if (!node.isArray()) {
      return List.of();
    }
    List<String> values = new ArrayList<>();
    for (JsonNode item : node) {
      if (item.isTextual() && !item.asText().isBlank()) {
        values.add(item.asText().trim());
      }
    }
    return values;
  }

  private List<Long> affectedTagIds(PracticeTurnContext context, JsonNode node) {
    if (!node.isArray()) {
      throw new IllegalArgumentException("Practice review affectedTagIds must be an array");
    }
    Set<Long> candidates = context.trustedProblemTags().stream().map(TrustedProblemTag::tagId)
        .collect(java.util.stream.Collectors.toSet());
    LinkedHashSet<Long> accepted = new LinkedHashSet<>();
    for (JsonNode item : node) {
      if (item.isIntegralNumber() && item.canConvertToLong() && item.longValue() > 0
          && candidates.contains(item.longValue())) {
        accepted.add(item.longValue());
      }
    }
    return List.copyOf(accepted);
  }

  private String textValue(JsonNode node, String field) {
    JsonNode value = node.path(field);
    return value.isTextual() ? value.asText().trim() : "";
  }
}
