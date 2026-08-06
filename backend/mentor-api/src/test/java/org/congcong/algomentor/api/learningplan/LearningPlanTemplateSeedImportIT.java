package org.congcong.algomentor.api.learningplan;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.congcong.algomentor.api.learningplan.mapper.LearningPlanTemplateMapper;
import org.congcong.algomentor.api.learningplan.repository.MyBatisLearningPlanTemplateRepository;
import org.congcong.algomentor.api.learningplan.service.LearningPlanTemplateSeedConstants;
import org.congcong.algomentor.api.learningplan.service.LearningPlanTemplateSeedImportService;
import org.congcong.algomentor.api.learningplan.service.LearningPlanTemplateSeedReader;
import org.congcong.algomentor.api.problem.repository.ProblemRepository;
import org.congcong.algomentor.api.support.PostgresIntegrationTestSupport;
import org.congcong.algomentor.mentor.application.learningplan.template.LearningPlanTemplate;
import org.congcong.algomentor.mentor.application.learningplan.template.LearningPlanTemplateImportRun;
import org.congcong.algomentor.mentor.application.learningplan.template.LearningPlanTemplateRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LearningPlanTemplateSeedImportIT extends PostgresIntegrationTestSupport {

  @TempDir
  private Path tempDir;

  @Test
  void rollsBackTemplatesAndPhasesWhenTheAuditWriteFails() throws Exception {
    migrateLatest();
    writeValidSeed(tempDir);
    ObjectMapper objectMapper = new ObjectMapper();
    LearningPlanTemplateMapper mapper = sqlSessionTemplate("mapper/learningplan/LearningPlanTemplateMapper.xml")
        .getMapper(LearningPlanTemplateMapper.class);
    LearningPlanTemplateRepository databaseRepository = new MyBatisLearningPlanTemplateRepository(mapper, objectMapper);
    LearningPlanTemplateRepository failingAuditRepository = failingAuditRepository(databaseRepository);
    ProblemRepository problemRepository = mock(ProblemRepository.class);
    when(problemRepository.findProblemBySlug("two-sum")).thenReturn(Optional.empty());
    LearningPlanTemplateSeedImportService service = new LearningPlanTemplateSeedImportService(
        objectProvider(failingAuditRepository),
        objectProvider(problemRepository),
        new LearningPlanTemplateSeedReader(objectMapper),
        objectMapper);

    assertThatThrownBy(() -> transactionTemplate().executeWithoutResult(status -> {
      try {
        service.importSeed(tempDir);
      } catch (java.io.IOException exception) {
        throw new java.io.UncheckedIOException(exception);
      }
    }))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("audit write failed");
    assertThat(count("learning_plan_template")).isZero();
    assertThat(count("learning_plan_template_phase")).isZero();
    assertThat(count("learning_plan_template_problem_ref")).isZero();
    assertThat(count("learning_plan_template_import_run")).isZero();
  }

  private LearningPlanTemplateRepository failingAuditRepository(LearningPlanTemplateRepository delegate) {
    return new LearningPlanTemplateRepository() {
      @Override
      public List<LearningPlanTemplate> findAllTemplates() {
        return delegate.findAllTemplates();
      }

      @Override
      public Optional<LearningPlanTemplate> findByTemplateId(String templateId) {
        return delegate.findByTemplateId(templateId);
      }

      @Override
      public LearningPlanTemplate saveTemplate(LearningPlanTemplate template) {
        return delegate.saveTemplate(template);
      }

      @Override
      public void insertImportRun(LearningPlanTemplateImportRun importRun) {
        throw new IllegalStateException("audit write failed");
      }
    };
  }

  private void writeValidSeed(Path dir) throws Exception {
    Files.writeString(dir.resolve(LearningPlanTemplateSeedConstants.TEMPLATES_FILE), """
        {"templateId":"rollback_template","title":"标题","titleEn":"Title","summary":"摘要","summaryEn":"Summary","catalogCategory":"SYSTEMATIC_LEARNING","recommendedOrder":1,"intent":"LONG_TERM_LEARNING","goal":"目标","goalEn":"Goal","defaultDurationWeeks":1,"level":"BEGINNER","defaultWeeklyHours":5,"difficultyPreference":"MEDIUM","topicPreferences":["Array"],"targetAudience":"学习者","targetAudienceEn":"Learner","prerequisites":["基础"],"prerequisitesEn":["Basics"],"recommendedFor":["学习"],"recommendedForEn":["Learning"],"notRecommendedFor":["不适用"],"notRecommendedForEn":["Not suitable"],"expectedOutcome":"结果","expectedOutcomeEn":"Outcome","englishContentReady":true,"sourceName":"test-source","sourceUrl":"https://example.test/source","sourceCommit":"test-commit","sourceDataPath":"seed.json","sourceDescription":"source","curationNotes":"notes","licenseNotice":"license","metadata":{},"phases":[{"phaseIndex":1,"title":"阶段","titleEn":"Phase","durationWeeks":1,"focus":"重点","focusEn":"Focus"}]}
        """);
    Files.writeString(dir.resolve(LearningPlanTemplateSeedConstants.PROBLEM_REFS_FILE), """
        {"templateId":"rollback_template","phaseIndex":1,"sortOrder":1,"sourceOrder":1,"problemSlug":"two-sum","sourceTitle":"Two Sum","sourceDifficulty":"Easy","pattern":"Array","sourceUrl":"https://example.test/two-sum","metadata":{}}
        """);
    Files.writeString(dir.resolve(LearningPlanTemplateSeedConstants.MANIFEST_FILE), """
        {"source":{"commit":"test-commit"}}
        """);
    Files.writeString(dir.resolve(LearningPlanTemplateSeedConstants.METADATA_FILE), "# metadata\n");
  }
}
