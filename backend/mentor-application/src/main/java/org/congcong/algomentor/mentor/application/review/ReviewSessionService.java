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
  private final FsrsReviewSchedulerService schedulerService;
  private final ReviewCardService cardService;
  private final RecallJudgeService judgeService;
  private final ReviewRecallEvaluationRepository evaluationRepository;
  private final ReviewPreferenceService preferenceService;
  private final ReviewCardPregenerationService pregenerationService;
  private final ReviewCardProperties cardProperties;
  private final ReviewSchedulerProperties schedulerProperties;
  private final ObjectMapper objectMapper;
  private final MistakeReviewMetrics metrics;
  private final Clock clock;

  public ReviewSessionService(
      MistakeNoteRepository noteRepository,
      ReviewLogRepository logRepository,
      FsrsReviewSchedulerService schedulerService,
      ReviewCardService cardService,
      RecallJudgeService judgeService,
      ReviewRecallEvaluationRepository evaluationRepository,
      ReviewPreferenceService preferenceService,
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
    this.evaluationRepository = Objects.requireNonNull(evaluationRepository, "evaluationRepository must not be null");
    this.preferenceService = Objects.requireNonNull(preferenceService, "preferenceService must not be null");
    this.pregenerationService = Objects.requireNonNull(pregenerationService, "pregenerationService must not be null");
    this.cardProperties = Objects.requireNonNull(cardProperties, "cardProperties must not be null");
    this.schedulerProperties = Objects.requireNonNull(schedulerProperties, "schedulerProperties must not be null");
    this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
    this.metrics = Objects.requireNonNull(metrics, "metrics must not be null");
    this.clock = Objects.requireNonNull(clock, "clock must not be null");
  }

  public ReviewSessionService(
      MistakeNoteRepository noteRepository,
      ReviewLogRepository logRepository,
      ReviewSchedulerService ignoredSchedulerService,
      ReviewCardService cardService,
      RecallJudgeService judgeService,
      ReviewCardPregenerationService pregenerationService,
      ReviewCardProperties cardProperties,
      ReviewSchedulerProperties schedulerProperties,
      ObjectMapper objectMapper,
      MistakeReviewMetrics metrics,
      Clock clock
  ) {
    this(
        noteRepository,
        logRepository,
        new FsrsReviewSchedulerService(schedulerProperties),
        cardService,
        judgeService,
        ReviewRecallEvaluationRepository.memory(),
        new ReviewPreferenceService(ReviewPreferenceRepository.empty(), schedulerProperties, clock),
        pregenerationService,
        cardProperties,
        schedulerProperties,
        objectMapper,
        metrics,
        clock);
  }

  public ReviewQueue dueQueue(long userId, int limit) {
    Instant now = Instant.now(clock);
    ReviewPreference preference = preferenceService.get(userId);
    int effectiveLimit = limit <= 0 ? preference.dailyReviewLimit() : Math.min(limit, 100);
    List<MistakeNote> notes = splitQueue(
        noteRepository.findDue(userId, now, 200),
        preference,
        effectiveLimit);
    notes.stream().limit(cardProperties.prefetchCount()).forEach(note -> pregenerationService.enqueue(note.id()));
    return new ReviewQueue(notes, Math.min(noteRepository.countDue(userId, now), effectiveLimit));
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
    ReviewRecallEvaluationResult evaluation = evaluateRecall(userId, noteId, recallText, transientNote, true);
    RecallConfirmResult confirmed = confirmRecall(
        userId,
        noteId,
        evaluation.evaluation().id(),
        evaluation.evaluation().suggestedRating());
    RecallJudgment judgment = new RecallJudgment(
        confirmed.rating().legacyGrade(),
        evaluation.evaluation().hitPoints(),
        evaluation.evaluation().missedPoints(),
        evaluation.evaluation().gapSummary());
    return new RecallReviewResult(judgment, confirmed.nextDueAt(), confirmed.scheduling());
  }

  public ReviewRecallEvaluationResult evaluateRecall(
      long userId,
      long noteId,
      String recallText,
      String transientNote
  ) {
    return evaluateRecall(userId, noteId, recallText, transientNote, false);
  }

  private ReviewRecallEvaluationResult evaluateRecall(
      long userId,
      long noteId,
      String recallText,
      String transientNote,
      boolean forceAi
  ) {
    MistakeNote note = noteRepository.findForUser(userId, noteId)
        .orElseThrow(() -> new MistakeReviewException("MISTAKE_NOTE_NOT_FOUND", "错题记录不存在。"));
    String effectiveRecall = requireRecallText(recallText);
    ReviewPreference preference = preferenceService.get(userId);
    boolean useAi = forceAi || preference.aiSuggestionEnabled();
    RecallJudgment judgment = useAi
        ? judgeService.judge(note, effectiveRecall)
        : new RecallJudgment(ReviewGrade.MASTERED, List.of(), List.of(), "");
    ReviewRating suggestedRating = useAi ? ReviewRating.fromGrade(judgment.grade()) : null;
    ReviewRecallEvaluation saved = evaluationRepository.save(new ReviewRecallEvaluation(
        0L,
        noteId,
        userId,
        effectiveRecall,
        truncate(transientNote, MistakeReviewConstants.TRANSIENT_NOTE_MAX_CHARS),
        suggestedRating,
        judgment.hitPoints(),
        judgment.missedPoints(),
        judgment.gapSummary(),
        useAi,
        Instant.now(clock)));
    Instant now = Instant.now(clock);
    return new ReviewRecallEvaluationResult(
        saved,
        List.of(
            schedulerService.preview(note, ReviewRating.AGAIN, preference, now),
            schedulerService.preview(note, ReviewRating.HARD, preference, now),
            schedulerService.preview(note, ReviewRating.GOOD, preference, now),
            schedulerService.preview(note, ReviewRating.EASY, preference, now)));
  }

  public RecallConfirmResult confirmRecall(long userId, long noteId, long evaluationId, ReviewRating rating) {
    MistakeNote note = noteRepository.findForUser(userId, noteId)
        .orElseThrow(() -> new MistakeReviewException("MISTAKE_NOTE_NOT_FOUND", "错题记录不存在。"));
    ReviewRecallEvaluation evaluation = evaluationRepository.findForUser(userId, evaluationId)
        .filter(item -> item.noteId() == noteId)
        .orElseThrow(() -> new MistakeReviewException("RECALL_EVALUATION_NOT_FOUND", "复述评估不存在或已失效。"));
    ReviewRating finalRating = Objects.requireNonNull(rating, "rating must not be null");
    return applyRecallRating(
        userId,
        noteId,
        note,
        finalRating,
        evaluation.recallText(),
        evaluation.transientNote(),
        objectMapper.valueToTree(evaluation),
        evaluation.suggestedRating(),
        evaluation.aiSuggested());
  }

  public RecallConfirmResult rateRecall(long userId, long noteId, ReviewRating rating) {
    MistakeNote note = noteRepository.findForUser(userId, noteId)
        .orElseThrow(() -> new MistakeReviewException("MISTAKE_NOTE_NOT_FOUND", "错题记录不存在。"));
    ReviewRating finalRating = Objects.requireNonNull(rating, "rating must not be null");
    return applyRecallRating(
        userId,
        noteId,
        note,
        finalRating,
        null,
        null,
        null,
        null,
        false);
  }

  private RecallConfirmResult applyRecallRating(
      long userId,
      long noteId,
      MistakeNote note,
      ReviewRating finalRating,
      String userRecallText,
      String transientNote,
      JsonNode aiJudgmentJson,
      ReviewRating suggestedRating,
      boolean aiSuggested
  ) {
    Instant reviewedAt = Instant.now(clock);
    ReviewPreference preference = preferenceService.get(userId);
    FsrsReviewSchedulerService.Scheduled scheduled = schedulerService.apply(note, finalRating, preference, reviewedAt);
    noteRepository.updateScheduling(
        noteId,
        scheduled.state(),
        scheduled.dueAt(),
        finalRating.legacyGrade(),
        finalRating,
        reviewedAt);
    ReviewCard card = cardService.getOrFallback(note);
    JsonNode cardJson = objectMapper.valueToTree(card);
    logRepository.append(new ReviewLogEntry(
        noteId,
        userId,
        ReviewMode.RECALL,
        card.cardVariant(),
        cardJson,
        userRecallText,
        transientNote,
        finalRating.legacyGrade(),
        finalRating,
        GradeSource.SELF,
        aiJudgmentJson,
        null,
        null,
        note.scheduling().intervalDays(),
        scheduled.state().intervalDays(),
        note.scheduling().easeFactor(),
        scheduled.state().easeFactor(),
        reviewedAt));
    metrics.recordSessionSubmit();
    pregenerationService.enqueue(noteId);
    return new RecallConfirmResult(
        finalRating,
        suggestedRating,
        scheduled.dueAt(),
        scheduled.state(),
        aiSuggested);
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

  private List<MistakeNote> splitQueue(List<MistakeNote> due, ReviewPreference preference, int limit) {
    List<MistakeNote> learning = due.stream()
        .filter(note -> isLearning(note) && note.lastReviewedAt() != null)
        .limit(preference.dailyLearningLimit())
        .toList();
    List<MistakeNote> reviews = due.stream()
        .filter(note -> "REVIEW".equals(note.scheduling().fsrsState()))
        .limit(preference.dailyReviewLimit())
        .toList();
    List<MistakeNote> newLike = due.stream()
        .filter(note -> isLearning(note) && note.lastReviewedAt() == null)
        .limit(preference.dailyNewLimit())
        .toList();
    return java.util.stream.Stream.of(learning, reviews, newLike)
        .flatMap(List::stream)
        .limit(limit)
        .toList();
  }

  private boolean isLearning(MistakeNote note) {
    return "LEARNING".equals(note.scheduling().fsrsState()) || "RELEARNING".equals(note.scheduling().fsrsState());
  }
}
