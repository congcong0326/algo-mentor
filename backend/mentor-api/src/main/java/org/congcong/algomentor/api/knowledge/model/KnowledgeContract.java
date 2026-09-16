package org.congcong.algomentor.api.knowledge.model;

import java.util.Set;

/** 知识库文件格式、API 和导入共用契约。 */
public final class KnowledgeContract {
  private KnowledgeContract() {}

  public static final String API = "/api/knowledge";
  /** 卡片复习加入状态的幂等写入与移除入口。 */
  public static final String REVIEW_ENROLLMENT_PATH = "/cards/{slug}/review-enrollment";
  /** 持久化复习加入状态字段。 */
  public static final String REVIEW_ENROLLED = "review_enrolled";
  public static final String NODE_SUFFIX = ".node";
  public static final String CARD_SUFFIX = ".card.md";
  public static final String ARTICLE_SUFFIX = ".article.md";
  public static final String SLUG = "slug";
  public static final String TAGS = "tags";
  public static final String STATUS = "status";
  public static final String ORDER = "order";
  public static final String RELATIONS = "relations";
  public static final Set<String> METADATA_KEYS = Set.of(SLUG, TAGS, STATUS, ORDER, RELATIONS);
  public static final String SLUG_PATTERN = "[a-z0-9]+(?:-[a-z0-9]+)*";
  public static final int SLUG_LIMIT = 160;
  public static final int ANSWER_LIMIT = 20000;
  public static final String PUBLISHED = "PUBLISHED";
  public static final String DRAFT = "DRAFT";
  public static final String ROOT = "ROOT";
  public static final String NODE = "NODE";
  public static final String FILTER_ALL = "ALL";
  public static final String FILTER_DUE = "DUE";

  /** 与评价事务共享：导入排他锁、评价共享锁，避免导入期间读取陈旧内容。 */
  public static final long IMPORT_LOCK = 7616091601L;

  public static final String VALIDATE_COMMAND = "--knowledge-validate";
  public static final String IMPORT_COMMAND = "--knowledge-import";
  public static final String DEFAULT_DIRECTORY = "knowledge-base";
  public static final String INVALID_REQUEST = "KNOWLEDGE_INVALID_REQUEST";
  public static final String NOT_FOUND = "KNOWLEDGE_NOT_FOUND";
  public static final String ATTEMPT_CONFLICT = "KNOWLEDGE_ATTEMPT_CONFLICT";
  public static final String UNAVAILABLE = "KNOWLEDGE_SERVICE_UNAVAILABLE";
}
