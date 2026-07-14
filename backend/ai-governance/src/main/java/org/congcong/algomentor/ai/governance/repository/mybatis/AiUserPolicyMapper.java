package org.congcong.algomentor.ai.governance.repository.mybatis;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.congcong.algomentor.ai.governance.repository.mybatis.model.AiUserPolicyRow;

@Mapper
public interface AiUserPolicyMapper {

  AiUserPolicyRow findByUserId(@Param("userId") long userId);

  int upsert(AiUserPolicyRow row);

  int deleteByUserId(@Param("userId") long userId);
}
