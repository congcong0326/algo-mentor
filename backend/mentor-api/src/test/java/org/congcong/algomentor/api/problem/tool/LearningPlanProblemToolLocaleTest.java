package org.congcong.algomentor.api.problem.tool;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import java.util.List;
import java.util.Map;
import org.congcong.algomentor.agent.core.AgentExecutionContext;
import org.congcong.algomentor.api.problem.model.ProblemFilters;
import org.congcong.algomentor.api.problem.model.ProblemListRequest;
import org.congcong.algomentor.api.problem.model.ProblemLocale;
import org.congcong.algomentor.api.problem.model.ProblemPage;
import org.congcong.algomentor.api.problem.service.ProblemService;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanDraftMetadataKeys;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class LearningPlanProblemToolLocaleTest {

  @Test
  void listFiltersUsesTrustedPlanLocaleInsteadOfModelArgument() {
    ProblemService problemService = mock(ProblemService.class);
    when(problemService.findProblemFilters(ProblemLocale.EN_US))
        .thenReturn(new ProblemFilters(0, List.of(), List.of(), List.of(), List.of(), List.of(), List.of()));
    var arguments = JsonNodeFactory.instance.objectNode().put(ProblemAgentToolNames.LOCALE, "zh-CN");

    new ListProblemFiltersTool(problemService).execute(arguments, context("en-US"));

    verify(problemService).findProblemFilters(ProblemLocale.EN_US);
  }

  @Test
  void searchUsesTrustedPlanLocaleInsteadOfModelArgument() {
    ProblemService problemService = mock(ProblemService.class);
    when(problemService.findProblems(any())).thenReturn(new ProblemPage<>(List.of(), 0, 1, 20));
    var arguments = JsonNodeFactory.instance.objectNode().put(ProblemAgentToolNames.LOCALE, "zh-CN");

    new SearchProblemsTool(problemService).execute(arguments, context("en-US"));

    ArgumentCaptor<ProblemListRequest> request = ArgumentCaptor.forClass(ProblemListRequest.class);
    verify(problemService).findProblems(request.capture());
    assertThat(request.getValue().locale()).isEqualTo(ProblemLocale.EN_US);
  }

  private AgentExecutionContext context(String contentLocale) {
    return new AgentExecutionContext(
        "run-1",
        1,
        Map.of(LearningPlanDraftMetadataKeys.CONTENT_LOCALE, contentLocale),
        false);
  }
}
