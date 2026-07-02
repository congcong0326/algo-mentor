package org.congcong.algomentor.mentor.application.review;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

public class ReviewSessionService {

  private final MistakeNoteRepository noteRepository;
  private final ReviewLogRepository logRepository;
  private final ReviewSchedulerService schedulerService;
  private final ReviewCardService cardService;
  private final RecallJudgeService judgeService;
  private final ReviewCardPregenerationService pregenerationService;
  private final ReviewCardProperties cardProperties;
  private final ReviewSchedulerProperties schedulerProperties;
  private final ObjectMapper objectMapper;
  private final MistakeReviewMetrics metrics;
  private final Clock clock;

  public ReviewSessionService(
      MistakeNoteRepository noteRepository,
      ReviewLogRepository logRepository,
      ReviewSchedulerService schedulerService,
      ReviewCardService cardService,
      RecallJudgeService judgeService,
      ReviewCardPregenerationService pregenerationService,
      ReviewCardProperties cardProperties,
      ReviewSchedulerProperties schedulerProperties,
      ObjectMapper objectMapper,
      MistakeReviewMetrics metrics,
      Clock clock
  ) {
    this.noteRepository = Objects.requireNonNull(noteRepository, "noteRepository must not be null");
    this.logRepository = Objects.requireNonNull(logRepository, "logRepository must not be null");
    this.schedulerService = Objects.requireNonNull(schedulerService, "schedulerService must not be null");
    this.cardService = Objects.requireNonNull(cardService, "cardService must not be null");
    this.judgeService = Objects.requireNonNull(judgeService, "judgeService must not be null");
    this.pregenerationService = Objects.requireNonNull(pregenerationService, "pregenerationService must not be null");
    this.cardProperties = Objects.requireNonNull(cardProperties, "cardProperties must not be null");
    this.schedulerProperties = Objects.requireNonNull(schedulerProperties, "schedulerProperties must not be null");
    this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
    this.metrics = Objects.requireNonNull(metrics, "metrics must not be null");
    this.clock = Objects.requireNonNull(clock, "clock must not be null");
  }

  public ReviewQueue dueQueue(long userId, int limit) {
    Instant now = Instant.now(clock);
    int effectiveLimit = limit <= 0 ? 20 : Math.min(limit, 100);
    List<MistakeNote> notes = noteRepository.findDue(userId, now, effectiveLimit);
    notes.stream().limit(cardProperties.prefetchCount()).forEach(note -> pregenerationService.enqueue(note.id()));
    return new ReviewQueue(notes, Math.min(noteRepository.countDue(userId, now), schedulerProperties.queueDailyCap()));
  }

  public ReviewCard card(long userId, long noteId) {
    MistakeNote note = noteRepository.findForUser(userId, noteId)
        .orElseThrow(() -> new MistakeReviewException("MISTAKE_NOTE_NOT_FOUND", "错题记录不存在。"));
    ReviewCard card = cardService.getOrFallback(note);
    if (card.cardVariant() == CardVariant.RULE_BASED) {
      metrics.recordCardGeneration(CardGenerationOutcome.FALLBACK_RULE);
    }
    return card;
  }

  public ReviewCardDetail cardDetail(long userId, long noteId) {
    MistakeNote note = noteRepository.findForUser(userId, noteId)
        .orElseThrow(() -> new MistakeReviewException("MISTAKE_NOTE_NOT_FOUND", "错题记录不存在。"));
    ReviewCard card = cardService.getOrFallback(note);
    if (card.cardVariant() == CardVariant.RULE_BASED) {
      metrics.recordCardGeneration(CardGenerationOutcome.FALLBACK_RULE);
    }
    return new ReviewCardDetail(
        card,
        note.userNotePersistent(),
        logRepository.findRecentRecallHistory(userId, noteId, 5));
  }

  public RecallReviewResult submitRecall(long userId, long noteId, String recallText, String transientNote) {
    MistakeNote note = noteRepository.findForUser(userId, noteId)
        .orElseThrow(() -> new MistakeReviewException("MISTAKE_NOTE_NOT_FOUND", "错题记录不存在。"));
    String effectiveRecall = requireRecallText(recallText);
    RecallJudgment judgment = judgeService.judge(note, effectiveRecall);
    Instant reviewedAt = Instant.now(clock);
    ReviewSchedulerService.Scheduled scheduled = schedulerService.apply(note.scheduling(), judgment.grade(), reviewedAt);
    noteRepository.updateScheduling(noteId, scheduled.state(), scheduled.dueAt(), judgment.grade(), reviewedAt);
    ReviewCard card = cardService.getOrFallback(note);
    JsonNode cardJson = objectMapper.valueToTree(card);
    logRepository.append(new ReviewLogEntry(
        noteId,
        userId,
        ReviewMode.RECALL,
        card.cardVariant(),
        cardJson,
        effectiveRecall,
        truncate(transientNote, MistakeReviewConstants.TRANSIENT_NOTE_MAX_CHARS),
        judgment.grade(),
        GradeSource.AI_RECALL,
        objectMapper.valueToTree(judgment),
        null,
        null,
        note.scheduling().intervalDays(),
        scheduled.state().intervalDays(),
        note.scheduling().easeFactor(),
        scheduled.state().easeFactor(),
        reviewedAt));
    metrics.recordSessionSubmit();
    pregenerationService.enqueue(noteId);
    return new RecallReviewResult(judgment, scheduled.dueAt(), scheduled.state());
  }

  public ReviewSummary summary(long userId) {
    return new ReviewSummary(Math.min(
        noteRepository.countDue(userId, Instant.now(clock)),
        schedulerProperties.queueDailyCap()));
  }

  private String requireRecallText(String recallText) {
    if (recallText == null || recallText.isBlank()) {
      throw new MistakeReviewException("RECALL_TEXT_REQUIRED", "复述内容不能为空。");
    }
    return recallText.trim();
  }

  private String truncate(String text, int maxChars) {
    if (text == null) {
      return "";
    }
    return text.length() <= maxChars ? text : text.substring(0, maxChars);
  }
}
