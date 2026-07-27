package org.congcong.algomentor.ai.governance.provider.repository.mybatis;

import java.util.List;
import java.util.Optional;
import org.congcong.algomentor.ai.governance.provider.model.AiProviderInstance;
import org.congcong.algomentor.ai.governance.provider.repository.AiProviderInstanceRepository;
import org.congcong.algomentor.ai.governance.provider.repository.mybatis.model.AiProviderInstanceRow;

/** PostgreSQL/MyBatis provider instance repository. */
public class MyBatisAiProviderInstanceRepository implements AiProviderInstanceRepository {

  private final AiProviderInstanceMapper mapper;

  public MyBatisAiProviderInstanceRepository(AiProviderInstanceMapper mapper) {
    this.mapper = mapper;
  }

  @Override
  public List<AiProviderInstance> findAll() {
    return mapper.findAll().stream().map(AiProviderInstanceRow::toDomain).toList();
  }

  @Override
  public Optional<AiProviderInstance> findById(long id) {
    return Optional.ofNullable(mapper.findById(id)).map(AiProviderInstanceRow::toDomain);
  }

  @Override
  public Optional<AiProviderInstance> findByName(String name) {
    return Optional.ofNullable(mapper.findByName(name)).map(AiProviderInstanceRow::toDomain);
  }

  @Override
  public AiProviderInstance insert(AiProviderInstance instance) {
    long id = mapper.insert(AiProviderInstanceRow.fromDomain(instance));
    return findById(id).orElseThrow(() -> new IllegalStateException("Inserted provider instance was not found"));
  }

  @Override
  public boolean update(AiProviderInstance instance) {
    if (instance.id() == null) {
      throw new IllegalArgumentException("provider instance id is required for update");
    }
    return mapper.update(AiProviderInstanceRow.fromDomain(instance)) == 1;
  }
}
