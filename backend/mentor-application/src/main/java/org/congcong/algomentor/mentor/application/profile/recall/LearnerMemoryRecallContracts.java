package org.congcong.algomentor.mentor.application.profile.recall;

import org.congcong.algomentor.mentor.application.profile.tool.LearnerMemoryAgentToolContracts;

/** Practice Chat 记忆召回的稳定 Prompt 与 metadata 契约。 */
public final class LearnerMemoryRecallContracts {

  public static final String SECTION_ID = "practice.memory.learner-memory";
  public static final String VARIABLE_SNAPSHOT = "learnerMemoryRecallSnapshot";
  public static final String METADATA_SCOPE_REF = LearnerMemoryAgentToolContracts.METADATA_SCOPE_REF;
  public static final String METADATA_DOCUMENT_REVISION = "learnerMemoryDocumentRevision";
  public static final String METADATA_SECTION_COUNT = "learnerMemorySectionCount";
  public static final String METADATA_CLAIM_COUNT = "learnerMemoryClaimCount";
  public static final String METADATA_BOOTSTRAP_TOKEN_ESTIMATE = "learnerMemoryBootstrapTokenEstimate";
  public static final String METADATA_BOOTSTRAP_TRIMMED = "learnerMemoryBootstrapTrimmed";
  public static final int MAX_ACTIVE_CLAIMS = 1_000;
  public static final int MAX_DIRECT_HITS = 8;
  public static final int MIN_DIRECT_HITS_WHEN_AVAILABLE = 3;
  public static final int DEFAULT_BOOTSTRAP_TOKEN_BUDGET = 1_000;
  public static final int MAX_BOOTSTRAP_TOKEN_BUDGET = 1_500;
  public static final String DOCUMENT_REVISION_VERSION = "learner-memory-document-v1";

  private LearnerMemoryRecallContracts() {
  }
}
