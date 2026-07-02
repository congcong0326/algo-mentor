package org.congcong.algomentor.api.review.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.congcong.algomentor.api.review.mapper.model.ReviewLogInsertRow;
import org.congcong.algomentor.api.review.mapper.model.ReviewRecallHistoryRow;

@Mapper
public interface ReviewLogMapper {

  int insert(ReviewLogInsertRow row);

  List<ReviewRecallHistoryRow> findRecentRecallHistory(
      @Param("userId") long userId,
      @Param("noteId") long noteId,
      @Param("limit") int limit
  );
}
