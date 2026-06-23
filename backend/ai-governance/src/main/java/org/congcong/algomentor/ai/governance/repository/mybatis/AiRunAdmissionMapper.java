package org.congcong.algomentor.ai.governance.repository.mybatis;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.congcong.algomentor.ai.governance.repository.mybatis.model.AiRunAdmissionRow;
import org.congcong.algomentor.ai.governance.repository.mybatis.model.AiRunStatusUpdate;

@Mapper
public interface AiRunAdmissionMapper {

  long insertAdmission(AiRunAdmissionRow row);

  int updateStatus(AiRunStatusUpdate update);

  AiRunAdmissionRow findByRunId(@Param("runId") String runId);
}
