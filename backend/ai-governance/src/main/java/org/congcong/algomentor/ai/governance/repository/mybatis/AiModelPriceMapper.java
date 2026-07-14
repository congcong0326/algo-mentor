package org.congcong.algomentor.ai.governance.repository.mybatis;

import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.congcong.algomentor.ai.governance.repository.mybatis.model.AiModelPriceRow;

@Mapper
public interface AiModelPriceMapper {

  List<AiModelPriceRow> findAll();

  AiModelPriceRow findById(@Param("id") long id);

  AiModelPriceRow findByProviderAndModel(@Param("provider") String provider, @Param("model") String model);

  long insert(AiModelPriceRow row);

  int update(AiModelPriceRow row);
}
