package org.congcong.algomentor.api.knowledge.importer;

import static org.congcong.algomentor.api.knowledge.model.KnowledgeContract.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import org.commonmark.node.Heading;
import org.commonmark.parser.IncludeSourceSpans;
import org.commonmark.parser.Parser;
import org.congcong.algomentor.api.knowledge.model.KnowledgeRelationType;
import org.congcong.algomentor.api.knowledge.model.KnowledgeSource;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

/** 只沿 .node 目录扫描；先完整解析和校验，再允许数据库替换。 */
public final class KnowledgeDirectoryReader {
  private final Parser markdown =
      Parser.builder().includeSourceSpans(IncludeSourceSpans.BLOCKS).build();

  public KnowledgeSource read(Path root) throws IOException {
    if (!Files.isDirectory(root) || Files.isSymbolicLink(root)) throw invalid(root, "根目录不存在或为符号链接");
    var nodes = new ArrayList<KnowledgeSource.Node>();
    var cards = new ArrayList<KnowledgeSource.Card>();
    var articles = new ArrayList<KnowledgeSource.Article>();
    scan(root, root, nodes, cards, articles);
    if (nodes.isEmpty() || cards.isEmpty() && articles.isEmpty())
      throw invalid(root, "没有可导入内容，拒绝空快照");
    var bySlug = new LinkedHashMap<String, KnowledgeSource.Card>();
    for (var card : cards)
      if (bySlug.putIfAbsent(card.slug(), card) != null)
        throw invalid(root, "重复 slug：" + card.slug());
    for (var card : cards)
      for (var relation : card.relations().values())
        for (var target : relation) {
          if (target.equals(card.slug()) || !bySlug.containsKey(target))
            throw invalid(root, "无效关系：" + card.slug() + " → " + target);
        }
    var done = new HashSet<String>();
    for (var slug : bySlug.keySet()) visit(slug, bySlug, new HashSet<>(), done);
    return new KnowledgeSource(nodes, cards, articles);
  }

  private void scan(
      Path root,
      Path directory,
      List<KnowledgeSource.Node> nodes,
      List<KnowledgeSource.Card> cards,
      List<KnowledgeSource.Article> articles)
      throws IOException {
    List<Path> entries;
    try (var stream = Files.list(directory)) {
      entries = stream.sorted(Comparator.comparing(p -> p.getFileName().toString())).toList();
    }
    int order = 0;
    for (var path : entries) {
      String name = path.getFileName().toString();
      boolean marked =
          name.endsWith(NODE_SUFFIX) || name.endsWith(CARD_SUFFIX) || name.endsWith(ARTICLE_SUFFIX);
      if (!marked) continue;
      if (Files.isSymbolicLink(path)) throw invalid(path, "正式内容不支持符号链接");
      if (name.endsWith(NODE_SUFFIX)) {
        if (!Files.isDirectory(path)) throw invalid(path, ".node 必须是目录");
        String title = title(path, NODE_SUFFIX, 200);
        if (title.length() > 220) throw invalid(path, "节点名称过长");
        nodes.add(
            new KnowledgeSource.Node(
                relative(root, path), relative(root, directory), title, order++));
        scan(root, path, nodes, cards, articles);
      } else {
        if (root.equals(directory)) throw invalid(path, "卡片和文章必须放在 .node 目录内");
        if (!Files.isRegularFile(path)) throw invalid(path, "内容必须是普通文件");
        String content = Files.readString(path).replace("\r\n", "\n").replace('\r', '\n');
        if (content.indexOf('\0') >= 0) throw invalid(path, "正文不能含 NUL 字符");
        if (name.endsWith(CARD_SUFFIX)) cards.add(card(root, path, content));
        else {
          if (content.isBlank()) throw invalid(path, "文章正文不能为空");
          articles.add(
              new KnowledgeSource.Article(
                  relative(root, directory), title(path, ARTICLE_SUFFIX, 200), content, order++));
        }
      }
    }
  }

  private KnowledgeSource.Card card(Path root, Path path, String content) {
    var lines = content.split("\n", -1);
    if (lines.length < 3 || !lines[0].equals("---")) throw invalid(path, "卡片必须以 YAML 元数据开始");
    int end = 1;
    while (end < lines.length && !lines[end].equals("---")) end++;
    if (end == lines.length) throw invalid(path, "元数据缺少结束分隔符");
    var options = new LoaderOptions();
    options.setAllowDuplicateKeys(false);
    options.setMaxAliasesForCollections(0);
    Object parsed;
    try {
      parsed =
          new Yaml(new SafeConstructor(options))
              .load(String.join("\n", Arrays.copyOfRange(lines, 1, end)));
    } catch (RuntimeException e) {
      throw invalid(path, "YAML 无法解析：" + e.getMessage());
    }
    if (!(parsed instanceof Map<?, ?> meta)) throw invalid(path, "元数据必须是对象");
    for (var key : meta.keySet())
      if (!METADATA_KEYS.contains(key)) throw invalid(path, "未知元数据：" + key);
    String slug = slug(path, meta.get(SLUG));
    String status = meta.containsKey(STATUS) ? text(path, meta.get(STATUS)) : "published";
    if (!Set.of("published", "draft").contains(status))
      throw invalid(path, "status 只能是 published 或 draft");
    int order = 0;
    if (meta.containsKey(ORDER)) {
      if (!(meta.get(ORDER) instanceof Integer value) || value < 0)
        throw invalid(path, "order 必须是非负整数");
      order = value;
    }
    var tags = strings(path, meta.get(TAGS));
    var relations = new EnumMap<KnowledgeRelationType, List<String>>(KnowledgeRelationType.class);
    if (meta.containsKey(RELATIONS)) {
      if (!(meta.get(RELATIONS) instanceof Map<?, ?> map)) throw invalid(path, "relations 必须是对象");
      for (var entry : map.entrySet()) {
        var type = KnowledgeRelationType.parse(text(path, entry.getKey()));
        var targets = strings(path, entry.getValue());
        targets.forEach(target -> slug(path, target));
        relations.put(type, targets);
      }
    }
    String body = String.join("\n", Arrays.copyOfRange(lines, end + 1, lines.length));
    int boundary = body.length();
    var document = markdown.parse(body);
    // 仅识别文档顶层的二级标题；代码、引用和列表内部的标题不作为字段边界。
    for (var node = document.getFirstChild(); node != null; node = node.getNext()) {
      if (node instanceof Heading heading && heading.getLevel() == 2) {
        int line = heading.getSourceSpans().get(0).getLineIndex();
        boundary = 0;
        for (int i = 0; i < line; i++) boundary = body.indexOf('\n', boundary) + 1;
        break;
      }
    }
    String answer = trimBlankLines(body.substring(0, boundary));
    String explanation = trimBlankLines(body.substring(boundary));
    if (answer.isBlank() || answer.codePointCount(0, answer.length()) > ANSWER_LIMIT)
      throw invalid(path, "核心回答不能为空且不能超过 " + ANSWER_LIMIT + " 字符");
    return new KnowledgeSource.Card(
        relative(root, path.getParent()),
        slug,
        title(path, CARD_SUFFIX, 500),
        answer,
        explanation.isBlank() ? null : explanation,
        tags,
        status.toUpperCase(Locale.ROOT),
        order,
        Map.copyOf(relations));
  }

  private void visit(
      String slug,
      Map<String, KnowledgeSource.Card> cards,
      Set<String> visiting,
      Set<String> done) {
    if (done.contains(slug)) return;
    if (!visiting.add(slug)) throw new IllegalArgumentException("前置关系存在循环：" + slug);
    for (var target :
        cards.get(slug).relations().getOrDefault(KnowledgeRelationType.PREREQUISITES, List.of()))
      visit(target, cards, visiting, done);
    visiting.remove(slug);
    done.add(slug);
  }

  private static String trimBlankLines(String value) {
    return value.replaceAll("\\A(?:[ \\t]*\\n)+|(?:\\n[ \\t]*)+\\z", "");
  }

  private static String relative(Path root, Path path) {
    return root.relativize(path).toString().replace('\\', '/');
  }

  private static String title(Path path, String suffix, int limit) {
    String name = path.getFileName().toString();
    String title = name.substring(0, name.length() - suffix.length());
    if (title.isBlank() || title.codePointCount(0, title.length()) > limit)
      throw invalid(path, "标题为空或过长");
    return title;
  }

  private static String text(Path path, Object value) {
    if (!(value instanceof String s) || s.isBlank()) throw invalid(path, "字段必须是非空字符串");
    return s;
  }

  private static String slug(Path path, Object value) {
    String slug = text(path, value);
    if (slug.length() > SLUG_LIMIT || !slug.matches(SLUG_PATTERN))
      throw invalid(path, "slug 必须是小写字母、数字和单连字符，最长 " + SLUG_LIMIT);
    return slug;
  }

  private static List<String> strings(Path path, Object value) {
    if (value == null) return List.of();
    if (!(value instanceof List<?> list)) throw invalid(path, "字段必须是字符串列表");
    var result = list.stream().map(item -> text(path, item)).toList();
    if (new HashSet<>(result).size() != result.size()) throw invalid(path, "列表不能重复");
    return result;
  }

  private static IllegalArgumentException invalid(Path path, String message) {
    return new IllegalArgumentException(path + "：" + message);
  }
}
