package org.congcong.algomentor.mentor.application.conversation;

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

/** 为普通 Mentor 会话适配 task 互斥、幂等 replay 和 Runtime 所需的已准备 run。 */
public final class MentorConversationRunAdapter {

  private final AgentConversationService conversationService;
  private final AgentRunLockManager lockManager;
  private final AgentRunLockOwnerProvider lockOwnerProvider;

  public MentorConversationRunAdapter(
      AgentConversationService conversationService,
      AgentRunLockManager lockManager,
      AgentRunLockOwnerProvider lockOwnerProvider
  ) {
    if (conversationService == null) {
      throw new IllegalArgumentException("Mentor conversation service must not be null");
    }
    if (lockManager == null) {
      throw new IllegalArgumentException("Mentor conversation lock manager must not be null");
    }
    if (lockOwnerProvider == null) {
      throw new IllegalArgumentException("Mentor conversation lock owner provider must not be null");
    }
    this.conversationService = conversationService;
    this.lockManager = lockManager;
    this.lockOwnerProvider = lockOwnerProvider;
  }

  public AgentPreparedRequest prepare(MentorConversationAgentInput input) {
    AgentRunLockToken lockToken = null;
    if (input.taskId() != null) {
      AgentRunLockAcquireResult lockResult = tryAcquire(input.taskId(), preRunMetadata(input));
      if (lockResult.acquired()) {
        lockToken = lockResult.token();
      } else if (sameIdempotencyKey(lockResult.conflict(), input.idempotencyKey())) {
        AgentConversationRun replay = conversationService.findMentorRunByIdempotencyKey(input)
            .orElseThrow(() -> new AgentConversationRunInProgressException(input.taskId()));
        return preparedRequest(replay, AgentRunResource.none());
      } else {
        throw new AgentConversationRunInProgressException(input.taskId());
      }
    }

    try {
      AgentConversationRun run = conversationService.prepareMentorRun(input);
      if (run.idempotentReplay()) {
        release(lockToken);
        return preparedRequest(run, AgentRunResource.none());
      }
      if (lockToken == null) {
        lockToken = acquire(run, input.idempotencyKey());
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

  private AgentRunLockToken acquire(AgentConversationRun run, String idempotencyKey) {
    AgentRunLockAcquireResult lockResult = tryAcquire(run.taskId(), Map.of(
        AgentRuntimeMetadataKeys.TASK_ID, run.taskId(),
        AgentRuntimeMetadataKeys.RUN_DB_ID, run.runId(),
        AgentRunLockConstants.RUN_UUID_METADATA_KEY, run.runUuid(),
        AgentRunLockConstants.IDEMPOTENCY_KEY_METADATA_KEY, idempotencyKey));
    if (!lockResult.acquired()) {
      throw new AgentConversationRunInProgressException(run.taskId());
    }
    return lockResult.token();
  }

  private AgentRunLockAcquireResult tryAcquire(long taskId, Map<String, Object> metadata) {
    return lockManager.tryAcquire(new AgentRunLockRequest(
        AgentRunLockConstants.TASK_LOCK_KEY_PREFIX + taskId,
        lockOwnerProvider.ownerId(),
        null,
        metadata));
  }

  private Map<String, Object> preRunMetadata(MentorConversationAgentInput input) {
    return Map.of(
        AgentRuntimeMetadataKeys.TASK_ID, input.taskId(),
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
    if (conflict == null) {
      return false;
    }
    return idempotencyKey.equals(conflict.metadata().get(AgentRunLockConstants.IDEMPOTENCY_KEY_METADATA_KEY));
  }

  private void release(AgentRunLockToken lockToken) {
    if (lockToken != null) {
      lockManager.release(lockToken);
    }
  }
}
