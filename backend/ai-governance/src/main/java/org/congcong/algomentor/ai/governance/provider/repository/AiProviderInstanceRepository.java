package org.congcong.algomentor.ai.governance.provider.repository;

import java.util.List;
import java.util.Optional;
import org.congcong.algomentor.ai.governance.provider.model.AiProviderInstance;

/** 提供商实例的持久化边界；不负责 adapter 或远程连接测试。 */
public interface AiProviderInstanceRepository {

  List<AiProviderInstance> findAll();

  Optional<AiProviderInstance> findById(long id);

  Optional<AiProviderInstance> findByName(String name);

  AiProviderInstance insert(AiProviderInstance instance);

  boolean update(AiProviderInstance instance);
}
