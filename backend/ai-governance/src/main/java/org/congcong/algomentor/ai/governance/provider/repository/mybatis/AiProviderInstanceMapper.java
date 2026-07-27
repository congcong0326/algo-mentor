package org.congcong.algomentor.ai.governance.provider.repository.mybatis;

import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.congcong.algomentor.ai.governance.provider.repository.mybatis.model.AiProviderInstanceRow;

@Mapper
public interface AiProviderInstanceMapper {

  List<AiProviderInstanceRow> findAll();

  AiProviderInstanceRow findById(@Param("id") long id);

  AiProviderInstanceRow findByName(@Param("name") String name);

  long insert(AiProviderInstanceRow row);

  int update(AiProviderInstanceRow row);
}
