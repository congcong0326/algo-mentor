package org.congcong.algomentor.mentor.application.profile.tool;

import java.util.Map;
import java.util.Objects;
import org.congcong.algomentor.agent.core.runtime.definition.AgentRunResource;

/** 为 Practice Chat 提供当前用户、当前训练题目的 Review 轨迹 capability。 */
public final class PracticeChatReviewTrajectoryScopeService {

  private final LearnerMemoryRunScopeRegistry scopeRegistry;

  public PracticeChatReviewTrajectoryScopeService(LearnerMemoryRunScopeRegistry scopeRegistry) {
    this.scopeRegistry = Objects.requireNonNull(scopeRegistry, "scopeRegistry must not be null");
  }

  public OpenedScope openScope(long userId, String problemSlug) {
    LearnerMemoryRunScopeRegistry.ScopeLease lease = scopeRegistry.openPracticeChatTrajectoryScope(userId, problemSlug);
    return new OpenedScope(scopeRegistry.initialRequestMetadata(lease), lease);
  }

  /** 只向 Agent request metadata 暴露不可枚举的 scope ref，lease 由 run 终态释放。 */
  public record OpenedScope(Map<String, Object> metadata, AgentRunResource runResource) {

    public OpenedScope {
      metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
      runResource = runResource == null ? AgentRunResource.none() : runResource;
    }
  }
}
