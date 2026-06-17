package org.congcong.algomentor.api.controller.problem;

import org.congcong.algomentor.common.api.ApiResponse;
import org.congcong.algomentor.api.problem.service.ProblemService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = ProblemController.class)
public class ProblemExceptionHandler {

  public static final String PROBLEM_NOT_FOUND_CODE = "PROBLEM_NOT_FOUND";

  @ExceptionHandler(ProblemController.ProblemNotFoundException.class)
  @ResponseStatus(HttpStatus.NOT_FOUND)
  public ApiResponse<Void> problemNotFound(ProblemController.ProblemNotFoundException exception) {
    return ApiResponse.failure(PROBLEM_NOT_FOUND_CODE, exception.getMessage());
  }

  @ExceptionHandler(ProblemService.ProblemRepositoryUnavailableException.class)
  @ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
  public ApiResponse<Void> repositoryUnavailable(ProblemService.ProblemRepositoryUnavailableException exception) {
    return ApiResponse.failure("PROBLEM_REPOSITORY_UNAVAILABLE", exception.getMessage());
  }
}
