package org.congcong.algomentor.api.practice.service;

import java.util.List;
import java.util.Optional;
import org.congcong.algomentor.api.problem.model.ProblemCodeTemplateSeedRecord;
import org.congcong.algomentor.api.problem.model.ProblemLocale;
import org.congcong.algomentor.api.problem.model.ProblemTag;
import org.congcong.algomentor.api.problem.repository.ProblemLearningMetadataRepository;
import org.congcong.algomentor.api.problem.service.ProblemService;
import org.congcong.algomentor.mentor.application.practice.PracticeChatProblemCatalog;
import org.congcong.algomentor.mentor.application.practice.PracticeChatProblemDetail;
import org.congcong.algomentor.mentor.application.practice.PracticeCodeTemplate;
import org.congcong.algomentor.mentor.application.practice.PracticeRelatedProblemCatalog;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class ProblemServicePracticeChatProblemCatalog implements PracticeChatProblemCatalog, PracticeRelatedProblemCatalog {

  private final ProblemService problemService;
  private final ProblemLearningMetadataRepository metadataRepository;

  public ProblemServicePracticeChatProblemCatalog(ProblemService problemService) {
    this(problemService, null);
  }

  @Autowired
  public ProblemServicePracticeChatProblemCatalog(
      ProblemService problemService,
      ObjectProvider<ProblemLearningMetadataRepository> metadataRepositoryProvider
  ) {
    this.problemService = problemService;
    this.metadataRepository = metadataRepositoryProvider.getIfAvailable();
  }

  @Override
  public Optional<PracticeChatProblemDetail> findProblemBySlug(String slug, String locale) {
    return problemService.findProblemBySlug(slug, ProblemLocale.parse(locale))
        .map(problem -> new PracticeChatProblemDetail(
            problem.slug(),
            problem.frontendId(),
            problem.title(),
            problemService.findProblemBySlug(slug, ProblemLocale.ZH_CN)
                .map(zhProblem -> zhProblem.title())
                .orElse(problem.title()),
            problem.difficulty() == null ? null : problem.difficulty().name(),
            tagLabels(problem.tags()),
            problem.contentMarkdown(),
            problem.leetcodeUrl(),
            codeTemplates(slug)));
  }

  @Override
  public List<String> findRelatedProblemSlugs(String problemSlug) {
    if (metadataRepository == null || problemSlug == null || problemSlug.isBlank()) {
      return List.of();
    }
    return metadataRepository.findLeetCodeSimilarProblemSlugs(problemSlug.trim());
  }

  private List<String> tagLabels(List<ProblemTag> tags) {
    return tags.stream()
        .map(ProblemTag::label)
        .toList();
  }

  private List<PracticeCodeTemplate> codeTemplates(String problemSlug) {
    if (metadataRepository == null) {
      return List.of();
    }
    return metadataRepository.findCodeTemplatesByProblemSlug(problemSlug).stream()
        .map(this::toCodeTemplate)
        .toList();
  }

  private PracticeCodeTemplate toCodeTemplate(ProblemCodeTemplateSeedRecord template) {
    return new PracticeCodeTemplate(template.languageSlug(), template.languageLabel(), template.code());
  }
}
