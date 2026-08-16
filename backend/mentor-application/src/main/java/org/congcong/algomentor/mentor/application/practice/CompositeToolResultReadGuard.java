package org.congcong.algomentor.mentor.application.practice;

import java.util.List;
import org.congcong.algomentor.agent.core.AgentExecutionContext;
import org.congcong.algomentor.agent.core.toolresult.ToolResultProvenance;
import org.congcong.algomentor.agent.core.toolresult.ToolResultReadGuard;

/** 将彼此独立 provenance 的范围读取 guard 合并，避免某个 capability 覆盖另一个。 */
public final class CompositeToolResultReadGuard implements ToolResultReadGuard {

  private final List<ToolResultReadGuard> delegates;

  public CompositeToolResultReadGuard(List<ToolResultReadGuard> delegates) {
    this.delegates = delegates == null ? List.of() : delegates.stream()
        .filter(java.util.Objects::nonNull)
        .toList();
  }

  @Override
  public ToolResultReadPermit beforeRead(
      AgentExecutionContext context,
      ToolResultProvenance provenance,
      int requestedMaxChars
  ) {
    for (ToolResultReadGuard delegate : delegates) {
      ToolResultReadPermit permit = delegate.beforeRead(context, provenance, requestedMaxChars);
      if (!permit.allowed() || permit.trackingToken() != null) {
        return permit.trackingToken() == null ? permit : ToolResultReadPermit.track(
            permit.maxVisibleChars(), new DelegatedPermit(delegate, permit));
      }
    }
    return ToolResultReadPermit.allow(requestedMaxChars);
  }

  @Override
  public void afterRead(ToolResultReadPermit permit, int visibleChars) {
    if (permit != null && permit.trackingToken() instanceof DelegatedPermit delegated) {
      delegated.delegate().afterRead(delegated.permit(), visibleChars);
    }
  }

  private record DelegatedPermit(ToolResultReadGuard delegate, ToolResultReadPermit permit) {
  }
}
