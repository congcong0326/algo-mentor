package org.congcong.algomentor.api.review.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.congcong.algomentor.api.review.mapper.model.ReviewLogInsertRow;

@Mapper
public interface ReviewLogMapper {

  int insert(ReviewLogInsertRow row);
}
