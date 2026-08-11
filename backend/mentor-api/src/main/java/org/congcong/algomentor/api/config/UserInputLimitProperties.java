package org.congcong.algomentor.api.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.unit.DataSize;
import org.springframework.validation.annotation.Validated;

/**
 * 用户主动提交内容的统一数量上限。
 *
 * <p>这里只管理可调数值；枚举、白名单和必填规则仍由接口契约负责。</p>
 */
@Validated
@ConfigurationProperties(prefix = MentorConfigurationKeys.USER_INPUT_LIMITS_PREFIX)
public class UserInputLimitProperties {

  private static final int ABSOLUTE_TEXT_MAX_CHARS = 10_000;
  private static final long ABSOLUTE_REQUEST_MAX_BYTES = 64 * 1024L;

  @Valid
  @NotNull
  private ReviewNote reviewNote = new ReviewNote();

  @Valid
  @NotNull
  private LearningPlanCreate learningPlanCreate = new LearningPlanCreate();

  @Valid
  @NotNull
  private PracticeMessage practiceMessage = new PracticeMessage();

  @AssertTrue(message = "User input request limits must not exceed 64 KiB")
  public boolean isRequestSizeWithinAbsoluteLimit() {
    return reviewNote != null
        && learningPlanCreate != null
        && practiceMessage != null
        && withinAbsoluteRequestLimit(reviewNote.requestMaxBytes)
        && withinAbsoluteRequestLimit(learningPlanCreate.requestMaxBytes)
        && withinAbsoluteRequestLimit(practiceMessage.requestMaxBytes)
        && withinAbsoluteRequestLimit(practiceMessage.messageMaxBytes);
  }

  @AssertTrue(message = "Practice request max bytes must exceed practice message max bytes")
  public boolean isPracticeRequestLargerThanMessage() {
    return practiceMessage != null
        && practiceMessage.requestMaxBytes != null
        && practiceMessage.messageMaxBytes != null
        && practiceMessage.requestMaxBytes.toBytes() > practiceMessage.messageMaxBytes.toBytes();
  }

  private boolean withinAbsoluteRequestLimit(DataSize value) {
    return value != null && value.toBytes() >= 1 && value.toBytes() <= ABSOLUTE_REQUEST_MAX_BYTES;
  }

  public ReviewNote getReviewNote() {
    return reviewNote;
  }

  public void setReviewNote(ReviewNote reviewNote) {
    this.reviewNote = reviewNote;
  }

  public LearningPlanCreate getLearningPlanCreate() {
    return learningPlanCreate;
  }

  public void setLearningPlanCreate(LearningPlanCreate learningPlanCreate) {
    this.learningPlanCreate = learningPlanCreate;
  }

  public PracticeMessage getPracticeMessage() {
    return practiceMessage;
  }

  public void setPracticeMessage(PracticeMessage practiceMessage) {
    this.practiceMessage = practiceMessage;
  }

  public static class ReviewNote {

    @Min(1)
    @Max(ABSOLUTE_TEXT_MAX_CHARS)
    private int coreIdeaMaxChars = 2_000;

    @Min(1)
    @Max(ABSOLUTE_TEXT_MAX_CHARS)
    private int dataStructureNotesMaxChars = 1_000;

    @Min(1)
    @Max(ABSOLUTE_TEXT_MAX_CHARS)
    private int algorithmNotesMaxChars = 1_000;

    @Min(1)
    @Max(ABSOLUTE_TEXT_MAX_CHARS)
    private int customItemMaxChars = 50;

    @Min(1)
    @Max(100)
    private int customItemMaxCount = 10;

    @Min(1)
    @Max(ABSOLUTE_TEXT_MAX_CHARS)
    private int customComplexityMaxChars = 100;

    @Min(1)
    @Max(ABSOLUTE_TEXT_MAX_CHARS)
    private int edgeCasesMaxChars = 1_000;

    @NotNull
    private DataSize requestMaxBytes = DataSize.ofKilobytes(32);

    public int getCoreIdeaMaxChars() {
      return coreIdeaMaxChars;
    }

    public void setCoreIdeaMaxChars(int coreIdeaMaxChars) {
      this.coreIdeaMaxChars = coreIdeaMaxChars;
    }

    public int getDataStructureNotesMaxChars() {
      return dataStructureNotesMaxChars;
    }

    public void setDataStructureNotesMaxChars(int dataStructureNotesMaxChars) {
      this.dataStructureNotesMaxChars = dataStructureNotesMaxChars;
    }

    public int getAlgorithmNotesMaxChars() {
      return algorithmNotesMaxChars;
    }

    public void setAlgorithmNotesMaxChars(int algorithmNotesMaxChars) {
      this.algorithmNotesMaxChars = algorithmNotesMaxChars;
    }

    public int getCustomItemMaxChars() {
      return customItemMaxChars;
    }

    public void setCustomItemMaxChars(int customItemMaxChars) {
      this.customItemMaxChars = customItemMaxChars;
    }

    public int getCustomItemMaxCount() {
      return customItemMaxCount;
    }

    public void setCustomItemMaxCount(int customItemMaxCount) {
      this.customItemMaxCount = customItemMaxCount;
    }

    public int getCustomComplexityMaxChars() {
      return customComplexityMaxChars;
    }

    public void setCustomComplexityMaxChars(int customComplexityMaxChars) {
      this.customComplexityMaxChars = customComplexityMaxChars;
    }

    public int getEdgeCasesMaxChars() {
      return edgeCasesMaxChars;
    }

    public void setEdgeCasesMaxChars(int edgeCasesMaxChars) {
      this.edgeCasesMaxChars = edgeCasesMaxChars;
    }

    public DataSize getRequestMaxBytes() {
      return requestMaxBytes;
    }

    public void setRequestMaxBytes(DataSize requestMaxBytes) {
      this.requestMaxBytes = requestMaxBytes;
    }
  }

  public static class LearningPlanCreate {

    @Min(1)
    @Max(ABSOLUTE_TEXT_MAX_CHARS)
    private int objectiveMaxChars = 300;

    @Min(1)
    @Max(ABSOLUTE_TEXT_MAX_CHARS)
    private int additionalConstraintsMaxChars = 1_000;

    @Min(1)
    @Max(520)
    private int durationWeeksMax = 52;

    @Min(1)
    @Max(168)
    private int weeklyHoursMax = 80;

    @NotNull
    private DataSize requestMaxBytes = DataSize.ofKilobytes(8);

    public int getObjectiveMaxChars() {
      return objectiveMaxChars;
    }

    public void setObjectiveMaxChars(int objectiveMaxChars) {
      this.objectiveMaxChars = objectiveMaxChars;
    }

    public int getAdditionalConstraintsMaxChars() {
      return additionalConstraintsMaxChars;
    }

    public void setAdditionalConstraintsMaxChars(int additionalConstraintsMaxChars) {
      this.additionalConstraintsMaxChars = additionalConstraintsMaxChars;
    }

    public int getDurationWeeksMax() {
      return durationWeeksMax;
    }

    public void setDurationWeeksMax(int durationWeeksMax) {
      this.durationWeeksMax = durationWeeksMax;
    }

    public int getWeeklyHoursMax() {
      return weeklyHoursMax;
    }

    public void setWeeklyHoursMax(int weeklyHoursMax) {
      this.weeklyHoursMax = weeklyHoursMax;
    }

    public DataSize getRequestMaxBytes() {
      return requestMaxBytes;
    }

    public void setRequestMaxBytes(DataSize requestMaxBytes) {
      this.requestMaxBytes = requestMaxBytes;
    }
  }

  public static class PracticeMessage {

    @NotNull
    private DataSize messageMaxBytes = DataSize.ofKilobytes(16);

    @NotNull
    private DataSize requestMaxBytes = DataSize.ofKilobytes(20);

    public DataSize getMessageMaxBytes() {
      return messageMaxBytes;
    }

    public void setMessageMaxBytes(DataSize messageMaxBytes) {
      this.messageMaxBytes = messageMaxBytes;
    }

    public DataSize getRequestMaxBytes() {
      return requestMaxBytes;
    }

    public void setRequestMaxBytes(DataSize requestMaxBytes) {
      this.requestMaxBytes = requestMaxBytes;
    }
  }
}
