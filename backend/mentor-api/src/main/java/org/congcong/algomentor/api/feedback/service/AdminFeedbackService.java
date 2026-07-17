package org.congcong.algomentor.api.feedback.service;

import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.congcong.algomentor.api.feedback.metrics.FeedbackMetrics;
import org.congcong.algomentor.api.feedback.model.AdminFeedbackQuery;
import org.congcong.algomentor.api.feedback.model.FeedbackOverviewStats;
import org.congcong.algomentor.api.feedback.model.FeedbackSenderType;
import org.congcong.algomentor.api.feedback.model.FeedbackStatus;
import org.congcong.algomentor.api.feedback.model.FeedbackThread;
import org.congcong.algomentor.api.feedback.model.FeedbackThreadPage;
import org.congcong.algomentor.api.feedback.repository.FeedbackRepository;
import org.congcong.algomentor.identity.model.AuthUser;
import org.congcong.algomentor.identity.repository.IdentityUserRepository;

public class AdminFeedbackService {
  private final FeedbackRepository repository;
  private final FeedbackMutationExecutor mutationExecutor;
  private final IdentityUserRepository identityUserRepository;
  private final FeedbackMetrics metrics;
  private final Clock clock;

  public AdminFeedbackService(FeedbackRepository repository, FeedbackMutationExecutor mutationExecutor,
      IdentityUserRepository identityUserRepository, FeedbackMetrics metrics, Clock clock) {
    this.repository = repository;
    this.mutationExecutor = mutationExecutor;
    this.identityUserRepository = identityUserRepository;
    this.metrics = metrics;
    this.clock = clock;
  }

  public FeedbackThreadPage list(int page, int pageSize, String status, String category, Long userId, boolean unreadOnly) {
    if (userId != null && userId < 1) throw new FeedbackException(FeedbackErrorCode.FEEDBACK_PAGE_INVALID, "用户标识不合法。");
    AdminFeedbackQuery query = new AdminFeedbackQuery(FeedbackInputNormalizer.page(page), FeedbackInputNormalizer.pageSize(pageSize),
        blank(status) ? null : FeedbackInputNormalizer.status(status), blank(category) ? null : FeedbackInputNormalizer.category(category), userId, unreadOnly);
    return new FeedbackThreadPage(repository.findAdminThreadPage(query, offset(query.page(), query.pageSize())),
        repository.countAdminThreads(query), query.page(), query.pageSize(), repository.countUnreadMessagesForAdmin());
  }

  public FeedbackThreadDetail detail(long threadId) { return detail(threadId, false); }

  public FeedbackThreadDetail reply(long operatorUserId, long threadId, String content) {
    String normalized = FeedbackInputNormalizer.content(content);
    FeedbackThreadDetail detail = mutationExecutor.execute(() -> {
      requireLocked(threadId);
      Instant now = Instant.now(clock);
      repository.insertMessage(threadId, FeedbackSenderType.ADMIN, operatorUserId, normalized, now);
      repository.updateThreadActivity(threadId, now);
      return detail(threadId, false);
    });
    metrics.messageSent(FeedbackSenderType.ADMIN);
    return detail;
  }

  public FeedbackThreadDetail updateStatus(long operatorUserId, long threadId, String status) {
    FeedbackStatus requested = FeedbackInputNormalizer.status(status);
    FeedbackThreadDetail detail = mutationExecutor.execute(() -> {
      FeedbackThread current = requireLocked(threadId);
      if (current.status() != requested) {
        Instant now = Instant.now(clock);
        if (requested == FeedbackStatus.CLOSED) repository.closeThread(threadId, operatorUserId, now);
        else repository.reopenThread(threadId, now);
        metrics.statusChanged(requested);
      }
      return detail(threadId, false);
    });
    return detail;
  }

  public FeedbackReadResult markRead(long threadId) {
    return mutationExecutor.execute(() -> {
      requireLocked(threadId);
      int count = repository.markUserMessagesReadByAdmin(threadId, Instant.now(clock));
      metrics.messagesRead(FeedbackSenderType.ADMIN, count);
      return new FeedbackReadResult(threadId, count, unreadForThread(threadId, FeedbackSenderType.USER));
    });
  }

  public FeedbackOverviewStats overviewStats() { return repository.feedbackOverviewStats(); }

  public Map<Long, FeedbackUserSummary> usersFor(FeedbackThreadPage page) {
    Map<Long, FeedbackUserSummary> users = new LinkedHashMap<>();
    page.items().forEach(item -> identityUserRepository.findUserById(item.userId())
        .ifPresent(user -> users.put(user.id(), toUserSummary(user))));
    return users;
  }

  private FeedbackThreadDetail detail(long threadId, boolean ignored) {
    FeedbackThread thread = repository.findThreadById(threadId).orElseThrow(this::notFound);
    return new FeedbackThreadDetail(thread, unreadForThread(threadId, FeedbackSenderType.USER), repository.findMessagesByThreadId(threadId));
  }
  private FeedbackThread requireLocked(long threadId) { return repository.findThreadByIdForUpdate(threadId).orElseThrow(this::notFound); }
  private long unreadForThread(long threadId, FeedbackSenderType senderType) { return repository.findMessagesByThreadId(threadId).stream().filter(message -> message.senderType() == senderType && message.readAt() == null).count(); }
  private int offset(int page, int pageSize) { return Math.multiplyExact(page - 1, pageSize); }
  private boolean blank(String value) { return value == null || value.isBlank(); }
  private FeedbackException notFound() { return new FeedbackException(FeedbackErrorCode.FEEDBACK_THREAD_NOT_FOUND, "反馈会话不存在。"); }
  private FeedbackUserSummary toUserSummary(AuthUser user) { return new FeedbackUserSummary(user.id(), user.email(), user.displayName(), user.status().name()); }

  public record FeedbackUserSummary(long id, String email, String displayName, String status) { }
}
