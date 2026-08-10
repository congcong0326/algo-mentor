package org.congcong.algomentor.mentor.application.practice;

import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/** 对当前用户消息执行保守的题目笔记正文读取授权。 */
final class PracticeNoteBodyAccessPolicy {

  private static final Pattern CLAUSE_SEPARATOR = Pattern.compile("[，。！？；,.!?;\\r\\n]+");
  private static final List<String> CHINESE_STRONG_MARKERS = List.of(
      "笔记正文",
      "笔记全文",
      "完整笔记",
      "完整的笔记",
      "笔记内容",
      "笔记里写了什么",
      "笔记里记了什么",
      "笔记里记录了什么");
  private static final List<String> CHINESE_READ_VERBS = List.of(
      "读取", "查看", "打开", "展示", "显示", "看看", "看下", "读一下", "给我看");
  private static final List<String> CHINESE_COACH_SUMMARY_VERBS = List.of(
      "生成", "更新", "替换", "保存", "重写", "重新生成", "整理", "总结");
  private static final List<String> CHINESE_NEGATIONS = List.of(
      "不要", "不用", "无需", "不需要", "别");
  private static final List<String> ENGLISH_BODY_MARKERS = List.of(
      "body", "content", "full", "complete", "entire", "what did i write", "what have i written");
  private static final List<String> ENGLISH_READ_VERBS = List.of("read", "show", "open", "display", "view");
  private static final List<String> ENGLISH_COACH_SUMMARY_VERBS = List.of(
      "create", "generate", "update", "replace", "save", "rewrite", "summarize");
  private static final List<String> ENGLISH_NEGATIONS = List.of(
      "do not", "don't", "dont", "never", "no need", "without");

  boolean isExplicitlyRequested(String userMessage) {
    String normalized = userMessage == null ? "" : userMessage.strip().toLowerCase(Locale.ROOT);
    if (normalized.isEmpty()) {
      return false;
    }
    return CLAUSE_SEPARATOR.splitAsStream(normalized)
        .map(String::strip)
        .filter(clause -> !clause.isEmpty())
        .anyMatch(this::isExplicitRequestClause);
  }

  private boolean isExplicitRequestClause(String clause) {
    boolean mentionsChineseNote = clause.contains("笔记");
    if (mentionsChineseNote && containsAny(clause, CHINESE_NEGATIONS)) {
      return false;
    }
    if (containsAny(clause, CHINESE_STRONG_MARKERS)) {
      return true;
    }
    if (clause.contains("教练总结") && containsAny(clause, CHINESE_COACH_SUMMARY_VERBS)) {
      return true;
    }
    if (mentionsChineseNote && !clause.contains("提纲")
        && containsAny(clause, CHINESE_READ_VERBS)) {
      return true;
    }
    boolean mentionsEnglishNote = clause.contains("note");
    if (!mentionsEnglishNote || clause.contains("outline") || containsAny(clause, ENGLISH_NEGATIONS)) {
      return clause.contains("coach summary") && containsAny(clause, ENGLISH_COACH_SUMMARY_VERBS);
    }
    return containsAny(clause, ENGLISH_BODY_MARKERS)
        || containsAny(clause, ENGLISH_READ_VERBS);
  }

  private boolean containsAny(String value, List<String> markers) {
    return markers.stream().anyMatch(value::contains);
  }
}
