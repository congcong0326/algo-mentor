package org.congcong.algomentor.api.problem.service;

import java.nio.file.Path;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "algo-mentor.problem.seed", name = "enabled", havingValue = "true")
public class ProblemSeedRunner implements ApplicationRunner {

  private static final Logger log = LoggerFactory.getLogger(ProblemSeedRunner.class);

  private final ProblemSeedImporter importer;
  private final ProblemCompanySeedImportService companySeedImportService;
  private final ConfigurableApplicationContext applicationContext;
  private final Path seedPath;
  private final Path companySeedPath;
  private final boolean companySeedEnabled;

  public ProblemSeedRunner(
      ProblemSeedImporter importer,
      ProblemCompanySeedImportService companySeedImportService,
      ConfigurableApplicationContext applicationContext,
      @Value("${algo-mentor.problem.seed.path:data/seed}") String seedPath,
      @Value("${algo-mentor.problem.company-seed.path:data/company-seed}") String companySeedPath,
      @Value("${algo-mentor.problem.company-seed.enabled:false}") boolean companySeedEnabled
  ) {
    this.importer = importer;
    this.companySeedImportService = companySeedImportService;
    this.applicationContext = applicationContext;
    this.seedPath = Path.of(seedPath);
    this.companySeedPath = Path.of(companySeedPath);
    this.companySeedEnabled = companySeedEnabled;
  }

  @Override
  public void run(ApplicationArguments args) throws Exception {
    int imported = importer.importSeed(seedPath);
    log.info("Imported problem seed rows: {}", imported);
    if (companySeedEnabled) {
      ProblemCompanySeedImportResult companyResult = companySeedImportService.importSeed(companySeedPath, seedPath);
      log.info(
          "Imported problem company signal rows: read={}, matched={}, skipped={}",
          companyResult.readSignalCount(),
          companyResult.matchedSignalCount(),
          companyResult.skippedSignalCount());
    }
    int exitCode = SpringApplication.exit(applicationContext, () -> 0);
    System.exit(exitCode);
  }
}
