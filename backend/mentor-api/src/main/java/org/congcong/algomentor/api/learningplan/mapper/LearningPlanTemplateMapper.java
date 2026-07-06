package org.congcong.algomentor.api.learningplan.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.congcong.algomentor.api.learningplan.mapper.model.LearningPlanTemplateImportRunRow;
import org.congcong.algomentor.api.learningplan.mapper.model.LearningPlanTemplatePhaseRow;
import org.congcong.algomentor.api.learningplan.mapper.model.LearningPlanTemplateProblemRefRow;
import org.congcong.algomentor.api.learningplan.mapper.model.LearningPlanTemplateRow;

@Mapper
public interface LearningPlanTemplateMapper {

  long upsertTemplate(LearningPlanTemplateRow row);

  int deleteProblemRefsByTemplateId(@Param("templateDbId") long templateDbId);

  int deletePhasesByTemplateId(@Param("templateDbId") long templateDbId);

  long insertPhase(LearningPlanTemplatePhaseRow row);

  int insertProblemRef(LearningPlanTemplateProblemRefRow row);

  List<LearningPlanTemplateRow> findAllTemplates();

  LearningPlanTemplateRow findByTemplateId(@Param("templateId") String templateId);

  List<LearningPlanTemplatePhaseRow> findPhasesByTemplateDbId(@Param("templateDbId") long templateDbId);

  List<LearningPlanTemplateProblemRefRow> findProblemRefsByTemplateDbId(@Param("templateDbId") long templateDbId);

  int insertImportRun(LearningPlanTemplateImportRunRow row);
}
