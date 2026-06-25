package org.congcong.algomentor.mentor.application.practice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.reflect.Method;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.congcong.algomentor.agent.core.runtime.model.AgentAssistantSeedMessageRequest;
import org.congcong.algomentor.agent.core.runtime.model.AgentActiveRun;
import org.congcong.algomentor.agent.core.runtime.model.AgentMessage;
import org.congcong.algomentor.agent.core.runtime.model.AgentTaskCreationRequest;
import org.congcong.algomentor.agent.core.runtime.model.AgentTaskRef;
import org.congcong.algomentor.agent.core.runtime.repository.AgentTaskMessageRepository;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlan;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanDifficultyPreference;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanDraftPlan;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanIntent;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanLevel;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanPhaseDraft;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanProblemDraft;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanRepository;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanStatus;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;

class PracticeSessionServiceTest {

  @Test
  void createsSessionTaskSeedAndProgress() {
    InMemoryPracticeSessionRepository sessionRepository = new InMemoryPracticeSessionRepository();
    InMemoryAgentTaskMessageRepository messageRepository = new InMemoryAgentTaskMessageRepository();
    PracticeSessionService service = service(sessionRepository, messageRepository);

    PracticeSessionResult result = service.createOrReuse(7, reference());

    assertThat(result.session().id()).isEqualTo(50);
    assertThat(result.session().agentTaskId()).isEqualTo(100);
    assertThat(result.session().problemStatementMessageId()).isEqualTo(200);
    assertThat(result.session().progressStatus()).isEqualTo(PracticeProgressStatus.IN_PROGRESS);
    assertThat(result.problem().slug()).isEqualTo("two-sum");
    assertThat(result.messages())
        .extracting(PracticeSessionMessage::id)
        .containsExactly(200L);
    assertThat(result.messages().get(0).messageType())
        .isEqualTo(PracticeChatPromptConstants.MESSAGE_TYPE_PROBLEM_STATEMENT);
    assertThat(messageRepository.taskRequests)
        .singleElement()
        .satisfies(request -> assertThat(request.metadata())
            .containsEntry(PracticeChatPromptConstants.METADATA_SCENARIO, PracticeChatPromptConstants.SCENARIO)
            .containsEntry(PracticeChatPromptConstants.METADATA_PRACTICE_SESSION_ID, 50L)
            .containsEntry(PracticeChatPromptConstants.METADATA_PLAN_ID, 12L)
            .containsEntry(PracticeChatPromptConstants.METADATA_PHASE_INDEX, 1)
            .containsEntry(PracticeChatPromptConstants.METADATA_PROBLEM_SLUG, "two-sum")
            .containsEntry(PracticeChatPromptConstants.METADATA_LOCALE, "zh-CN"));
    assertThat(messageRepository.seedRequests)
        .singleElement()
        .satisfies(request -> {
          assertThat(request.taskId()).isEqualTo(100);
          assertThat(request.content()).contains("# Two Sum");
          assertThat(request.metadata())
              .containsEntry(PracticeChatPromptConstants.MESSAGE_TYPE_METADATA_KEY,
                  PracticeChatPromptConstants.MESSAGE_TYPE_PROBLEM_STATEMENT)
              .containsEntry(PracticeChatPromptConstants.METADATA_PRACTICE_SESSION_ID, 50L)
              .containsEntry(PracticeChatPromptConstants.METADATA_LOCALE, "zh-CN");
        });
  }

  @Test
  void persistsLocaleForMetadataAndGet() {
    InMemoryPracticeSessionRepository sessionRepository = new InMemoryPracticeSessionRepository();
    InMemoryAgentTaskMessageRepository messageRepository = new InMemoryAgentTaskMessageRepository();
    FakeProblemCatalog problemCatalog = new FakeProblemCatalog();
    PracticeSessionService service = service(sessionRepository, messageRepository, problemCatalog);

    PracticeSessionResult created = service.createOrReuse(7, new PracticeChatReference(12, 1, "two-sum", "en-US"));
    PracticeSessionResult loaded = service.get(7, created.session().id());

    assertThat(created.session().locale()).isEqualTo("en-US");
    assertThat(loaded.session().locale()).isEqualTo("en-US");
    assertThat(problemCatalog.locales).containsExactly("en-US", "en-US");
    assertThat(messageRepository.taskRequests.get(0).metadata())
        .containsEntry(PracticeChatPromptConstants.METADATA_LOCALE, "en-US");
    assertThat(messageRepository.seedRequests.get(0).metadata())
        .containsEntry(PracticeChatPromptConstants.METADATA_LOCALE, "en-US");
  }

  @Test
  void reusesExistingSeedWithoutCreatingAnotherMessage() {
    InMemoryPracticeSessionRepository sessionRepository = new InMemoryPracticeSessionRepository();
    InMemoryAgentTaskMessageRepository messageRepository = new InMemoryAgentTaskMessageRepository();
    PracticeSessionService service = service(sessionRepository, messageRepository);

    service.createOrReuse(7, reference());
    PracticeSessionResult reused = service.createOrReuse(7, reference());

    assertThat(reused.session().agentTaskId()).isEqualTo(100);
    assertThat(reused.session().problemStatementMessageId()).isEqualTo(200);
    assertThat(messageRepository.taskRequests).hasSize(1);
    assertThat(messageRepository.seedRequests).hasSize(1);
    assertThat(reused.messages())
        .extracting(PracticeSessionMessage::id)
        .containsExactly(200L);
  }

  @Test
  void returnsActiveRunForSessionTask() {
    InMemoryPracticeSessionRepository sessionRepository = new InMemoryPracticeSessionRepository();
    InMemoryAgentTaskMessageRepository messageRepository = new InMemoryAgentTaskMessageRepository();
    messageRepository.activeRun = new AgentActiveRun(
        300L,
        100L,
        "run-300",
        "idem-300",
        Instant.parse("2026-01-01T00:02:00Z"));
    PracticeSessionService service = service(sessionRepository, messageRepository);

    PracticeSessionResult result = service.createOrReuse(7, reference());

    assertThat(result.activeRun()).isPresent();
    assertThat(result.activeRun().orElseThrow().runUuid()).isEqualTo("run-300");
  }

  @Test
  void completedProgressDoesNotReturnToInProgress() {
    InMemoryPracticeSessionRepository sessionRepository = new InMemoryPracticeSessionRepository();
    InMemoryAgentTaskMessageRepository messageRepository = new InMemoryAgentTaskMessageRepository();
    PracticeSessionService service = service(sessionRepository, messageRepository);

    PracticeSessionResult created = service.createOrReuse(7, reference());
    PracticeSession completed = service.updateProgressStatus(7, created.session().id(), PracticeProgressStatus.COMPLETED);
    PracticeSessionResult reused = service.createOrReuse(7, reference());

    assertThat(completed.progressStatus()).isEqualTo(PracticeProgressStatus.COMPLETED);
    assertThat(reused.session().progressStatus()).isEqualTo(PracticeProgressStatus.COMPLETED);
    assertThat(sessionRepository.progress.status()).isEqualTo(PracticeProgressStatus.COMPLETED);
  }

  @Test
  void skippedProgressReturnsToInProgressWhenSessionOpens() {
    InMemoryPracticeSessionRepository sessionRepository = new InMemoryPracticeSessionRepository();
    sessionRepository.progress = sessionRepository.progress(PracticeProgressStatus.SKIPPED);
    InMemoryAgentTaskMessageRepository messageRepository = new InMemoryAgentTaskMessageRepository();
    PracticeSessionService service = service(sessionRepository, messageRepository);

    PracticeSessionResult result = service.createOrReuse(7, reference());

    assertThat(result.session().progressStatus()).isEqualTo(PracticeProgressStatus.IN_PROGRESS);
    assertThat(sessionRepository.progress.status()).isEqualTo(PracticeProgressStatus.IN_PROGRESS);
  }

  @Test
  void createOrReuseAndProgressUpdateAreTransactional() throws NoSuchMethodException {
    Method createOrReuse = PracticeSessionService.class.getMethod(
        "createOrReuse", long.class, PracticeChatReference.class);
    Method updateProgressStatus = PracticeSessionService.class.getMethod(
        "updateProgressStatus", long.class, long.class, PracticeProgressStatus.class);

    assertThat(createOrReuse.getAnnotation(Transactional.class)).isNotNull();
    assertThat(updateProgressStatus.getAnnotation(Transactional.class)).isNotNull();
  }

  @Test
  void rejectsInvalidPracticeProgressAndSession() {
    assertThatThrownBy(() -> new PracticeProgress(0, 7, 12, 1, "two-sum",
        PracticeProgressStatus.IN_PROGRESS, Instant.EPOCH, Instant.EPOCH))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("progress id");
    assertThatThrownBy(() -> new PracticeSession(50, 0, 12, 1, "two-sum", PracticeSessionStatus.ACTIVE,
        null, null, PracticeProgressStatus.IN_PROGRESS, null, Instant.EPOCH, Instant.EPOCH, "zh-CN"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("session user id");
    assertThatThrownBy(() -> new PracticeSession(50, 7, 12, 1, " ", PracticeSessionStatus.ACTIVE,
        null, null, PracticeProgressStatus.IN_PROGRESS, null, Instant.EPOCH, Instant.EPOCH, "zh-CN"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("problem slug");
  }

  private PracticeSessionService service(
      InMemoryPracticeSessionRepository sessionRepository,
      InMemoryAgentTaskMessageRepository messageRepository) {
    return new PracticeSessionService(
        new InMemoryPlanRepository(),
        new FakeProblemCatalog(),
        sessionRepository,
        messageRepository);
  }

  private PracticeSessionService service(
      InMemoryPracticeSessionRepository sessionRepository,
      InMemoryAgentTaskMessageRepository messageRepository,
      FakeProblemCatalog problemCatalog) {
    return new PracticeSessionService(
        new InMemoryPlanRepository(),
        problemCatalog,
        sessionRepository,
        messageRepository);
  }

  private PracticeChatReference reference() {
    return new PracticeChatReference(12, 1, "two-sum", "zh-CN");
  }

  private static final class InMemoryPracticeSessionRepository implements PracticeSessionRepository {

    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");

    private PracticeProgress progress;
    private PracticeSession session;

    @Override
    public PracticeProgress upsertAndAdvanceProgress(long userId, long planId, int phaseIndex, String problemSlug) {
      if (progress == null) {
        progress = progress(PracticeProgressStatus.IN_PROGRESS);
        return progress;
      }
      if (progress.status() == PracticeProgressStatus.NOT_STARTED || progress.status() == PracticeProgressStatus.SKIPPED) {
        progress = new PracticeProgress(progress.id(), userId, planId, phaseIndex, problemSlug,
            PracticeProgressStatus.IN_PROGRESS, progress.createdAt(), NOW);
      }
      return progress;
    }

    @Override
    public PracticeSession upsertAndLockSession(
        long userId,
        long planId,
        int phaseIndex,
        String problemSlug,
        String locale) {
      if (session == null) {
        session = new PracticeSession(50, userId, planId, phaseIndex, problemSlug, PracticeSessionStatus.ACTIVE,
            null, null, PracticeProgressStatus.NOT_STARTED, null, NOW, NOW, locale);
      }
      return session;
    }

    @Override
    public Optional<PracticeSession> findSessionForUser(long sessionId, long userId) {
      return Optional.ofNullable(session)
          .filter(value -> value.id() == sessionId && value.userId() == userId);
    }

    @Override
    public PracticeSession attachAgentTask(long sessionId, long agentTaskId) {
      session = session.withAgentTaskId(agentTaskId);
      return session;
    }

    @Override
    public PracticeSession attachProblemStatementMessage(long sessionId, long messageId) {
      session = session.withProblemStatementMessageId(messageId);
      return session;
    }

    @Override
    public PracticeProgress updateProgressStatus(long sessionId, long userId, PracticeProgressStatus status) {
      progress = new PracticeProgress(progress.id(), progress.userId(), progress.planId(), progress.phaseIndex(),
          progress.problemSlug(), status, progress.createdAt(), NOW);
      return progress;
    }

    @Override
    public void touchLastMessageAt(long sessionId) {
    }

    private PracticeProgress progress(PracticeProgressStatus status) {
      return new PracticeProgress(70, 7, 12, 1, "two-sum", status, NOW, NOW);
    }
  }

  private static final class InMemoryAgentTaskMessageRepository implements AgentTaskMessageRepository {

    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");

    private final List<AgentTaskCreationRequest> taskRequests = new ArrayList<>();
    private final List<AgentAssistantSeedMessageRequest> seedRequests = new ArrayList<>();
    private final List<AgentMessage> messages = new ArrayList<>();
    private AgentActiveRun activeRun;

    @Override
    public AgentTaskRef createTask(AgentTaskCreationRequest request) {
      taskRequests.add(request);
      return new AgentTaskRef(100);
    }

    @Override
    public AgentMessage createAssistantSeedMessage(AgentAssistantSeedMessageRequest request) {
      seedRequests.add(request);
      AgentMessage message = new AgentMessage(200, request.taskId(), messages.size() + 1L,
          AgentMessage.Role.ASSISTANT, request.content(), NOW.plusSeconds(messages.size()), request.metadata());
      messages.add(message);
      return message;
    }

    @Override
    public List<AgentMessage> messages(long taskId, int messageLimit) {
      return messages.stream()
          .filter(message -> message.taskId() == taskId)
          .sorted(Comparator.comparingLong(AgentMessage::sequenceNo))
          .limit(messageLimit)
          .toList();
    }

    @Override
    public Optional<AgentActiveRun> activeRun(long taskId) {
      return Optional.ofNullable(activeRun)
          .filter(run -> run.taskId() == taskId);
    }
  }

  private static final class InMemoryPlanRepository implements LearningPlanRepository {

    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");

    @Override
    public LearningPlan save(LearningPlan plan) {
      return plan;
    }

    @Override
    public List<LearningPlan> findByUserId(long userId) {
      return List.of(plan());
    }

    @Override
    public Optional<LearningPlan> findPlanByIdForUser(long planId, long userId) {
      if (planId == 12 && userId == 7) {
        return Optional.of(plan());
      }
      return Optional.empty();
    }

    private LearningPlan plan() {
      LearningPlanProblemDraft problem = new LearningPlanProblemDraft(
          "two-sum",
          1,
          "Two Sum",
          "两数之和",
          "EASY",
          List.of("Array", "Hash Table"),
          "建立哈希查找模式。",
          1);
      LearningPlanPhaseDraft phase = new LearningPlanPhaseDraft(
          1,
          "哈希表基础",
          1,
          "补数查找",
          List.of(),
          List.of(),
          List.of(),
          "",
          List.of(problem));
      LearningPlanDraftPlan snapshot = new LearningPlanDraftPlan(
          "哈希表训练",
          "summary",
          LearningPlanIntent.INTERVIEW_SPRINT,
          "4 周内准备后端面试",
          4,
          LearningPlanLevel.INTERMEDIATE,
          8,
          "Java",
          LearningPlanDifficultyPreference.MEDIUM,
          true,
          List.of("Hash Table"),
          "profile",
          List.of(phase),
          Map.of());
      return new LearningPlan(12L, 7L, LearningPlanStatus.ACTIVE, snapshot, NOW, NOW);
    }
  }

  private static final class FakeProblemCatalog implements PracticeChatProblemCatalog {

    private final List<String> locales = new ArrayList<>();

    @Override
    public Optional<PracticeChatProblemDetail> findProblemBySlug(String slug, String locale) {
      locales.add(locale);
      if (!"two-sum".equals(slug)) {
        return Optional.empty();
      }
      return Optional.of(new PracticeChatProblemDetail(
          "two-sum",
          1,
          "Two Sum",
          "EASY",
          List.of("Array", "Hash Table"),
          "# Two Sum",
          "https://leetcode.com/problems/two-sum/"));
    }
  }
}
