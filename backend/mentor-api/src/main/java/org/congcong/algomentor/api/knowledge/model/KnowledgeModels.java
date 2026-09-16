package org.congcong.algomentor.api.knowledge.model;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** 用户侧契约：卡片以 slug 定位，大纲和文章 ID 只在当前导入快照内有效。 */
public final class KnowledgeModels {
  private KnowledgeModels() {}

  public record Topics(List<Node> items, Instant asOf) {}

  public record Node(
      long id,
      Long parentId,
      String slug,
      String title,
      String summary,
      int sortOrder,
      long directCardCount,
      long directArticleCount,
      long subtreeCardCount,
      long subtreeEnrolledCardCount,
      boolean hasChildren) {}

  public record NodeTree(Node node, List<NodeTree> children) {}

  public record Breadcrumb(long id, String title, String slug) {}

  public record NodeDetail(
      Node node, List<Breadcrumb> breadcrumbs, List<Node> children, Instant asOf) {}

  public record LearningState(
      boolean enrolled,
      String phase,
      Instant dueAt,
      boolean isDue,
      String lastRating,
      Instant lastReviewedAt) {}

  public record CardSummary(
      String slug,
      long outlineNodeId,
      String question,
      int sortOrder,
      List<String> tags,
      LearningState learningState) {}

  public record Relation(String type, String slug, String question) {}

  public record CardDetail(
      String slug,
      long outlineNodeId,
      String question,
      String answerMarkdown,
      String explanationMarkdown,
      List<String> tags,
      List<Relation> relations,
      List<Breadcrumb> breadcrumbs,
      LearningState learningState) {}

  public record Page<T>(List<T> items, long total, int page, int pageSize, Instant asOf) {}

  public record ArticleSummary(long id, long outlineNodeId, String title) {}

  public record ArticleDetail(
      long id,
      long outlineNodeId,
      String title,
      String bodyMarkdown,
      List<Breadcrumb> breadcrumbs) {}

  public record Option(String rating, Instant dueAt, int intervalDays) {}

  public record Preview(
      String cardSlug, boolean enrolled, String timezone, Instant asOf, List<Option> options) {}

  public record ReviewResult(
      long id,
      String cardSlug,
      UUID clientAttemptId,
      String rating,
      Instant reviewedAt,
      Instant dueAt,
      boolean firstReview,
      boolean duplicate) {}

  public record Summary(long enrolledCount, long dueCount, Instant nextDueAt, Instant asOf) {}
}
