package org.congcong.algomentor.mentor.application.practice;

import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class PracticeCodeReviewStructuredOutputMapper {

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
      requiredBoolean(structuredOutput, "passed");

      JsonNode scores = requiredObject(structuredOutput, "scores");
      JudgeAssessment judgeAssessment = judgeAssessment(requiredObject(
          structuredOutput,
          PracticeCodeReviewConstants.JSON_JUDGE_ASSESSMENT));
      BigDecimal correctness = score(scores, "correctness", new BigDecimal("4"));
      BigDecimal complexity = score(scores, "complexity", new BigDecimal("2"));
      BigDecimal edgeCases = score(scores, "edgeCases", new BigDecimal("2"));
      BigDecimal codeQuality = score(scores, "codeQuality", BigDecimal.ONE);
      BigDecimal problemFit = score(scores, "problemFit", BigDecimal.ONE);

      List<PracticeCodeReviewEvidence> evidence = new ArrayList<>(evidence(structuredOutput.path("evidence")));
      addJudgeEvidence(evidence, judgeAssessment);

      boolean judgeBlocking = judgeAssessment.blockingIssue()
          || !judgeAssessment.verdict().allowsPassing();
      if (judgeBlocking) {
        correctness = minimum(correctness, PracticeCodeReviewConstants.BLOCKING_CORRECTNESS_CAP);
        if (judgeAssessment.verdict() == PracticeCodeReviewJudgeVerdict.TIME_LIMIT_EXCEEDED) {
          complexity = BigDecimal.ZERO;
        } else if (judgeAssessment.verdict() == PracticeCodeReviewJudgeVerdict.MEMORY_LIMIT_EXCEEDED) {
          complexity = minimum(complexity, PracticeCodeReviewConstants.MEMORY_LIMIT_COMPLEXITY_CAP);
        }
      } else if (!judgeAssessment.meetsExpectedComplexity()) {
        complexity = minimum(complexity, PracticeCodeReviewConstants.SUBOPTIMAL_COMPLEXITY_CAP);
      }

      BigDecimal total = total(correctness, complexity, edgeCases, codeQuality, problemFit);
      if (judgeBlocking && total.compareTo(PracticeCodeReviewConstants.BLOCKING_TOTAL_CAP) > 0) {
        total = PracticeCodeReviewConstants.BLOCKING_TOTAL_CAP;
      }
      if (judgeBlocking) {
        evidence.add(new PracticeCodeReviewEvidence(
            PracticeCodeReviewConstants.EVIDENCE_JUDGE_BLOCKING_CAP,
            "judge verdict %s caps correctness at %s and total score at %s".formatted(
                judgeAssessment.verdict().name(),
                PracticeCodeReviewConstants.BLOCKING_CORRECTNESS_CAP.toPlainString(),
                PracticeCodeReviewConstants.BLOCKING_TOTAL_CAP.toPlainString())));
      } else if (correctness.compareTo(PracticeCodeReviewConstants.BLOCKING_CORRECTNESS_CAP) <= 0
          && total.compareTo(PracticeCodeReviewConstants.BLOCKING_TOTAL_CAP) > 0) {
        total = PracticeCodeReviewConstants.BLOCKING_TOTAL_CAP;
        evidence.add(new PracticeCodeReviewEvidence(
            PracticeCodeReviewConstants.EVIDENCE_CORRECTNESS_BLOCKING_CAP,
            "correctness <= 2 caps total score at 5.0"));
      } else if (!judgeAssessment.meetsExpectedComplexity()) {
        if (total.compareTo(PracticeCodeReviewConstants.SUBOPTIMAL_TOTAL_CAP) > 0) {
          total = PracticeCodeReviewConstants.SUBOPTIMAL_TOTAL_CAP;
        }
        evidence.add(new PracticeCodeReviewEvidence(
            PracticeCodeReviewConstants.EVIDENCE_SUBOPTIMAL_COMPLEXITY_CAP,
            "missing expected complexity caps complexity at 1.0 and total score at 8.0"));
      }

      boolean passed = !judgeBlocking
          && judgeAssessment.verdict().allowsPassing()
          && total.compareTo(PracticeCodeReviewConstants.PASS_SCORE) >= 0;
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
          passed,
          deductionReasons(structuredOutput.path("deductionReasons"), judgeAssessment, judgeBlocking),
          stringList(structuredOutput.path("improvementSuggestions")),
          textValue(structuredOutput, "reviewMarkdown"),
          affectedTagIds(context, structuredOutput.path(PracticeCodeReviewConstants.JSON_AFFECTED_TAG_IDS)));
      return PracticeReviewResult.reviewed(draft);
    } catch (IllegalArgumentException exception) {
      return invalid();
    }
  }

  private JudgeAssessment judgeAssessment(JsonNode node) {
    return new JudgeAssessment(
        enumValue(
            node,
            PracticeCodeReviewConstants.JSON_JUDGE_VERDICT,
            PracticeCodeReviewJudgeVerdict.class),
        enumValue(
            node,
            PracticeCodeReviewConstants.JSON_VERDICT_BASIS,
            PracticeCodeReviewVerdictBasis.class),
        requiredBoolean(node, PracticeCodeReviewConstants.JSON_BLOCKING_ISSUE),
        requiredBoolean(node, PracticeCodeReviewConstants.JSON_MEETS_EXPECTED_COMPLEXITY),
        requiredText(node, PracticeCodeReviewConstants.JSON_TIME_COMPLEXITY),
        requiredText(node, PracticeCodeReviewConstants.JSON_SPACE_COMPLEXITY),
        requiredText(node, PracticeCodeReviewConstants.JSON_EXPECTED_TIME_COMPLEXITY),
        requiredText(node, PracticeCodeReviewConstants.JSON_CONSTRAINT_ANALYSIS));
  }

  private <E extends Enum<E>> E enumValue(JsonNode node, String field, Class<E> enumType) {
    String value = requiredText(node, field);
    try {
      return Enum.valueOf(enumType, value);
    } catch (IllegalArgumentException exception) {
      throw new IllegalArgumentException("Practice review structured output " + field + " is invalid", exception);
    }
  }

  private String requiredText(JsonNode node, String field) {
    JsonNode value = node.path(field);
    if (!value.isTextual() || value.asText().isBlank()) {
      throw new IllegalArgumentException("Practice review structured output " + field + " must be non-blank text");
    }
    return value.asText().trim();
  }

  private BigDecimal total(
      BigDecimal correctness,
      BigDecimal complexity,
      BigDecimal edgeCases,
      BigDecimal codeQuality,
      BigDecimal problemFit
  ) {
    return correctness.add(complexity).add(edgeCases).add(codeQuality).add(problemFit);
  }

  private BigDecimal minimum(BigDecimal value, BigDecimal maximum) {
    return value.compareTo(maximum) > 0 ? maximum : value;
  }

  private void addJudgeEvidence(
      List<PracticeCodeReviewEvidence> evidence,
      JudgeAssessment judgeAssessment
  ) {
    evidence.add(new PracticeCodeReviewEvidence(
        PracticeCodeReviewConstants.EVIDENCE_JUDGE_VERDICT,
        "%s (%s)".formatted(judgeAssessment.verdict().name(), judgeAssessment.basis().name())));
    evidence.add(new PracticeCodeReviewEvidence(
        PracticeCodeReviewConstants.EVIDENCE_TIME_COMPLEXITY,
        judgeAssessment.timeComplexity()));
    evidence.add(new PracticeCodeReviewEvidence(
        PracticeCodeReviewConstants.EVIDENCE_SPACE_COMPLEXITY,
        judgeAssessment.spaceComplexity()));
    evidence.add(new PracticeCodeReviewEvidence(
        PracticeCodeReviewConstants.EVIDENCE_EXPECTED_TIME_COMPLEXITY,
        judgeAssessment.expectedTimeComplexity()));
    evidence.add(new PracticeCodeReviewEvidence(
        PracticeCodeReviewConstants.EVIDENCE_CONSTRAINT_ANALYSIS,
        judgeAssessment.constraintAnalysis()));
  }

  private List<String> deductionReasons(
      JsonNode node,
      JudgeAssessment judgeAssessment,
      boolean judgeBlocking
  ) {
    List<String> values = new ArrayList<>(stringList(node));
    String normalizedReason = null;
    if (judgeBlocking) {
      String description = judgeAssessment.verdict().allowsPassing()
          ? "结构化评审标记了影响通过的阻断问题"
          : judgeAssessment.verdict().descriptionZh();
      normalizedReason = "评测阻断：" + description + "。";
    } else if (!judgeAssessment.meetsExpectedComplexity()) {
      normalizedReason = "复杂度未达到题目预期，复杂度分最高为 1.0，总分最高为 8.0。";
    }
    if (normalizedReason != null && !values.contains(normalizedReason)) {
      values.add(0, normalizedReason);
    }
    return List.copyOf(values);
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

  private record JudgeAssessment(
      PracticeCodeReviewJudgeVerdict verdict,
      PracticeCodeReviewVerdictBasis basis,
      boolean blockingIssue,
      boolean meetsExpectedComplexity,
      String timeComplexity,
      String spaceComplexity,
      String expectedTimeComplexity,
      String constraintAnalysis
  ) {
  }
}
