package org.congcong.algomentor.mentor.application.profile.document;

import java.util.List;

/** 文档投影的批量只读端口，禁止回退到旧画像正文。 */
public interface LearnerProfileDocumentProjectionRepository {

  LearnerProfileProjectionSnapshot loadSnapshot(long userId);

  boolean existsActiveStatement(long userId, long claimRevisionId);

  List<LearnerProfileDocument.EvidenceItem> findActiveEvidence(
      long userId,
      long claimRevisionId,
      LearnerProfileStatementReferenceCodec.EvidenceCursor cursor,
      int limitPlusOne);
}
