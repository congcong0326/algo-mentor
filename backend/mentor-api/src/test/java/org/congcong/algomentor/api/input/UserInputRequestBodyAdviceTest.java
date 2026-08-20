package org.congcong.algomentor.api.input;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.congcong.algomentor.api.config.UserInputLimitProperties;
import org.congcong.algomentor.api.controller.LocalizedApiExceptionHandler;
import org.congcong.algomentor.api.learningplan.model.LearningPlanCreateDraftRequest;
import org.congcong.algomentor.api.practice.model.PracticeMessageRequest;
import org.congcong.algomentor.api.review.model.UpsertUserProblemNoteRequest;
import org.congcong.algomentor.common.api.ApiResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class UserInputRequestBodyAdviceTest {

  private UserInputLimitProperties properties;
  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    properties = new UserInputLimitProperties();
    mockMvc = MockMvcBuilders.standaloneSetup(new TestController())
        .setControllerAdvice(
            new UserInputRequestBodyAdvice(new ObjectMapper(), properties),
            new LocalizedApiExceptionHandler())
        .build();
  }

  @Test
  void rejectsPracticeMessageOverUtf8ByteLimit() throws Exception {
    String message = "你".repeat(2_731);

    mockMvc.perform(post("/test/practice")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"message\":\"" + message + "\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value(UserInputValidationException.LIMIT_EXCEEDED_CODE))
        .andExpect(jsonPath("$.error.metadata.field").value("message"))
        .andExpect(jsonPath("$.error.metadata.max").value(8_192))
        .andExpect(jsonPath("$.error.metadata.unit").value("bytes"));
  }

  @Test
  void rejectsRawRequestBeforeDeserialization() throws Exception {
    String oversizedBody = "{" + " ".repeat(20_480) + "}";

    mockMvc.perform(post("/test/practice")
            .contentType(MediaType.APPLICATION_JSON)
            .content(oversizedBody))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value(UserInputValidationException.LIMIT_EXCEEDED_CODE))
        .andExpect(jsonPath("$.error.metadata.field").value("$request"))
        .andExpect(jsonPath("$.error.metadata.max").value(20_480));
  }

  @Test
  void countsReviewTextByUnicodeCodePoint() throws Exception {
    properties.getReviewNote().setCoreIdeaMaxChars(2);

    mockMvc.perform(post("/test/review-note")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {
                  "outline": {
                    "schemaVersion": 1,
                    "coreIdea": "😀😀😀"
                  },
                  "expectedRevision": 0
                }
                """))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.metadata.field").value("outline.coreIdea"))
        .andExpect(jsonPath("$.error.metadata.actual").value(3));
  }

  @Test
  void rejectsConfiguredLearningPlanTextLimit() throws Exception {
    properties.getLearningPlanCreate().setObjectiveMaxChars(2);

    mockMvc.perform(post("/test/learning-plan")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {
                  "intent": "INTERVIEW_SPRINT",
                  "objective": "目标过长",
                  "targetProblemCount": 15,
                  "level": "INTERMEDIATE",
                  "programmingLanguage": "Java",
                  "difficultyDistribution": {
                    "easyPercent": 25,
                    "mediumPercent": 55,
                    "hardPercent": 20
                  },
                  "topicPreferences": []
                }
                """))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value(UserInputValidationException.LIMIT_EXCEEDED_CODE))
        .andExpect(jsonPath("$.error.metadata.field").value("objective"))
        .andExpect(jsonPath("$.error.metadata.max").value(2));
  }

  @Test
  void rejectsLearningPlanRangeAndAllowlistViolations() throws Exception {
    mockMvc.perform(post("/test/learning-plan")
            .contentType(MediaType.APPLICATION_JSON)
            .content(validPlanJson(12, "Java", "Array")))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value(UserInputValidationException.INVALID_CODE))
        .andExpect(jsonPath("$.error.metadata.field").value("targetProblemCount"));

    mockMvc.perform(post("/test/learning-plan")
            .contentType(MediaType.APPLICATION_JSON)
            .content(validPlanJson(10, "Brainfuck", "Array")))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value(UserInputValidationException.INVALID_CODE))
        .andExpect(jsonPath("$.error.metadata.field").value("programmingLanguage"));

    mockMvc.perform(post("/test/learning-plan")
            .contentType(MediaType.APPLICATION_JSON)
            .content(validPlanJson(10, "Java", "Unknown Topic")))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value(UserInputValidationException.INVALID_CODE))
        .andExpect(jsonPath("$.error.metadata.field").value("topicPreferences"));
  }

  private String validPlanJson(int targetProblemCount, String language, String topic) {
    return """
        {
          "intent": "INTERVIEW_SPRINT",
          "targetProblemCount": %d,
          "level": "INTERMEDIATE",
          "programmingLanguage": "%s",
          "difficultyDistribution": {
            "easyPercent": 25,
            "mediumPercent": 55,
            "hardPercent": 20
          },
          "topicPreferences": ["%s"]
        }
        """.formatted(targetProblemCount, language, topic);
  }

  @RestController
  private static class TestController {

    @PostMapping("/test/practice")
    ApiResponse<Void> practice(@RequestBody PracticeMessageRequest request) {
      return ApiResponse.success(null);
    }

    @PostMapping("/test/learning-plan")
    ApiResponse<Void> learningPlan(@RequestBody LearningPlanCreateDraftRequest request) {
      return ApiResponse.success(null);
    }

    @PostMapping("/test/review-note")
    ApiResponse<Void> reviewNote(@RequestBody UpsertUserProblemNoteRequest request) {
      return ApiResponse.success(null);
    }
  }
}
