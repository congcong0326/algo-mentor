package org.congcong.algomentor.agent.persistence.postgres.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.congcong.algomentor.agent.persistence.postgres.mapper.model.ContextSnapshotRow;

@Mapper
public interface AgentContextSnapshotMapper {

  long insertSnapshot(ContextSnapshotRow row);
}
