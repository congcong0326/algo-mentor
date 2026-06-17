package org.congcong.algomentor.api.controller.problem;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Optional;
import org.congcong.algomentor.api.problem.model.ProblemDetail;
import org.congcong.algomentor.api.problem.model.ProblemDifficulty;
import org.congcong.algomentor.api.problem.model.ProblemListItem;
import org.congcong.algomentor.api.problem.model.ProblemPage;
import org.congcong.algomentor.api.problem.service.ProblemService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = ProblemController.class)
@Import(ProblemExceptionHandler.class)
class ProblemControllerTest {

  @Autowired
  private MockMvc mockMvc;

  @MockBean
  private ProblemService problemService;

  @Test
  void listProblemsReturnsPageEnvelope() throws Exception {
    when(problemService.findProblems(any())).thenReturn(new ProblemPage<>(List.of(
        new ProblemListItem("two-sum", 1, "Two Sum", "两数之和", ProblemDifficulty.EASY, List.of("Array"))
    ), 1, 1, 20));

    mockMvc.perform(get("/api/problems")
            .param("keyword", "sum")
            .param("difficulty", "easy")
            .param("tag", "Array")
            .param("page", "1")
            .param("pageSize", "20"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.total").value(1))
        .andExpect(jsonPath("$.data.items[0].slug").value("two-sum"))
        .andExpect(jsonPath("$.data.items[0].difficulty").value("EASY"));
  }

  @Test
  void getProblemReturnsDetail() throws Exception {
    when(problemService.findProblemBySlug(eq("two-sum"))).thenReturn(Optional.of(new ProblemDetail(
        "two-sum",
        1,
        "Two Sum",
        "两数之和",
        ProblemDifficulty.EASY,
        List.of("Array"),
        "# Two Sum",
        "https://leetcode.com/problems/two-sum/",
        "[2,7]\n9",
        "class Solution:\n    pass",
        "abc123")));

    mockMvc.perform(get("/api/problems/two-sum"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.slug").value("two-sum"))
        .andExpect(jsonPath("$.data.contentMarkdown").value("# Two Sum"))
        .andExpect(jsonPath("$.data.python3Template").value("class Solution:\n    pass"));
  }

  @Test
  void getProblemReturns404ForUnknownSlug() throws Exception {
    when(problemService.findProblemBySlug(eq("missing"))).thenReturn(Optional.empty());

    mockMvc.perform(get("/api/problems/missing"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.error.code").value(ProblemExceptionHandler.PROBLEM_NOT_FOUND_CODE));
  }
}
