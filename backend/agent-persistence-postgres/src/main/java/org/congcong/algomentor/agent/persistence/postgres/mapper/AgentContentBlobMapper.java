package org.congcong.algomentor.agent.persistence.postgres.mapper;

import java.util.Optional;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.congcong.algomentor.agent.persistence.postgres.mapper.model.ContentBlobInsertRow;
import org.congcong.algomentor.agent.persistence.postgres.mapper.model.ContentBlobRow;

@Mapper
public interface AgentContentBlobMapper {

  Long insertBlob(ContentBlobInsertRow row);

  Optional<ContentBlobRow> findById(@Param("id") long id);
}
