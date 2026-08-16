package org.congcong.algomentor.mentor.application.practice;

import java.util.Locale;
import java.util.regex.Pattern;

/** 对当前用户消息作保守判定，决定是否允许读取旧正式提交的源码。 */
final class PracticeSubmissionHistoryCodeIntent {

  private static final Pattern EXPLICIT_CODE_REQUEST = Pattern.compile(
      "(?is)(?:查看|看看|看一下|看|展示|贴出|拿出|复盘|比较|对比).{0,24}(?:旧|之前|历史|那版|提交).{0,24}(?:代码|源码|实现)"
          + "|(?:旧|之前|历史|那版|提交).{0,24}(?:代码|源码|实现).{0,24}(?:查看|看看|看一下|看|展示|贴出|复盘|比较|对比|怎么写|如何写|写法|哪里错|什么问题|错误)"
          + "|(?:code[- ]?level\s+(?:review|retrospective)|(?:show|view|review|inspect|compare|walk through).{0,24}(?:old|previous|historical).{0,24}(?:code|submission|implementation)|(?:old|previous|historical).{0,24}(?:code|submission|implementation).{0,24}(?:show|view|review|inspect|compare|walk through))");

  private PracticeSubmissionHistoryCodeIntent() {
  }

  static boolean hasExplicitCodeRequest(String userMessage) {
    if (userMessage == null || userMessage.isBlank()) {
      return false;
    }
    String normalized = userMessage.replaceAll("\\s+", " ").trim().toLowerCase(Locale.ROOT);
    return EXPLICIT_CODE_REQUEST.matcher(normalized).find();
  }
}
