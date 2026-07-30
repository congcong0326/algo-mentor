package org.congcong.algomentor.mentor.application.practice;

import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import org.congcong.algomentor.agent.core.AgentRequest;
import org.congcong.algomentor.agent.core.runlock.AgentRunLockAcquireResult;
import org.congcong.algomentor.agent.core.runlock.AgentRunLockConflict;
import org.congcong.algomentor.agent.core.runlock.AgentRunLockConstants;
import org.congcong.algomentor.agent.core.runlock.AgentRunLockManager;
import org.congcong.algomentor.agent.core.runlock.AgentRunLockOwnerProvider;
import org.congcong.algomentor.agent.core.runlock.AgentRunLockRequest;
import org.congcong.algomentor.agent.core.runlock.AgentRunLockToken;
import org.congcong.algomentor.agent.core.runtime.definition.AgentPreparedRequest;
import org.congcong.algomentor.agent.core.runtime.definition.AgentRunResource;
import org.congcong.algomentor.agent.core.runtime.model.AgentRuntimeMetadataKeys;
import org.congcong.algomentor.mentor.application.conversation.AgentConversationRun;
import org.congcong.algomentor.mentor.application.conversation.AgentConversationRunInProgressException;
import org.congcong.algomentor.mentor.application.conversation.AgentConversationService;

/** 为 Practice Chat 复用 session task、幂等 replay 与 task 级互斥。 */
public final class PracticeChatRunAdapter {

  private final AgentConversationService conversationService;
  private final AgentRunLockManager lockManager;
  private final AgentRunLockOwnerProvider lockOwnerProvider;

  public PracticeChatRunAdapter(
      AgentConversationService conversationService,
      AgentRunLockManager lockManager,
      AgentRunLockOwnerProvider lockOwnerProvider
  ) {
    if (conversationService == null) {
      throw new IllegalArgumentException("Practice chat conversation service must not be null");
    }
    if (lockManager == null) {
      throw new IllegalArgumentException("Practice chat lock manager must not be null");
    }
    if (lockOwnerProvider == null) {
      throw new IllegalArgumentException("Practice chat lock owner provider must not be null");
    }
    this.conversationService = conversationService;
    this.lockManager = lockManager;
    this.lockOwnerProvider = lockOwnerProvider;
  }

  public AgentPreparedRequest prepare(PracticeChatAgentInput input) {
    AgentRunLockToken lockToken = null;
    try {
      AgentRunLockAcquireResult lockResult = tryAcquire(input.agentTaskId(), preRunMetadata(input));
      if (lockResult.acquired()) {
        lockToken = lockResult.token();
      } else if (sameIdempotencyKey(lockResult.conflict(), input.idempotencyKey())) {
        AgentConversationRun replay = conversationService.findPracticeRunByIdempotencyKey(input)
            .orElseThrow(() -> new AgentConversationRunInProgressException(input.agentTaskId()));
        return preparedRequest(replay, AgentRunResource.none());
      } else {
        throw new AgentConversationRunInProgressException(input.agentTaskId());
      }

      AgentConversationRun run = conversationService.preparePracticeRun(input);
      if (run.idempotentReplay()) {
        release(lockToken);
        return preparedRequest(run, AgentRunResource.none());
      }
      return preparedRequest(run, lockResource(lockToken));
    } catch (RuntimeException failure) {
      release(lockToken);
      throw failure;
    }
  }

  private AgentPreparedRequest preparedRequest(AgentConversationRun run, AgentRunResource runResource) {
    AgentRequest request = run.agentRequest();
    return new AgentPreparedRequest(
        request.messages(),
        request.metadata(),
        request.executionOptions(),
        run.preparedRun(),
        run.idempotentReplay(),
        runResource);
  }

  private AgentRunLockAcquireResult tryAcquire(long taskId, Map<String, Object> metadata) {
    return lockManager.tryAcquire(new AgentRunLockRequest(
        AgentRunLockConstants.TASK_LOCK_KEY_PREFIX + taskId,
        lockOwnerProvider.ownerId(),
        null,
        metadata));
  }

  private Map<String, Object> preRunMetadata(PracticeChatAgentInput input) {
    return Map.of(
        AgentRuntimeMetadataKeys.TASK_ID, input.agentTaskId(),
        AgentRunLockConstants.IDEMPOTENCY_KEY_METADATA_KEY, input.idempotencyKey());
  }

  private AgentRunResource lockResource(AgentRunLockToken lockToken) {
    AtomicBoolean released = new AtomicBoolean();
    return () -> {
      if (released.compareAndSet(false, true)) {
        lockManager.release(lockToken);
      }
    };
  }

  private boolean sameIdempotencyKey(AgentRunLockConflict conflict, String idempotencyKey) {
    return conflict != null
        && idempotencyKey.equals(conflict.metadata().get(AgentRunLockConstants.IDEMPOTENCY_KEY_METADATA_KEY));
  }

  private void release(AgentRunLockToken lockToken) {
    if (lockToken != null) {
      lockManager.release(lockToken);
    }
  }
}
