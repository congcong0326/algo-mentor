package org.congcong.algomentor.api.knowledge;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.mock;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import org.congcong.algomentor.api.knowledge.importer.*;
import org.congcong.algomentor.api.knowledge.model.KnowledgeSource;
import org.congcong.algomentor.api.knowledge.repository.KnowledgeRepository;
import org.congcong.algomentor.api.knowledge.service.*;
import org.congcong.algomentor.api.support.PostgresIntegrationTestSupport;
import org.congcong.algomentor.mentor.application.review.preference.ReviewPreferenceService;
import org.congcong.algomentor.mentor.application.review.schedule.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;

class KnowledgeImportIT extends PostgresIntegrationTestSupport {
  @TempDir Path root;
  private KnowledgeContentImporter importer;
  private KnowledgeService service;
  private final KnowledgeDirectoryReader reader = new KnowledgeDirectoryReader();

  @BeforeEach
  void prepare() throws Exception {
    migrateLatest();
    importer = new KnowledgeContentImporter(dataSource());
    service =
        new KnowledgeService(
            new KnowledgeRepository(new JdbcTemplate(dataSource())),
            new FsrsReviewSchedulerService(ReviewSchedulerProperties.defaults()),
            mock(ReviewPreferenceService.class),
            new ObjectMapper());
    write(
        "Java.node/基础.node/问题.card.md",
        "---\n"
            + "slug: stable-card\n"
            + "tags: [Java]\n"
            + "relations:\n"
            + "  related: [other-card, draft-card]\n"
            + "---\n"
            + "核心回答\n\n"
            + "## 细节\n"
            + "解释");
    write("Java.node/集合.node/问题.card.md", "---\nslug: other-card\n---\n另一个回答");
    write("Java.node/集合.node/草稿.card.md", "---\nslug: draft-card\nstatus: draft\n---\n草稿回答");
    write("Java.node/基础.node/文章.article.md", "文章\n## 详情\n内容");
    importer.replace(reader.read(root));
  }

  private void write(String path, String content) throws Exception {
    var file = root.resolve(path);
    Files.createDirectories(file.getParent());
    Files.writeString(file, content);
  }

  @Test
  void reimportMoveDeleteRestoreKeepsReviewStateAndReplay() throws Exception {
    long user = insertUser();
    long other = insertUser();
    UUID attempt = UUID.randomUUID();
    long oldId = queryLong("SELECT id FROM knowledge_card WHERE slug='stable-card'");
    var result =
        transactionTemplate()
            .execute(tx -> service.review("stable-card", user, attempt, "AGAIN", "UTC"));
    long stateId = queryLong("SELECT id FROM knowledge_card_user_state WHERE user_id=?", user);
    String due =
        queryString("SELECT due_at::text FROM knowledge_card_user_state WHERE id=?", stateId);
    assertThat(result.firstReview()).isTrue();
    assertThat(service.summary(other).enrolledCount()).isZero();
    importer.replace(reader.read(root));
    assertThat(queryLong("SELECT id FROM knowledge_card WHERE slug='stable-card'"))
        .isNotEqualTo(oldId);
    assertThat(queryLong("SELECT id FROM knowledge_card_user_state WHERE user_id=?", user))
        .isEqualTo(stateId);
    assertThat(
            queryString("SELECT due_at::text FROM knowledge_card_user_state WHERE id=?", stateId))
        .isEqualTo(due);
    assertThat(service.card("stable-card", user).learningState().enrolled()).isTrue();
    Files.move(
        root.resolve("Java.node/基础.node/问题.card.md"), root.resolve("Java.node/集合.node/改名.card.md"));
    write("Java.node/集合.node/改名.card.md", "---\nslug: stable-card\n---\n修改后的核心回答\n\n## 更新解释\n内容");
    importer.replace(reader.read(root));
    var card = service.card("stable-card", user);
    assertThat(card.question()).isEqualTo("改名");
    assertThat(card.answerMarkdown()).isEqualTo("修改后的核心回答");
    assertThat(card.breadcrumbs()).extracting(b -> b.title()).containsExactly("Java", "集合");
    var retry =
        transactionTemplate()
            .execute(tx -> service.review("stable-card", user, attempt, "AGAIN", "UTC"));
    assertThat(retry.duplicate()).isTrue();
    assertThat(retry.dueAt()).isEqualTo(result.dueAt());
    assertThat(count("knowledge_card_review_attempt")).isEqualTo(1);
    assertThatThrownBy(
            () ->
                transactionTemplate()
                    .execute(tx -> service.review("other-card", user, attempt, "AGAIN", "UTC")))
        .isInstanceOf(KnowledgeException.class);
    Files.delete(root.resolve("Java.node/集合.node/改名.card.md"));
    importer.replace(reader.read(root));
    assertThat(service.summary(user).enrolledCount()).isZero();
    assertThat(count("knowledge_card_user_state")).isEqualTo(1);
    write("Java.node/集合.node/恢复.card.md", "---\nslug: stable-card\n---\n恢复答案");
    importer.replace(reader.read(root));
    assertThat(service.summary(user).enrolledCount()).isEqualTo(1);
    assertThat(count("knowledge_card_review_attempt")).isEqualTo(1);
  }

  @Test
  void queriesTreeArticlesRelationsAndDraftVisibility() throws Exception {
    long user = insertUser();
    var topics = service.topics(user);
    assertThat(topics.items()).hasSize(1);
    assertThat(topics.items().get(0).subtreeCardCount()).isEqualTo(2);
    var tree = service.tree(topics.items().get(0).id(), user);
    assertThat(tree.children()).hasSize(2);
    var card = service.card("stable-card", user);
    assertThat(card.relations()).extracting(r -> r.slug()).containsExactly("other-card");
    assertThat(card.explanationMarkdown()).startsWith("## 细节");
    assertThat(card.tags()).containsExactly("Java");
    var articles = service.articles(card.outlineNodeId(), 1, 20);
    assertThat(articles.total()).isEqualTo(1);
    assertThat(service.article(articles.items().get(0).id()).bodyMarkdown()).contains("## 详情");
    assertThatThrownBy(() -> service.card("draft-card", user))
        .isInstanceOf(KnowledgeException.class);
    assertThatThrownBy(() -> service.cards(card.outlineNodeId(), user, 0, 20))
        .isInstanceOf(IllegalArgumentException.class);
    assertThat(service.preview("stable-card", user, "UTC").options()).hasSize(4);
    assertThat(count("knowledge_card_user_state")).isZero();
  }

  @Test
  void enrollmentPreservesHistoryAndDoesNotCreateRatings() throws Exception {
    long user = insertUser();
    long other = insertUser();
    transactionTemplate().execute(tx -> service.setEnrollment("stable-card", user, false));
    assertThat(count("knowledge_card_user_state")).isZero();
    var enrolled = transactionTemplate().execute(tx -> service.setEnrollment("stable-card", user, true));
    assertThat(enrolled.enrolled()).isTrue();
    assertThat(enrolled.isDue()).isTrue();
    assertThat(enrolled.lastRating()).isNull();
    assertThat(enrolled.lastReviewedAt()).isNull();
    assertThat(count("knowledge_card_review_attempt")).isZero();
    var duplicate = transactionTemplate().execute(tx -> service.setEnrollment("stable-card", user, true));
    assertThat(duplicate.dueAt()).isEqualTo(enrolled.dueAt());
    assertThat(count("knowledge_card_user_state")).isEqualTo(1);
    assertThat(service.reviewCards(user, "DUE", 1, 20).items()).hasSize(1);
    assertThat(service.summary(other).enrolledCount()).isZero();
    assertThat(service.topics(user).items().get(0).subtreeEnrolledCardCount()).isEqualTo(1);

    var attempt = UUID.randomUUID();
    var review = transactionTemplate().execute(tx -> service.review("stable-card", user, attempt, "GOOD", "UTC"));
    assertThat(review.firstReview()).isTrue();
    var scheduled = service.card("stable-card", user).learningState();
    var removed = transactionTemplate().execute(tx -> service.setEnrollment("stable-card", user, false));
    assertThat(removed.enrolled()).isFalse();
    assertThat(removed.isDue()).isFalse();
    assertThat(service.summary(user).enrolledCount()).isZero();
    assertThat(service.reviewCards(user, "ALL", 1, 20).items()).isEmpty();
    assertThat(service.topics(user).items().get(0).subtreeEnrolledCardCount()).isZero();
    assertThat(service.preview("stable-card", user, "UTC").enrolled()).isFalse();
    assertThat(count("knowledge_card_review_attempt")).isEqualTo(1);
    var replay = transactionTemplate().execute(tx -> service.review("stable-card", user, attempt, "GOOD", "UTC"));
    assertThat(replay.duplicate()).isTrue();
    assertThat(service.card("stable-card", user).learningState().enrolled()).isFalse();
    var restored = transactionTemplate().execute(tx -> service.setEnrollment("stable-card", user, true));
    assertThat(restored.dueAt()).isEqualTo(scheduled.dueAt());
    assertThat(restored.lastReviewedAt()).isEqualTo(scheduled.lastReviewedAt());
    assertThat(restored.lastRating()).isEqualTo(scheduled.lastRating());
    transactionTemplate().execute(tx -> service.setEnrollment("stable-card", user, false));
    var next = transactionTemplate().execute(tx -> service.review("stable-card", user, UUID.randomUUID(), "GOOD", "UTC"));
    assertThat(next.firstReview()).isFalse();
    assertThat(service.card("stable-card", user).learningState().enrolled()).isTrue();
    assertThatThrownBy(() -> transactionTemplate().execute(tx -> service.setEnrollment("draft-card", user, true)))
        .isInstanceOf(KnowledgeException.class);
  }

  @Test
  void cardPaginationSearchesQuestionAndTagsWithMatchingTotals() throws Exception {
    write("Java.node/基础.node/第二问题.card.md", "---\nslug: second-card\n---\n第二回答");
    importer.replace(reader.read(root));
    long user = insertUser();
    long node = service.card("stable-card", user).outlineNodeId();
    var first = service.cards(node, user, 1, 1, "问题");
    var second = service.cards(node, user, 2, 1, "问题");
    assertThat(first.total()).isEqualTo(2);
    assertThat(first.items()).hasSize(1);
    assertThat(second.items()).hasSize(1);
    assertThat(first.items().get(0).slug()).isNotEqualTo(second.items().get(0).slug());
    assertThat(service.cards(node, user, 1, 20, " java ").items())
        .extracting(c -> c.slug()).containsExactly("stable-card");
    assertThat(service.cards(node, user, 1, 20, "%").total()).isZero();
  }

  @Test
  void failedReplacementRollsBackOldContent() throws Exception {
    var source = reader.read(root);
    long old = queryLong("SELECT id FROM knowledge_card WHERE slug='stable-card'");
    var invalid =
        new KnowledgeSource(
            source.nodes(),
            List.of(
                new KnowledgeSource.Card(
                    "Java.node/基础.node",
                    "duplicate",
                    "过长".repeat(300),
                    "回答",
                    null,
                    List.of(),
                    "PUBLISHED",
                    0,
                    Map.of())),
            source.articles());
    assertThatThrownBy(() -> importer.replace(invalid)).isInstanceOf(RuntimeException.class);
    assertThat(queryLong("SELECT id FROM knowledge_card WHERE slug='stable-card'")).isEqualTo(old);
    assertThat(count("knowledge_card")).isEqualTo(3);
    assertThat(count("knowledge_article")).isEqualTo(1);
    write("Java.node/重复.card.md", "---\nslug: stable-card\n---\n重复");
    assertThatThrownBy(() -> importer.replace(reader.read(root))).hasMessageContaining("重复 slug");
    assertThat(queryLong("SELECT id FROM knowledge_card WHERE slug='stable-card'")).isEqualTo(old);
  }

  @Test
  void concurrentImportsAndDuplicateReviewsRemainConsistent() throws Exception {
    var source = reader.read(root);
    long user = insertUser();
    UUID attempt = UUID.randomUUID();
    var pool = Executors.newFixedThreadPool(2);
    try {
      var one = pool.submit(() -> importer.replace(source));
      var two = pool.submit(() -> importer.replace(source));
      one.get(30, TimeUnit.SECONDS);
      two.get(30, TimeUnit.SECONDS);
      var reviewOne =
          pool.submit(
              () ->
                  transactionTemplate()
                      .execute(tx -> service.review("stable-card", user, attempt, "GOOD", "UTC")));
      var reviewTwo =
          pool.submit(
              () ->
                  transactionTemplate()
                      .execute(tx -> service.review("stable-card", user, attempt, "GOOD", "UTC")));
      var a = reviewOne.get(30, TimeUnit.SECONDS);
      var b = reviewTwo.get(30, TimeUnit.SECONDS);
      assertThat(a.id()).isEqualTo(b.id());
      assertThat(a.duplicate()).isNotEqualTo(b.duplicate());
      assertThat(count("knowledge_card")).isEqualTo(3);
      assertThat(count("knowledge_card_user_state")).isEqualTo(1);
      assertThat(count("knowledge_card_review_attempt")).isEqualTo(1);
    } finally {
      pool.shutdownNow();
    }
  }
}
