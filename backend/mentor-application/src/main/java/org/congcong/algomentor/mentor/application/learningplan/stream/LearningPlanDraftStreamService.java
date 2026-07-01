package org.congcong.algomentor.mentor.application.learningplan.stream;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Flow;
import java.util.concurrent.SubmissionPublisher;
import java.util.concurrent.atomic.AtomicReference;
import org.congcong.algomentor.agent.core.AgentExecutionOptions;
import org.congcong.algomentor.agent.core.AgentLoopRunner;
import org.congcong.algomentor.agent.core.AgentRequest;
import org.congcong.algomentor.agent.core.AgentStreamEvent;
import org.congcong.algomentor.agent.core.AgentStructuredOutputOptions;
import org.congcong.algomentor.agent.core.StructuredOutputStrategy;
import org.congcong.algomentor.agent.core.work.AgentWorkStatusEvent;
import org.congcong.algomentor.agent.core.work.AgentWorkStatusProfile;
import org.congcong.algomentor.agent.core.work.AgentWorkStatusProjector;
import org.congcong.algomentor.llm.core.request.LlmGenerationOptions;
import org.congcong.algomentor.llm.core.request.LlmResponseFormat;
import org.congcong.algomentor.llm.core.stream.LlmStreamEvent;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanDraft;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanDraftCommand;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanDraftPlan;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanDraftRepository;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanDraftResult;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanDraftStatus;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanDraftValidator;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanException;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanProblemCatalog;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 学习计划草案流式生成编排服务。
 */
public class LearningPlanDraftStreamService {

  private static final Logger log = LoggerFactory.getLogger(LearningPlanDraftStreamService.class);

  private final LearningPlanDraftRepository draftRepository;
  private final LearningPlanDraftValidator validator;
  private final AgentLoopRunner agentLoopRunner;
  private final LearningPlanDraftPromptBuilder promptBuilder;
  private final LearningPlanDraftStructuredOutputMapper outputMapper;
  private final ObjectMapper objectMapper;
  private final Clock clock;

  public LearningPlanDraftStreamService(
      LearningPlanDraftRepository draftRepository,
      LearningPlanDraftValidator validator,
      AgentLoopRunner agentLoopRunner,
      LearningPlanDraftPromptBuilder promptBuilder,
      ObjectMapper objectMapper,
      LearningPlanProblemCatalog problemCatalog,
      Clock clock
  ) {
    this.draftRepository = draftRepository;
    this.validator = validator;
    this.agentLoopRunner = agentLoopRunner;
    this.promptBuilder = promptBuilder;
    this.objectMapper = objectMapper;
    this.outputMapper = new LearningPlanDraftStructuredOutputMapper(objectMapper, problemCatalog);
    this.clock = clock;
  }

  public Flow.Publisher<LearningPlanDraftStreamEvent> stream(
      long userId,
      LearningPlanDraftCommand command,
      String runId,
      Map<String, Object> metadata
  ) {
    Objects.requireNonNull(command, "command must not be null");
    List<String> missingFields = validator.missingRequiredFields(command);
    if (!missingFields.isEmpty()) {
      return immediateCollecting(userId, command, missingFields);
    }
    // 第一次写库：先落一条空草案，拿到稳定 draft id；通用 Agent 只负责生成，不直接持有学习计划仓储。
    LearningPlanDraft draft = createInitialDraft(userId, command);
    AgentRequest request = new AgentRequest(
        runId,
        null,
        promptBuilder.build(command),
        metadata,
        executionOptions());
    return subscriber -> {
      SubmissionPublisher<LearningPlanDraftStreamEvent> publisher = new SubmissionPublisher<>();
      publisher.subscribe(subscriber);
      AgentWorkStatusProjector projector = new AgentWorkStatusProjector(learningPlanProfile(), clock);
      // 从这里进入通用 Agent loop；学习计划草案的解析和持久化由下面的 StreamSubscriber 接管。
      agentLoopRunner.stream(request).subscribe(new StreamSubscriber(
          publisher,
          projector,
          draft,
          command));
    };
  }

  private Flow.Publisher<LearningPlanDraftStreamEvent> immediateCollecting(
      long userId,
      LearningPlanDraftCommand command,
      List<String> missingFields
  ) {
    return subscriber -> {
      SubmissionPublisher<LearningPlanDraftStreamEvent> publisher = new SubmissionPublisher<>();
      publisher.subscribe(subscriber);
      LearningPlanDraft draft = createInitialDraft(userId, command).withState(
          LearningPlanDraftStatus.COLLECTING,
          missingFields,
          clarificationFor(missingFields.get(0)),
          null,
          clock.instant());
      LearningPlanDraft saved = draftRepository.save(draft);
      publisher.submit(new LearningPlanDraftStreamEvent.Draft(new LearningPlanDraftEvent.DraftReady(
          LearningPlanDraftResult.fromDraft(saved))));
      publisher.close();
    };
  }

  private LearningPlanDraft createInitialDraft(long userId, LearningPlanDraftCommand command) {
    Instant now = clock.instant();
    return draftRepository.save(new LearningPlanDraft(
        null,
        userId,
        LearningPlanDraftStatus.COLLECTING,
        command,
        List.of(),
        List.of(),
        null,
        null,
        null,
        now.plus(14, ChronoUnit.DAYS),
        now,
        now));
  }

  private AgentExecutionOptions executionOptions() {
    return new AgentExecutionOptions(
        LlmGenerationOptions.defaults(),
        new LlmResponseFormat.JsonSchema(
            LearningPlanStreamConstants.SCHEMA_NAME,
            LearningPlanDraftJsonSchema.schema(),
            true),
        new AgentStructuredOutputOptions(
            StructuredOutputStrategy.PROVIDER_NATIVE,
            LearningPlanStreamConstants.SCHEMA_NAME,
            LearningPlanStreamConstants.SCHEMA_VERSION,
            true));
  }

  private AgentWorkStatusProfile learningPlanProfile() {
    return new AgentWorkStatusProfile(
        LearningPlanStreamConstants.SCENARIO,
        "开始生成学习计划",
        "正在规划",
        Map.of(
            "list_problem_filters", "正在查询题库标签",
            "search_problems", "正在搜索候选题",
            "get_problem_statement", "正在读取题目信息"),
        24,
        Duration.ofMillis(500),
        true);
  }

  private String clarificationFor(String field) {
    return switch (field) {
      case "intent" -> "你想创建哪类学习计划？例如面试冲刺、专题突破或长期学习。";
      case "goal" -> "请补充这份计划的学习目标，例如准备 Java 后端算法面试。";
      case "durationWeeks" -> "你希望计划持续几周？";
      case "level" -> "你当前算法水平更接近入门、中级还是高级？";
      case "weeklyHours" -> "你每周大约可以投入几小时学习算法？";
      default -> "请补充一个最关键的信息，方便继续生成计划。";
    };
  }

  private final class StreamSubscriber implements Flow.Subscriber<AgentStreamEvent> {

    private final SubmissionPublisher<LearningPlanDraftStreamEvent> publisher;
    private final AgentWorkStatusProjector projector;
    private final LearningPlanDraft draft;
    private final LearningPlanDraftCommand command;
    private final AtomicReference<Flow.Subscription> subscription = new AtomicReference<>();
    private final StringBuilder stepContent = new StringBuilder();
    private String finalContent;

    private StreamSubscriber(
        SubmissionPublisher<LearningPlanDraftStreamEvent> publisher,
        AgentWorkStatusProjector projector,
        LearningPlanDraft draft,
        LearningPlanDraftCommand command
    ) {
      this.publisher = publisher;
      this.projector = projector;
      this.draft = draft;
      this.command = command;
    }

    @Override
    public void onSubscribe(Flow.Subscription subscription) {
      this.subscription.set(subscription);
      subscription.request(1);
    }

    @Override
    public void onNext(AgentStreamEvent event) {
      captureContent(event);
      projector.project(event)
          .map(LearningPlanDraftStreamEvent.Work::new)
          .ifPresent(publisher::submit);
      if (event instanceof AgentStreamEvent.AgentRunEnd) {
        completeWithDraft(event);
        return;
      }
      if (event instanceof AgentStreamEvent.AgentError error) {
        failDraft(
            error.error().code().name(),
            "学习计划生成失败，请稍后重试。",
            error.error().retryable(),
            error.error());
        return;
      }
      subscription.get().request(1);
    }

    @Override
    public void onError(Throwable throwable) {
      failDraft("LEARNING_PLAN_STREAM_FAILED", "学习计划生成失败，请稍后重试。", false, throwable);
    }

    @Override
    public void onComplete() {
      publisher.close();
    }

    private void completeWithDraft(AgentStreamEvent event) {
      try {
        if (!(event instanceof AgentStreamEvent.AgentRunEnd)) {
          throw new LearningPlanException("LEARNING_PLAN_AGENT_NOT_DONE", "学习计划生成未完成。");
        }
        if (finalContent == null || finalContent.isBlank()) {
          throw new LearningPlanException("LEARNING_PLAN_FINAL_OUTPUT_MISSING", "模型未返回学习计划结果。");
        }
        // AgentRunEnd 表示最后一个无工具调用 step 已完成，此时 finalContent 才是可落库的结构化计划。
        LearningPlanDraftPlan plan = outputMapper.map(objectMapper.readTree(finalContent), command);
        validator.validateGeneratedPlan(plan);
        // 第二次写库：把模型最终 JSON 规范化后的领域计划写入 draft_plan_json，并把状态置为 GENERATED。
        LearningPlanDraft saved = draftRepository.save(draft.withState(
            LearningPlanDraftStatus.GENERATED,
            List.of(),
            "已生成学习计划草案。",
            plan,
            clock.instant()));
        publisher.submit(new LearningPlanDraftStreamEvent.Draft(new LearningPlanDraftEvent.DraftReady(
            LearningPlanDraftResult.fromDraft(saved))));
        publisher.close();
      } catch (com.fasterxml.jackson.core.JsonProcessingException exception) {
        failDraft("LEARNING_PLAN_STRUCTURED_OUTPUT_INVALID", "学习计划结构化结果解析失败。", true, exception);
      } catch (LearningPlanException exception) {
        failDraft(exception.code(), exception.getMessage(), true, exception);
      }
    }

    private void captureContent(AgentStreamEvent event) {
      if (event instanceof AgentStreamEvent.AgentStepStart) {
        stepContent.setLength(0);
        return;
      }
      if (event instanceof AgentStreamEvent.Llm llm && llm.event() instanceof LlmStreamEvent.ContentDelta delta) {
        stepContent.append(delta.content());
        return;
      }
      // 有工具调用的 step 内容只是中间推理/工具阶段输出；无工具调用的 step 才被视为最终答案。
      if (event instanceof AgentStreamEvent.AgentStepEnd end && end.toolCallCount() == 0) {
        finalContent = stepContent.toString();
      }
    }

    private void failDraft(String code, String message, boolean retryable) {
      failDraft(code, message, retryable, null);
    }

    private void failDraft(String code, String message, boolean retryable, Throwable cause) {
      if (cause == null) {
        log.warn("Learning plan draft stream failed: code={}, retryable={}, message={}", code, retryable, message);
      } else {
        log.warn(
            "Learning plan draft stream failed: code={}, retryable={}, message={}, causeMessage={}",
            code,
            retryable,
            message,
            cause.getMessage(),
            cause);
      }
      draftRepository.save(draft.withState(
          LearningPlanDraftStatus.GENERATION_FAILED,
          List.of(),
          message,
          null,
          clock.instant()));
      publisher.submit(new LearningPlanDraftStreamEvent.Draft(new LearningPlanDraftEvent.DraftError(
          code,
          message,
          retryable)));
      publisher.close();
    }
  }
}
