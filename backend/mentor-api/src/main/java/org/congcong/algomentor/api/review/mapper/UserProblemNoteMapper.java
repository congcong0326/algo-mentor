package org.congcong.algomentor.api.review.mapper;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.congcong.algomentor.api.review.mapper.model.UserProblemNoteRow;
import org.congcong.algomentor.api.review.mapper.model.UserProblemNoteSummaryRow;

@Mapper
public interface UserProblemNoteMapper {

  UserProblemNoteSummaryRow findSummary(
      @Param("userId") long userId,
      @Param("problemSlug") String problemSlug
  );

  UserProblemNoteRow find(
      @Param("userId") long userId,
      @Param("problemSlug") String problemSlug
  );

  UserProblemNoteRow insert(
      @Param("userId") long userId,
      @Param("problemSlug") String problemSlug,
      @Param("outlineJson") JsonNode outlineJson,
      @Param("noteMarkdown") String noteMarkdown,
      @Param("now") Instant now
  );

  UserProblemNoteRow update(
      @Param("userId") long userId,
      @Param("problemSlug") String problemSlug,
      @Param("outlineJson") JsonNode outlineJson,
      @Param("noteMarkdown") String noteMarkdown,
      @Param("expectedRevision") long expectedRevision,
      @Param("now") Instant now
  );

  int delete(@Param("userId") long userId, @Param("problemSlug") String problemSlug);
}
