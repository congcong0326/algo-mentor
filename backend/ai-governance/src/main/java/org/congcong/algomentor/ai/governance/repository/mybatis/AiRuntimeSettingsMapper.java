package org.congcong.algomentor.ai.governance.repository.mybatis;

import org.apache.ibatis.annotations.Mapper;
import org.congcong.algomentor.ai.governance.repository.mybatis.model.AiRuntimeSettingsRow;

@Mapper
public interface AiRuntimeSettingsMapper {

  AiRuntimeSettingsRow findSingleton();

  int update(AiRuntimeSettingsRow row);
}
