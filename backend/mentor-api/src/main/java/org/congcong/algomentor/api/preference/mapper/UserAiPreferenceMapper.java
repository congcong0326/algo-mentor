package org.congcong.algomentor.api.preference.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.congcong.algomentor.api.preference.mapper.model.UserAiPreferenceRow;

@Mapper
public interface UserAiPreferenceMapper {

  UserAiPreferenceRow findByUserId(@Param("userId") long userId);

  UserAiPreferenceRow upsert(
      @Param("userId") long userId,
      @Param("coachStyle") String coachStyle
  );
}
