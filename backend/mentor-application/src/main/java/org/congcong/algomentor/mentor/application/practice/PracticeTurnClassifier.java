package org.congcong.algomentor.mentor.application.practice;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class PracticeTurnClassifier {

  public static final String EVIDENCE_FENCED_CODE_BLOCK = "FENCED_CODE_BLOCK";
  public static final String EVIDENCE_CLASS_SOLUTION = "CLASS_SOLUTION";
  public static final String EVIDENCE_FUNCTION_SIGNATURE = "FUNCTION_SIGNATURE";
  public static final String EVIDENCE_RETURN_STATEMENT = "RETURN_STATEMENT";
  public static final String EVIDENCE_LANGUAGE_HINT = "LANGUAGE_HINT";
  public static final String EVIDENCE_PROBLEM_SLUG_OR_TITLE = "PROBLEM_SLUG_OR_TITLE";

  private static final Pattern FENCED_CODE = Pattern.compile("(?s)```\\s*([A-Za-z0-9_+#.-]*)\\s*\\R(.*?)\\R?```");
  private static final Pattern JAVA_CLASS_SOLUTION = Pattern.compile("\\bclass\\s+Solution\\b");
  private static final Pattern PYTHON_FUNCTION = Pattern.compile("(?m)^\\s*def\\s+[A-Za-z_][A-Za-z0-9_]*\\s*\\(");
  private static final Pattern JS_FUNCTION = Pattern.compile("\\bfunction\\s+[A-Za-z_$][A-Za-z0-9_$]*\\s*\\(");
  private static final Pattern STACK_TRACE_MARKER = Pattern.compile(
      "(?i)(Exception|Traceback|Runtime Error|Compile Error|^\\s*at\\s+[\\w.$]+\\()");

  public PracticeTurnClassification classify(String message, String problemSlug, String problemTitle) {
    String original = message == null ? "" : message.strip();
    if (original.isBlank()) {
      return PracticeTurnClassification.notCodeLike(original, "", Map.of());
    }

    CandidateText candidateText = candidateText(original);
    String code = candidateText.code().strip();
    List<PracticeCodeReviewEvidence> evidence = new ArrayList<>();
    Map<String, Object> metadata = new LinkedHashMap<>();
    String languageHint = normalizeLanguage(candidateText.languageHint());

    if (candidateText.fromFence()) {
      evidence.add(new PracticeCodeReviewEvidence(EVIDENCE_FENCED_CODE_BLOCK, "markdown fenced code block"));
    }
    if (languageHint != null) {
      evidence.add(new PracticeCodeReviewEvidence(EVIDENCE_LANGUAGE_HINT, languageHint));
      metadata.put("languageHint", languageHint);
    }
    if (mentionsProblem(original, problemSlug, problemTitle)) {
      evidence.add(new PracticeCodeReviewEvidence(EVIDENCE_PROBLEM_SLUG_OR_TITLE, nonBlank(problemSlug, problemTitle)));
    }

    CodeShape codeShape = detectCodeShape(code, languageHint, evidence);
    if (!codeShape.codeLike()) {
      return PracticeTurnClassification.notCodeLike(original, "", metadata, evidence.toArray(PracticeCodeReviewEvidence[]::new));
    }
    if (languageHint == null) {
      languageHint = codeShape.language();
    }
    if (isDominatedByStackTrace(original) && !hasSolutionStructure(code)) {
      return PracticeTurnClassification.notCodeLike(original, "", metadata, evidence.toArray(PracticeCodeReviewEvidence[]::new));
    }
    return new PracticeTurnClassification(true, languageHint, code, original, metadata, evidence);
  }

  private CandidateText candidateText(String original) {
    Matcher matcher = FENCED_CODE.matcher(original);
    CandidateText best = null;
    while (matcher.find()) {
      String language = matcher.group(1);
      String code = matcher.group(2);
      if (best == null || code.length() > best.code().length()) {
        best = new CandidateText(code, language, true);
      }
    }
    return best == null ? new CandidateText(original, null, false) : best;
  }

  private CodeShape detectCodeShape(
      String code,
      String languageHint,
      List<PracticeCodeReviewEvidence> evidence
  ) {
    boolean hasReturn = code.contains("return");
    if (hasReturn) {
      evidence.add(new PracticeCodeReviewEvidence(EVIDENCE_RETURN_STATEMENT, "return"));
    }

    if (JAVA_CLASS_SOLUTION.matcher(code).find()
        && code.contains("{")
        && code.contains("}")
        && code.contains(";")
        && code.contains("public")
        && hasReturn) {
      evidence.add(new PracticeCodeReviewEvidence(EVIDENCE_CLASS_SOLUTION, "class Solution"));
      return new CodeShape(true, "java");
    }

    if (PYTHON_FUNCTION.matcher(code).find() && hasReturn && hasIndentedLine(code)) {
      evidence.add(new PracticeCodeReviewEvidence(EVIDENCE_FUNCTION_SIGNATURE, "def"));
      return new CodeShape(true, "python");
    }

    if (hasJavaScriptShape(code) && hasReturn) {
      evidence.add(new PracticeCodeReviewEvidence(EVIDENCE_FUNCTION_SIGNATURE, "javascript function"));
      String language = "typescript".equals(languageHint) ? "typescript" : "javascript";
      return new CodeShape(true, language);
    }

    return new CodeShape(false, languageHint);
  }

  private boolean hasJavaScriptShape(String code) {
    return JS_FUNCTION.matcher(code).find()
        || code.contains("=>")
        || Pattern.compile("\\bconst\\s+[A-Za-z_$][A-Za-z0-9_$]*\\s*=").matcher(code).find();
  }

  private boolean hasIndentedLine(String code) {
    return Pattern.compile("(?m)^\\s{2,}\\S").matcher(code).find();
  }

  private boolean isDominatedByStackTrace(String original) {
    long nonBlankLines = original.lines().filter(line -> !line.isBlank()).count();
    long stackTraceLines = original.lines()
        .filter(line -> STACK_TRACE_MARKER.matcher(line).find())
        .count();
    return stackTraceLines > 0 && stackTraceLines * 2 >= Math.max(1, nonBlankLines);
  }

  private boolean hasSolutionStructure(String code) {
    return JAVA_CLASS_SOLUTION.matcher(code).find() || PYTHON_FUNCTION.matcher(code).find() || hasJavaScriptShape(code);
  }

  private boolean mentionsProblem(String original, String problemSlug, String problemTitle) {
    String lower = original.toLowerCase(Locale.ROOT);
    return (problemSlug != null && !problemSlug.isBlank() && lower.contains(problemSlug.toLowerCase(Locale.ROOT)))
        || (problemTitle != null && !problemTitle.isBlank() && lower.contains(problemTitle.toLowerCase(Locale.ROOT)));
  }

  private String normalizeLanguage(String language) {
    if (language == null || language.isBlank()) {
      return null;
    }
    return switch (language.trim().toLowerCase(Locale.ROOT)) {
      case "py", "python3" -> "python";
      case "js", "node", "nodejs" -> "javascript";
      case "ts" -> "typescript";
      case "java", "python", "javascript", "typescript" -> language.trim().toLowerCase(Locale.ROOT);
      default -> language.trim().toLowerCase(Locale.ROOT);
    };
  }

  private String nonBlank(String first, String second) {
    if (first != null && !first.isBlank()) {
      return first.trim();
    }
    return second == null ? "" : second.trim();
  }

  private record CandidateText(String code, String languageHint, boolean fromFence) {}

  private record CodeShape(boolean codeLike, String language) {}
}
