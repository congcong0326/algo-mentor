package org.congcong.algomentor.api.activity.mapper;

import java.time.Instant;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.congcong.algomentor.api.activity.mapper.model.ActivityContributionRow;

@Mapper
public interface ActivityContributionMapper {

  List<ActivityContributionRow> findDailyCounts(
      @Param("userId") long userId,
      @Param("timezone") String timezone,
      @Param("fromInclusive") Instant fromInclusive,
      @Param("toExclusive") Instant toExclusive
  );
}
