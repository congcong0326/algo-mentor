package org.congcong.algomentor.mentor.application.practice;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PracticeTurnClassifierTest {

  private final PracticeTurnClassifier classifier = new PracticeTurnClassifier();

  @Test
  void detectsJavaClassSolution() {
    PracticeTurnClassification result = classifier.classify("""
        class Solution {
          public int climbStairs(int n) {
            return n <= 2 ? n : climbStairs(n - 1) + climbStairs(n - 2);
          }
        }
        """, "climbing-stairs", "Climbing Stairs");

    assertThat(result.codeSubmissionCandidate()).isTrue();
    assertThat(result.languageHint()).isEqualTo("java");
    assertThat(result.extractedCode()).contains("class Solution");
    assertThat(result.evidence())
        .extracting(PracticeCodeReviewEvidence::type)
        .contains(
            PracticeTurnClassifier.EVIDENCE_CLASS_SOLUTION,
            PracticeTurnClassifier.EVIDENCE_RETURN_STATEMENT);
  }

  @Test
  void detectsPythonFunctionSubmission() {
    PracticeTurnClassification result = classifier.classify("""
        def climbStairs(n):
            if n <= 2:
                return n
            return climbStairs(n - 1) + climbStairs(n - 2)
        """, "climbing-stairs", "Climbing Stairs");

    assertThat(result.codeSubmissionCandidate()).isTrue();
    assertThat(result.languageHint()).isEqualTo("python");
    assertThat(result.extractedCode()).contains("def climbStairs");
    assertThat(result.evidence())
        .extracting(PracticeCodeReviewEvidence::type)
        .contains(
            PracticeTurnClassifier.EVIDENCE_FUNCTION_SIGNATURE,
            PracticeTurnClassifier.EVIDENCE_RETURN_STATEMENT);
  }

  @Test
  void extractsMarkdownFencedCodeLanguage() {
    PracticeTurnClassification result = classifier.classify("""
        这是我的提交：
        ```java
        class Solution {
          public int climbStairs(int n) {
            return n;
          }
        }
        ```
        """, "climbing-stairs", "Climbing Stairs");

    assertThat(result.codeSubmissionCandidate()).isTrue();
    assertThat(result.languageHint()).isEqualTo("java");
    assertThat(result.extractedCode()).contains("class Solution");
    assertThat(result.evidence())
        .extracting(PracticeCodeReviewEvidence::type)
        .contains(
            PracticeTurnClassifier.EVIDENCE_FENCED_CODE_BLOCK,
            PracticeTurnClassifier.EVIDENCE_LANGUAGE_HINT);
  }

  @Test
  void rejectsPlainQuestion() {
    PracticeTurnClassification result = classifier.classify(
        "这题为什么可以用动态规划？我还没写代码。",
        "climbing-stairs",
        "Climbing Stairs");

    assertThat(result.codeSubmissionCandidate()).isFalse();
    assertThat(result.languageHint()).isNull();
    assertThat(result.extractedCode()).isBlank();
  }

  @Test
  void rejectsStackTraceOnly() {
    PracticeTurnClassification result = classifier.classify("""
        Runtime Error
        java.lang.NullPointerException
          at Solution.climbStairs(Solution.java:4)
          at Main.main(Main.java:12)
        """, "climbing-stairs", "Climbing Stairs");

    assertThat(result.codeSubmissionCandidate()).isFalse();
    assertThat(result.extractedCode()).isBlank();
  }

  @Test
  void rejectsPseudocodeWithoutCodeShape() {
    PracticeTurnClassification result = classifier.classify("""
        思路是先处理 n=1 和 n=2，然后每一步把前两个状态加起来，最后返回答案。
        """, "climbing-stairs", "Climbing Stairs");

    assertThat(result.codeSubmissionCandidate()).isFalse();
    assertThat(result.extractedCode()).isBlank();
  }

  @Test
  void rejectsJavaClassWithoutReturnStatement() {
    PracticeTurnClassification result = classifier.classify("""
        class Solution {
          public int x;
        }
        """, "climbing-stairs", "Climbing Stairs");

    assertThat(result.codeSubmissionCandidate()).isFalse();
    assertThat(result.extractedCode()).isBlank();
  }
}
