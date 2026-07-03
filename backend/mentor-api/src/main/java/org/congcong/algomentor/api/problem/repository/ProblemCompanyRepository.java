package org.congcong.algomentor.api.problem.repository;

import org.congcong.algomentor.api.problem.model.ProblemCompanySeedRecord;

public interface ProblemCompanyRepository {

  boolean upsertSignal(ProblemCompanySeedRecord record);

  void insertImportRun(
      String sourceName,
      String sourceCommit,
      String manifestPath,
      int matchedSignalCount,
      int skippedSignalCount,
      int errorCount,
      String metadataJson
  );
}
