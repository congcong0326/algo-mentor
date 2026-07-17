package org.congcong.algomentor.api.admin.overview;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.function.Supplier;
import org.congcong.algomentor.ai.governance.adminquery.AiOverviewSnapshot;
import org.congcong.algomentor.ai.governance.adminquery.AiQuotaRiskUser;
import org.congcong.algomentor.ai.governance.adminquery.AiAdminUsageQueryService;
import org.congcong.algomentor.ai.governance.model.AiPurpose;
import org.congcong.algomentor.ai.governance.policy.AiGovernanceProperties;
import org.congcong.algomentor.ai.governance.policy.AiPurposePolicyResolver;
import org.congcong.algomentor.ai.governance.policy.runtime.AiRuntimeAdminService;
import org.congcong.algomentor.api.feedback.model.FeedbackOverviewStats;
import org.congcong.algomentor.api.feedback.service.AdminFeedbackService;
import org.congcong.algomentor.auth.betaaccess.model.BetaAccessOverviewSummary;
import org.congcong.algomentor.auth.betaaccess.service.BetaAccessAdminService;
import org.congcong.algomentor.common.trace.RequestTraceContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** 不跨模块读取 Mapper 的管理员概览聚合服务。 */
public class AdminOverviewService {
  private static final Logger log = LoggerFactory.getLogger(AdminOverviewService.class);
  private static final String BETA_ACCESS = "betaAccess";
  private static final String AI_RUNTIME = "aiRuntime";
  private static final String AI_TODAY = "aiToday";
  private static final String QUOTA_RISKS = "quotaRisks";
  private static final String FEEDBACK = "feedback";
  private static final String RECENT_FAILED_RUNS = "recentFailedRuns";
  public static final int QUOTA_RISK_THRESHOLD_PERCENT = 80;

  private final BetaAccessAdminService betaAccess;
  private final AiRuntimeAdminService aiRuntime;
  private final AiAdminUsageQueryService aiUsage;
  private final AdminFeedbackService feedback;
  private final AiPurposePolicyResolver policyResolver;
  private final AiGovernanceProperties aiProperties;
  private final Clock clock;
  private final AdminOverviewMetrics metrics;

  public AdminOverviewService(BetaAccessAdminService betaAccess, AiRuntimeAdminService aiRuntime,
      AiAdminUsageQueryService aiUsage, AdminFeedbackService feedback,
      AiPurposePolicyResolver policyResolver, AiGovernanceProperties aiProperties, Clock clock,
      AdminOverviewMetrics metrics) {
    this.betaAccess = betaAccess;
    this.aiRuntime = aiRuntime;
    this.aiUsage = aiUsage;
    this.feedback = feedback;
    this.policyResolver = policyResolver;
    this.aiProperties = aiProperties;
    this.clock = clock;
    this.metrics = metrics;
  }

  public AdminOverview getOverview() {
    AiOverviewSnapshot snapshot = section(AI_TODAY, () -> aiUsage.overview(aiProperties.getQuotaZone())).data();
    AdminOverviewSection<AiToday> aiToday = snapshot == null
        ? AdminOverviewSection.unavailable()
        : AdminOverviewSection.available(toAiToday(snapshot));
    AdminOverviewSection<QuotaRisks> quotaRisks = snapshot == null
        ? AdminOverviewSection.unavailable()
        : AdminOverviewSection.available(new QuotaRisks(QUOTA_RISK_THRESHOLD_PERCENT, snapshot.quotaRisks()));
    LocalDate quotaDate = snapshot == null ? LocalDate.now(aiProperties.getQuotaZone()) : snapshot.quotaDate();
    return new AdminOverview(
        Instant.now(clock), quotaDate, aiProperties.getQuotaZone().getId(),
        section(BETA_ACCESS, betaAccess::overviewSummary),
        section(AI_RUNTIME, () -> aiRuntime.getSettings(policyResolver.resolve(AiPurpose.LEARNING_CHAT))),
        aiToday,
        quotaRisks,
        section(FEEDBACK, feedback::overviewStats),
        unavailableRecentFailedRuns());
  }

  private AdminOverviewSection<RecentFailedRuns> unavailableRecentFailedRuns() {
    // 阶段三公开 run 查询端口尚未在当前基线提供。
    return unavailable(RECENT_FAILED_RUNS);
  }

  private <T> AdminOverviewSection<T> section(String name, Supplier<T> supplier) {
    try {
      return AdminOverviewSection.available(supplier.get());
    } catch (RuntimeException exception) {
      return unavailable(name, exception);
    }
  }

  private <T> AdminOverviewSection<T> unavailable(String name) {
    return unavailable(name, null);
  }

  private <T> AdminOverviewSection<T> unavailable(String name, RuntimeException exception) {
    metrics.sectionFailure(name);
    String requestId = RequestTraceContext.currentRequestId().orElse("unknown");
    if (exception == null) log.info("Admin overview section unavailable. section={} requestId={}", name, requestId);
    else log.warn("Admin overview section failed. section={} requestId={} errorType={}", name, requestId,
        exception.getClass().getSimpleName());
    return AdminOverviewSection.unavailable();
  }

  private AiToday toAiToday(AiOverviewSnapshot snapshot) {
    var entries = snapshot.entryRequests();
    var usage = snapshot.usage();
    return new AiToday(new EntryRequests(entries.total(), entries.completed(), entries.failed(), entries.cancelled(),
        entries.quotaRejected(), entries.inProgress(), entries.otherRejected()), usage.modelCallCount(),
        usage.inputTokens(), usage.cachedTokens(), usage.outputTokens(), usage.totalTokens(),
        decimal(usage.estimatedCostUsd()), usage.unpricedCallCount(), usage.unpricedTokenCount());
  }

  private String decimal(BigDecimal value) { return value == null ? "0" : value.toPlainString(); }

  public record AdminOverview(Instant generatedAt, LocalDate quotaDate, String quotaZone,
      AdminOverviewSection<BetaAccessOverviewSummary> betaAccess,
      AdminOverviewSection<org.congcong.algomentor.ai.governance.policy.runtime.AiRuntimeSettings> aiRuntime,
      AdminOverviewSection<AiToday> aiToday,
      AdminOverviewSection<QuotaRisks> quotaRisks,
      AdminOverviewSection<FeedbackOverviewStats> feedback,
      AdminOverviewSection<RecentFailedRuns> recentFailedRuns) { }
  public record EntryRequests(long total, long completed, long failed, long cancelled, long quotaRejected,
      long inProgress, long otherRejected) { }
  public record AiToday(EntryRequests entryRequests, long modelCallCount, long inputTokens, long cachedTokens,
      long outputTokens, long totalTokens, String estimatedCostUsd, long unpricedCallCount, long unpricedTokenCount) { }
  public record QuotaRisks(int thresholdPercent, List<AiQuotaRiskUser> items) { public QuotaRisks { items = items == null ? List.of() : List.copyOf(items); } }
  public record RecentFailedRuns(List<Object> items) { public RecentFailedRuns { items = items == null ? List.of() : List.copyOf(items); } }
}
