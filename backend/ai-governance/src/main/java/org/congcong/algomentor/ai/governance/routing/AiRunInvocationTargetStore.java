package org.congcong.algomentor.ai.governance.routing;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.congcong.algomentor.agent.core.AgentInvocationTargetResolver;
import org.congcong.algomentor.agent.core.AgentRequest;
import org.congcong.algomentor.ai.governance.model.AiGovernanceMetadataKeys;
import org.congcong.algomentor.llm.core.model.LlmInvocationTarget;

/**
 * 仅在本 JVM 内保存正在执行的 AI run 的调用目标。
 *
 * <p>SDK Client 不进入 Agent metadata、trace、SSE 或数据库。终态治理回调会移除该项。</p>
 */
public class AiRunInvocationTargetStore implements AgentInvocationTargetResolver {

  private final ConcurrentMap<String, LlmInvocationTarget> targets = new ConcurrentHashMap<>();

  public void bind(String runId, LlmInvocationTarget target) {
    if (runId == null || runId.isBlank() || target == null) {
      throw new IllegalArgumentException("runId and invocation target are required");
    }
    targets.put(runId, target);
  }

  /** 返回准入阶段已经绑定的调用目标，不触发新的模型路由解析。 */
  public Optional<LlmInvocationTarget> find(String runId) {
    if (runId == null || runId.isBlank()) {
      return Optional.empty();
    }
    return Optional.ofNullable(targets.get(runId));
  }

  @Override
  public Optional<LlmInvocationTarget> resolve(AgentRequest request) {
    if (request == null) {
      return Optional.empty();
    }
    Object admittedRunId = request.metadata().get(AiGovernanceMetadataKeys.RUN_ID);
    if (admittedRunId instanceof String value && !value.isBlank()) {
      LlmInvocationTarget target = targets.get(value);
      if (target != null) {
        return Optional.of(target);
      }
    }
    return find(request.runId());
  }

  public void remove(String runId) {
    if (runId != null) {
      targets.remove(runId);
    }
  }
}
