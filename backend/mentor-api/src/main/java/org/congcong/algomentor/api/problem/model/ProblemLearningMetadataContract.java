package org.congcong.algomentor.api.problem.model;

/** 题目学习元数据 seed、数据库来源值与配置键的稳定契约。 */
public final class ProblemLearningMetadataContract {

  public static final String METADATA_SEED_ENABLED_PROPERTY = "algo-mentor.problem.metadata-seed.enabled";
  public static final String METADATA_SEED_PATH_PROPERTY = "algo-mentor.problem.metadata-seed.path";
  public static final String DEFAULT_SEED_PATH = "data/problem-metadata-seed";

  public static final String RELATIONS_FILE = "problem_relations.jsonl";
  public static final String HINTS_FILE = "problem_hints.jsonl";
  public static final String CODE_TEMPLATES_FILE = "problem_code_templates.jsonl";
  public static final String CATEGORIES_FILE = "problem_categories.jsonl";
  public static final String CATEGORY_ITEMS_FILE = "problem_category_items.jsonl";
  public static final String MANIFEST_FILE = "problem_metadata_seed_manifest.json";
  public static final String AUDIT_REPORT_FILE = "problem_metadata_audit_report.json";

  public static final String SOURCE_LEETCODE = "LEETCODE";
  public static final String SOURCE_LEGACY = "LEGACY";
  public static final String RELATION_TYPE_LEETCODE_SIMILAR = "LEETCODE_SIMILAR";
  public static final String SOURCE_SITE_LEETCODE_COM = "LEETCODE_COM";
  public static final String SOURCE_SITE_LEETCODE_CN = "LEETCODE_CN";
  public static final String AUDIT_OUTCOME_PASSED = "PASSED";

  public static final String JSON_FIELD_SOURCE_SNAPSHOT = "sourceSnapshot";
  public static final String JSON_FIELD_OUTPUT_SHA256 = "outputSha256";
  public static final String JSON_FIELD_OUTCOME = "outcome";
  public static final String JSON_FIELD_MANUAL_SAMPLING = "manualSampling";
  public static final String JSON_FIELD_STATUS = "status";
  public static final String JSON_FIELD_FETCH_REPORT_PATH = "fetchReportPath";
  public static final String JSON_FIELD_FETCH_REPORT_SHA256 = "fetchReportSha256";

  private ProblemLearningMetadataContract() {
  }
}
