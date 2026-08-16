package org.congcong.algomentor.mentor.application.practice;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import org.congcong.algomentor.agent.core.runtime.definition.AgentRunResource;

/** 在 Practice Chat Prompt 索引生成后，为同一 run 打开历史提交 capability scope。 */
public final class PracticeSubmissionHistoryScopeService {

  private final PracticeSubmissionHistoryRunScopeRegistry registry;

  public PracticeSubmissionHistoryScopeService(PracticeSubmissionHistoryRunScopeRegistry registry) {
    this.registry = Objects.requireNonNull(registry, "registry must not be null");
  }

  public OpenedScope openScope(
      long userId,
      String locale,
      PracticeSubmissionHistoryContext context,
      String currentUserMessage
  ) {
    Objects.requireNonNull(context, "Practice submission history context must not be null");
    PracticeSubmissionHistoryRunScopeRegistry.ScopeLease lease = registry.openScope(
        userId, locale, context.scopeInputs());
    Map<String, Object> metadata = new LinkedHashMap<>();
    metadata.put(PracticeSubmissionHistoryToolContracts.METADATA_SCOPE_REF, lease.scopeRef());
    metadata.put(
        PracticeSubmissionHistoryToolContracts.METADATA_CODE_DETAIL_INTENT,
        PracticeSubmissionHistoryCodeIntent.hasExplicitCodeRequest(currentUserMessage));
    return new OpenedScope(Map.copyOf(metadata), lease);
  }

  /** request metadata 仅携带随机 scopeRef 与布尔意图判定，实际 scope 数据由 lease 持有。 */
  public record OpenedScope(Map<String, Object> metadata, AgentRunResource runResource) {

    public OpenedScope {
      metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
      runResource = runResource == null ? AgentRunResource.none() : runResource;
    }
  }
}
