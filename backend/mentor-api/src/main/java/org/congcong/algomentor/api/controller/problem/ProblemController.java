package org.congcong.algomentor.api.controller.problem;

import org.congcong.algomentor.api.config.ApiContractConstants;
import org.congcong.algomentor.api.problem.model.ProblemDetail;
import org.congcong.algomentor.api.problem.model.ProblemDifficulty;
import org.congcong.algomentor.api.problem.model.ProblemListItem;
import org.congcong.algomentor.api.problem.model.ProblemListRequest;
import org.congcong.algomentor.api.problem.model.ProblemPage;
import org.congcong.algomentor.api.problem.model.ProblemSort;
import org.congcong.algomentor.api.problem.service.ProblemService;
import org.congcong.algomentor.common.api.ApiResponse;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(ApiContractConstants.PROBLEMS_BASE_PATH)
public class ProblemController {

  private final ProblemService problemService;

  public ProblemController(ProblemService problemService) {
    this.problemService = problemService;
  }

  @GetMapping
  public ApiResponse<ProblemPage<ProblemListItem>> listProblems(
      @RequestParam(required = false) String keyword,
      @RequestParam(required = false) String difficulty,
      @RequestParam(required = false) String tag,
      @RequestParam(required = false) String category,
      @RequestParam(required = false) String sort,
      @RequestParam(defaultValue = "1") int page,
      @RequestParam(defaultValue = "20") int pageSize
  ) {
    ProblemListRequest request = new ProblemListRequest(
        keyword,
        ProblemDifficulty.parse(difficulty),
        tag,
        category,
        ProblemSort.parse(sort),
        page,
        pageSize);
    return ApiResponse.success(problemService.findProblems(request));
  }

  @GetMapping("/{slug}")
  public ApiResponse<ProblemDetail> getProblem(@PathVariable String slug) {
    return problemService.findProblemBySlug(slug)
        .map(ApiResponse::success)
        .orElseThrow(() -> new ProblemNotFoundException(slug));
  }

  @ResponseStatus(HttpStatus.NOT_FOUND)
  static class ProblemNotFoundException extends RuntimeException {
    ProblemNotFoundException(String slug) {
      super("Problem not found: " + slug);
    }
  }
}
