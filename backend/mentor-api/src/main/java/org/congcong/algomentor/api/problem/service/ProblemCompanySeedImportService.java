package org.congcong.algomentor.api.problem.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import org.congcong.algomentor.api.problem.model.ProblemCompanySeedRecord;
import org.congcong.algomentor.api.problem.repository.ProblemCompanyRepository;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProblemCompanySeedImportService {

  private static final String SOURCE_NAME = "problem-company-seed";
  private static final String MANIFEST_FILE = "problem_company_seed_manifest.json";

  private final ObjectProvider<ProblemCompanyRepository> repositoryProvider;
  private final ProblemCompanySeedReader reader;
  private final ObjectMapper objectMapper;

  public ProblemCompanySeedImportService(
      ObjectProvider<ProblemCompanyRepository> repositoryProvider,
      ProblemCompanySeedReader reader,
      ObjectMapper objectMapper
  ) {
    this.repositoryProvider = repositoryProvider;
    this.reader = reader;
    this.objectMapper = objectMapper;
  }

  @Transactional
  public ProblemCompanySeedImportResult importSeed(Path seedDirectory, Path problemSeedDirectory) throws IOException {
    List<ProblemCompanySeedRecord> records = reader.read(seedDirectory);
    ProblemCompanyRepository repository = repository();
    int matched = 0;
    int skipped = 0;
    String sourceCommit = null;
    for (ProblemCompanySeedRecord record : records) {
      if (sourceCommit == null && record.sourceCommit() != null && !record.sourceCommit().isBlank()) {
        sourceCommit = record.sourceCommit();
      }
      if (repository.upsertSignal(record)) {
        matched += 1;
      } else {
        skipped += 1;
      }
    }
    Path manifestPath = seedDirectory.resolve(MANIFEST_FILE);
    repository.insertImportRun(
        SOURCE_NAME,
        sourceCommit,
        manifestPath.toString(),
        matched,
        skipped,
        0,
        metadataJson(seedDirectory, problemSeedDirectory, records.size(), matched, skipped, manifestPath));
    return new ProblemCompanySeedImportResult(records.size(), matched, skipped);
  }

  private String metadataJson(
      Path seedDirectory,
      Path problemSeedDirectory,
      int readSignalCount,
      int matched,
      int skipped,
      Path manifestPath
  ) throws IOException {
    ObjectNode metadata = objectMapper.createObjectNode();
    Path signalsFile = seedDirectory.resolve(ProblemCompanySeedReader.SIGNALS_FILE);
    metadata.put("seedChecksum", checksum(signalsFile));
    metadata.put("problemSeedManifestPath", problemSeedDirectory.resolve("manifest.json").toString());
    metadata.put("companySeedManifestPath", manifestPath.toString());
    metadata.put("readSignalCount", readSignalCount);
    metadata.put("matchedSignalCount", matched);
    metadata.put("skippedSignalCount", skipped);
    if (Files.exists(manifestPath)) {
      JsonNode manifest = objectMapper.readTree(manifestPath.toFile());
      metadata.set("companySeedManifest", manifest);
    }
    return objectMapper.writeValueAsString(metadata);
  }

  private String checksum(Path path) throws IOException {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      digest.update(Files.readAllBytes(path));
      return HexFormat.of().formatHex(digest.digest());
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException("SHA-256 is unavailable.", exception);
    }
  }

  private ProblemCompanyRepository repository() {
    ProblemCompanyRepository repository = repositoryProvider.getIfAvailable();
    if (repository == null) {
      throw new ProblemService.ProblemRepositoryUnavailableException();
    }
    return repository;
  }
}
