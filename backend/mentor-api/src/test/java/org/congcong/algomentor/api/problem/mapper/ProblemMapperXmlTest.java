package org.congcong.algomentor.api.problem.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.Reader;
import org.congcong.algomentor.agent.persistence.postgres.json.JsonbTypeHandler;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.session.Configuration;
import org.congcong.algomentor.api.problem.mapper.model.ProblemUpsertRow;
import org.junit.jupiter.api.Test;

class ProblemMapperXmlTest {

  @Test
  void mybatisLoadsProblemMapperXml() throws Exception {
    Configuration configuration = new Configuration();
    configuration.setMapUnderscoreToCamelCase(true);
    configuration.getTypeHandlerRegistry().register(
        com.fasterxml.jackson.databind.JsonNode.class,
        new JsonbTypeHandler(new ObjectMapper()));
    configuration.getTypeHandlerRegistry().register(new JsonbTypeHandler(new ObjectMapper()));

    try (Reader reader = Resources.getResourceAsReader("mapper/problem/ProblemMapper.xml")) {
      new XMLMapperBuilder(
          reader,
          configuration,
          "mapper/problem/ProblemMapper.xml",
          configuration.getSqlFragments()).parse();
    }

    assertThat(configuration.hasStatement(
        "org.congcong.algomentor.api.problem.mapper.ProblemMapper.findProblems")).isTrue();
    assertThat(configuration.hasStatement(
        "org.congcong.algomentor.api.problem.mapper.ProblemMapper.findProblemBySlug")).isTrue();
    assertThat(configuration.hasStatement(
        "org.congcong.algomentor.api.problem.mapper.ProblemMapper.countAllProblems")).isTrue();
    assertThat(configuration.hasStatement(
        "org.congcong.algomentor.api.problem.mapper.ProblemMapper.countProblemsByDifficulty")).isTrue();
    assertThat(configuration.hasStatement(
        "org.congcong.algomentor.api.problem.mapper.ProblemMapper.countProblemsByTag")).isTrue();
    assertThat(configuration.hasStatement(
        "org.congcong.algomentor.api.problem.mapper.ProblemMapper.countProblemCategories")).isTrue();
    assertThat(configuration.hasStatement(
        "org.congcong.algomentor.api.problem.mapper.ProblemMapper.countProblemCompanies")).isTrue();
    assertThat(configuration.hasStatement(
        "org.congcong.algomentor.api.problem.mapper.ProblemMapper.countProblemSignalRoles")).isTrue();
    assertThat(configuration.hasStatement(
        "org.congcong.algomentor.api.problem.mapper.ProblemMapper.countProblemSignalRecencyBuckets")).isTrue();
    assertThat(configuration.hasStatement(
        "org.congcong.algomentor.api.problem.mapper.ProblemMapper.clearConflictingFrontendId")).isTrue();
    assertThat(configuration.hasStatement(
        "org.congcong.algomentor.api.problem.mapper.ProblemMapper.clearConflictingFrontendDisplayId")).isTrue();
    assertThat(configuration.hasStatement(
        "org.congcong.algomentor.api.problem.mapper.ProblemMapper.upsertProblem")).isTrue();
    String upsertSql = configuration.getMappedStatement(
            "org.congcong.algomentor.api.problem.mapper.ProblemMapper.upsertProblem")
        .getBoundSql(new ProblemUpsertRow(
            null, null, null, null, null, null, null, null, null, null,
            null, null, null, null, null, null, null, null, null))
        .getSql();
    assertThat(upsertSql)
        .contains("recommendation_reason_en", "recommendation_reason_zh", "IS DISTINCT FROM");
    String detailSql = configuration.getMappedStatement(
            "org.congcong.algomentor.api.problem.mapper.ProblemMapper.findProblemBySlug")
        .getBoundSql("two-sum")
        .getSql();
    assertThat(detailSql)
        .contains("p.recommendation_reason_en", "p.recommendation_reason_zh");

    try (Reader reader = Resources.getResourceAsReader("mapper/problem/ProblemCompanyMapper.xml")) {
      new XMLMapperBuilder(
          reader,
          configuration,
          "mapper/problem/ProblemCompanyMapper.xml",
          configuration.getSqlFragments()).parse();
    }

    assertThat(configuration.hasStatement(
        "org.congcong.algomentor.api.problem.mapper.ProblemCompanyMapper.upsertCompany")).isTrue();
    assertThat(configuration.hasStatement(
        "org.congcong.algomentor.api.problem.mapper.ProblemCompanyMapper.upsertSignal")).isTrue();
    assertThat(configuration.hasStatement(
        "org.congcong.algomentor.api.problem.mapper.ProblemCompanyMapper.insertImportRun")).isTrue();
  }

  @Test
  void mybatisLoadsLearningPlanMapperXml() throws Exception {
    Configuration configuration = new Configuration();
    configuration.setMapUnderscoreToCamelCase(true);

    try (Reader reader = Resources.getResourceAsReader("mapper/learningplan/LearningPlanMapper.xml")) {
      new XMLMapperBuilder(
          reader,
          configuration,
          "mapper/learningplan/LearningPlanMapper.xml",
          configuration.getSqlFragments()).parse();
    }

    assertThat(configuration.hasStatement(
        "org.congcong.algomentor.api.learningplan.mapper.LearningPlanMapper.insertDraft")).isTrue();
    assertThat(configuration.hasStatement(
        "org.congcong.algomentor.api.learningplan.mapper.LearningPlanMapper.findDraftByIdForUser")).isTrue();
    assertThat(configuration.hasStatement(
        "org.congcong.algomentor.api.learningplan.mapper.LearningPlanMapper.findDraftByIdForUserForUpdate")).isTrue();
    assertThat(configuration.hasStatement(
        "org.congcong.algomentor.api.learningplan.mapper.LearningPlanMapper.insertPlan")).isTrue();
    assertThat(configuration.hasStatement(
        "org.congcong.algomentor.api.learningplan.mapper.LearningPlanMapper.findPlansByUserId")).isTrue();
    assertThat(configuration.hasStatement(
        "org.congcong.algomentor.api.learningplan.mapper.LearningPlanMapper.findPlanByIdForUserForUpdate")).isTrue();
    assertThat(configuration.hasStatement(
        "org.congcong.algomentor.api.learningplan.mapper.LearningPlanMapper.insertProposalGroup")).isTrue();
    assertThat(configuration.hasStatement(
        "org.congcong.algomentor.api.learningplan.mapper.LearningPlanMapper.lockProposalGroupForUpdate")).isTrue();
    assertThat(configuration.hasStatement(
        "org.congcong.algomentor.api.learningplan.mapper.LearningPlanMapper.lockProposalGroupByIdForUpdate")).isTrue();
    assertThat(configuration.hasStatement(
        "org.congcong.algomentor.api.learningplan.mapper.LearningPlanMapper.discardActiveExtensionProposalGroup"))
        .isTrue();
    assertThat(configuration.hasStatement(
        "org.congcong.algomentor.api.learningplan.mapper.LearningPlanMapper.insertDraftRevision")).isTrue();
    assertThat(configuration.hasStatement(
        "org.congcong.algomentor.api.learningplan.mapper.LearningPlanMapper.insertExtensionRevision")).isTrue();
    assertThat(configuration.hasStatement(
        "org.congcong.algomentor.api.learningplan.mapper.LearningPlanMapper.markReadyExtensionRevisionsSuperseded"))
        .isTrue();
    assertThat(configuration.hasStatement(
        "org.congcong.algomentor.api.learningplan.mapper.LearningPlanMapper.updatePlanJsonSnapshot")).isTrue();

    try (Reader reader = Resources.getResourceAsReader("mapper/learningplan/LearningPlanTemplateMapper.xml")) {
      new XMLMapperBuilder(
          reader,
          configuration,
          "mapper/learningplan/LearningPlanTemplateMapper.xml",
          configuration.getSqlFragments()).parse();
    }

    assertThat(configuration.hasStatement(
        "org.congcong.algomentor.api.learningplan.mapper.LearningPlanTemplateMapper.upsertTemplate")).isTrue();
    assertThat(configuration.hasStatement(
        "org.congcong.algomentor.api.learningplan.mapper.LearningPlanTemplateMapper.insertPhase")).isTrue();
    assertThat(configuration.hasStatement(
        "org.congcong.algomentor.api.learningplan.mapper.LearningPlanTemplateMapper.insertProblemRef")).isTrue();
    assertThat(configuration.hasStatement(
        "org.congcong.algomentor.api.learningplan.mapper.LearningPlanTemplateMapper.findAllTemplates")).isTrue();
    assertThat(configuration.hasStatement(
        "org.congcong.algomentor.api.learningplan.mapper.LearningPlanTemplateMapper.findByTemplateId")).isTrue();
    assertThat(configuration.hasStatement(
        "org.congcong.algomentor.api.learningplan.mapper.LearningPlanTemplateMapper.insertImportRun")).isTrue();
  }
}
