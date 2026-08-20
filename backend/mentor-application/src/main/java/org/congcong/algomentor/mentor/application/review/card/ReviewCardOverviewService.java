package org.congcong.algomentor.mentor.application.review.card;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import org.congcong.algomentor.mentor.application.practice.PracticeCodeReviewIndexEntry;
import org.congcong.algomentor.mentor.application.practice.PracticeCodeReviewIndexRepository;
import org.congcong.algomentor.mentor.application.review.ReviewContractConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** 通过一次批量查询为复习卡列表补充代码 Review 时间线。 */
public class ReviewCardOverviewService {

  private static final Logger log = LoggerFactory.getLogger(ReviewCardOverviewService.class);

  private final ReviewCardService cardService;
  private final PracticeCodeReviewIndexRepository reviewIndexRepository;
  private final ReviewMetrics metrics;

  public ReviewCardOverviewService(
      ReviewCardService cardService,
      PracticeCodeReviewIndexRepository reviewIndexRepository,
      ReviewMetrics metrics
  ) {
    this.cardService = Objects.requireNonNull(cardService, "cardService must not be null");
    this.reviewIndexRepository = Objects.requireNonNull(reviewIndexRepository, "reviewIndexRepository must not be null");
    this.metrics = metrics == null ? ReviewMetrics.NOOP : metrics;
  }

  public ReviewCardOverviewPage list(
      long userId,
      ReviewCardSource source,
      boolean mistakeOnly,
      String keyword,
      int page
  ) {
    ReviewCardListCounts counts = cardService.countList(userId, source, mistakeOnly, keyword);
    int pageSize = ReviewContractConstants.REVIEW_CARD_LIST_PAGE_SIZE;
    int totalPages = Math.max(1, (int) Math.ceil((double) counts.total() / pageSize));
    int normalizedPage = Math.min(Math.max(1, page), totalPages);
    List<ProblemReviewCard> cards = cardService.list(
        userId,
        source,
        mistakeOnly,
        keyword,
        pageSize,
        (normalizedPage - 1) * pageSize);
    long indexQueryStartedAtNanos = System.nanoTime();
    if (cards.isEmpty()) {
      metrics.recordCodeReviewIndexQuery(0, 0, System.nanoTime() - indexQueryStartedAtNanos);
      return new ReviewCardOverviewPage(
          List.of(),
          counts.total(),
          counts.activeCount(),
          counts.mistakeCount(),
          normalizedPage,
          pageSize);
    }

    List<String> problemSlugs = cards.stream()
        .map(ProblemReviewCard::problemSlug)
        .distinct()
        .toList();
    List<PracticeCodeReviewIndexEntry> entries = reviewIndexRepository.findRecentByProblemSlugs(
        userId,
        problemSlugs,
        ReviewContractConstants.RECENT_CODE_REVIEW_INDEX_LIMIT);
    Map<String, List<PracticeCodeReviewIndexEntry>> entriesBySlug = entries.stream()
        .collect(Collectors.groupingBy(
            PracticeCodeReviewIndexEntry::problemSlug,
            java.util.LinkedHashMap::new,
            Collectors.toList()));

    List<ReviewCardOverview> overviews = cards.stream().map(card -> {
      List<PracticeCodeReviewIndexEntry> cardEntries = entriesBySlug.getOrDefault(card.problemSlug(), List.of());
      if (card.source() != ReviewCardSource.USER_MARKED && cardEntries.isEmpty()) {
        metrics.recordCodeReviewIndexMissingHistory();
        log.warn("Review card has no code review index entries. cardId={} problemSlug={} source={}",
            card.id(), card.problemSlug(), card.source());
      }
      return new ReviewCardOverview(card, cardEntries);
    }).toList();
    int missingReviewHistory = (int) overviews.stream()
        .filter(overview -> overview.card().source() != ReviewCardSource.USER_MARKED)
        .filter(overview -> overview.recentCodeReviews().isEmpty())
        .count();
    metrics.recordCodeReviewIndexQuery(cards.size(), entries.size(), System.nanoTime() - indexQueryStartedAtNanos);
    if (missingReviewHistory > 0) {
      log.info("Review card code review index completed with missing histories. cardCount={} reviewCount={} missingHistoryCount={}",
          cards.size(), entries.size(), missingReviewHistory);
    }
    return new ReviewCardOverviewPage(
        overviews,
        counts.total(),
        counts.activeCount(),
        counts.mistakeCount(),
        normalizedPage,
        pageSize);
  }
}
