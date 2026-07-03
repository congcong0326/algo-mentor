package org.congcong.algomentor.api.problem.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.congcong.algomentor.api.problem.mapper.model.CompanyUpsertRow;
import org.congcong.algomentor.api.problem.mapper.model.ProblemCompanyImportRunRow;
import org.congcong.algomentor.api.problem.mapper.model.ProblemCompanySignalUpsertRow;

@Mapper
public interface ProblemCompanyMapper {

  Long upsertCompany(CompanyUpsertRow row);

  int upsertSignal(ProblemCompanySignalUpsertRow row);

  int insertImportRun(ProblemCompanyImportRunRow row);
}
