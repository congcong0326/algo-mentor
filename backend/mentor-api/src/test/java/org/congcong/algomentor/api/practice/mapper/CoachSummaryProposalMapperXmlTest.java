package org.congcong.algomentor.api.practice.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.Reader;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;

class CoachSummaryProposalMapperXmlTest {

  @Test
  void loadsCoachSummaryProposalStatements() throws Exception {
    Configuration configuration = new Configuration();
    String resource = "mapper/practice/CoachSummaryProposalMapper.xml";
    try (Reader reader = Resources.getResourceAsReader(resource)) {
      new XMLMapperBuilder(reader, configuration, resource, configuration.getSqlFragments()).parse();
    }

    assertThat(configuration.hasStatement(CoachSummaryProposalMapper.class.getName() + ".lockScope")).isTrue();
    assertThat(configuration.hasStatement(CoachSummaryProposalMapper.class.getName() + ".findBySource")).isTrue();
    assertThat(configuration.hasStatement(CoachSummaryProposalMapper.class.getName() + ".supersedePending")).isTrue();
    assertThat(configuration.hasStatement(CoachSummaryProposalMapper.class.getName() + ".insert")).isTrue();
    assertThat(configuration.hasStatement(CoachSummaryProposalMapper.class.getName() + ".findForUpdate")).isTrue();
    assertThat(configuration.hasStatement(CoachSummaryProposalMapper.class.getName() + ".markApplied")).isTrue();
    assertThat(configuration.hasStatement(CoachSummaryProposalMapper.class.getName() + ".markSuperseded")).isTrue();
    assertThat(configuration.hasStatement(CoachSummaryProposalMapper.class.getName() + ".findMessageActions")).isTrue();
  }
}
