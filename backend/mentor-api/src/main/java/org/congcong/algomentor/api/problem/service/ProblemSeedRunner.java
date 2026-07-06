package org.congcong.algomentor.api.problem.service;

import java.nio.file.Path;
import org.congcong.algomentor.api.learningplan.service.LearningPlanTemplateSeedImportResult;
import org.congcong.algomentor.api.learningplan.service.LearningPlanTemplateSeedImportService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.stereotype.Component;

@Component
public class ProblemSeedRunner implements ApplicationRunner {

  private static final Logger log = LoggerFactory.getLogger(ProblemSeedRunner.class);

  private final ProblemSeedImporter importer;
  private final ProblemCompanySeedImportService companySeedImportService;
  private final LearningPlanTemplateSeedImportService templateSeedImportService;
  private final ConfigurableApplicationContext applicationContext;
  private final boolean problemSeedEnabled;
  private final Path seedPath;
  private final Path companySeedPath;
  private final boolean companySeedEnabled;
  private final Path templateSeedPath;
  private final boolean templateSeedEnabled;

  public ProblemSeedRunner(
      ProblemSeedImporter importer,
      ProblemCompanySeedImportService companySeedImportService,
      LearningPlanTemplateSeedImportService templateSeedImportService,
      ConfigurableApplicationContext applicationContext,
      @Value("${algo-mentor.problem.seed.enabled:false}") boolean problemSeedEnabled,
      @Value("${algo-mentor.problem.seed.path:data/seed}") String seedPath,
      @Value("${algo-mentor.problem.company-seed.path:data/company-seed}") String companySeedPath,
      @Value("${algo-mentor.problem.company-seed.enabled:false}") boolean companySeedEnabled,
      @Value("${algo-mentor.learning-plan-template.seed.path:data/learning-plan-template-seed}") String templateSeedPath,
      @Value("${algo-mentor.learning-plan-template.seed.enabled:false}") boolean templateSeedEnabled
  ) {
    this.importer = importer;
    this.companySeedImportService = companySeedImportService;
    this.templateSeedImportService = templateSeedImportService;
    this.applicationContext = applicationContext;
    this.problemSeedEnabled = problemSeedEnabled;
    this.seedPath = Path.of(seedPath);
    this.companySeedPath = Path.of(companySeedPath);
    this.companySeedEnabled = companySeedEnabled;
    this.templateSeedPath = Path.of(templateSeedPath);
    this.templateSeedEnabled = templateSeedEnabled;
  }

  @Override
  public void run(ApplicationArguments args) throws Exception {
    if (!problemSeedEnabled && !templateSeedEnabled) {
      return;
    }
    if (problemSeedEnabled) {
      int imported = importer.importSeed(seedPath);
      log.info("Imported problem seed rows: {}", imported);
    }
    if (problemSeedEnabled && companySeedEnabled) {
      ProblemCompanySeedImportResult companyResult = companySeedImportService.importSeed(companySeedPath, seedPath);
      log.info(
          "Imported problem company signal rows: read={}, matched={}, skipped={}",
          companyResult.readSignalCount(),
          companyResult.matchedSignalCount(),
          companyResult.skippedSignalCount());
    }
    if (templateSeedEnabled) {
      LearningPlanTemplateSeedImportResult templateResult = templateSeedImportService.importSeed(templateSeedPath);
      log.info(
          "Imported learning plan template seed rows: templates={}, refs={}, matched={}, missing={}",
          templateResult.templateCount(),
          templateResult.problemRefCount(),
          templateResult.matchedProblemCount(),
          templateResult.missingProblemCount());
    }
    int exitCode = SpringApplication.exit(applicationContext, () -> 0);
    System.exit(exitCode);
  }
}
