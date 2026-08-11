package org.congcong.algomentor.api.config;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;
import org.springframework.util.unit.DataSize;

class UserInputLimitPropertiesTest {

  private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

  @Test
  void defaultsAreValid() {
    assertThat(validator.validate(new UserInputLimitProperties())).isEmpty();
  }

  @Test
  void rejectsConfigurationThatDisablesAbsoluteSafetyCeilings() {
    UserInputLimitProperties properties = new UserInputLimitProperties();
    properties.getReviewNote().setRequestMaxBytes(DataSize.ofKilobytes(65));

    assertThat(validator.validate(properties))
        .extracting(violation -> violation.getPropertyPath().toString())
        .contains("requestSizeWithinAbsoluteLimit");
  }

  @Test
  void practiceRequestMustLeaveRoomForJsonEnvelope() {
    UserInputLimitProperties properties = new UserInputLimitProperties();
    properties.getPracticeMessage().setRequestMaxBytes(DataSize.ofKilobytes(16));

    assertThat(validator.validate(properties))
        .extracting(violation -> violation.getPropertyPath().toString())
        .contains("practiceRequestLargerThanMessage");
  }
}
