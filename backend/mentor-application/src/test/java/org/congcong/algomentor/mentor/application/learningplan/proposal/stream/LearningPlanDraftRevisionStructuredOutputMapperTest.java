package org.congcong.algomentor.mentor.application.learningplan.proposal.stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanException;
import org.junit.jupiter.api.Test;

class LearningPlanDraftRevisionStructuredOutputMapperTest {

  @Test
  void mapsCompiledArtifactReference() throws Exception {
    ObjectMapper objectMapper = new ObjectMapper();
    LearningPlanDraftRevisionStructuredOutputMapper mapper = new LearningPlanDraftRevisionStructuredOutputMapper();

    LearningPlanDraftRevisionOutput output = mapper.map(objectMapper.readTree("""
        {
          "status": "COMPILED",
          "artifactRef": "  draft-revision:42:compiled  "
        }
        """));

    assertThat(output.status()).isEqualTo("COMPILED");
    assertThat(output.artifactRef()).isEqualTo("draft-revision:42:compiled");
  }

  @Test
  void rejectsOutputThatDidNotCompileAnArtifact() throws Exception {
    ObjectMapper objectMapper = new ObjectMapper();
    LearningPlanDraftRevisionStructuredOutputMapper mapper = new LearningPlanDraftRevisionStructuredOutputMapper();

    assertThatThrownBy(() -> mapper.map(objectMapper.readTree("""
        {"status":"NEEDS_REVISION","artifactRef":"draft-revision:42:compiled"}
        """)))
        .isInstanceOf(LearningPlanException.class)
        .hasMessage("学习计划修订结构化结果解析失败。");
  }
}
