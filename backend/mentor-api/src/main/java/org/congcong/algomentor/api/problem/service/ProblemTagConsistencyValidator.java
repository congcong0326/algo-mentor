package org.congcong.algomentor.api.problem.service;

import java.util.List;
import org.congcong.algomentor.api.problem.mapper.ProblemTagMapper;
import org.congcong.algomentor.api.problem.model.ProblemTagDefinition;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

/**
 * 在 seed 导入提交前校验兼容数组与规范化标签关系。
 */
@Service
public class ProblemTagConsistencyValidator {

  private static final int VIOLATION_SAMPLE_SIZE = 10;

  private final ObjectProvider<ProblemTagMapper> mapperProvider;

  public ProblemTagConsistencyValidator(ObjectProvider<ProblemTagMapper> mapperProvider) {
    this.mapperProvider = mapperProvider;
  }

  public void verifyAvailable() {
    mapper();
  }

  public void validate(List<ProblemTagDefinition> importedCatalog) {
    ProblemTagMapper mapper = mapper();
    assertNoViolations("array consistency", mapper.findArrayConsistencyViolationSlugs());
    assertNoViolations("assignment ordinals", mapper.findAssignmentOrdinalViolationSlugs());
    assertNoViolations("dangling assignments", mapper.findDanglingAssignmentReferences());
    assertNoViolations("duplicate catalog values", mapper.findDuplicateCatalogValues());
    if (!importedCatalog.isEmpty()) {
      assertNoViolations(
          "inactive imported catalog values",
          mapper.findInactiveCatalogValues(importedCatalog.stream().map(ProblemTagDefinition::value).toList()));
    }
  }

  private ProblemTagMapper mapper() {
    ProblemTagMapper mapper = mapperProvider.getIfAvailable();
    if (mapper == null) {
      throw new ProblemService.ProblemRepositoryUnavailableException();
    }
    return mapper;
  }

  private void assertNoViolations(String type, List<String> violations) {
    if (violations.isEmpty()) {
      return;
    }
    List<String> sample = violations.stream().limit(VIOLATION_SAMPLE_SIZE).toList();
    throw new IllegalStateException(
        "Problem tag consistency validation failed (" + type + "): " + sample);
  }
}
