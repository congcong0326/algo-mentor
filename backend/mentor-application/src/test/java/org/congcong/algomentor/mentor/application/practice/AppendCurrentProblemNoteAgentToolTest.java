package org.congcong.algomentor.mentor.application.practice;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.congcong.algomentor.agent.core.AgentExecutionContext;
import org.congcong.algomentor.agent.core.runtime.model.AgentRuntimeMetadataKeys;
import org.congcong.algomentor.mentor.application.review.ReviewContractConstants;
import org.congcong.algomentor.mentor.application.review.note.ProblemSolutionOutlineV1;
import org.congcong.algomentor.mentor.application.review.note.UserProblemNote;
import org.congcong.algomentor.mentor.application.review.note.UserProblemNoteRepository;
import org.congcong.algomentor.mentor.application.review.note.UserProblemNoteAppendService;
import org.junit.jupiter.api.Test;

class AppendCurrentProblemNoteAgentToolTest {

  private static final long USER_ID = 7L;
  private static final long SESSION_ID = 50L;
  private static final long PLAN_ID = 12L;
  private static final int PHASE_INDEX = 1;
  private static final String PROBLEM_SLUG = "two-sum";
  private static final Instant NOW = Instant.parse("2026-07-31T12:00:00Z");

  private final InMemoryNoteRepository noteRepository = new InMemoryNoteRepository();
  private final UserProblemNoteAppendService noteAppendService = new UserProblemNoteAppendService(
      noteRepository, Clock.fixed(NOW, ZoneOffset.UTC));
  private final AppendCurrentProblemNoteAgentTool tool = new AppendCurrentProblemNoteAgentTool(
      new StubPracticeSessionRepository(session(PROBLEM_SLUG)), noteAppendService);

  @Test
  void appendsTheConfirmedMarkdownToTheTrustedCurrentProblem() {
    JsonNode first = tool.execute(arguments("  **关键点**：先查补数。  "), context(metadata()));
    JsonNode second = tool.execute(arguments("注意不能复用同一元素。"), context(metadata()));

    assertThat(first.path(AppendCurrentProblemNoteAgentToolContracts.FIELD_STATUS).asText())
        .isEqualTo(AppendCurrentProblemNoteAgentToolContracts.STATUS_APPENDED);
    assertThat(second.path(AppendCurrentProblemNoteAgentToolContracts.FIELD_REVISION).asLong()).isEqualTo(2);
    assertThat(second.path(AppendCurrentProblemNoteAgentToolContracts.FIELD_APPENDED_CHARS).asInt())
        .isEqualTo("注意不能复用同一元素。".length());
    assertThat(noteRepository.find(USER_ID, PROBLEM_SLUG).orElseThrow().noteMarkdown())
        .isEqualTo("**关键点**：先查补数。\n\n注意不能复用同一元素。");
  }

  @Test
  void rejectsUnexpectedArgumentsWithoutWriting() {
    JsonNode arguments = arguments("保存这一点");
    ((com.fasterxml.jackson.databind.node.ObjectNode) arguments).put("problemSlug", "other-problem");

    JsonNode result = tool.execute(arguments, context(metadata()));

    assertThat(result.path(AppendCurrentProblemNoteAgentToolContracts.FIELD_STATUS).asText())
        .isEqualTo(AppendCurrentProblemNoteAgentToolContracts.STATUS_FAILED);
    assertThat(result.path(AppendCurrentProblemNoteAgentToolContracts.FIELD_FAILURE_CODE).asText())
        .isEqualTo(AppendCurrentProblemNoteAgentToolContracts.FAILURE_INVALID_ARGUMENTS);
    assertThat(noteRepository.find(USER_ID, PROBLEM_SLUG)).isEmpty();
  }

  @Test
  void rejectsAProblemMismatchFromTrustedMetadata() {
    Map<String, Object> metadata = new HashMap<>(metadata());
    metadata.put(PracticeChatPromptConstants.METADATA_PROBLEM_SLUG, "three-sum");

    JsonNode result = tool.execute(arguments("保存这一点"), context(metadata));

    assertThat(result.path(AppendCurrentProblemNoteAgentToolContracts.FIELD_FAILURE_CODE).asText())
        .isEqualTo(AppendCurrentProblemNoteAgentToolContracts.FAILURE_CURRENT_PROBLEM_MISMATCH);
    assertThat(noteRepository.find(USER_ID, PROBLEM_SLUG)).isEmpty();
  }

  @Test
  void reportsTheNoteLimitWithoutOverwritingExistingContent() {
    noteAppendService.appendToTrustedProblem(
        USER_ID, PROBLEM_SLUG, "x".repeat(ReviewContractConstants.NOTE_MARKDOWN_MAX_CHARS - 1));

    JsonNode result = tool.execute(arguments("yy"), context(metadata()));

    assertThat(result.path(AppendCurrentProblemNoteAgentToolContracts.FIELD_FAILURE_CODE).asText())
        .isEqualTo(AppendCurrentProblemNoteAgentToolContracts.FAILURE_NOTE_TOO_LONG);
    assertThat(noteRepository.find(USER_ID, PROBLEM_SLUG).orElseThrow().noteMarkdown())
        .hasSize(ReviewContractConstants.NOTE_MARKDOWN_MAX_CHARS - 1);
  }

  @Test
  void declaresAStrictSingleFieldSchema() {
    JsonNode schema = tool.spec().inputSchema();

    assertThat(schema.path("additionalProperties").asBoolean()).isFalse();
    assertThat(schema.path("required").get(0).asText())
        .isEqualTo(AppendCurrentProblemNoteAgentToolContracts.ARGUMENT_CONTENT_MARKDOWN);
    assertThat(schema.path("properties").size()).isEqualTo(1);
  }

  private JsonNode arguments(String contentMarkdown) {
    return JsonNodeFactory.instance.objectNode()
        .put(AppendCurrentProblemNoteAgentToolContracts.ARGUMENT_CONTENT_MARKDOWN, contentMarkdown);
  }

  private AgentExecutionContext context(Map<String, Object> metadata) {
    return new AgentExecutionContext("run-1", 1, metadata, false);
  }

  private Map<String, Object> metadata() {
    return Map.of(
        AgentRuntimeMetadataKeys.USER_ID, USER_ID,
        PracticeChatPromptConstants.METADATA_SCENARIO, PracticeChatPromptConstants.SCENARIO,
        PracticeChatPromptConstants.METADATA_PRACTICE_SESSION_ID, SESSION_ID,
        PracticeChatPromptConstants.METADATA_PLAN_ID, PLAN_ID,
        PracticeChatPromptConstants.METADATA_PHASE_INDEX, PHASE_INDEX,
        PracticeChatPromptConstants.METADATA_PROBLEM_SLUG, PROBLEM_SLUG);
  }

  private static PracticeSession session(String problemSlug) {
    return new PracticeSession(
        SESSION_ID,
        USER_ID,
        PLAN_ID,
        PHASE_INDEX,
        problemSlug,
        PracticeSessionStatus.ACTIVE,
        100L,
        200L,
        PracticeProgressStatus.IN_PROGRESS,
        null,
        NOW,
        NOW,
        "zh-CN");
  }

  private static final class StubPracticeSessionRepository implements PracticeSessionRepository {
    private final PracticeSession session;

    private StubPracticeSessionRepository(PracticeSession session) {
      this.session = session;
    }

    @Override
    public Optional<PracticeSession> findSessionForUser(long sessionId, long userId) {
      return Optional.of(session)
          .filter(value -> value.id() == sessionId && value.userId() == userId);
    }

    @Override public PracticeProgress upsertAndAdvanceProgress(long userId, long planId, int phaseIndex,
        String problemSlug) { throw new UnsupportedOperationException(); }
    @Override public PracticeSession upsertAndLockSession(long userId, long planId, int phaseIndex,
        String problemSlug, String locale) { throw new UnsupportedOperationException(); }
    @Override public PracticeSession attachAgentTask(long sessionId, long agentTaskId) {
      throw new UnsupportedOperationException();
    }
    @Override public PracticeSession attachProblemStatementMessage(long sessionId, long messageId) {
      throw new UnsupportedOperationException();
    }
    @Override public PracticeProgress updateProgressStatus(long sessionId, long userId,
        PracticeProgressStatus status) { throw new UnsupportedOperationException(); }
    @Override public void touchLastMessageAt(long sessionId) { throw new UnsupportedOperationException(); }
  }

  private static final class InMemoryNoteRepository implements UserProblemNoteRepository {
    private final Map<String, UserProblemNote> notes = new HashMap<>();
    private long nextId = 1;

    @Override
    public Optional<UserProblemNote> find(long userId, String problemSlug) {
      return Optional.ofNullable(notes.get(key(userId, problemSlug)));
    }

    @Override
    public Optional<UserProblemNote> append(long userId, String problemSlug, ProblemSolutionOutlineV1 initialOutline,
        String contentMarkdown, Instant now) {
      String key = key(userId, problemSlug);
      UserProblemNote current = notes.get(key);
      String currentMarkdown = current == null ? "" : current.noteMarkdown();
      String separator = currentMarkdown.isEmpty() ? "" : ReviewContractConstants.NOTE_MARKDOWN_APPEND_SEPARATOR;
      String appended = currentMarkdown + separator + contentMarkdown;
      if (appended.length() > ReviewContractConstants.NOTE_MARKDOWN_MAX_CHARS) {
        return Optional.empty();
      }
      UserProblemNote saved = current == null
          ? new UserProblemNote(nextId++, userId, problemSlug, initialOutline, appended, 1, now, now)
          : new UserProblemNote(current.id(), userId, problemSlug, current.outline(), appended,
              current.revision() + 1, current.createdAt(), now);
      notes.put(key, saved);
      return Optional.of(saved);
    }

    @Override public Optional<UserProblemNote> insert(long userId, String problemSlug,
        ProblemSolutionOutlineV1 outline, String noteMarkdown, Instant now) {
      throw new UnsupportedOperationException();
    }
    @Override public Optional<UserProblemNote> update(long userId, String problemSlug,
        ProblemSolutionOutlineV1 outline, String noteMarkdown, long expectedRevision, Instant now) {
      throw new UnsupportedOperationException();
    }
    @Override public boolean delete(long userId, String problemSlug) { return false; }

    private String key(long userId, String problemSlug) {
      return userId + ":" + problemSlug;
    }
  }
}
