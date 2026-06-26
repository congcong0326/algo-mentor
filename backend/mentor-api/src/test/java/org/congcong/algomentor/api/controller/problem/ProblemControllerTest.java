package org.congcong.algomentor.api.controller.problem;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Optional;
import org.congcong.algomentor.api.controller.LocalizedApiExceptionHandler;
import org.congcong.algomentor.api.problem.model.ProblemDetail;
import org.congcong.algomentor.api.problem.model.ProblemDifficulty;
import org.congcong.algomentor.api.problem.model.ProblemLocale;
import org.congcong.algomentor.api.problem.model.ProblemListItem;
import org.congcong.algomentor.api.problem.model.ProblemPage;
import org.congcong.algomentor.api.problem.model.ProblemTag;
import org.congcong.algomentor.api.problem.service.ProblemService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = ProblemController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(LocalizedApiExceptionHandler.class)
class ProblemControllerTest {

  @Autowired
  private MockMvc mockMvc;

  @MockBean
  private ProblemService problemService;

  @Test
  void listProblemsReturnsPageEnvelope() throws Exception {
    when(problemService.findProblems(any())).thenReturn(new ProblemPage<>(List.of(
        new ProblemListItem("two-sum", 1, "两数之和", ProblemDifficulty.EASY, List.of(
            new ProblemTag("array", "数组")))
    ), 1, 1, 20));

    mockMvc.perform(get("/api/problems")
            .param("keyword", "sum")
            .param("difficulty", "easy")
            .param("tag", "array")
            .param("locale", "zh-CN")
            .param("page", "1")
            .param("pageSize", "20"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.total").value(1))
        .andExpect(jsonPath("$.data.items[0].slug").value("two-sum"))
        .andExpect(jsonPath("$.data.items[0].title").value("两数之和"))
        .andExpect(jsonPath("$.data.items[0].titleCn").doesNotExist())
        .andExpect(jsonPath("$.data.items[0].tags[0].value").value("array"))
        .andExpect(jsonPath("$.data.items[0].tags[0].label").value("数组"))
        .andExpect(jsonPath("$.data.items[0].difficulty").value("EASY"));
  }

  @Test
  void getProblemReturnsDetail() throws Exception {
    when(problemService.findProblemBySlug(eq("two-sum"), eq(ProblemLocale.EN_US))).thenReturn(Optional.of(new ProblemDetail(
        "two-sum",
        1,
        "Two Sum",
        ProblemDifficulty.EASY,
        List.of(new ProblemTag("array", "Array")),
        "# Two Sum",
        "https://leetcode.com/problems/two-sum/",
        "[2,7]\n9",
        "class Solution:\n    pass",
        "abc123")));

    mockMvc.perform(get("/api/problems/two-sum").param("locale", "en-US"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.slug").value("two-sum"))
        .andExpect(jsonPath("$.data.title").value("Two Sum"))
        .andExpect(jsonPath("$.data.titleCn").doesNotExist())
        .andExpect(jsonPath("$.data.contentMarkdown").value("# Two Sum"))
        .andExpect(jsonPath("$.data.python3Template").value("class Solution:\n    pass"));
  }

  @Test
  void listProblemsReturns400ForUnsupportedLocale() throws Exception {
    mockMvc.perform(get("/api/problems").param("locale", "fr-FR"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.error.code").value(ProblemExceptionHandler.UNSUPPORTED_PROBLEM_LOCALE_CODE))
        .andExpect(jsonPath("$.error.messageKey").value("api.error.UNSUPPORTED_PROBLEM_LOCALE"))
        .andExpect(jsonPath("$.error.message").value("题目语言暂不支持。"));
  }

  @Test
  void getProblemReturns404ForUnknownSlugInEnglish() throws Exception {
    when(problemService.findProblemBySlug(eq("missing"), eq(ProblemLocale.DEFAULT))).thenReturn(Optional.empty());

    mockMvc.perform(get("/api/problems/missing").header("Accept-Language", "en-US"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.error.code").value(ProblemExceptionHandler.PROBLEM_NOT_FOUND_CODE))
        .andExpect(jsonPath("$.error.messageKey").value("api.error.PROBLEM_NOT_FOUND"))
        .andExpect(jsonPath("$.error.message").value("Problem not found."));
  }
}
