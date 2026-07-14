package org.congcong.algomentor.api.admin.audit;

import java.time.Instant;
import org.apache.ibatis.annotations.Param;

public interface AdminOperationAuditMapper {

  int insert(
      @Param("operatorUserId") long operatorUserId,
      @Param("action") String action,
      @Param("targetType") String targetType,
      @Param("targetRef") String targetRef,
      @Param("outcome") String outcome,
      @Param("requestId") String requestId,
      @Param("metadataJson") String metadataJson,
      @Param("createdAt") Instant createdAt
  );
}
