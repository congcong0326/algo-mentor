package org.congcong.algomentor.api.feedback.service;

import java.time.Clock;
import java.time.Instant;
import org.congcong.algomentor.api.feedback.metrics.FeedbackMetrics;
import org.congcong.algomentor.api.feedback.model.FeedbackSenderType;
import org.congcong.algomentor.api.feedback.model.FeedbackStatus;
import org.congcong.algomentor.api.feedback.model.FeedbackThread;
import org.congcong.algomentor.api.feedback.model.FeedbackThreadPage;
import org.congcong.algomentor.api.feedback.model.UserFeedbackQuery;
import org.congcong.algomentor.api.feedback.repository.FeedbackRepository;

public class UserFeedbackService {
  private final FeedbackRepository repository;
  private final FeedbackMutationExecutor mutationExecutor;
  private final FeedbackRunOwnershipVerifier runOwnershipVerifier;
  private final FeedbackMetrics metrics;
  private final Clock clock;

  public UserFeedbackService(FeedbackRepository repository, FeedbackMutationExecutor mutationExecutor,
      FeedbackRunOwnershipVerifier runOwnershipVerifier, FeedbackMetrics metrics, Clock clock) {
    this.repository = repository;
    this.mutationExecutor = mutationExecutor;
    this.runOwnershipVerifier = runOwnershipVerifier;
    this.metrics = metrics;
    this.clock = clock;
  }

  public FeedbackThreadDetail create(long userId, FeedbackInput rawInput) {
    FeedbackInput input = normalize(rawInput);
    if (input.sourceRunId() != null && !runOwnershipVerifier.belongsToUser(input.sourceRunId(), userId)) {
      throw new FeedbackException(FeedbackErrorCode.FEEDBACK_SOURCE_RUN_INVALID, "反馈来源运行不存在或不属于当前用户。");
    }
    FeedbackThreadDetail detail = mutationExecutor.execute(() -> {
      Instant now = Instant.now(clock);
      long threadId = repository.insertThread(userId, input.category(), input.subject(), input.sourcePath(),
          input.sourceRequestId(), input.sourceRunId(), now);
      repository.insertMessage(threadId, FeedbackSenderType.USER, userId, input.content(), now);
      return detail(threadId, userId, true);
    });
    metrics.threadCreated(input.category());
    metrics.messageSent(FeedbackSenderType.USER);
    return detail;
  }

  public FeedbackThreadPage list(long userId, int page, int pageSize, String status) {
    UserFeedbackQuery query = new UserFeedbackQuery(FeedbackInputNormalizer.page(page),
        FeedbackInputNormalizer.pageSize(pageSize), status == null || status.isBlank() ? null : FeedbackInputNormalizer.status(status));
    return new FeedbackThreadPage(repository.findUserThreadPage(userId, query, offset(query.page(), query.pageSize())),
        repository.countUserThreads(userId, query), query.page(), query.pageSize(), repository.countUnreadMessagesForUser(userId));
  }

  public FeedbackThreadDetail detail(long userId, long threadId) {
    return detail(threadId, userId, true);
  }

  public FeedbackThreadDetail reply(long userId, long threadId, String content) {
    String normalizedContent = FeedbackInputNormalizer.content(content);
    FeedbackThreadDetail detail = mutationExecutor.execute(() -> {
      FeedbackThread thread = ownedLocked(threadId, userId);
      Instant now = Instant.now(clock);
      repository.insertMessage(threadId, FeedbackSenderType.USER, userId, normalizedContent, now);
      if (thread.status() == FeedbackStatus.CLOSED) repository.reopenThreadAfterUserReply(threadId, now);
      else repository.updateThreadActivity(threadId, now);
      return detail(threadId, userId, true);
    });
    metrics.messageSent(FeedbackSenderType.USER);
    return detail;
  }

  public FeedbackReadResult markRead(long userId, long threadId) {
    return mutationExecutor.execute(() -> {
      ownedLocked(threadId, userId);
      int count = repository.markAdminMessagesReadByUser(threadId, userId, Instant.now(clock));
      metrics.messagesRead(FeedbackSenderType.USER, count);
      return new FeedbackReadResult(threadId, count, unreadForThread(threadId, FeedbackSenderType.ADMIN));
    });
  }

  private FeedbackThreadDetail detail(long threadId, long userId, boolean enforceOwnership) {
    FeedbackThread thread = repository.findThreadById(threadId).orElseThrow(this::notFound);
    if (enforceOwnership && thread.userId() != userId) throw forbidden();
    return new FeedbackThreadDetail(thread, unreadForThread(threadId, FeedbackSenderType.ADMIN), repository.findMessagesByThreadId(threadId));
  }

  private FeedbackThread ownedLocked(long threadId, long userId) {
    FeedbackThread thread = repository.findThreadByIdForUpdate(threadId).orElseThrow(this::notFound);
    if (thread.userId() != userId) throw forbidden();
    return thread;
  }

  private long unreadForThread(long threadId, FeedbackSenderType senderType) {
    return repository.findMessagesByThreadId(threadId).stream().filter(message -> message.senderType() == senderType && message.readAt() == null).count();
  }

  private FeedbackInput normalize(FeedbackInput input) {
    if (input == null) throw new FeedbackException(FeedbackErrorCode.FEEDBACK_MESSAGE_INVALID, "反馈请求不能为空。");
    if (input.category() == null) {
      throw new FeedbackException(FeedbackErrorCode.FEEDBACK_CATEGORY_INVALID, "反馈分类不合法。");
    }
    return new FeedbackInput(input.category(), FeedbackInputNormalizer.subject(input.subject()),
        FeedbackInputNormalizer.content(input.content()), FeedbackInputNormalizer.sourcePath(input.sourcePath()),
        FeedbackInputNormalizer.sourceRequestId(input.sourceRequestId()), FeedbackInputNormalizer.sourceRunId(input.sourceRunId()));
  }
  private int offset(int page, int pageSize) { return Math.multiplyExact(page - 1, pageSize); }
  private FeedbackException notFound() { return new FeedbackException(FeedbackErrorCode.FEEDBACK_THREAD_NOT_FOUND, "反馈会话不存在。"); }
  private FeedbackException forbidden() { return new FeedbackException(FeedbackErrorCode.FEEDBACK_THREAD_FORBIDDEN, "无权访问该反馈会话。"); }
}
