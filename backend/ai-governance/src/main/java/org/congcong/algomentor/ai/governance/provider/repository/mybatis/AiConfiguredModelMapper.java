package org.congcong.algomentor.ai.governance.provider.repository.mybatis;

import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.congcong.algomentor.ai.governance.provider.repository.mybatis.model.AiConfiguredModelRow;

@Mapper
public interface AiConfiguredModelMapper {

  List<AiConfiguredModelRow> findByProviderInstanceId(@Param("providerInstanceId") long providerInstanceId);

  AiConfiguredModelRow findById(@Param("id") long id);

  AiConfiguredModelRow findByProviderInstanceIdAndModelId(
      @Param("providerInstanceId") long providerInstanceId,
      @Param("modelId") String modelId);

  long insert(AiConfiguredModelRow row);

  int update(AiConfiguredModelRow row);
}
