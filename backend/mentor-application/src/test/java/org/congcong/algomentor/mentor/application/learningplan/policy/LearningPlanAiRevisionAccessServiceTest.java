package org.congcong.algomentor.mentor.application.learningplan.policy;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;
import java.util.ArrayList;
import java.util.List;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanException;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanDraftSource;
import org.junit.jupiter.api.Test;

class LearningPlanAiRevisionAccessServiceTest {
  @Test
  void mapsDraftSourceToIndependentCapabilitiesAndRecordsDenial() {
    LearningPlanAiRevisionPolicyResolver resolver = ignored -> new LearningPlanAiRevisionCapabilities(true, false, false);
    List<String> records = new ArrayList<>();
    LearningPlanAiRevisionAccessMetrics metrics = (action, outcome) -> records.add(action + ":" + outcome);
    LearningPlanAiRevisionAccessService service = new LearningPlanAiRevisionAccessService(resolver, metrics);

    service.requireDraftRevision(7L, LearningPlanDraftSource.TEMPLATE);
    assertThatThrownBy(() -> service.requireDraftRevision(7L, LearningPlanDraftSource.AI_PERSONALIZED))
        .isInstanceOf(LearningPlanException.class);
    assertThat(records).contains("PERSONALIZED_DRAFT_REVISION:DENIED");
  }
}
