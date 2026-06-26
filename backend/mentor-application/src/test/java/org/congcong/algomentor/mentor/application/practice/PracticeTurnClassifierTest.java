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
  void detectsJavaVoidMethodSubmission() {
    PracticeTurnClassification result = classifier.classify("""
        解答如下class Solution {
            public void merge(int[] nums1, int m, int[] nums2, int n) {
                int i = m - 1;
                int j = n - 1;
                int k = m + n - 1;

                while (i >= 0 && j >= 0) {
                    if (nums1[i] > nums2[j]) {
                        nums1[k] = nums1[i];
                        i--;
                    } else {
                        nums1[k] = nums2[j];
                        j--;
                    }
                    k--;
                }

                while (j >= 0) {
                    nums1[k] = nums2[j];
                    j--;
                    k--;
                }
            }
        }
        """, "merge-sorted-array", "合并两个有序数组");

    assertThat(result.codeSubmissionCandidate()).isTrue();
    assertThat(result.languageHint()).isEqualTo("java");
    assertThat(result.extractedCode()).contains("class Solution");
    assertThat(result.evidence())
        .extracting(PracticeCodeReviewEvidence::type)
        .contains(PracticeTurnClassifier.EVIDENCE_CLASS_SOLUTION);
  }

  @Test
  void detectsPlainPastedJavaVoidMethodSubmission() {
    PracticeTurnClassification result = classifier.classify("""
        class Solution {
            public void merge(int[] nums1, int m, int[] nums2, int n) {
                int[] temp = new int[m + n];

                int i = 0;
                int j = 0;
                int k = 0;

                while (i < m && j < n) {
                    if (nums1[i] <= nums2[j]) {
                        temp[k] = nums1[i];
                        i++;
                    } else {
                        temp[k] = nums2[j];
                        j++;
                    }
                    k++;
                }

                while (i < m) {
                    temp[k] = nums1[i];
                    i++;
                    k++;
                }

                while (j < n) {
                    temp[k] = nums2[j];
                    j++;
                    k++;
                }

                for (int x = 0; x < m + n; x++) {
                    nums1[x] = temp[x];
                }
            }
        }
        """, "merge-sorted-array", "合并两个有序数组");

    assertThat(result.codeSubmissionCandidate()).isTrue();
    assertThat(result.languageHint()).isEqualTo("java");
    assertThat(result.extractedCode()).contains("int[] temp = new int[m + n]");
    assertThat(result.evidence())
        .extracting(PracticeCodeReviewEvidence::type)
        .contains(PracticeTurnClassifier.EVIDENCE_CLASS_SOLUTION);
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
