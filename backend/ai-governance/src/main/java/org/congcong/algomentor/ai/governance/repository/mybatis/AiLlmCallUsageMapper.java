package org.congcong.algomentor.ai.governance.repository.mybatis;

import org.apache.ibatis.annotations.Mapper;
import org.congcong.algomentor.ai.governance.repository.mybatis.model.AiLlmCallUsageRow;
import org.congcong.algomentor.ai.governance.repository.mybatis.model.AiLlmCallUsageUpdate;

@Mapper
public interface AiLlmCallUsageMapper {

  int insert(AiLlmCallUsageRow row);

  int updateTerminal(AiLlmCallUsageUpdate update);
}
