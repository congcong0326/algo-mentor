package org.congcong.algomentor.api.practice.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.congcong.algomentor.api.practice.mapper.model.PracticeCodeReviewInsertRow;
import org.congcong.algomentor.api.practice.mapper.model.PracticeCodeReviewRow;
import org.congcong.algomentor.api.practice.mapper.model.PracticeCodeReviewSessionLockRow;
import org.congcong.algomentor.api.practice.mapper.model.PracticeCodeReviewSummaryRow;
import org.congcong.algomentor.api.practice.mapper.model.PracticeCodeReviewIndexRow;
import org.congcong.algomentor.api.practice.mapper.model.LearnerMemoryCodeReviewFactRow;
import org.congcong.algomentor.api.practice.mapper.model.CodeReviewHistoryRow;
import org.congcong.algomentor.api.practice.mapper.model.CodeReviewEvidenceDetailRow;
import org.congcong.algomentor.api.practice.mapper.model.CodeReviewSubmissionVersionRow;
import org.congcong.algomentor.api.practice.mapper.model.PracticeCodeReviewHistoricalFactRow;
import org.congcong.algomentor.api.practice.mapper.model.PracticeSubmissionHistoryProblemRow;

@Mapper
public interface PracticeCodeReviewMapper {

  PracticeCodeReviewSessionLockRow lockSessionForReviewInsert(
      @Param("userId") long userId,
      @Param("sessionId") long sessionId
  );

  PracticeCodeReviewRow insert(PracticeCodeReviewInsertRow row);

  PracticeCodeReviewRow findByUserMessageForUpdate(
      @Param("userId") long userId,
      @Param("sessionId") long sessionId,
      @Param("userMessageId") long userMessageId
  );

  int insertAffectedTags(@Param("reviewId") long reviewId, @Param("tagIds") List<Long> tagIds);

  List<Long> findAffectedTagIds(@Param("reviewId") long reviewId);

  PracticeCodeReviewRow findLatest(@Param("userId") long userId, @Param("sessionId") long sessionId);

  PracticeCodeReviewSummaryRow findLatestSummary(@Param("userId") long userId, @Param("sessionId") long sessionId);

  List<PracticeCodeReviewSummaryRow> findSummaries(@Param("userId") long userId, @Param("sessionId") long sessionId);

  List<PracticeCodeReviewIndexRow> findRecentByProblemSlugs(
      @Param("userId") long userId,
      @Param("problemSlugs") List<String> problemSlugs,
      @Param("perProblemLimit") int perProblemLimit
  );

  PracticeCodeReviewRow findById(
      @Param("userId") long userId,
      @Param("sessionId") long sessionId,
      @Param("reviewId") long reviewId
  );

  PracticeCodeReviewRow findByUserMessage(
      @Param("userId") long userId,
      @Param("sessionId") long sessionId,
      @Param("userMessageId") long userMessageId
  );

  List<LearnerMemoryCodeReviewFactRow> findProfileFactsByReviewIds(
      @Param("userId") long userId,
      @Param("reviewIds") List<Long> reviewIds
  );

  List<LearnerMemoryCodeReviewFactRow> findLatestProfileFactsForProblemSlugs(
      @Param("userId") long userId,
      @Param("problemSlugs") List<String> problemSlugs
  );

  List<LearnerMemoryCodeReviewFactRow> findRecentDistinctProfileFacts(
      @Param("userId") long userId,
      @Param("excludedProblemSlugs") List<String> excludedProblemSlugs,
      @Param("limit") int limit
  );

  List<CodeReviewHistoryRow> findLatestHistoryForProblem(
      @Param("userId") long userId,
      @Param("problemSlug") String problemSlug,
      @Param("limit") int limit
  );

  List<PracticeCodeReviewHistoricalFactRow> findRecentHistoryFactsForProblem(
      @Param("userId") long userId,
      @Param("problemSlug") String problemSlug,
      @Param("limit") int limit
  );

  List<PracticeSubmissionHistoryProblemRow> findRecentSubmittedProblems(
      @Param("userId") long userId,
      @Param("limit") int limit
  );

  List<PracticeSubmissionHistoryProblemRow> findRecentSubmittedProblemsForSlugs(
      @Param("userId") long userId,
      @Param("problemSlugs") List<String> problemSlugs,
      @Param("limit") int limit
  );

  CodeReviewEvidenceDetailRow findEvidenceDetail(
      @Param("userId") long userId,
      @Param("reviewId") long reviewId
  );

  List<CodeReviewSubmissionVersionRow> findNormalizedSubmissionVersions(
      @Param("userId") long userId,
      @Param("reviewIds") List<Long> reviewIds
  );

  List<CodeReviewHistoryRow> verifyHistoryReviews(
      @Param("userId") long userId,
      @Param("reviewIds") List<Long> reviewIds
  );
}
