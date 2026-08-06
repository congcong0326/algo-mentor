package org.congcong.algomentor.api.controller.learningplan;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanContentLocale;
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
    when(templateDraftService.listTemplates(LearningPlanContentLocale.ZH_CN)).thenReturn(List.of(template()));

    mockMvc.perform(get("/api/learning-plan-templates"))
        .andExpect(status().isOk())
        .andExpect(header().string("Vary", "Accept-Language"))
        .andExpect(jsonPath("$.data[0].templateId").value("neetcode_blind_75_interview_core"))
        .andExpect(jsonPath("$.data[0].contentLocale").value("zh-CN"))
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
    when(templateDraftService.getTemplate(
        "neetcode_blind_75_interview_core",
        LearningPlanContentLocale.ZH_CN)).thenReturn(template());

    mockMvc.perform(get("/api/learning-plan-templates/neetcode_blind_75_interview_core"))
        .andExpect(status().isOk())
        .andExpect(header().string("Vary", "Accept-Language"))
        .andExpect(jsonPath("$.data.templateId").value("neetcode_blind_75_interview_core"))
        .andExpect(jsonPath("$.data.contentLocale").value("zh-CN"))
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

  @Test
  void getTemplateReturnsOneCompleteEnglishBundle() throws Exception {
    when(templateDraftService.getTemplate(
        "neetcode_blind_75_interview_core",
        LearningPlanContentLocale.EN_US)).thenReturn(bilingualTemplate(true));

    mockMvc.perform(get("/api/learning-plan-templates/neetcode_blind_75_interview_core")
            .header("Accept-Language", "en-US,en;q=0.9"))
        .andExpect(status().isOk())
        .andExpect(header().string("Vary", "Accept-Language"))
        .andExpect(jsonPath("$.data.contentLocale").value("en-US"))
        .andExpect(jsonPath("$.data.title").value("Blind 75 English"))
        .andExpect(jsonPath("$.data.summary").value("English summary"))
        .andExpect(jsonPath("$.data.goal").value("English goal"))
        .andExpect(jsonPath("$.data.targetAudience").value("English audience"))
        .andExpect(jsonPath("$.data.prerequisites[0]").value("English prerequisite"))
        .andExpect(jsonPath("$.data.recommendedFor[0]").value("English recommendation"))
        .andExpect(jsonPath("$.data.notRecommendedFor[0]").value("English exclusion"))
        .andExpect(jsonPath("$.data.expectedOutcome").value("English outcome"))
        .andExpect(jsonPath("$.data.phases[0].title").value("English phase"))
        .andExpect(jsonPath("$.data.phases[0].focus").value("English focus"));
  }

  @Test
  void englishRequestFallsBackToOneCompleteChineseBundleWhenEnglishIsNotReady() throws Exception {
    when(templateDraftService.getTemplate(
        "neetcode_blind_75_interview_core",
        LearningPlanContentLocale.EN_US)).thenReturn(bilingualTemplate(false));

    mockMvc.perform(get("/api/learning-plan-templates/neetcode_blind_75_interview_core")
            .header("Accept-Language", "en-US"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.contentLocale").value("zh-CN"))
        .andExpect(jsonPath("$.data.title").value("Blind 75 中文"))
        .andExpect(jsonPath("$.data.summary").value("中文摘要"))
        .andExpect(jsonPath("$.data.goal").value("中文目标"))
        .andExpect(jsonPath("$.data.phases[0].title").value("中文阶段"))
        .andExpect(jsonPath("$.data.phases[0].focus").value("中文重点"));
  }

  @Test
  void unknownAcceptLanguageUsesChineseCatalog() throws Exception {
    when(templateDraftService.listTemplates(LearningPlanContentLocale.ZH_CN))
        .thenReturn(List.of(bilingualTemplate(true)));

    mockMvc.perform(get("/api/learning-plan-templates").header("Accept-Language", "fr-FR"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data[0].contentLocale").value("zh-CN"))
        .andExpect(jsonPath("$.data[0].title").value("Blind 75 中文"));
  }

  private LearningPlanTemplate template() {
    return new LearningPlanTemplate(
        1L,
        "neetcode_blind_75_interview_core",
        "Blind 75",null,
        "summary",null,
        LearningPlanTemplateCatalogCategory.INTERVIEW_PREP,
        1,
        LearningPlanIntent.INTERVIEW_SPRINT,
        "goal",null,
        4,
        LearningPlanLevel.INTERMEDIATE,
        8,
        "Java",
        LearningPlanDifficultyPreference.MEDIUM,
        List.of("Array"),
        "audience",null,
        List.of("basic"),List.of(),
        List.of("interview"),List.of(),
        List.of("zero"),List.of(),
        "outcome",null,false,
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
            "phase",null,
            1,
            "focus",null,
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

  private LearningPlanTemplate bilingualTemplate(boolean englishContentReady) {
    return new LearningPlanTemplate(
        1L,
        "neetcode_blind_75_interview_core",
        "Blind 75 中文",
        "Blind 75 English",
        "中文摘要",
        "English summary",
        LearningPlanTemplateCatalogCategory.INTERVIEW_PREP,
        1,
        LearningPlanIntent.INTERVIEW_SPRINT,
        "中文目标",
        "English goal",
        4,
        LearningPlanLevel.INTERMEDIATE,
        8,
        "Java",
        LearningPlanDifficultyPreference.MEDIUM,
        List.of("Array"),
        "中文受众",
        "English audience",
        List.of("中文前置"),
        List.of("English prerequisite"),
        List.of("中文推荐"),
        List.of("English recommendation"),
        List.of("中文不推荐"),
        List.of("English exclusion"),
        "中文结果",
        "English outcome",
        englishContentReady,
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
            "中文阶段",
            "English phase",
            1,
            "中文重点",
            "English focus",
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
