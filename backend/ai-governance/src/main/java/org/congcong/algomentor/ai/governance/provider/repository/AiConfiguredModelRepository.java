package org.congcong.algomentor.ai.governance.provider.repository;

import java.util.List;
import java.util.Optional;
import org.congcong.algomentor.ai.governance.provider.model.AiConfiguredModel;

/** 配置模型的持久化边界；模型由管理员显式维护且不会物理删除。 */
public interface AiConfiguredModelRepository {

  List<AiConfiguredModel> findByProviderInstanceId(long providerInstanceId);

  Optional<AiConfiguredModel> findById(long id);

  Optional<AiConfiguredModel> findByProviderInstanceIdAndUpstreamModelId(long providerInstanceId, String upstreamModelId);

  AiConfiguredModel insert(AiConfiguredModel model);

  boolean update(AiConfiguredModel model);
}
