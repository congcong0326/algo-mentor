package org.congcong.algomentor.api.controller;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Flow;
import org.congcong.algomentor.agent.core.AgentStreamEvent;
import org.congcong.algomentor.agent.core.runtime.model.AgentRuntimeMetadataKeys;
import org.congcong.algomentor.ai.governance.admission.AiRunAdmission;
import org.congcong.algomentor.ai.governance.admission.AiRunAdmissionService;
import org.congcong.algomentor.ai.governance.model.AiActor;
import org.congcong.algomentor.ai.governance.model.AiPurpose;
import org.congcong.algomentor.ai.governance.model.AiRunContext;
import org.congcong.algomentor.ai.governance.model.AiRunSource;
import org.congcong.algomentor.api.config.ApiContractConstants;
import org.congcong.algomentor.api.service.AiActorResolver;
import org.congcong.algomentor.api.service.LlmStreamSseMapper;
import org.congcong.algomentor.api.service.SseLlmStreamSubscriber;
import org.congcong.algomentor.mentor.application.conversation.AgentConversationCommand;
import org.congcong.algomentor.mentor.application.conversation.AgentConversationRunCoordinator;
import org.congcong.algomentor.mentor.application.practice.PracticeChatPromptConstants;
import org.congcong.algomentor.mentor.application.practice.PracticeChatReference;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Validated
@RestController
@RequestMapping(ApiContractConstants.AGENT_CONVERSATIONS_BASE_PATH)
@ConditionalOnBean(AgentConversationRunCoordinator.class)
public class AgentConversationController {

  private final AgentConversationRunCoordinator runCoordinator;
  private final LlmStreamSseMapper sseMapper;
  private final AiActorResolver actorResolver;
  private final AiRunAdmissionService admissionService;

  public AgentConversationController(
      AgentConversationRunCoordinator runCoordinator,
      LlmStreamSseMapper sseMapper,
      AiActorResolver actorResolver,
      AiRunAdmissionService admissionService
  ) {
    this.runCoordinator = runCoordinator;
    this.sseMapper = sseMapper;
    this.actorResolver = actorResolver;
    this.admissionService = admissionService;
  }

  @PostMapping(value = ApiContractConstants.STREAM_PATH, produces = MediaType.TEXT_EVENT_STREAM_VALUE)
  public SseEmitter stream(
      @RequestHeader(name = ApiContractConstants.IDEMPOTENCY_KEY_HEADER, required = false) String idempotencyKey,
      @Valid @RequestBody ConversationStreamRequest request
  ) {
    String effectiveKey = idempotencyKey == null || idempotencyKey.isBlank()
        ? UUID.randomUUID().toString()
        : idempotencyKey;
    AiActor actor = actorResolver.currentActor();
    Map<String, Object> requestMetadata = new HashMap<>();
    if (request.taskId() != null) {
      requestMetadata.put(AgentRuntimeMetadataKeys.TASK_ID, request.taskId());
    }
    PracticeChatReference practiceReference = request.practiceReference();
    if (practiceReference != null) {
      /*
       * PracticeChatWorkbench 复用通用 AgentConversation SSE 入口。
       * 这里把前端传来的题目训练定位信息同步写入治理 metadata：
       * - scenario 标记本次 run 属于题目训练聊天，后续会使用 PRACTICE_CHAT_V1 profile；
       * - planId 用于按当前用户恢复学习计划；
       * - phaseIndex/problemSlug/locale 用于定位阶段、题目和题面语言。
       *
       * 真正组装模型上下文时，application 层仍会使用 practiceReference
       * 校验并加载学习计划、阶段和题面详情。
       */
      requestMetadata.put(PracticeChatPromptConstants.METADATA_SCENARIO, PracticeChatPromptConstants.SCENARIO);
      requestMetadata.put(PracticeChatPromptConstants.METADATA_PLAN_ID, practiceReference.planId());
      requestMetadata.put(PracticeChatPromptConstants.METADATA_PHASE_INDEX, practiceReference.phaseIndex());
      requestMetadata.put(PracticeChatPromptConstants.METADATA_PROBLEM_SLUG, practiceReference.problemSlug());
      requestMetadata.put(PracticeChatPromptConstants.METADATA_LOCALE, practiceReference.locale());
    }
    /*
     * 进入 Agent run 前先经过 AI 治理准入：
     * - 校验功能开关、登录态、权限、请求大小、每日额度和用户级并发锁；
     * - 写入准入/拒绝审计记录；
     * - 返回需要继续下传的治理 metadata，例如 admissionId、策略版本和锁 token。
     *
     * admission.metadata() 会合并进 AgentConversationCommand，后续 Agent run/trace
     * 依赖这些字段做观测关联和终态锁释放。
     */
    AiRunAdmission admission = admissionService.admit(new AiRunContext(
        UUID.randomUUID().toString(),
        actor,
        AiPurpose.LEARNING_CHAT,
        AiRunSource.LEARNING_CHAT,
        effectiveKey,
        request.message().getBytes(StandardCharsets.UTF_8).length,
        true,
        requestMetadata,
        Instant.now()));
    Flow.Publisher<AgentStreamEvent> publisher = runCoordinator.stream(new AgentConversationCommand(
        request.taskId(),
        actor.userId(),
        request.message(),
        effectiveKey,
        admission.metadata(),
        practiceReference));
    /*
     * 本接口使用 Spring MVC 的 SseEmitter 把 Agent 的异步事件流桥接成 HTTP SSE：
     *
     * 1. runCoordinator.stream(...) 返回 Flow.Publisher<AgentStreamEvent>。Publisher 是“事件源”，
     *    它背后会启动 Agent loop，并陆续发布 run start、LLM token、工具调用、run end/error 等事件。
     * 2. SseEmitter 是 Spring MVC 提供的“长连接响应句柄”。Controller 返回它以后，HTTP 响应不会立刻结束，
     *    后续可以在其他线程中持续调用 emitter.send(...) 向浏览器写入 text/event-stream 数据。
     * 3. SseLlmStreamSubscriber 是本项目的桥接订阅者：它订阅 Publisher，收到 AgentStreamEvent 后先通过
     *    LlmStreamSseMapper 映射成 SSE 的 event/data，再写入 SseEmitter。
     *
     * 简化链路：
     *   前端 POST /stream
     *     -> Controller 创建 Publisher + SseEmitter + Subscriber
     *     -> publisher.subscribe(subscriber)
     *     -> Agent loop 发布事件
     *     -> subscriber.onNext(...) 调用 emitter.send(...)
     *     -> 浏览器 EventSource/fetch stream 按 SSE 事件名消费数据
     */
    SseEmitter emitter = new SseEmitter(30_000L);
    SseLlmStreamSubscriber subscriber = new SseLlmStreamSubscriber(emitter, sseMapper);

    /*
     * 客户端断开、SSE 超时或写响应失败时，需要取消订阅。
     * 取消后上游 Publisher/Agent loop 可以停止继续生产 token 和工具事件，避免后台任务无意义运行。
     */
    emitter.onCompletion(subscriber::cancel);
    emitter.onTimeout(subscriber::cancel);
    emitter.onError(ignored -> subscriber.cancel());

    try {
      // subscribe 是整个流式链路的启动点；之后由 Subscriber 的回调方法接收并转发事件。
      publisher.subscribe(subscriber);
    } catch (RuntimeException ex) {
      subscriber.onError(ex);
    }
    return emitter;
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record ConversationStreamRequest(
      @Positive Long taskId,
      @NotBlank String message,
      @Valid
      PracticeChatRequest practice
  ) {

    PracticeChatReference practiceReference() {
      if (practice == null) {
        return null;
      }
      return new PracticeChatReference(
          practice.planId(),
          practice.phaseIndex(),
          practice.problemSlug(),
          practice.locale());
    }
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record PracticeChatRequest(
      @Positive long planId,
      @Positive int phaseIndex,
      @NotBlank String problemSlug,
      String locale
  ) {
  }
}
