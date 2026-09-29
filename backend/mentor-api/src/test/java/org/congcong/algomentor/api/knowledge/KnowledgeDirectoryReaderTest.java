package org.congcong.algomentor.api.knowledge;

import static org.assertj.core.api.Assertions.*;

import java.nio.file.*;
import org.congcong.algomentor.api.knowledge.importer.KnowledgeDirectoryReader;
import org.congcong.algomentor.api.knowledge.model.KnowledgeRelationType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class KnowledgeDirectoryReaderTest {
  @TempDir Path root;
  private final KnowledgeDirectoryReader reader = new KnowledgeDirectoryReader();

  private void write(String path, String content) throws Exception {
    var file = root.resolve(path);
    Files.createDirectories(file.getParent());
    Files.writeString(file, content);
  }

  private String card(String slug) {
    return "---\nslug: " + slug + "\n---\n\n核心回答\n\n## 解释\n\n详情\n";
  }

  @Test
  void scansOnlyMarkedTreeAndKeepsMarkdownBoundaries() throws Exception {
    write(
        "Java.node/基础.node/问题.card.md",
        "---\n"
            + "slug: example\n"
            + "tags: [Java]\n"
            + "order: 20\n"
            + "---\n\n"
            + "回答  \n"
            + "换行\n\n"
            + "```md\n"
            + "## 代码里的标题\n"
            + "```\n\n"
            + "> ## 引用里的标题\n\n"
            + "## 任意详情标题\n\n"
            + "### 更小章节\n"
            + "正文\n");
    write("Java.node/基础.node/文章.article.md", "文章正文\n## 章节\n详情");
    write("skills/示例.node/忽略.card.md", card("example"));
    write("Java.node/references/伪.node/忽略.card.md", "这不是合法卡片");
    write("Java.node/AGENTS.md", "维护说明");
    var source = reader.read(root);
    assertThat(source.nodes()).hasSize(2);
    assertThat(source.cards()).hasSize(1);
    assertThat(source.articles()).hasSize(1);
    var card = source.cards().get(0);
    assertThat(card.question()).isEqualTo("问题");
    assertThat(card.order()).isEqualTo(20);
    assertThat(card.answer()).contains("回答  \n换行", "## 代码里的标题", "> ## 引用里的标题");
    assertThat(card.explanation()).startsWith("## 任意详情标题").contains("### 更小章节");
    assertThat(reader.read(root)).isEqualTo(source);
  }

  @Test
  void supportsMinimalCardAndDraftRelations() throws Exception {
    write(
        "Java.node/问题.card.md", "---\nslug: example\nrelations:\n  related: [draft-card]\n---\n回答");
    write("Java.node/草稿.card.md", "---\nslug: draft-card\nstatus: draft\n---\n回答");
    var source = reader.read(root);
    var card =
        source.cards().stream().filter(c -> c.slug().equals("example")).findFirst().orElseThrow();
    assertThat(card.explanation()).isNull();
    assertThat(card.status()).isEqualTo("PUBLISHED");
    assertThat(card.relations().get(KnowledgeRelationType.RELATED)).containsExactly("draft-card");
  }

  @Test
  void rejectsDuplicateSlugAndYamlKeys() throws Exception {
    write("A.node/一.card.md", card("same"));
    write("B.node/二.card.md", card("same"));
    assertThatThrownBy(() -> reader.read(root)).hasMessageContaining("重复 slug");
    Files.delete(root.resolve("B.node/二.card.md"));
    write("A.node/一.card.md", "---\nslug: a\nslug: b\n---\n回答");
    assertThatThrownBy(() -> reader.read(root)).hasMessageContaining("YAML 无法解析");
  }

  @Test
  void rejectsDanglingSelfAndCyclicPrerequisites() throws Exception {
    write("A.node/一.card.md", "---\nslug: one\nrelations:\n  prerequisites: [two]\n---\n回答");
    assertThatThrownBy(() -> reader.read(root)).hasMessageContaining("无效关系");
    write("A.node/二.card.md", "---\nslug: two\nrelations:\n  prerequisites: [one]\n---\n回答");
    assertThatThrownBy(() -> reader.read(root)).hasMessageContaining("循环");
    write("A.node/二.card.md", "---\nslug: two\nrelations:\n  related: [two]\n---\n回答");
    assertThatThrownBy(() -> reader.read(root)).hasMessageContaining("无效关系");
  }

  @Test
  void rejectsEmptyRootAnswerAndWrongMetadata() throws Exception {
    assertThatThrownBy(() -> reader.read(root)).hasMessageContaining("空快照");
    write("A.node/问题.card.md", "---\nslug: example\n---\n## 没有核心回答\n详情");
    assertThatThrownBy(() -> reader.read(root)).hasMessageContaining("核心回答");
    write("A.node/问题.card.md", "---\nslug: example\norder: abc\n---\n回答");
    assertThatThrownBy(() -> reader.read(root)).hasMessageContaining("order");
    write("A.node/问题.card.md", "---\nslug: example\nunknown: a\n---\n回答");
    assertThatThrownBy(() -> reader.read(root)).hasMessageContaining("未知元数据");
  }

  @Test
  void rejectsRootContentAndMarkedSymlink() throws Exception {
    write("问题.card.md", card("example"));
    assertThatThrownBy(() -> reader.read(root)).hasMessageContaining("必须放在 .node");
    Files.delete(root.resolve("问题.card.md"));
    write("A.node/问题.card.md", card("example"));
    Files.createSymbolicLink(root.resolve("B.node"), root.resolve("A.node"));
    assertThatThrownBy(() -> reader.read(root)).hasMessageContaining("符号链接");
  }

  @Test
  void usesOutlineOrderAtEveryManagedLevelAndFilenameOrderElsewhere() throws Exception {
    write("Java.node/outline.json", """
        {"version":1,"children":[
          {"title":"集合","children":[{"title":"HashMap"}]},
          {"title":"基础","children":[{"title":"乙"},{"title":"甲"}]}
        ]}
        """);
    write("Java.node/基础.node/甲.node/问题.card.md", card("example"));
    Files.createDirectories(root.resolve("Java.node/基础.node/乙.node"));
    Files.createDirectories(root.resolve("Java.node/集合.node/HashMap.node"));
    Files.createDirectories(root.resolve("Z.node/乙.node"));
    Files.createDirectories(root.resolve("Z.node/甲.node"));

    var source = reader.read(root);
    assertThat(source.nodes()).filteredOn(n -> n.parentPath().equals("Java.node"))
        .extracting(n -> n.title() + ":" + n.order())
        .containsExactlyInAnyOrder("基础:1", "集合:0");
    assertThat(source.nodes()).filteredOn(n -> n.parentPath().equals("Java.node/基础.node"))
        .extracting(n -> n.title() + ":" + n.order())
        .containsExactlyInAnyOrder("甲:1", "乙:0");
    assertThat(source.nodes()).filteredOn(n -> n.parentPath().equals("Z.node"))
        .extracting(n -> n.title() + ":" + n.order())
        .containsExactly("乙:0", "甲:1");
  }

  @Test
  void rejectsOutlineMismatchAndOverlappingSpecs() throws Exception {
    write("Java.node/基础.node/问题.card.md", card("example"));
    write("Java.node/outline.json", """
        {"version":1,"children":[{"title":"集合"}]}
        """);
    assertThatThrownBy(() -> reader.read(root)).hasMessageContaining("清单外节点");

    write("Java.node/outline.json", """
        {"version":1,"children":[{"title":"基础"},{"title":"集合"}]}
        """);
    assertThatThrownBy(() -> reader.read(root)).hasMessageContaining("目录不存在");

    write("Java.node/outline.json", """
        {"version":1,"children":[{"title":"基础"}]}
        """);
    write("Java.node/基础.node/outline.json", """
        {"version":1,"children":[{"title":"子目录"}]}
        """);
    assertThatThrownBy(() -> reader.read(root)).hasMessageContaining("重复管理");
  }

  @Test
  void rejectsInvalidOutlineSchemaAndDuplicateJsonKeys() throws Exception {
    write("Java.node/问题.card.md", card("example"));
    write("Java.node/outline.json", """
        {"version":1,"children":[{"title":"重复"},{"title":"重复"}]}
        """);
    assertThatThrownBy(() -> reader.read(root)).hasMessageContaining("同级标题重复");
    write("Java.node/outline.json", """
        {"version":1,"version":1,"children":[{"title":"重复"}]}
        """);
    assertThatThrownBy(() -> reader.read(root)).hasMessageContaining("JSON 无法解析");
  }
}
