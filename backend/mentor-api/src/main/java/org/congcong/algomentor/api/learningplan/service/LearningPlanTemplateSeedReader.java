package org.congcong.algomentor.api.learningplan.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class LearningPlanTemplateSeedReader {

  private final ObjectMapper objectMapper;

  public LearningPlanTemplateSeedReader(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  public LearningPlanTemplateSeedData read(Path seedDirectory) throws IOException {
    requireFile(seedDirectory.resolve(LearningPlanTemplateSeedConstants.TEMPLATES_FILE));
    requireFile(seedDirectory.resolve(LearningPlanTemplateSeedConstants.PROBLEM_REFS_FILE));
    requireFile(seedDirectory.resolve(LearningPlanTemplateSeedConstants.MANIFEST_FILE));
    requireFile(seedDirectory.resolve(LearningPlanTemplateSeedConstants.METADATA_FILE));
    List<LearningPlanTemplateSeedRecord> templates = readJsonl(
        seedDirectory.resolve(LearningPlanTemplateSeedConstants.TEMPLATES_FILE),
        LearningPlanTemplateSeedRecord.class);
    List<LearningPlanTemplateProblemRefSeedRecord> problemRefs = readJsonl(
        seedDirectory.resolve(LearningPlanTemplateSeedConstants.PROBLEM_REFS_FILE),
        LearningPlanTemplateProblemRefSeedRecord.class);
    return new LearningPlanTemplateSeedData(templates, problemRefs);
  }

  private <T> List<T> readJsonl(Path path, Class<T> type) throws IOException {
    List<T> records = new ArrayList<>();
    try (BufferedReader reader = Files.newBufferedReader(path)) {
      String line;
      while ((line = reader.readLine()) != null) {
        if (!line.isBlank()) {
          records.add(objectMapper.readValue(line, type));
        }
      }
    }
    return records;
  }

  private void requireFile(Path path) {
    if (!Files.isRegularFile(path)) {
      throw new IllegalArgumentException("Learning plan template seed file is missing: " + path);
    }
  }

  public record LearningPlanTemplateSeedData(
      List<LearningPlanTemplateSeedRecord> templates,
      List<LearningPlanTemplateProblemRefSeedRecord> problemRefs
  ) {
  }
}
