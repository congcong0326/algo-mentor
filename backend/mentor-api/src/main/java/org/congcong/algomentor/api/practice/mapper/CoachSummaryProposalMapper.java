package org.congcong.algomentor.api.practice.mapper;

import java.time.Instant;
import java.util.List;
import org.apache.ibatis.annotations.Param;
import org.congcong.algomentor.api.practice.mapper.model.CoachSummaryMessageActionRow;
import org.congcong.algomentor.api.practice.mapper.model.CoachSummaryProposalRow;

public interface CoachSummaryProposalMapper {

  int lockScope(@Param("userId") long userId, @Param("problemSlug") String problemSlug);

  CoachSummaryProposalRow findBySource(
      @Param("sourceRunId") long sourceRunId,
      @Param("sourceToolCallId") String sourceToolCallId
  );

  int supersedePending(@Param("userId") long userId, @Param("problemSlug") String problemSlug);

  CoachSummaryProposalRow insert(CoachSummaryProposalRow row);

  CoachSummaryProposalRow findForUpdate(
      @Param("proposalId") String proposalId,
      @Param("userId") long userId,
      @Param("practiceSessionId") long practiceSessionId
  );

  CoachSummaryProposalRow markApplied(
      @Param("proposalId") String proposalId,
      @Param("appliedCoachSummaryRevision") long appliedCoachSummaryRevision,
      @Param("appliedAt") Instant appliedAt
  );

  CoachSummaryProposalRow markSuperseded(@Param("proposalId") String proposalId);

  List<CoachSummaryMessageActionRow> findMessageActions(
      @Param("userId") long userId,
      @Param("practiceSessionId") long practiceSessionId,
      @Param("assistantMessageIds") List<Long> assistantMessageIds
  );
}
