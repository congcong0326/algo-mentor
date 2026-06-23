package org.congcong.algomentor.ai.governance.repository.mybatis;

import java.time.LocalDate;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.congcong.algomentor.ai.governance.model.AiUsage;

@Mapper
public interface AiDailyUsageMapper {

  int insertIfAbsent(@Param("userId") long userId, @Param("quotaDate") LocalDate quotaDate,
      @Param("scope") String scope, @Param("limitCount") long limitCount);

  int incrementRequestIfWithinLimit(@Param("userId") long userId, @Param("quotaDate") LocalDate quotaDate,
      @Param("scope") String scope, @Param("limitCount") long limitCount);

  int addUsage(@Param("userId") long userId, @Param("quotaDate") LocalDate quotaDate,
      @Param("scope") String scope, @Param("usage") AiUsage usage);
}
