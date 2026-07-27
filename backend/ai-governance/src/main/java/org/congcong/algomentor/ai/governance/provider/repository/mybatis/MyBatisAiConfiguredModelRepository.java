package org.congcong.algomentor.ai.governance.provider.repository.mybatis;

import java.util.List;
import java.util.Optional;
import org.congcong.algomentor.ai.governance.provider.model.AiConfiguredModel;
import org.congcong.algomentor.ai.governance.provider.repository.AiConfiguredModelRepository;
import org.congcong.algomentor.ai.governance.provider.repository.mybatis.model.AiConfiguredModelRow;

/** PostgreSQL/MyBatis configured model repository. */
public class MyBatisAiConfiguredModelRepository implements AiConfiguredModelRepository {

  private final AiConfiguredModelMapper mapper;

  public MyBatisAiConfiguredModelRepository(AiConfiguredModelMapper mapper) {
    this.mapper = mapper;
  }

  @Override
  public List<AiConfiguredModel> findByProviderInstanceId(long providerInstanceId) {
    return mapper.findByProviderInstanceId(providerInstanceId).stream()
        .map(AiConfiguredModelRow::toDomain)
        .toList();
  }

  @Override
  public Optional<AiConfiguredModel> findById(long id) {
    return Optional.ofNullable(mapper.findById(id)).map(AiConfiguredModelRow::toDomain);
  }

  @Override
  public Optional<AiConfiguredModel> findByProviderInstanceIdAndUpstreamModelId(
      long providerInstanceId,
      String upstreamModelId
  ) {
    return Optional.ofNullable(mapper.findByProviderInstanceIdAndModelId(providerInstanceId, upstreamModelId))
        .map(AiConfiguredModelRow::toDomain);
  }

  @Override
  public AiConfiguredModel insert(AiConfiguredModel model) {
    long id = mapper.insert(AiConfiguredModelRow.fromDomain(model));
    return findById(id).orElseThrow(() -> new IllegalStateException("Inserted configured model was not found"));
  }

  @Override
  public boolean update(AiConfiguredModel model) {
    if (model.id() == null) {
      throw new IllegalArgumentException("configured model id is required for update");
    }
    return mapper.update(AiConfiguredModelRow.fromDomain(model)) == 1;
  }
}
