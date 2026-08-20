package org.congcong.algomentor.api.input;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.congcong.algomentor.api.config.UserInputLimitProperties;
import org.congcong.algomentor.api.learningplan.model.LearningPlanCreateDraftRequest;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanTargetSize;
import org.congcong.algomentor.api.practice.model.PracticeMessageRequest;
import org.congcong.algomentor.api.review.model.UpsertUserProblemNoteRequest;

/** 在反序列化和业务持久化之前执行用户输入上限及白名单校验。 */
public class UserInputValidator {

  private static final Set<String> PROGRAMMING_LANGUAGES = Set.of(
      "Java", "Python3", "C++", "JavaScript", "TypeScript", "Go",
      "C#", "C", "Kotlin", "Swift", "Rust", "SQL");

  private static final Set<String> TOPICS = Set.of(
      "Array", "Hash Table", "String", "Two Pointers", "Sliding Window", "Stack", "Queue",
      "Linked List", "Binary Tree", "Graph", "Depth-First Search", "Binary Search",
      "Dynamic Programming", "Greedy", "Heap", "Backtracking", "Bit Manipulation");

  private final ObjectMapper objectMapper;
  private final UserInputLimitProperties limits;

  public UserInputValidator(ObjectMapper objectMapper, UserInputLimitProperties limits) {
    this.objectMapper = objectMapper;
    this.limits = limits;
  }

  public long requestMaxBytes(Class<?> requestType) {
    if (requestType == UpsertUserProblemNoteRequest.class) {
      return limits.getReviewNote().getRequestMaxBytes().toBytes();
    }
    if (requestType == LearningPlanCreateDraftRequest.class) {
      return limits.getLearningPlanCreate().getRequestMaxBytes().toBytes();
    }
    if (requestType == PracticeMessageRequest.class) {
      return limits.getPracticeMessage().getRequestMaxBytes().toBytes();
    }
    throw new IllegalArgumentException("Unsupported user input request type: " + requestType.getName());
  }

  public void validateRaw(Class<?> requestType, byte[] requestBody) {
    JsonNode root;
    try {
      root = objectMapper.readTree(requestBody);
    } catch (IOException exception) {
      return;
    }
    if (root == null || !root.isObject()) {
      return;
    }
    if (requestType == UpsertUserProblemNoteRequest.class) {
      validateReviewNote(root.path("outline"));
      return;
    }
    if (requestType == LearningPlanCreateDraftRequest.class) {
      validateLearningPlan(root);
      return;
    }
    if (requestType == PracticeMessageRequest.class) {
      validateUtf8Bytes(root.path("message"), "message", limits.getPracticeMessage().getMessageMaxBytes().toBytes());
    }
  }

  public void validateParsed(Object body) {
    if (body instanceof LearningPlanCreateDraftRequest request) {
      validateLearningPlanRequest(request);
    }
  }

  private void validateReviewNote(JsonNode outline) {
    if (!outline.isObject()) {
      return;
    }
    UserInputLimitProperties.ReviewNote review = limits.getReviewNote();
    validateCodePoints(outline.path("coreIdea"), "outline.coreIdea", review.getCoreIdeaMaxChars());
    validateCodePoints(
        outline.path("dataStructureNotes"),
        "outline.dataStructureNotes",
        review.getDataStructureNotesMaxChars());
    validateCodePoints(
        outline.path("algorithmNotes"),
        "outline.algorithmNotes",
        review.getAlgorithmNotesMaxChars());
    validateCodePoints(outline.path("edgeCases"), "outline.edgeCases", review.getEdgeCasesMaxChars());
    validateCustomItems(outline.path("customDataStructures"), "outline.customDataStructures", review);
    validateCustomItems(outline.path("customAlgorithms"), "outline.customAlgorithms", review);
    validateCodePoints(
        outline.path("timeComplexity").path("customText"),
        "outline.timeComplexity.customText",
        review.getCustomComplexityMaxChars());
    validateCodePoints(
        outline.path("spaceComplexity").path("customText"),
        "outline.spaceComplexity.customText",
        review.getCustomComplexityMaxChars());
  }

  private void validateCustomItems(
      JsonNode values,
      String field,
      UserInputLimitProperties.ReviewNote review
  ) {
    if (!values.isArray()) {
      return;
    }
    if (values.size() > review.getCustomItemMaxCount()) {
      throw UserInputValidationException.limitExceeded(
          field,
          review.getCustomItemMaxCount(),
          "items",
          values.size());
    }
    for (int index = 0; index < values.size(); index++) {
      validateCodePoints(values.get(index), field + "[" + index + "]", review.getCustomItemMaxChars());
    }
  }

  private void validateLearningPlan(JsonNode root) {
    UserInputLimitProperties.LearningPlanCreate plan = limits.getLearningPlanCreate();
    validateCodePoints(root.path("objective"), "objective", plan.getObjectiveMaxChars());
    validateCodePoints(
        root.path("additionalConstraints"),
        "additionalConstraints",
        plan.getAdditionalConstraintsMaxChars());
  }

  private void validateLearningPlanRequest(LearningPlanCreateDraftRequest request) {
    validateTargetProblemCount(request.targetProblemCount());
    validateProgrammingLanguage(request.programmingLanguage());
    validateTopics(request.topicPreferences());
  }

  private void validateTargetProblemCount(Integer value) {
    if (!LearningPlanTargetSize.isSupported(value)) {
      throw UserInputValidationException.invalid(
          "targetProblemCount",
          "must be one of 5, 10, 15, 20, 25, 30");
    }
  }

  private void validateProgrammingLanguage(String value) {
    if (value == null) {
      return;
    }
    String normalized = value.strip();
    if (!PROGRAMMING_LANGUAGES.contains(normalized)) {
      throw UserInputValidationException.invalid("programmingLanguage", "unsupported value");
    }
  }

  private void validateTopics(List<String> values) {
    if (values == null) {
      return;
    }
    if (values.size() > TOPICS.size()) {
      throw UserInputValidationException.invalid("topicPreferences", "too many values");
    }
    Set<String> normalizedValues = new HashSet<>();
    for (String value : values) {
      String normalized = value == null ? "" : value.strip();
      if (!TOPICS.contains(normalized)) {
        throw UserInputValidationException.invalid("topicPreferences", "unsupported value");
      }
      if (!normalizedValues.add(normalized)) {
        throw UserInputValidationException.invalid("topicPreferences", "duplicate value");
      }
    }
  }

  private void validateCodePoints(JsonNode value, String field, int max) {
    if (!value.isTextual()) {
      return;
    }
    String text = value.textValue();
    int actual = text.codePointCount(0, text.length());
    if (actual > max) {
      throw UserInputValidationException.limitExceeded(field, max, "characters", actual);
    }
  }

  private void validateUtf8Bytes(JsonNode value, String field, long max) {
    if (!value.isTextual()) {
      return;
    }
    int actual = value.textValue().getBytes(StandardCharsets.UTF_8).length;
    if (actual > max) {
      throw UserInputValidationException.limitExceeded(field, max, "bytes", actual);
    }
  }
}
