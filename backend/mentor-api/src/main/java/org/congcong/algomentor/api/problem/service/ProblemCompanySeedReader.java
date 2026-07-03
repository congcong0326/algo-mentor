package org.congcong.algomentor.api.problem.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.congcong.algomentor.api.problem.model.ProblemCompanySeedRecord;
import org.springframework.stereotype.Component;

@Component
public class ProblemCompanySeedReader {

  public static final String SIGNALS_FILE = "problem_company_signals.jsonl";

  private final ObjectMapper objectMapper;

  public ProblemCompanySeedReader(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  public List<ProblemCompanySeedRecord> read(Path seedDirectory) throws IOException {
    Path signalsFile = seedDirectory.resolve(SIGNALS_FILE);
    List<ProblemCompanySeedRecord> records = new ArrayList<>();
    try (BufferedReader reader = Files.newBufferedReader(signalsFile)) {
      String line;
      while ((line = reader.readLine()) != null) {
        if (!line.isBlank()) {
          records.add(objectMapper.readValue(line, ProblemCompanySeedRecord.class));
        }
      }
    }
    return records;
  }
}
