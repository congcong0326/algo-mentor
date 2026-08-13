package org.congcong.algomentor.mentor.application.learningplan.stream;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicReference;
import org.congcong.algomentor.agent.core.AgentStreamEvent;
import org.congcong.algomentor.agent.core.work.AgentWorkStatusEvent;
import org.congcong.algomentor.agent.core.work.AgentWorkStatusProfile;
import org.congcong.algomentor.agent.core.work.AgentWorkStatusProjector;
import org.congcong.algomentor.llm.core.stream.LlmStreamEvent;
import org.congcong.algomentor.agent.core.runtime.api.AgentInvocation;
import org.congcong.algomentor.agent.core.runtime.api.AgentInvocationContext;
import org.congcong.algomentor.agent.core.runtime.api.AgentInvocationMode;
import org.congcong.algomentor.agent.core.runtime.api.AgentRuntime;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanDraft;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanBrief;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanDraftPlan;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanDraftRepository;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanDraftResult;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanDraftStatus;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanDraftSource;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanDraftValidator;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanException;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanCoveragePolicy;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanLoadService;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanSafeFailureReasonResolver;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanProblemCatalog;
import org.congcong.algomentor.mentor.application.learningplan.personalization.LearningPlanPersonalizationContextService;
import org.congcong.algomentor.mentor.application.learningplan.personalization.LearningPlanPersonalizationSnapshot;
import org.congcong.algomentor.mentor.application.learningplan.personalization.LearningPlanPersonalizationScenario;
import org.congcong.algomentor.mentor.application.learningplan.policy.LearningPlanCreationPolicyConstants;
import org.congcong.algomentor.mentor.application.learningplan.policy.LearningPlanCreationPolicyService;
import org.congcong.algomentor.mentor.application.learningplan.policy.LearningPlanDraftCreationAdmission;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 学习计划草案流式生成编排服务。
 */
public class LearningPlanDraftStreamService {

  private static final Logger log = LoggerFactory.getLogger(LearningPlanDraftStreamService.class);

  private final LearningPlanDraftRepository draftRepository;
  private final LearningPlanDraftValidator validator;
  private final AgentRuntime agentRuntime;
  private final LearningPlanDraftStructuredOutputMapper outputMapper;
  private final LearningPlanLoadService loadService;
  private final ObjectMapper objectMapper;
  private final Clock clock;
  private final LearningPlanPersonalizationContextService personalizationContextService;
  private final LearningPlanCreationPolicyService creationPolicyService;

  public LearningPlanDraftStreamService(
      LearningPlanDraftRepository draftRepository,
      LearningPlanDraftValidator validator,
      AgentRuntime agentRuntime,
      ObjectMapper objectMapper,
      LearningPlanProblemCatalog problemCatalog,
      LearningPlanLoadService loadService,
      Clock clock
  ) {
    this(
        draftRepository,
        validator,
        agentRuntime,
        objectMapper,
        problemCatalog,
        loadService,
        clock,
        new LearningPlanPersonalizationContextService(null),
        new LearningPlanCreationPolicyService());
  }

  public LearningPlanDraftStreamService(
      LearningPlanDraftRepository draftRepository,
      LearningPlanDraftValidator validator,
      AgentRuntime agentRuntime,
      ObjectMapper objectMapper,
      LearningPlanProblemCatalog problemCatalog,
      LearningPlanLoadService loadService,
      Clock clock,
      LearningPlanPersonalizationContextService personalizationContextService
  ) {
    this(
        draftRepository,
        validator,
        agentRuntime,
        objectMapper,
        problemCatalog,
        loadService,
        clock,
        personalizationContextService,
        new LearningPlanCreationPolicyService());
  }

  public LearningPlanDraftStreamService(
      LearningPlanDraftRepository draftRepository,
      LearningPlanDraftValidator validator,
      AgentRuntime agentRuntime,
      ObjectMapper objectMapper,
      LearningPlanProblemCatalog problemCatalog,
      LearningPlanLoadService loadService,
      Clock clock,
      LearningPlanPersonalizationContextService personalizationContextService,
      LearningPlanCreationPolicyService creationPolicyService
  ) {
    this.draftRepository = draftRepository;
    this.validator = validator;
    this.agentRuntime = Objects.requireNonNull(agentRuntime, "agentRuntime");
    this.objectMapper = objectMapper;
    this.outputMapper = new LearningPlanDraftStructuredOutputMapper(objectMapper, problemCatalog);
    this.loadService = loadService;
    this.clock = clock;
    this.personalizationContextService = Objects.requireNonNull(
        personalizationContextService, "personalizationContextService");
    this.creationPolicyService = Objects.requireNonNull(creationPolicyService, "creationPolicyService");
  }

  public Flow.Publisher<LearningPlanDraftStreamEvent> stream(
      long userId,
      LearningPlanBrief brief,
      String runId,
      Map<String, Object> metadata
  ) {
    Objects.requireNonNull(brief, "brief must not be null");
    List<String> missingFields = validator.missingRequiredFields(brief);
    if (!missingFields.isEmpty()) {
      return immediateCollecting(userId, brief, missingFields);
    }
    LearningPlanPersonalizationSnapshot personalizationSnapshot = personalizationContextService.snapshot(
        userId, brief.personalizationEnabled(), LearningPlanPersonalizationScenario.DRAFT);
    // 第一次写库：先落一条空草案，拿到稳定 draft id；通用 Agent 只负责生成，不直接持有学习计划仓储。
    LearningPlanDraft draft = createInitialDraft(userId, brief);
    return subscriber -> {
      SingleSubscriberSynchronousPublisher<LearningPlanDraftStreamEvent> publisher =
          new SingleSubscriberSynchronousPublisher<>();
      publisher.subscribe(subscriber);
      AgentWorkStatusProjector projector = new AgentWorkStatusProjector(learningPlanProfile(), clock);
      // 从这里进入通用 Agent loop；学习计划草案的解析和持久化由下面的 StreamSubscriber 接管。
      agentRuntime.stream(invocation(userId, brief, runId, personalizationSnapshot)).subscribe(new StreamSubscriber(
          publisher,
          projector,
          draft,
          brief));
    };
  }

  private Flow.Publisher<LearningPlanDraftStreamEvent> immediateCollecting(
      long userId,
      LearningPlanBrief brief,
      List<String> missingFields
  ) {
    return subscriber -> {
      SingleSubscriberSynchronousPublisher<LearningPlanDraftStreamEvent> publisher =
          new SingleSubscriberSynchronousPublisher<>();
      publisher.subscribe(subscriber);
      LearningPlanDraft draft = createInitialDraft(userId, brief).withState(
          LearningPlanDraftStatus.COLLECTING,
          missingFields,
          clarificationFor(missingFields.get(0)),
          null,
          clock.instant());
      LearningPlanDraft saved = draftRepository.save(draft);
      publisher.emit(new LearningPlanDraftStreamEvent.Draft(new LearningPlanDraftEvent.DraftReady(
          LearningPlanDraftResult.fromDraft(saved))));
      publisher.complete();
    };
  }

  private LearningPlanDraft createInitialDraft(long userId, LearningPlanBrief brief) {
    Instant now = clock.instant();
    LearningPlanDraftCreationAdmission admission = creationPolicyService.draftAdmission(userId, now);
    LearningPlanDraft draft = new LearningPlanDraft(
        null,
        userId,
        LearningPlanDraftSource.AI_PERSONALIZED,
        LearningPlanDraftStatus.COLLECTING,
        brief,
        List.of(),
        List.of(),
        null,
        null,
        null,
        admission.expiresAt(),
        now,
        now);
    return draftRepository.createWithinDailyLimit(
            draft,
            admission.quotaDate(),
            admission.dailyLimit(),
            now)
        .orElseThrow(() -> new LearningPlanException(
            LearningPlanCreationPolicyConstants.DRAFT_DAILY_LIMIT_EXCEEDED_CODE,
            "api.error." + LearningPlanCreationPolicyConstants.DRAFT_DAILY_LIMIT_EXCEEDED_CODE,
            "今天创建的学习计划草案已达到上限，请明天再试。"));
  }

  private AgentInvocation<LearningPlanDraftAgentInput> invocation(
      long userId,
      LearningPlanBrief brief,
      String idempotencyKey,
      LearningPlanPersonalizationSnapshot personalizationSnapshot
  ) {
    return new AgentInvocation<>(
        LearningPlanDraftAgentDefinition.KEY,
        new LearningPlanDraftAgentInput(userId, brief, idempotencyKey, personalizationSnapshot),
        new AgentInvocationContext(
            userId,
            AgentInvocationMode.USER_ENTRY,
            idempotencyKey,
            null,
            null,
            requestSize(brief),
            true));
  }

  private AgentWorkStatusProfile learningPlanProfile() {
    return new AgentWorkStatusProfile(
        LearningPlanStreamConstants.SCENARIO,
        "开始生成学习计划",
        "正在规划",
        Map.of(
            LearningPlanAgentToolNames.LIST_PROBLEM_FILTERS, "正在查询题库标签",
            LearningPlanAgentToolNames.SEARCH_PROBLEMS, "正在搜索候选题"),
        24,
        Duration.ofMillis(500),
        true);
  }

  private String clarificationFor(String field) {
    return switch (field) {
      case "intent" -> "你想创建哪类学习计划？例如面试冲刺、专题突破或长期学习。";
      case "objective" -> "请补充这份计划的具体目标，例如准备 Java 后端算法面试。";
      case "durationWeeks" -> "你希望计划持续几周？";
      case "level" -> "你当前算法水平更接近入门、中级还是高级？";
      case "weeklyHours" -> "你每周大约可以投入几小时学习算法？";
      default -> "请补充一个最关键的信息，方便继续生成计划。";
    };
  }

  private int requestSize(LearningPlanBrief brief) {
    try {
      return objectMapper.writeValueAsBytes(brief).length;
    } catch (com.fasterxml.jackson.core.JsonProcessingException exception) {
      throw new LearningPlanException("LEARNING_PLAN_BRIEF_INVALID", "学习计划输入无法序列化。");
    }
  }

  private final class StreamSubscriber implements Flow.Subscriber<AgentStreamEvent> {

    private final SingleSubscriberSynchronousPublisher<LearningPlanDraftStreamEvent> publisher;
    private final AgentWorkStatusProjector projector;
    private final LearningPlanDraft draft;
    private final LearningPlanBrief brief;
    private final AtomicReference<Flow.Subscription> subscription = new AtomicReference<>();
    private final StringBuilder stepContent = new StringBuilder();
    private String finalContent;

    private StreamSubscriber(
        SingleSubscriberSynchronousPublisher<LearningPlanDraftStreamEvent> publisher,
        AgentWorkStatusProjector projector,
        LearningPlanDraft draft,
        LearningPlanBrief brief
    ) {
      this.publisher = publisher;
      this.projector = projector;
      this.draft = draft;
      this.brief = brief;
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
          .ifPresent(publisher::emit);
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
      publisher.complete();
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
        LearningPlanDraftPlan plan = loadService.withLoadMetadata(
            outputMapper.map(objectMapper.readTree(finalContent), brief),
            LearningPlanCoveragePolicy.FIT_USER_BUDGET);
        validator.validateGeneratedPlan(plan);
        // 第二次写库：把模型最终 JSON 规范化后的领域计划写入 draft_plan_json，并把状态置为 GENERATED。
        LearningPlanDraft saved = draftRepository.save(draft.withState(
            LearningPlanDraftStatus.GENERATED,
            List.of(),
            "已生成学习计划草案。",
            plan,
            clock.instant()));
        publisher.emit(new LearningPlanDraftStreamEvent.Draft(new LearningPlanDraftEvent.DraftReady(
            LearningPlanDraftResult.fromDraft(saved))));
        publisher.complete();
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
      publisher.emit(new LearningPlanDraftStreamEvent.Draft(new LearningPlanDraftEvent.DraftError(
          code,
          message,
          retryable,
          LearningPlanSafeFailureReasonResolver.resolve(cause))));
      publisher.complete();
    }
  }
}
