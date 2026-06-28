package org.congcong.algomentor.api.ability.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.congcong.algomentor.api.ability.mapper.model.AbilityTagScoreRow;

@Mapper
public interface AbilityProfileMapper {

  List<AbilityTagScoreRow> findCommonTagScores(
      @Param("userId") long userId,
      @Param("minProblemCount") int minProblemCount
  );
}
