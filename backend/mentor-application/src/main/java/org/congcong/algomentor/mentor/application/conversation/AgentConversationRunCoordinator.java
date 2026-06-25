package org.congcong.algomentor.mentor.application.conversation;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Flow;
import org.congcong.algomentor.agent.core.AgentLoopRunner;
import org.congcong.algomentor.agent.core.AgentRequest;
import org.congcong.algomentor.agent.core.AgentStreamEvent;
import org.congcong.algomentor.agent.core.runlock.AgentRunLockAcquireResult;
import org.congcong.algomentor.agent.core.runlock.AgentRunLockConstants;
import org.congcong.algomentor.agent.core.runlock.AgentRunLockConflict;
import org.congcong.algomentor.agent.core.runlock.AgentRunLockManager;
import org.congcong.algomentor.agent.core.runlock.AgentRunLockOwnerProvider;
import org.congcong.algomentor.agent.core.runlock.AgentRunLockRequest;
import org.congcong.algomentor.agent.core.runlock.AgentRunLockToken;
import org.congcong.algomentor.agent.core.runtime.model.AgentRuntimeMetadataKeys;
import org.congcong.algomentor.llm.core.response.LlmFinishReason;

/**
 * Agent 会话流式 run 的应用层编排入口。
 *
 * <p>这里负责把会话 prepareRun、task 级互斥锁和通用 {@link AgentLoopRunner} 串起来。锁获取需要依赖
 * mentor 会话的 taskId/runId 语义，因此放在 application 边界；异步 run 的正常释放则通过
 * {@code AgentRunLockReleaseObserver} 在 Agent loop 终态回调中完成。</p>
 */
public class AgentConversationRunCoordinator {

  private final AgentConversationService conversationService;
  private final AgentLoopRunner agentLoopRunner;
  private final AgentRunLockManager lockManager;
  private final AgentRunLockOwnerProvider lockOwnerProvider;

  public AgentConversationRunCoordinator(
      AgentConversationService conversationService,
      AgentLoopRunner agentLoopRunner,
      AgentRunLockManager lockManager,
      AgentRunLockOwnerProvider lockOwnerProvider
  ) {
    if (conversationService == null) {
      throw new IllegalArgumentException("Agent conversation service must not be null");
    }
    if (agentLoopRunner == null) {
      throw new IllegalArgumentException("Agent loop runner must not be null");
    }
    if (lockManager == null) {
      throw new IllegalArgumentException("Agent run lock manager must not be null");
    }
    if (lockOwnerProvider == null) {
      throw new IllegalArgumentException("Agent run lock owner provider must not be null");
    }
    this.conversationService = conversationService;
    this.agentLoopRunner = agentLoopRunner;
    this.lockManager = lockManager;
    this.lockOwnerProvider = lockOwnerProvider;
  }

  /**
   * 创建或复用一次会话 run，并返回可被 Controller/SSE 层订阅的 Agent 事件流。
   *
   * <p>这个方法的职责是应用层编排，而不是执行模型推理本身：</p>
   * <ul>
   *   <li>在已知 taskId 时，先尝试获取 task 级运行锁，避免同一任务并发启动多个 Agent run；</li>
   *   <li>如果锁冲突来自相同幂等键，则优先查找并回放已有 run 的身份事件，避免重复创建消息和重复调用模型；</li>
   *   <li>调用 {@link AgentConversationService#prepareRun(AgentConversationCommand)} 持久化用户消息、
   *       恢复历史上下文，并生成通用 {@link AgentRequest}；</li>
   *   <li>把锁 token 写入 AgentRequest metadata，交给 Agent loop/observer 在异步终态释放锁；</li>
   *   <li>只在 prepare/subscribe 启动阶段同步失败时兜底释放锁，正常流式过程的释放不在这里做。</li>
   * </ul>
   */
  public Flow.Publisher<AgentStreamEvent> stream(AgentConversationCommand command) {
    if (command == null) {
      throw new IllegalArgumentException("Agent conversation command must not be null");
    }
    AgentRunLockToken lockToken = null;
    if (command.taskId() != null) {
      // taskId 已明确时，先加锁再 prepareRun，避免并发请求同时写入同一任务的会话上下文。
      AgentRunLockAcquireResult lockResult = tryAcquireLock(
          command.taskId(),
          preRunLockMetadata(command.taskId(), command.idempotencyKey()));
      if (lockResult.acquired()) {
        lockToken = lockResult.token();
      } else if (sameIdempotencyKey(lockResult.conflict(), command.idempotencyKey())) {
        // 相同幂等键说明很可能是客户端重试：不启动新 run，只回放已有 run 的起止身份事件。
        AgentConversationRun replay = conversationService
            .findRunByIdempotencyKey(command)
            .orElseThrow(() -> new AgentConversationRunInProgressException(command.taskId()));
        return replayPublisher(replay);
      } else {
        throw new AgentConversationRunInProgressException(command.taskId());
      }
    }
    try {
      AgentConversationRun run = conversationService.prepareRun(command);
      if (run.idempotentReplay()) {
        lockManager.release(lockToken);
        return replayPublisher(run);
      }
      if (lockToken == null) {
        // 无 taskId 请求会在 prepareRun 后得到实际 taskId，此时再补拿 task 级锁。
        lockToken = acquireLock(run.taskId(), lockMetadata(run, command.idempotencyKey()));
      }
      return new LockedAgentStreamPublisher(
          agentLoopRunner.stream(withLockToken(run.agentRequest(), lockToken)),
          lockManager,
          lockToken);
    } catch (RuntimeException ex) {
      lockManager.release(lockToken);
      throw ex;
    }
  }

  private AgentRunLockToken acquireLock(long taskId, Map<String, Object> metadata) {
    AgentRunLockAcquireResult lockResult = tryAcquireLock(taskId, metadata);
    if (!lockResult.acquired()) {
      throw new AgentConversationRunInProgressException(taskId);
    }
    return lockResult.token();
  }

  private AgentRunLockAcquireResult tryAcquireLock(long taskId, Map<String, Object> metadata) {
    return lockManager.tryAcquire(new AgentRunLockRequest(
        AgentRunLockConstants.TASK_LOCK_KEY_PREFIX + taskId,
        lockOwnerProvider.ownerId(),
        null,
        metadata));
  }

  private Map<String, Object> preRunLockMetadata(long taskId, String idempotencyKey) {
    return Map.of(
        AgentRuntimeMetadataKeys.TASK_ID, taskId,
        AgentRunLockConstants.IDEMPOTENCY_KEY_METADATA_KEY, idempotencyKey);
  }

  private Map<String, Object> lockMetadata(AgentConversationRun run, String idempotencyKey) {
    return Map.of(
        AgentRuntimeMetadataKeys.TASK_ID, run.taskId(),
        AgentRuntimeMetadataKeys.RUN_DB_ID, run.runId(),
        AgentRunLockConstants.RUN_UUID_METADATA_KEY, run.runUuid(),
        AgentRunLockConstants.IDEMPOTENCY_KEY_METADATA_KEY, idempotencyKey);
  }

  private AgentRequest withLockToken(AgentRequest request, Object lockToken) {
    Map<String, Object> metadata = new HashMap<>(request.metadata());
    metadata.put(AgentRunLockConstants.LOCK_TOKEN_METADATA_KEY, lockToken);
    return new AgentRequest(request.runId(), request.requestId(), request.messages(), metadata);
  }

  private boolean sameIdempotencyKey(AgentRunLockConflict conflict, String idempotencyKey) {
    if (conflict == null || idempotencyKey == null) {
      return false;
    }
    Object lockedKey = conflict.metadata().get(AgentRunLockConstants.IDEMPOTENCY_KEY_METADATA_KEY);
    return idempotencyKey.equals(lockedKey);
  }

  private Flow.Publisher<AgentStreamEvent> replayPublisher(AgentConversationRun run) {
    return subscriber -> subscriber.onSubscribe(new Flow.Subscription() {
      private boolean completed;

      @Override
      public void request(long n) {
        if (completed || n <= 0) {
          return;
        }
        completed = true;
        subscriber.onNext(new AgentStreamEvent.AgentRunStart(
            run.runUuid(),
            run.agentRequest().displayTitle(),
            1,
            run.agentRequest().metadata()));
        subscriber.onNext(new AgentStreamEvent.AgentRunEnd(
            run.runUuid(),
            1,
            LlmFinishReason.UNKNOWN,
            replayMetadata(run)));
        subscriber.onComplete();
      }

      @Override
      public void cancel() {
        completed = true;
      }
    });
  }

  private Map<String, Object> replayMetadata(AgentConversationRun run) {
    Map<String, Object> metadata = new HashMap<>(run.agentRequest().metadata());
    metadata.put(AgentRuntimeMetadataKeys.IDEMPOTENT_REPLAY, true);
    return Map.copyOf(metadata);
  }

  private record LockedAgentStreamPublisher(
      Flow.Publisher<AgentStreamEvent> delegate,
      AgentRunLockManager lockManager,
      AgentRunLockToken lockToken
  ) implements Flow.Publisher<AgentStreamEvent> {

    @Override
    public void subscribe(Flow.Subscriber<? super AgentStreamEvent> subscriber) {
      try {
        delegate.subscribe(subscriber);
      } catch (RuntimeException ex) {
        // 只处理启动/订阅阶段的同步失败；异步 run 结束由 AgentLoopObserver 释放锁。
        lockManager.release(lockToken);
        throw ex;
      }
    }
  }
}
