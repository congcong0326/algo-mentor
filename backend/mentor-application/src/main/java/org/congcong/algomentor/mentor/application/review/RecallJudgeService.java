package org.congcong.algomentor.mentor.application.review;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.congcong.algomentor.llm.core.exception.LlmException;
import org.congcong.algomentor.llm.core.gateway.LlmGateway;
import org.congcong.algomentor.llm.core.model.LlmModelSelector;
import org.congcong.algomentor.llm.core.provider.LlmCapability;
import org.congcong.algomentor.llm.core.request.LlmCompletionRequest;
import org.congcong.algomentor.llm.core.request.LlmMessage;
import org.congcong.algomentor.llm.core.request.LlmResponseFormat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RecallJudgeService {

  private static final Logger log = LoggerFactory.getLogger(RecallJudgeService.class);

  private final LlmGateway llmGateway;
  private final ObjectMapper objectMapper;
  private final MistakeReviewMetrics metrics;

  public RecallJudgeService(LlmGateway llmGateway, ObjectMapper objectMapper, MistakeReviewMetrics metrics) {
    this.llmGateway = Objects.requireNonNull(llmGateway, "llmGateway must not be null");
    this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
    this.metrics = Objects.requireNonNull(metrics, "metrics must not be null");
  }

  public RecallJudgment judge(MistakeNote note, String recallText) {
    try {
      RecallJudgment judgment = objectMapper.convertValue(
          llmGateway.complete(request(note, recallText)).structuredOutput(),
          RecallJudgment.class);
      metrics.recordRecallJudge(judgment.grade(), RecallJudgeOutcome.COMPLETED);
      return judgment;
    } catch (RuntimeException exception) {
      if (exception instanceof LlmException llmException) {
        log.warn(
            "Recall judge LLM failed. noteId={} userId={} problemSlug={} code={} retryable={} provider={} model={}",
            note.id(),
            note.userId(),
            note.problemSlug(),
            llmException.code(),
            llmException.retryable(),
            llmException.provider() == null ? "" : llmException.provider().value(),
            llmException.model() == null ? "" : llmException.model().value(),
            exception);
      } else {
        log.warn("Recall judge failed. noteId={} userId={} problemSlug={} exceptionType={}",
            note.id(), note.userId(), note.problemSlug(), exception.getClass().getSimpleName(), exception);
      }
      RecallJudgment fallback = new RecallJudgment(
          ReviewGrade.BARELY,
          List.of(),
          List.of(),
          "本次判定异常，按保守处理");
      metrics.recordRecallJudge(fallback.grade(), RecallJudgeOutcome.FAILED);
      return fallback;
    }
  }

  private LlmCompletionRequest request(MistakeNote note, String recallText) {
    return LlmCompletionRequest.builder()
        .modelSelector(LlmModelSelector.requiring(Set.of(LlmCapability.JSON_SCHEMA_OUTPUT)))
        .messages(List.of(
            LlmMessage.system("""
                你是 algo-mentor 的错题复述判定器。只输出结构化 JSON。
                按算法选型、关键步骤、复杂度、边界四项判断用户是否真正理解。
                无官方题解时基于通用算法知识判断；拿不准时偏保守给 BARELY。
                """.stripIndent()),
            LlmMessage.user("""
                题目 slug：%s
                题目标题：%s
                难度：%s
                复习卡关注点：%s
                用户复述：
                %s
                """.stripIndent().formatted(
                note.problemSlug(),
                note.sourceDetail().getOrDefault("titleCn", note.problemSlug()),
                note.sourceDetail().getOrDefault("difficulty", "UNKNOWN"),
                note.sourceDetail().getOrDefault("deductionReasons", "未记录"),
                safeRecallText(recallText)))))
        .responseFormat(new LlmResponseFormat.JsonSchema(
            MistakeReviewConstants.JUDGE_SCHEMA_NAME,
            RecallJudgeJsonSchema.schema(),
            true))
        .metadata(Map.of(MistakeReviewConstants.METADATA_MISTAKE_NOTE_ID, note.id()))
        .build();
  }

  private String safeRecallText(String recallText) {
    if (recallText == null || recallText.isBlank()) {
      return "(空)";
    }
    return recallText.length() <= 4000 ? recallText : recallText.substring(0, 4000);
  }
}
