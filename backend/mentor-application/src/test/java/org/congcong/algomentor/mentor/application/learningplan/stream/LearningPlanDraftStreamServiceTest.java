package org.congcong.algomentor.mentor.application.learningplan.stream;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Flow;
import java.util.concurrent.SubmissionPublisher;
import java.util.concurrent.TimeUnit;
import org.congcong.algomentor.agent.core.AgentLoopRunner;
import org.congcong.algomentor.agent.core.AgentRequest;
import org.congcong.algomentor.agent.core.AgentStreamEvent;
import org.congcong.algomentor.llm.core.response.LlmFinishReason;
import org.congcong.algomentor.llm.core.stream.LlmStreamEvent;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanDifficultyPreference;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanDraft;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanDraftCommand;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanDraftRepository;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanDraftStatus;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanDraftValidator;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanIntent;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanLevel;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanProblemCandidate;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanProblemCatalog;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanProblemSearch;
import org.junit.jupiter.api.Test;

class LearningPlanDraftStreamServiceTest {

  private final Clock clock = Clock.fixed(Instant.parse("2026-06-23T00:00:00Z"), ZoneOffset.UTC);
  private final InMemoryDraftRepository draftRepository = new InMemoryDraftRepository();
  private final FakeProblemCatalog problemCatalog = new FakeProblemCatalog();

  @Test
  void streamsWorkEventsAndDraftReadyWithValidatedProblems() {
    LearningPlanDraftStreamService service = serviceWithAgent(finalJson("two-sum", "fake-slug"));

    List<LearningPlanDraftStreamEvent> events = collect(service.stream(7L, command(), "run-1", Map.of()));

    assertThat(events).extracting(LearningPlanDraftStreamEvent::eventName)
        .contains("work_start", "work_progress", "work_done", "draft_ready");
    LearningPlanDraftStreamEvent.Draft draftEvent = (LearningPlanDraftStreamEvent.Draft) events.stream()
        .filter(event -> event.eventName().equals("draft_ready"))
        .findFirst()
        .orElseThrow();
    LearningPlanDraftEvent.DraftReady ready = (LearningPlanDraftEvent.DraftReady) draftEvent.event();
    assertThat(ready.draft().status()).isEqualTo(LearningPlanDraftStatus.GENERATED);
    assertThat(ready.draft().draftPlan().phases().get(0).problems())
        .extracting(problem -> problem.slug())
        .containsExactly("two-sum");
    assertThat(ready.draft().draftPlan().metadata())
        .containsEntry("problemRecommendationIncomplete", true);
  }

  @Test
  void missingFieldsReturnsCollectingDraftWithoutAgentRun() {
    LearningPlanDraftStreamService service = serviceWithAgent("{}");

    List<LearningPlanDraftStreamEvent> events = collect(service.stream(7L, new LearningPlanDraftCommand(
        null,
        "",
        4,
        LearningPlanLevel.INTERMEDIATE,
        6,
        "Java",
        LearningPlanDifficultyPreference.MEDIUM,
        true,
        List.of("Array")), "run-1", Map.of()));

    assertThat(events).extracting(LearningPlanDraftStreamEvent::eventName).containsExactly("draft_ready");
    LearningPlanDraftEvent.DraftReady ready = (LearningPlanDraftEvent.DraftReady)
        ((LearningPlanDraftStreamEvent.Draft) events.get(0)).event();
    assertThat(ready.draft().status()).isEqualTo(LearningPlanDraftStatus.COLLECTING);
    assertThat(ready.draft().missingFields()).contains("intent", "goal");
  }

  private LearningPlanDraftStreamService serviceWithAgent(String content) {
    AgentLoopRunner runner = new FakeAgentLoopRunner(content);
    return new LearningPlanDraftStreamService(
        draftRepository,
        new LearningPlanDraftValidator(),
        runner,
        new LearningPlanDraftPromptBuilder(),
        new ObjectMapper(),
        problemCatalog,
        clock);
  }

  private LearningPlanDraftCommand command() {
    return new LearningPlanDraftCommand(
        LearningPlanIntent.INTERVIEW_SPRINT,
        "准备 Java 后端算法面试",
        4,
        LearningPlanLevel.INTERMEDIATE,
        6,
        "Java",
        LearningPlanDifficultyPreference.MEDIUM,
        true,
        List.of("Array"));
  }

  private String finalJson(String... slugs) {
    String problems = java.util.Arrays.stream(slugs)
        .map(slug -> """
            {
              "slug": "%s",
              "frontendId": 1,
              "title": "Two Sum",
              "titleCn": "两数之和",
              "difficulty": "EASY",
              "tags": ["Array"],
              "reason": "匹配数组训练目标。",
              "sortOrder": 1
            }
            """.formatted(slug))
        .reduce((left, right) -> left + "," + right)
        .orElse("");
    return """
        {
          "title": "四周 Java 算法面试冲刺计划",
          "summary": "围绕数组和哈希表建立高频题能力。",
          "intent": "INTERVIEW_SPRINT",
          "goal": "准备 Java 后端算法面试",
          "durationWeeks": 4,
          "level": "INTERMEDIATE",
          "weeklyHours": 6,
          "programmingLanguage": "Java",
          "difficultyPreference": "MEDIUM",
          "interviewOriented": true,
          "topicPreferences": ["Array"],
          "profileSummary": "当前水平：中级，每周 6 小时，语言：Java",
          "phases": [
            {
              "phaseIndex": 1,
              "title": "数组与哈希表基础",
              "durationWeeks": 2,
              "focus": "Array",
              "objectives": ["掌握数组基础题型"],
              "recommendedTags": ["Array"],
              "acceptanceCriteria": ["能独立复盘错题"],
              "reviewAdvice": "记录边界条件。",
              "problems": [%s]
            },
            {
              "phaseIndex": 2,
              "title": "二分与双指针",
              "durationWeeks": 1,
              "focus": "Binary Search",
              "objectives": ["掌握二分"],
              "recommendedTags": ["Binary Search"],
              "acceptanceCriteria": ["能说明循环不变量"],
              "reviewAdvice": "总结模板。",
              "problems": []
            },
            {
              "phaseIndex": 3,
              "title": "动态规划入门",
              "durationWeeks": 1,
              "focus": "Dynamic Programming",
              "objectives": ["识别状态转移"],
              "recommendedTags": ["Dynamic Programming"],
              "acceptanceCriteria": ["能写出状态定义"],
              "reviewAdvice": "复盘状态设计。",
              "problems": []
            }
          ],
          "metadata": {
            "problemRecommendationIncomplete": false
          }
        }
        """.formatted(problems);
  }

  private List<LearningPlanDraftStreamEvent> collect(Flow.Publisher<LearningPlanDraftStreamEvent> publisher) {
    CollectingSubscriber subscriber = new CollectingSubscriber();
    publisher.subscribe(subscriber);
    subscriber.await();
    return subscriber.events;
  }

  private static class FakeAgentLoopRunner extends AgentLoopRunner {
    private final String content;

    FakeAgentLoopRunner(String content) {
      super(new org.congcong.algomentor.llm.core.gateway.LlmGateway() {
        @Override
        public org.congcong.algomentor.llm.core.response.LlmCompletionResult complete(
            org.congcong.algomentor.llm.core.request.LlmCompletionRequest request) {
          throw new UnsupportedOperationException();
        }

        @Override
        public Flow.Publisher<LlmStreamEvent> stream(
            org.congcong.algomentor.llm.core.request.LlmCompletionRequest request) {
          throw new UnsupportedOperationException();
        }
      }, "test-model", org.congcong.algomentor.agent.core.AgentToolRegistry.empty(), 1);
      this.content = content;
    }

    @Override
    public Flow.Publisher<AgentStreamEvent> stream(AgentRequest request) {
      return subscriber -> {
        SubmissionPublisher<AgentStreamEvent> publisher = new SubmissionPublisher<>();
        publisher.subscribe(subscriber);
        publisher.submit(new AgentStreamEvent.AgentRunStart(request.runId(), "learning_plan", 4));
        publisher.submit(new AgentStreamEvent.AgentStepStart(request.runId(), 1));
        publisher.submit(AgentStreamEvent.fromLlm(new LlmStreamEvent.ContentDelta(content)));
        publisher.submit(new AgentStreamEvent.AgentStepEnd(request.runId(), 1, LlmFinishReason.STOP, 0));
        publisher.submit(new AgentStreamEvent.AgentRunEnd(request.runId(), 1, LlmFinishReason.STOP, Map.of()));
        publisher.close();
      };
    }
  }

  private static class CollectingSubscriber implements Flow.Subscriber<LearningPlanDraftStreamEvent> {
    private final List<LearningPlanDraftStreamEvent> events = new ArrayList<>();
    private final CountDownLatch done = new CountDownLatch(1);
    private Flow.Subscription subscription;

    @Override
    public void onSubscribe(Flow.Subscription subscription) {
      this.subscription = subscription;
      subscription.request(1);
    }

    @Override
    public void onNext(LearningPlanDraftStreamEvent item) {
      events.add(item);
      subscription.request(1);
    }

    @Override
    public void onError(Throwable throwable) {
      done.countDown();
    }

    @Override
    public void onComplete() {
      done.countDown();
    }

    void await() {
      try {
        assertThat(done.await(5, TimeUnit.SECONDS)).isTrue();
      } catch (InterruptedException exception) {
        Thread.currentThread().interrupt();
        throw new AssertionError(exception);
      }
    }
  }

  private static class FakeProblemCatalog implements LearningPlanProblemCatalog {
    @Override
    public List<LearningPlanProblemCandidate> searchProblems(LearningPlanProblemSearch search) {
      return List.of();
    }

    @Override
    public Optional<LearningPlanProblemCandidate> findBySlug(String slug) {
      if (!"two-sum".equals(slug)) {
        return Optional.empty();
      }
      return Optional.of(new LearningPlanProblemCandidate(
          "two-sum",
          1,
          "Two Sum",
          "两数之和",
          "EASY",
          List.of("Array", "Hash Table")));
    }
  }

  private static class InMemoryDraftRepository implements LearningPlanDraftRepository {
    private final Map<Long, LearningPlanDraft> drafts = new HashMap<>();
    private long sequence = 100;

    @Override
    public LearningPlanDraft save(LearningPlanDraft draft) {
      long id = draft.id() == null ? sequence++ : draft.id();
      LearningPlanDraft saved = draft.withId(id);
      drafts.put(id, saved);
      return saved;
    }

    @Override
    public Optional<LearningPlanDraft> findDraftByIdForUser(long draftId, long userId) {
      return Optional.ofNullable(drafts.get(draftId)).filter(draft -> draft.userId() == userId);
    }
  }
}
