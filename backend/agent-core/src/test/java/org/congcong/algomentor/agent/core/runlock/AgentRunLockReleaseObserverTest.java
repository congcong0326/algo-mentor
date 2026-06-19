package org.congcong.algomentor.agent.core.runlock;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.congcong.algomentor.agent.core.AgentLoopContext;
import org.congcong.algomentor.agent.core.AgentRequest;
import org.congcong.algomentor.agent.core.AgentRunResult;
import org.congcong.algomentor.llm.core.request.LlmMessage;
import org.congcong.algomentor.llm.core.response.LlmFinishReason;
import org.junit.jupiter.api.Test;

class AgentRunLockReleaseObserverTest {

  @Test
  void releasesLockOnRunEndWhenMetadataContainsToken() {
    InMemoryAgentRunLockManager manager = new InMemoryAgentRunLockManager();
    AgentRunLockRequest request = new AgentRunLockRequest(
        AgentRunLockConstants.TASK_LOCK_KEY_PREFIX + 7,
        "owner-a",
        null,
        Map.of("taskId", 7L));
    AgentRunLockToken token = manager.tryAcquire(request).token();
    AgentRunLockReleaseObserver observer = new AgentRunLockReleaseObserver(manager);

    observer.onRunEnd(context(token), new AgentRunResult(1, LlmFinishReason.STOP, Map.of()));

    assertThat(manager.tryAcquire(request).acquired()).isTrue();
  }

  @Test
  void releasesLockOnErrorWhenMetadataContainsSerializedToken() {
    InMemoryAgentRunLockManager manager = new InMemoryAgentRunLockManager();
    AgentRunLockRequest request = new AgentRunLockRequest(
        AgentRunLockConstants.TASK_LOCK_KEY_PREFIX + 8,
        "owner-a",
        null,
        Map.of("taskId", 8L));
    AgentRunLockToken token = manager.tryAcquire(request).token();
    AgentRunLockReleaseObserver observer = new AgentRunLockReleaseObserver(manager);
    Map<String, Object> tokenMap = Map.of(
        "lockKey", token.lockKey(),
        "ownerId", token.ownerId(),
        "tokenId", token.tokenId());

    observer.onError(context(tokenMap), new org.congcong.algomentor.agent.core.AgentException(
        org.congcong.algomentor.agent.core.AgentErrorCode.UNKNOWN,
        "failed"));

    assertThat(manager.tryAcquire(request).acquired()).isTrue();
  }

  private AgentLoopContext context(Object token) {
    Map<String, Object> metadata = Map.of(AgentRunLockConstants.LOCK_TOKEN_METADATA_KEY, token);
    AgentRequest request = new AgentRequest("run-1", "request-1", List.of(LlmMessage.user("hello")), metadata);
    return new AgentLoopContext("run-1", request, 1, request.metadata());
  }
}
