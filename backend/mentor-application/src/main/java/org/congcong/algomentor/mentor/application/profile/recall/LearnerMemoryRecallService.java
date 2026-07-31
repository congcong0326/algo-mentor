package org.congcong.algomentor.mentor.application.profile.recall;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.congcong.algomentor.agent.core.runtime.definition.AgentRunResource;
import org.congcong.algomentor.mentor.application.practice.TrustedProblemTag;
import org.congcong.algomentor.mentor.application.practice.TrustedProblemTagCatalog;
import org.congcong.algomentor.mentor.application.profile.claim.model.LearnerMemoryClaimContract.Kind;
import org.congcong.algomentor.mentor.application.profile.claim.model.LearnerMemoryClaimRevision;
import org.congcong.algomentor.mentor.application.profile.claim.service.LearnerMemoryClaimQueryService;
import org.congcong.algomentor.mentor.application.profile.observability.LearnerMemoryMetrics;
import org.congcong.algomentor.mentor.application.profile.tool.LearnerMemoryRunScopeRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** 在 Agent loop 前读取并冻结当前 Practice Chat run 的 claim 快照。 */
public class LearnerMemoryRecallService {

  private static final Logger log = LoggerFactory.getLogger(LearnerMemoryRecallService.class);

  private final LearnerMemoryClaimQueryService claimQueryService;
  private final TrustedProblemTagCatalog trustedProblemTagCatalog;
  private final LearnerMemorySectionCatalog sectionCatalog;
  private final LearnerMemoryDirectHitSelector directHitSelector;
  private final LearnerMemoryRunScopeRegistry scopeRegistry;
  private final LearnerMemoryMetrics metrics;

  public LearnerMemoryRecallService(
      LearnerMemoryClaimQueryService claimQueryService,
      TrustedProblemTagCatalog trustedProblemTagCatalog,
      LearnerMemorySectionCatalog sectionCatalog,
      LearnerMemoryDirectHitSelector directHitSelector,
      LearnerMemoryRunScopeRegistry scopeRegistry
  ) {
    this(
        claimQueryService, trustedProblemTagCatalog, sectionCatalog, directHitSelector, scopeRegistry,
        LearnerMemoryMetrics.NOOP);
  }

  public LearnerMemoryRecallService(
      LearnerMemoryClaimQueryService claimQueryService,
      TrustedProblemTagCatalog trustedProblemTagCatalog,
      LearnerMemorySectionCatalog sectionCatalog,
      LearnerMemoryDirectHitSelector directHitSelector,
      LearnerMemoryRunScopeRegistry scopeRegistry,
      LearnerMemoryMetrics metrics
  ) {
    this.claimQueryService = Objects.requireNonNull(claimQueryService, "claimQueryService");
    this.trustedProblemTagCatalog = Objects.requireNonNull(trustedProblemTagCatalog, "trustedProblemTagCatalog");
    this.sectionCatalog = Objects.requireNonNull(sectionCatalog, "sectionCatalog");
    this.directHitSelector = Objects.requireNonNull(directHitSelector, "directHitSelector");
    this.scopeRegistry = Objects.requireNonNull(scopeRegistry, "scopeRegistry");
    this.metrics = metrics == null ? LearnerMemoryMetrics.NOOP : metrics;
  }

  public OpenedSnapshot openSnapshot(long userId, String currentUserMessage, String problemSlug, String locale) {
    try {
      return openSnapshot(
          userId,
          currentUserMessage,
          problemSlug,
          trustedProblemTagCatalog.findByProblemSlug(problemSlug),
          locale);
    } catch (RuntimeException exception) {
      log.warn("Learner memory recall failed and was omitted. exceptionType={}",
          exception.getClass().getSimpleName());
      return OpenedSnapshot.empty(locale);
    }
  }

  /** 允许上游传入已验证的题目标签，避免模型或 request metadata 扩大 tag 范围。 */
  public OpenedSnapshot openSnapshot(
      long userId,
      String currentUserMessage,
      String problemSlug,
      Collection<TrustedProblemTag> trustedProblemTags,
      String locale
  ) {
    try {
      if (userId < 1 || problemSlug == null || problemSlug.isBlank()) {
        throw new IllegalArgumentException("Learner memory recall input is invalid");
      }
      List<LearnerMemoryClaimRevision> claims = claimQueryService.snapshot(userId).activeClaims().stream()
          .limit(LearnerMemoryRecallContracts.MAX_ACTIVE_CLAIMS)
          .toList();
      List<TrustedProblemTag> tags = trustedProblemTags == null ? List.of() : List.copyOf(trustedProblemTags);
      if (tags.stream().anyMatch(Objects::isNull)) {
        throw new IllegalArgumentException("Trusted problem tags must not contain null");
      }
      Map<Long, TrustedProblemTag> tagsById = tags.stream().collect(Collectors.toMap(
          TrustedProblemTag::tagId, Function.identity(), (left, right) -> left));
      List<LearnerMemoryRecallSnapshot.SectionInput> sections = sectionInputs(claims, tagsById, locale);
      List<LearnerMemoryClaimRevision> directHits = directHitSelector.select(
          claims, currentUserMessage, tagsById.keySet());
      String documentRevision = LearnerMemoryDocumentRevision.calculate(locale, claims);
      LearnerMemoryRunScopeRegistry.RecallScopeLease lease = scopeRegistry.openRecallScope(
          userId,
          documentRevision,
          locale,
          sections,
          directHits.stream().map(LearnerMemoryClaimRevision::id).toList());
      LearnerMemoryRecallSnapshot snapshot = lease.snapshot();
      claims.stream().map(claim -> claim.scope().kind()).distinct()
          .forEach(kind -> metrics.recordRecall("PRACTICE_CHAT", kind));
      log.info("Learner memory recall opened. claimCount={} sectionCount={} directHitCount={} currentProblemMatchCount={}",
          snapshot.claimCount(), snapshot.sections().size(), snapshot.directHits().size(), snapshot.currentProblemMatchCount());
      return new OpenedSnapshot(snapshot, lease);
    } catch (RuntimeException exception) {
      log.warn("Learner memory recall failed and was omitted. exceptionType={}",
          exception.getClass().getSimpleName());
      return OpenedSnapshot.empty(locale);
    }
  }

  private List<LearnerMemoryRecallSnapshot.SectionInput> sectionInputs(
      List<LearnerMemoryClaimRevision> claims,
      Map<Long, TrustedProblemTag> tagsById,
      String locale
  ) {
    return sectionCatalog.sections().stream().map(section -> {
      List<LearnerMemoryRecallSnapshot.StatementInput> statements = claims.stream()
          .filter(section::matches)
          .map(claim -> new LearnerMemoryRecallSnapshot.StatementInput(
              claim,
              sourceSummary(claim, tagsById.get(claim.scope().tagId()), locale),
              currentProblemMatch(claim, tagsById)))
          .toList();
      return new LearnerMemoryRecallSnapshot.SectionInput(section.id(), section.title(), statements);
    }).filter(section -> !section.statements().isEmpty()).toList();
  }

  private boolean currentProblemMatch(LearnerMemoryClaimRevision claim, Map<Long, TrustedProblemTag> tagsById) {
    return claim.scope().kind() == Kind.TAG_ASSESSMENT && tagsById.containsKey(claim.scope().tagId());
  }

  private String sourceSummary(LearnerMemoryClaimRevision claim, TrustedProblemTag tag, String locale) {
    return switch (claim.scope().kind()) {
      case DECLARED_FACT -> "用户明确自述";
      case GENERAL_OBSERVATION -> "正式代码复盘";
      case TAG_ASSESSMENT -> tag == null
          ? "正式代码复盘标签"
          : "当前题目标签：" + (LearnerMemoryDocumentRevision.normalizeLocale(locale).startsWith("zh")
              ? tag.labelZh() : tag.labelEn());
    };
  }

  public record OpenedSnapshot(LearnerMemoryRecallSnapshot snapshot, AgentRunResource lease) {

    public OpenedSnapshot {
      if (snapshot == null) {
        throw new IllegalArgumentException("Learner memory recall snapshot must not be null");
      }
      lease = lease == null ? AgentRunResource.none() : lease;
    }

    static OpenedSnapshot empty(String locale) {
      String documentRevision = LearnerMemoryDocumentRevision.calculate(locale, List.of());
      LearnerMemoryRecallSnapshot snapshot = new LearnerMemoryRecallSnapshot(
          "unavailable", documentRevision, locale, List.of(), List.of());
      return new OpenedSnapshot(snapshot, AgentRunResource.none());
    }
  }
}
