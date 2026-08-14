package org.congcong.algomentor.api.problem.service;

import com.fasterxml.jackson.databind.JsonNode;
import java.nio.file.Path;
import java.util.List;
import org.congcong.algomentor.api.problem.model.ProblemCategoryItemSeedRecord;
import org.congcong.algomentor.api.problem.model.ProblemCategorySeedRecord;
import org.congcong.algomentor.api.problem.model.ProblemCodeTemplateSeedRecord;
import org.congcong.algomentor.api.problem.model.ProblemHintSeedRecord;
import org.congcong.algomentor.api.problem.model.ProblemMetadataSourceProblemRecord;
import org.congcong.algomentor.api.problem.model.ProblemRelationSeedRecord;

/** 读取完成但尚未通过导入前校验的 metadata seed 聚合。 */
public record ProblemLearningMetadataSeed(
    Path seedDirectory,
    Path manifestPath,
    Path auditReportPath,
    JsonNode manifest,
    JsonNode auditReport,
    List<ProblemMetadataSourceProblemRecord> sourceProblems,
    List<ProblemRelationSeedRecord> relations,
    List<ProblemHintSeedRecord> hints,
    List<ProblemCodeTemplateSeedRecord> codeTemplates,
    List<ProblemCategorySeedRecord> categories,
    List<ProblemCategoryItemSeedRecord> categoryItems
) {
}
