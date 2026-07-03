package org.congcong.algomentor.api.problem.repository;

import org.congcong.algomentor.api.problem.mapper.ProblemCompanyMapper;
import org.congcong.algomentor.api.problem.mapper.model.CompanyUpsertRow;
import org.congcong.algomentor.api.problem.mapper.model.ProblemCompanyImportRunRow;
import org.congcong.algomentor.api.problem.mapper.model.ProblemCompanySignalUpsertRow;
import org.congcong.algomentor.api.problem.model.ProblemCompanySeedRecord;

public class MyBatisProblemCompanyRepository implements ProblemCompanyRepository {

  private final ProblemCompanyMapper mapper;

  public MyBatisProblemCompanyRepository(ProblemCompanyMapper mapper) {
    this.mapper = mapper;
  }

  @Override
  public boolean upsertSignal(ProblemCompanySeedRecord record) {
    Long companyId = mapper.upsertCompany(new CompanyUpsertRow(
        record.companySlug(),
        record.companyName(),
        record.companyMarket()));
    int rows = mapper.upsertSignal(new ProblemCompanySignalUpsertRow(
        record.problemSlug(),
        companyId,
        record.role(),
        record.recencyBucket(),
        record.frequencyScore(),
        record.rank(),
        record.acceptanceRate(),
        record.sourceName(),
        record.sourceUrl(),
        record.sourceCommit(),
        record.sourceProblemUrl()));
    return rows > 0;
  }

  @Override
  public void insertImportRun(
      String sourceName,
      String sourceCommit,
      String manifestPath,
      int matchedSignalCount,
      int skippedSignalCount,
      int errorCount,
      String metadataJson
  ) {
    mapper.insertImportRun(new ProblemCompanyImportRunRow(
        sourceName,
        sourceCommit,
        manifestPath,
        matchedSignalCount,
        skippedSignalCount,
        errorCount,
        metadataJson));
  }
}
