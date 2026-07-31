package org.congcong.algomentor.mentor.application.profile.document;

/** 画像投影只输出文本节点；这里不解析 Markdown、HTML 或链接。 */
public final class LearnerProfilePlainTextPolicy {

  private LearnerProfilePlainTextPolicy() {
  }

  public static String normalize(String value) {
    if (value == null) {
      return "";
    }
    StringBuilder normalized = new StringBuilder(value.length());
    boolean previousWhitespace = false;
    for (int index = 0; index < value.length(); index++) {
      char character = value.charAt(index);
      if (Character.isISOControl(character) && !Character.isWhitespace(character)) {
        continue;
      }
      if (Character.isWhitespace(character)) {
        if (!previousWhitespace) {
          normalized.append(' ');
        }
        previousWhitespace = true;
      } else {
        normalized.append(character);
        previousWhitespace = false;
      }
    }
    return normalized.toString().trim();
  }
}
