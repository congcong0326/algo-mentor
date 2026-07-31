package org.congcong.algomentor.mentor.application.practice;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.congcong.algomentor.agent.core.AgentExecutionContext;
import org.congcong.algomentor.agent.core.AgentTool;
import org.congcong.algomentor.agent.core.runtime.model.AgentRuntimeMetadataKeys;
import org.congcong.algomentor.agent.core.runtime.model.AgentTurnMessages;
import org.congcong.algomentor.agent.core.runtime.repository.AgentTurnMessageLookupRepository;
import org.congcong.algomentor.llm.core.tool.LlmToolSpec;
import org.congcong.algomentor.mentor.application.profile.review.history.CodeReviewHistory;
import org.congcong.algomentor.mentor.application.profile.review.history.CodeReviewHistoryRepository;
import org.congcong.algomentor.mentor.application.review.card.ProblemReviewCard;
import org.congcong.algomentor.mentor.application.review.card.ReviewCardRepository;
import org.congcong.algomentor.mentor.application.review.note.ProblemComplexityValue;
import org.congcong.algomentor.mentor.application.review.note.ProblemSolutionOutlineV1;
import org.congcong.algomentor.mentor.application.review.note.UserProblemNote;
import org.congcong.algomentor.mentor.application.review.note.UserProblemNoteRepository;
import org.congcong.algomentor.mentor.application.review.note.UserProblemNoteSummary;

/** 按需读取当前 Practice Chat 题目的学习状态，不接受用户、session 或题目参数。 */
public final class GetCurrentProblemLearningStateAgentTool implements AgentTool {

  private static final LlmToolSpec SPEC = new LlmToolSpec(
      PracticeLearningStateAgentToolContracts.TOOL_NAME,
      """
          Read the active practice problem's current completion status, latest formal Review summary, review schedule, \
          and saved note outline. The server derives the user, practice session, plan, phase, and problem from trusted \
          execution metadata. Set includeNoteBody=true only when the current user message explicitly asks to read the \
          saved note body or full note content. Use get_problem_review_trajectory for detailed multi-version changes. \
          This tool never returns source code, full chat history, or full Review Markdown.
          """.strip(),
      inputSchema(),
      true);

  private final PracticeSessionRepository sessionRepository;
  private final AgentTurnMessageLookupRepository turnMessageLookupRepository;
  private final CodeReviewHistoryRepository reviewHistoryRepository;
  private final ReviewCardRepository reviewCardRepository;
  private final UserProblemNoteRepository noteRepository;
  private final PracticeNoteBodyAccessPolicy noteBodyAccessPolicy;

  public GetCurrentProblemLearningStateAgentTool(
      PracticeSessionRepository sessionRepository,
      AgentTurnMessageLookupRepository turnMessageLookupRepository,
      CodeReviewHistoryRepository reviewHistoryRepository,
      ReviewCardRepository reviewCardRepository,
      UserProblemNoteRepository noteRepository
  ) {
    this.sessionRepository = Objects.requireNonNull(sessionRepository, "sessionRepository must not be null");
    this.turnMessageLookupRepository = Objects.requireNonNull(
        turnMessageLookupRepository, "turnMessageLookupRepository must not be null");
    this.reviewHistoryRepository = Objects.requireNonNull(
        reviewHistoryRepository, "reviewHistoryRepository must not be null");
    this.reviewCardRepository = Objects.requireNonNull(reviewCardRepository, "reviewCardRepository must not be null");
    this.noteRepository = Objects.requireNonNull(noteRepository, "noteRepository must not be null");
    this.noteBodyAccessPolicy = new PracticeNoteBodyAccessPolicy();
  }

  @Override
  public LlmToolSpec spec() {
    return SPEC;
  }

  @Override
  public JsonNode execute(JsonNode arguments, AgentExecutionContext context) {
    Boolean includeNoteBody = includeNoteBody(arguments);
    if (includeNoteBody == null) {
      return failure(PracticeLearningStateAgentToolContracts.FAILURE_INVALID_ARGUMENTS);
    }
    TrustedPracticeContext trustedContext = trustedContext(context, includeNoteBody);
    if (trustedContext.failureCode() != null) {
      return failure(trustedContext.failureCode());
    }

    try {
      Optional<PracticeSession> sessionResult = sessionRepository.findSessionForUser(
          trustedContext.sessionId(), trustedContext.userId());
      if (sessionResult.isEmpty()) {
        return failure(PracticeLearningStateAgentToolContracts.FAILURE_PRACTICE_SESSION_NOT_FOUND);
      }
      PracticeSession session = sessionResult.orElseThrow();
      if (!matchesCurrentProblem(session, trustedContext)) {
        return failure(PracticeLearningStateAgentToolContracts.FAILURE_CURRENT_PROBLEM_MISMATCH);
      }

      boolean bodyAuthorized = includeNoteBody && noteBodyExplicitlyRequested(trustedContext.runDbId());
      Optional<CodeReviewHistory> latestReview = reviewHistoryRepository.findLatestForProblem(
              trustedContext.userId(), session.problemSlug(), 1).stream()
          .max(Comparator.comparingInt(CodeReviewHistory::versionNo)
              .thenComparing(CodeReviewHistory::createdAt));
      Optional<ProblemReviewCard> reviewCard = reviewCardRepository.findByUserAndSlug(
          trustedContext.userId(), session.problemSlug());
      NoteSnapshot note = loadNote(
          trustedContext.userId(), session.problemSlug(), includeNoteBody, bodyAuthorized);
      return render(session, latestReview, reviewCard, note);
    } catch (RuntimeException exception) {
      return failure(PracticeLearningStateAgentToolContracts.FAILURE_INTERNAL);
    }
  }

  private NoteSnapshot loadNote(long userId, String problemSlug, boolean bodyRequested, boolean bodyAuthorized) {
    if (bodyRequested && bodyAuthorized) {
      Optional<UserProblemNote> note = noteRepository.find(userId, problemSlug);
      if (note.isEmpty()) {
        return NoteSnapshot.empty(userId, problemSlug, PracticeLearningStateAgentToolContracts.NOTE_BODY_EMPTY);
      }
      UserProblemNote value = note.orElseThrow();
      String bodyStatus = value.noteMarkdown().isBlank()
          ? PracticeLearningStateAgentToolContracts.NOTE_BODY_EMPTY
          : PracticeLearningStateAgentToolContracts.NOTE_BODY_INCLUDED;
      return new NoteSnapshot(UserProblemNoteSummary.from(value), value.noteMarkdown(), bodyStatus);
    }

    String bodyStatus = bodyRequested
        ? PracticeLearningStateAgentToolContracts.NOTE_BODY_EXPLICIT_REQUEST_REQUIRED
        : PracticeLearningStateAgentToolContracts.NOTE_BODY_NOT_REQUESTED;
    return noteRepository.findSummary(userId, problemSlug)
        .map(summary -> new NoteSnapshot(summary, null, bodyStatus))
        .orElseGet(() -> NoteSnapshot.empty(userId, problemSlug, bodyStatus));
  }

  private boolean noteBodyExplicitlyRequested(long runDbId) {
    return turnMessageLookupRepository.findByRunId(runDbId)
        .map(AgentTurnMessages::userMessage)
        .map(message -> noteBodyAccessPolicy.isExplicitlyRequested(message.content()))
        .orElse(false);
  }

  private boolean matchesCurrentProblem(PracticeSession session, TrustedPracticeContext context) {
    return session.id() == context.sessionId()
        && session.userId() == context.userId()
        && session.planId() == context.planId()
        && session.phaseIndex() == context.phaseIndex()
        && session.problemSlug().equals(context.problemSlug());
  }

  private JsonNode render(
      PracticeSession session,
      Optional<CodeReviewHistory> latestReview,
      Optional<ProblemReviewCard> reviewCard,
      NoteSnapshot note
  ) {
    ObjectNode root = JsonNodeFactory.instance.objectNode();
    root.put(PracticeLearningStateAgentToolContracts.FIELD_TYPE, PracticeLearningStateAgentToolContracts.RESULT_TYPE);
    root.put(PracticeLearningStateAgentToolContracts.FIELD_STATUS, PracticeLearningStateAgentToolContracts.STATUS_OK);
    root.put(PracticeLearningStateAgentToolContracts.FIELD_PROBLEM_SLUG, session.problemSlug());
    writePractice(root.putObject(PracticeLearningStateAgentToolContracts.FIELD_PRACTICE), session);
    writeLatestReview(root.putObject(PracticeLearningStateAgentToolContracts.FIELD_LATEST_FORMAL_REVIEW), latestReview);
    writeReviewSchedule(root.putObject(PracticeLearningStateAgentToolContracts.FIELD_REVIEW_SCHEDULE), reviewCard);
    writeNote(root.putObject(PracticeLearningStateAgentToolContracts.FIELD_NOTE), note);
    return root;
  }

  private void writePractice(ObjectNode target, PracticeSession session) {
    target.put(PracticeLearningStateAgentToolContracts.FIELD_PROGRESS_STATUS, session.progressStatus().name());
    target.put(PracticeLearningStateAgentToolContracts.FIELD_COMPLETED,
        session.progressStatus() == PracticeProgressStatus.COMPLETED);
    target.put(PracticeLearningStateAgentToolContracts.FIELD_SKIPPED,
        session.progressStatus() == PracticeProgressStatus.SKIPPED);
    writeInstant(target, PracticeLearningStateAgentToolContracts.FIELD_UPDATED_AT, session.updatedAt());
  }

  private void writeLatestReview(ObjectNode target, Optional<CodeReviewHistory> reviewResult) {
    target.put(PracticeLearningStateAgentToolContracts.FIELD_EXISTS, reviewResult.isPresent());
    reviewResult.ifPresent(review -> {
      target.put(PracticeLearningStateAgentToolContracts.FIELD_REVIEW_ID, review.reviewId());
      target.put(PracticeLearningStateAgentToolContracts.FIELD_VERSION_NO, review.versionNo());
      writeInstant(target, PracticeLearningStateAgentToolContracts.FIELD_CREATED_AT, review.createdAt());
      target.put(PracticeLearningStateAgentToolContracts.FIELD_TOTAL_SCORE, review.totalScore());
      target.put(PracticeLearningStateAgentToolContracts.FIELD_PASSED, review.passed());
      writeStrings(target.putArray(PracticeLearningStateAgentToolContracts.FIELD_DEDUCTION_REASONS),
          review.deductionReasons());
      writeStrings(target.putArray(PracticeLearningStateAgentToolContracts.FIELD_IMPROVEMENT_SUGGESTIONS),
          review.improvementSuggestions());
      review.affectedTagIds().forEach(
          target.putArray(PracticeLearningStateAgentToolContracts.FIELD_AFFECTED_TAG_IDS)::add);
    });
  }

  private void writeReviewSchedule(ObjectNode target, Optional<ProblemReviewCard> cardResult) {
    target.put(PracticeLearningStateAgentToolContracts.FIELD_EXISTS, cardResult.isPresent());
    cardResult.ifPresent(card -> {
      target.put(PracticeLearningStateAgentToolContracts.FIELD_ARCHIVED, card.archived());
      writeInstant(target, PracticeLearningStateAgentToolContracts.FIELD_DUE_AT, card.dueAt());
      writeInstant(target, PracticeLearningStateAgentToolContracts.FIELD_LAST_REVIEWED_AT, card.lastReviewedAt());
      if (card.lastRating() == null) {
        target.putNull(PracticeLearningStateAgentToolContracts.FIELD_LAST_RATING);
      } else {
        target.put(PracticeLearningStateAgentToolContracts.FIELD_LAST_RATING, card.lastRating().name());
      }
      target.put(PracticeLearningStateAgentToolContracts.FIELD_FSRS_STATE, card.scheduling().fsrsState().name());
      target.put(PracticeLearningStateAgentToolContracts.FIELD_REPETITIONS, card.scheduling().repetitions());
      target.put(PracticeLearningStateAgentToolContracts.FIELD_INTERVAL_DAYS, card.scheduling().intervalDays());
      target.put(PracticeLearningStateAgentToolContracts.FIELD_LAPSES, card.scheduling().lapses());
    });
  }

  private void writeNote(ObjectNode target, NoteSnapshot note) {
    UserProblemNoteSummary summary = note.summary();
    target.put(PracticeLearningStateAgentToolContracts.FIELD_EXISTS, summary.exists());
    target.put(PracticeLearningStateAgentToolContracts.FIELD_HAS_CONTENT, summary.hasContent());
    target.put(PracticeLearningStateAgentToolContracts.FIELD_REVISION, summary.revision());
    writeInstant(target, PracticeLearningStateAgentToolContracts.FIELD_UPDATED_AT, summary.updatedAt());
    writeOutline(target.putObject(PracticeLearningStateAgentToolContracts.FIELD_OUTLINE), summary.outline());
    target.put(PracticeLearningStateAgentToolContracts.FIELD_NOTE_BODY_STATUS, note.bodyStatus());
    boolean bodyIncluded = PracticeLearningStateAgentToolContracts.NOTE_BODY_INCLUDED.equals(note.bodyStatus());
    target.put(PracticeLearningStateAgentToolContracts.FIELD_NOTE_BODY_INCLUDED, bodyIncluded);
    if (bodyIncluded) {
      target.put(PracticeLearningStateAgentToolContracts.FIELD_NOTE_MARKDOWN, note.noteMarkdown());
    }
  }

  private void writeOutline(ObjectNode target, ProblemSolutionOutlineV1 outline) {
    target.put(PracticeLearningStateAgentToolContracts.FIELD_SCHEMA_VERSION, outline.schemaVersion());
    target.put(PracticeLearningStateAgentToolContracts.FIELD_CORE_IDEA, outline.coreIdea());
    outline.dataStructures().forEach(
        value -> target.withArray(PracticeLearningStateAgentToolContracts.FIELD_DATA_STRUCTURES).add(value.name()));
    writeStrings(target.putArray(PracticeLearningStateAgentToolContracts.FIELD_CUSTOM_DATA_STRUCTURES),
        outline.customDataStructures());
    target.put(PracticeLearningStateAgentToolContracts.FIELD_DATA_STRUCTURE_NOTES, outline.dataStructureNotes());
    outline.algorithms().forEach(
        value -> target.withArray(PracticeLearningStateAgentToolContracts.FIELD_ALGORITHMS).add(value.name()));
    writeStrings(target.putArray(PracticeLearningStateAgentToolContracts.FIELD_CUSTOM_ALGORITHMS),
        outline.customAlgorithms());
    target.put(PracticeLearningStateAgentToolContracts.FIELD_ALGORITHM_NOTES, outline.algorithmNotes());
    writeComplexity(target.putObject(PracticeLearningStateAgentToolContracts.FIELD_TIME_COMPLEXITY),
        outline.timeComplexity());
    writeComplexity(target.putObject(PracticeLearningStateAgentToolContracts.FIELD_SPACE_COMPLEXITY),
        outline.spaceComplexity());
    target.put(PracticeLearningStateAgentToolContracts.FIELD_EDGE_CASES, outline.edgeCases());
  }

  private void writeComplexity(ObjectNode target, ProblemComplexityValue complexity) {
    if (complexity.key() == null) {
      target.putNull(PracticeLearningStateAgentToolContracts.FIELD_KEY);
    } else {
      target.put(PracticeLearningStateAgentToolContracts.FIELD_KEY, complexity.key().name());
    }
    if (complexity.customText() == null) {
      target.putNull(PracticeLearningStateAgentToolContracts.FIELD_CUSTOM_TEXT);
    } else {
      target.put(PracticeLearningStateAgentToolContracts.FIELD_CUSTOM_TEXT, complexity.customText());
    }
  }

  private void writeStrings(ArrayNode target, List<String> values) {
    values.forEach(target::add);
  }

  private void writeInstant(ObjectNode target, String field, Instant value) {
    if (value == null) {
      target.putNull(field);
    } else {
      target.put(field, value.toString());
    }
  }

  private TrustedPracticeContext trustedContext(AgentExecutionContext context, boolean noteBodyRequested) {
    if (context == null) {
      return TrustedPracticeContext.failed(PracticeLearningStateAgentToolContracts.FAILURE_MISSING_METADATA);
    }
    Map<String, Object> metadata = context.requestMetadata();
    Object scenario = metadata.get(PracticeChatPromptConstants.METADATA_SCENARIO);
    if (scenario == null || scenario.toString().isBlank()) {
      return TrustedPracticeContext.failed(PracticeLearningStateAgentToolContracts.FAILURE_MISSING_METADATA);
    }
    if (!PracticeChatPromptConstants.SCENARIO.equals(scenario.toString())) {
      return TrustedPracticeContext.failed(PracticeLearningStateAgentToolContracts.FAILURE_NOT_PRACTICE_CHAT);
    }
    Long userId = positiveLong(metadata.get(AgentRuntimeMetadataKeys.USER_ID));
    Long sessionId = positiveLong(metadata.get(PracticeChatPromptConstants.METADATA_PRACTICE_SESSION_ID));
    Long planId = positiveLong(metadata.get(PracticeChatPromptConstants.METADATA_PLAN_ID));
    Integer phaseIndex = positiveInteger(metadata.get(PracticeChatPromptConstants.METADATA_PHASE_INDEX));
    String problemSlug = nonBlank(metadata.get(PracticeChatPromptConstants.METADATA_PROBLEM_SLUG));
    Long runDbId = noteBodyRequested ? positiveLong(metadata.get(AgentRuntimeMetadataKeys.RUN_DB_ID)) : 0L;
    if (userId == null || sessionId == null || planId == null || phaseIndex == null || problemSlug == null
        || runDbId == null) {
      return TrustedPracticeContext.failed(PracticeLearningStateAgentToolContracts.FAILURE_MISSING_METADATA);
    }
    return new TrustedPracticeContext(userId, sessionId, planId, phaseIndex, problemSlug, runDbId, null);
  }

  private Boolean includeNoteBody(JsonNode arguments) {
    if (arguments == null || !arguments.isObject() || arguments.size() != 1) {
      return null;
    }
    JsonNode value = arguments.get(PracticeLearningStateAgentToolContracts.ARGUMENT_INCLUDE_NOTE_BODY);
    return value != null && value.isBoolean() ? value.booleanValue() : null;
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
    result.put(PracticeLearningStateAgentToolContracts.FIELD_TYPE, PracticeLearningStateAgentToolContracts.RESULT_TYPE);
    result.put(PracticeLearningStateAgentToolContracts.FIELD_STATUS,
        PracticeLearningStateAgentToolContracts.STATUS_FAILED);
    result.put(PracticeLearningStateAgentToolContracts.FIELD_FAILURE_CODE, failureCode);
    result.put(PracticeLearningStateAgentToolContracts.FIELD_MESSAGE, failureMessage(failureCode));
    return result;
  }

  private String failureMessage(String failureCode) {
    return switch (failureCode) {
      case PracticeLearningStateAgentToolContracts.FAILURE_INVALID_ARGUMENTS -> "Tool arguments are invalid.";
      case PracticeLearningStateAgentToolContracts.FAILURE_MISSING_METADATA ->
          "Trusted practice context is unavailable.";
      case PracticeLearningStateAgentToolContracts.FAILURE_NOT_PRACTICE_CHAT ->
          "This tool is only available in Practice Chat.";
      case PracticeLearningStateAgentToolContracts.FAILURE_PRACTICE_SESSION_NOT_FOUND ->
          "The current practice session is unavailable.";
      case PracticeLearningStateAgentToolContracts.FAILURE_CURRENT_PROBLEM_MISMATCH ->
          "The current practice problem context does not match the session.";
      default -> "Current problem learning state could not be loaded.";
    };
  }

  private static ObjectNode inputSchema() {
    ObjectNode schema = JsonNodeFactory.instance.objectNode();
    schema.put("type", "object");
    schema.put("additionalProperties", false);
    ObjectNode property = schema.putObject("properties")
        .putObject(PracticeLearningStateAgentToolContracts.ARGUMENT_INCLUDE_NOTE_BODY);
    property.put("type", "boolean");
    property.put("description",
        "True only when the current user explicitly asks to read the saved note body or full note content.");
    schema.putArray("required").add(PracticeLearningStateAgentToolContracts.ARGUMENT_INCLUDE_NOTE_BODY);
    return schema;
  }

  private record TrustedPracticeContext(
      long userId,
      long sessionId,
      long planId,
      int phaseIndex,
      String problemSlug,
      long runDbId,
      String failureCode
  ) {

    private static TrustedPracticeContext failed(String failureCode) {
      return new TrustedPracticeContext(0, 0, 0, 0, "", 0, failureCode);
    }
  }

  private record NoteSnapshot(UserProblemNoteSummary summary, String noteMarkdown, String bodyStatus) {

    private static NoteSnapshot empty(long userId, String problemSlug, String bodyStatus) {
      return new NoteSnapshot(
          new UserProblemNoteSummary(
              null, userId, problemSlug, ProblemSolutionOutlineV1.empty(), false, 0, null, null),
          null,
          bodyStatus);
    }
  }
}
