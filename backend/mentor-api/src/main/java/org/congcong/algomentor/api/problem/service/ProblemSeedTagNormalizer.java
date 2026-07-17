package org.congcong.algomentor.api.problem.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import org.congcong.algomentor.api.problem.model.NormalizedProblemSeed;
import org.congcong.algomentor.api.problem.model.ProblemSeedRecord;
import org.congcong.algomentor.api.problem.model.ProblemSeedTag;
import org.congcong.algomentor.api.problem.model.ProblemTagDefinition;
import org.congcong.algomentor.api.problem.model.ProblemTagNormalizationResult;
import org.springframework.stereotype.Service;

/**
 * seed 标签 fallback、去重和目录名称决胜规则的唯一 Java 实现。
 */
@Service
public class ProblemSeedTagNormalizer {

  public ProblemTagNormalizationResult normalize(List<ProblemSeedRecord> problems) {
    Objects.requireNonNull(problems, "problems must not be null");

    List<NormalizedProblemSeed> normalizedProblems = new ArrayList<>(problems.size());
    Map<String, Map<String, Integer>> englishCandidates = new HashMap<>();
    Map<String, Map<String, Integer>> chineseCandidates = new HashMap<>();

    for (ProblemSeedRecord problem : problems) {
      List<ProblemSeedTag> tags = normalizeProblemTags(problem);
      normalizedProblems.add(new NormalizedProblemSeed(problem, tags));
      for (ProblemSeedTag tag : tags) {
        increment(englishCandidates, tag.value(), tag.labelEn());
        if (isChineseCandidate(tag)) {
          increment(chineseCandidates, tag.value(), tag.labelZh());
        }
      }
    }

    List<ProblemTagDefinition> catalog = new ArrayList<>(englishCandidates.size());
    Map<String, ProblemTagDefinition> catalogByValue = new TreeMap<>();
    for (Map.Entry<String, Map<String, Integer>> entry : englishCandidates.entrySet()) {
      String value = entry.getKey();
      String labelEn = selectStableWinner(entry.getValue());
      String labelZh = chineseCandidates.containsKey(value)
          ? selectStableWinner(chineseCandidates.get(value))
          : labelEn;
      ProblemTagDefinition definition = new ProblemTagDefinition(value, labelEn, labelZh);
      catalogByValue.put(value, definition);
    }
    catalog.addAll(catalogByValue.values());

    List<NormalizedProblemSeed> catalogNormalizedProblems = normalizedProblems.stream()
        .map(problem -> withCatalogLabels(problem, catalogByValue))
        .toList();
    return new ProblemTagNormalizationResult(catalog, catalogNormalizedProblems);
  }

  private List<ProblemSeedTag> normalizeProblemTags(ProblemSeedRecord problem) {
    if (problem == null) {
      throw new IllegalArgumentException("Problem seed must not be null");
    }
    String slug = requiredSlug(problem.slug());
    List<String> values = problem.tagValues();
    List<String> labelsEn = problem.tagLabelsEn();
    List<String> labelsZh = problem.tagLabelsZh();
    if (values.size() != labelsEn.size() || values.size() != labelsZh.size()) {
      throw new IllegalArgumentException("Problem tag arrays must have equal lengths: " + slug);
    }

    Map<String, ProblemSeedTag> firstTagByValue = new LinkedHashMap<>();
    for (int index = 0; index < values.size(); index++) {
      String value = requiredValue(values.get(index), slug);
      String labelEn = fallback(labelsEn.get(index), value);
      String labelZh = fallback(labelsZh.get(index), labelEn);
      ProblemSeedTag existing = firstTagByValue.get(value);
      if (existing == null) {
        firstTagByValue.put(value, new ProblemSeedTag(value, labelEn, labelZh, index));
      } else if (!existing.labelEn().equals(labelEn) || !existing.labelZh().equals(labelZh)) {
        throw new IllegalArgumentException(
            "Conflicting duplicate problem tag labels: slug=" + slug + ", value=" + value);
      }
    }

    List<ProblemSeedTag> tags = new ArrayList<>(firstTagByValue.size());
    int ordinal = ProblemSeedTag.FIRST_ORDINAL;
    for (ProblemSeedTag tag : firstTagByValue.values()) {
      tags.add(new ProblemSeedTag(tag.value(), tag.labelEn(), tag.labelZh(), ordinal++));
    }
    return List.copyOf(tags);
  }

  private NormalizedProblemSeed withCatalogLabels(
      NormalizedProblemSeed normalizedProblem,
      Map<String, ProblemTagDefinition> catalogByValue
  ) {
    List<ProblemSeedTag> tags = normalizedProblem.tags().stream()
        .map(tag -> {
          ProblemTagDefinition definition = catalogByValue.get(tag.value());
          if (definition == null) {
            throw new IllegalStateException("Missing normalized problem tag catalog entry: " + tag.value());
          }
          return new ProblemSeedTag(tag.value(), definition.labelEn(), definition.labelZh(), tag.ordinal());
        })
        .toList();
    return new NormalizedProblemSeed(normalizedProblem.problem(), tags);
  }

  private void increment(
      Map<String, Map<String, Integer>> candidates,
      String value,
      String label
  ) {
    candidates.computeIfAbsent(value, ignored -> new HashMap<>())
        .merge(label, 1, Integer::sum);
  }

  private boolean isChineseCandidate(ProblemSeedTag tag) {
    return !tag.labelZh().equals(tag.labelEn()) && !tag.labelZh().equals(tag.value());
  }

  private String selectStableWinner(Map<String, Integer> candidates) {
    return candidates.entrySet().stream()
        .sorted(Comparator
            .<Map.Entry<String, Integer>>comparingInt(Map.Entry::getValue)
            .reversed()
            .thenComparing(Map.Entry::getKey))
        .map(Map.Entry::getKey)
        .findFirst()
        .orElseThrow(() -> new IllegalStateException("Tag label candidates must not be empty"));
  }

  private String requiredSlug(String slug) {
    String normalized = trimToNull(slug);
    if (normalized == null) {
      throw new IllegalArgumentException("Problem seed slug must not be blank");
    }
    return normalized;
  }

  private String requiredValue(String value, String slug) {
    String normalized = trimToNull(value);
    if (normalized == null) {
      throw new IllegalArgumentException("Problem tag value must not be blank: slug=" + slug);
    }
    return normalized;
  }

  private String fallback(String value, String fallback) {
    String normalized = trimToNull(value);
    return normalized == null ? fallback : normalized;
  }

  private String trimToNull(String value) {
    return value == null || value.isBlank() ? null : value.trim();
  }
}
