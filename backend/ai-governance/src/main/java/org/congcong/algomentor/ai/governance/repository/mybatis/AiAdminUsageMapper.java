package org.congcong.algomentor.ai.governance.repository.mybatis;

import java.time.LocalDate;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.congcong.algomentor.ai.governance.adminquery.AiUsageQuery;
import org.congcong.algomentor.ai.governance.repository.mybatis.model.AiObservedUnpricedModelRow;
import org.congcong.algomentor.ai.governance.repository.mybatis.model.AiUsageAggregationRow;

@Mapper
public interface AiAdminUsageMapper {

  List<AiUsageAggregationRow> summary(AiUsageQuery query);

  List<AiUsageAggregationRow> byUser(AiUsageQuery query);

  List<AiUsageAggregationRow> byModel(AiUsageQuery query);

  List<AiUsageAggregationRow> bySource(AiUsageQuery query);

  long admittedEntryRequestCount(AiUsageQuery query);

  List<AiObservedUnpricedModelRow> observedUnpricedModels(AiUsageQuery query);
}
