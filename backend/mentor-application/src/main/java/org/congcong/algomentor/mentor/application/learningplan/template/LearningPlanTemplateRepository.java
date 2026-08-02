package org.congcong.algomentor.mentor.application.learningplan.template;

import java.util.List;
import java.util.Optional;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanContentLocale;

public interface LearningPlanTemplateRepository {

  List<LearningPlanTemplate> findAllTemplates();

  Optional<LearningPlanTemplate> findByTemplateId(String templateId);

  default List<LearningPlanTemplate> findAllTemplates(LearningPlanContentLocale locale) {
    return findAllTemplates();
  }

  default Optional<LearningPlanTemplate> findByTemplateId(
      String templateId,
      LearningPlanContentLocale locale
  ) {
    return findByTemplateId(templateId);
  }

  LearningPlanTemplate saveTemplate(LearningPlanTemplate template);

  void insertImportRun(LearningPlanTemplateImportRun importRun);
}
