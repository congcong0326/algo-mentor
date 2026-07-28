package org.congcong.algomentor.api.controller.learningplan;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanDifficultyPreference;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanIntent;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanLevel;
import org.congcong.algomentor.mentor.application.learningplan.template.LearningPlanTemplate;
import org.congcong.algomentor.mentor.application.learningplan.template.LearningPlanTemplateCatalogCategory;
import org.congcong.algomentor.mentor.application.learningplan.template.LearningPlanTemplateDraftService;
import org.congcong.algomentor.mentor.application.learningplan.template.LearningPlanTemplatePhase;
import org.congcong.algomentor.mentor.application.learningplan.template.LearningPlanTemplateProblemRef;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = LearningPlanTemplateController.class)
@AutoConfigureMockMvc(addFilters = false)
class LearningPlanTemplateControllerTest {

  @Autowired
  private MockMvc mockMvc;

  @MockBean
  private LearningPlanTemplateDraftService templateDraftService;

  @Test
  void listTemplatesReturnsSummaries() throws Exception {
    when(templateDraftService.listTemplates()).thenReturn(List.of(template()));

    mockMvc.perform(get("/api/learning-plan-templates"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data[0].templateId").value("neetcode_blind_75_interview_core"))
        .andExpect(jsonPath("$.data[0].catalogCategory").value("INTERVIEW_PREP"))
        .andExpect(jsonPath("$.data[0].recommendedOrder").value(1))
        .andExpect(jsonPath("$.data[0].programmingLanguage").value("Java"))
        .andExpect(jsonPath("$.data[0].defaultDurationWeeks").value(4))
        .andExpect(jsonPath("$.data[0].plannedProblemCount").value(69))
        .andExpect(jsonPath("$.data[0].sourceCommit").doesNotExist())
        .andExpect(jsonPath("$.data[0].problemCount").doesNotExist())
        .andExpect(jsonPath("$.data[0].matchedProblemCount").doesNotExist())
        .andExpect(jsonPath("$.data[0].missingProblemCount").doesNotExist())
        .andExpect(jsonPath("$.data[0].defaultLoadSummary.plannedProblemCount").value(1))
        .andExpect(jsonPath("$.data[0].defaultRhythmSettings.dailyProblemCount").value(4))
        .andExpect(jsonPath("$.data[0].defaultRhythmSettings.trainingDaysPerWeek").value(5))
        .andExpect(jsonPath("$.data[0].defaultRhythmSettings.estimatedRemainingWeeks").value(4));
  }

  @Test
  void getTemplateReturnsPhaseAndProblemRefs() throws Exception {
    when(templateDraftService.getTemplate("neetcode_blind_75_interview_core")).thenReturn(template());

    mockMvc.perform(get("/api/learning-plan-templates/neetcode_blind_75_interview_core"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.templateId").value("neetcode_blind_75_interview_core"))
        .andExpect(jsonPath("$.data.sourceName").value("neetcode-gh/leetcode"))
        .andExpect(jsonPath("$.data.plannedProblemCount").value(69))
        .andExpect(jsonPath("$.data.sourceCommit").doesNotExist())
        .andExpect(jsonPath("$.data.sourceDataPath").doesNotExist())
        .andExpect(jsonPath("$.data.metadata").doesNotExist())
        .andExpect(jsonPath("$.data.problemCount").doesNotExist())
        .andExpect(jsonPath("$.data.matchedProblemCount").doesNotExist())
        .andExpect(jsonPath("$.data.missingProblemCount").doesNotExist())
        .andExpect(jsonPath("$.data.defaultLoadSummary.intensity").exists())
        .andExpect(jsonPath("$.data.defaultRhythmSettings.remainingProblemCount").value(69))
        .andExpect(jsonPath("$.data.defaultRhythmSettings.completedProblemCount").value(0))
        .andExpect(jsonPath("$.data.phases[0].phaseIndex").value(1))
        .andExpect(jsonPath("$.data.phases[0].plannedProblemCount").value(1))
        .andExpect(jsonPath("$.data.phases[0].problemRefs").doesNotExist());
  }

  private LearningPlanTemplate template() {
    return new LearningPlanTemplate(
        1L,
        "neetcode_blind_75_interview_core",
        "Blind 75",
        "summary",
        LearningPlanTemplateCatalogCategory.INTERVIEW_PREP,
        1,
        LearningPlanIntent.INTERVIEW_SPRINT,
        "goal",
        4,
        LearningPlanLevel.INTERMEDIATE,
        8,
        "Java",
        LearningPlanDifficultyPreference.MEDIUM,
        true,
        List.of("Array"),
        "audience",
        Map.of("Easy", Map.of("count", 1)),
        List.of("basic"),
        List.of("interview"),
        List.of("zero"),
        "outcome",
        "neetcode-gh/leetcode",
        "https://github.com/neetcode-gh/leetcode",
        "9907b7fed441fa55083c0751e208b7197101dbba",
        ".problemSiteData.json",
        "source",
        "notes",
        "MIT metadata only",
        75,
        69,
        6,
        Map.of(),
        List.of(new LearningPlanTemplatePhase(
            10L,
            1,
            "phase",
            1,
            "focus",
            List.of("objective"),
            List.of("Array"),
            List.of("done"),
            "review",
            List.of(new LearningPlanTemplateProblemRef(
                100L,
                1,
                1,
                1,
                "two-sum",
                "Two Sum",
                "Easy",
                "Arrays & Hashing",
                "https://neetcode.io/problems/two-sum",
                true,
                Map.of())))));
  }
}
