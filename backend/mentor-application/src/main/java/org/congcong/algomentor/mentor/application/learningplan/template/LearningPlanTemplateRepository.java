package org.congcong.algomentor.mentor.application.learningplan.template;

import java.util.List;
import java.util.Optional;

public interface LearningPlanTemplateRepository {

  List<LearningPlanTemplate> findAllTemplates();

  Optional<LearningPlanTemplate> findByTemplateId(String templateId);

  LearningPlanTemplate saveTemplate(LearningPlanTemplate template);

  void insertImportRun(LearningPlanTemplateImportRun importRun);
}
