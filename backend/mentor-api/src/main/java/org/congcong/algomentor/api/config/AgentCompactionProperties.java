package org.congcong.algomentor.api.config;

import org.congcong.algomentor.agent.core.compaction.ToolResultCompactionPolicy;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = MentorConfigurationKeys.AGENT_COMPACTION_PREFIX)
public class AgentCompactionProperties {

  private int inlineMaxChars = 12_000;
  private int previewMaxChars = 2_000;
  private int rangeReadMaxChars = 8_000;
  private boolean blobEnabled = true;
  private int toolResultsTotalMaxChars = 60_000;
  private int keepRecentToolResults = 3;
  private boolean compactOldToolResults = true;
  private int inputTokenBudget = 120_000;
  private int maxMessageGroups = 80;
  private int snipKeepHeadGroups = 2;
  private int snipKeepTailGroups = 24;
  private boolean groupAwareSnipEnabled = true;
  private boolean llmCompactEnabled = false;

  public ToolResultCompactionPolicy toPolicy() {
    return new ToolResultCompactionPolicy(
        inlineMaxChars,
        previewMaxChars,
        rangeReadMaxChars,
        blobEnabled,
        toolResultsTotalMaxChars,
        keepRecentToolResults,
        compactOldToolResults,
        inputTokenBudget,
        maxMessageGroups,
        snipKeepHeadGroups,
        snipKeepTailGroups,
        groupAwareSnipEnabled,
        llmCompactEnabled);
  }

  public int getInlineMaxChars() {
    return inlineMaxChars;
  }

  public void setInlineMaxChars(int inlineMaxChars) {
    this.inlineMaxChars = inlineMaxChars;
  }

  public int getPreviewMaxChars() {
    return previewMaxChars;
  }

  public void setPreviewMaxChars(int previewMaxChars) {
    this.previewMaxChars = previewMaxChars;
  }

  public int getRangeReadMaxChars() {
    return rangeReadMaxChars;
  }

  public void setRangeReadMaxChars(int rangeReadMaxChars) {
    this.rangeReadMaxChars = rangeReadMaxChars;
  }

  public boolean isBlobEnabled() {
    return blobEnabled;
  }

  public void setBlobEnabled(boolean blobEnabled) {
    this.blobEnabled = blobEnabled;
  }

  public int getToolResultsTotalMaxChars() {
    return toolResultsTotalMaxChars;
  }

  public void setToolResultsTotalMaxChars(int toolResultsTotalMaxChars) {
    this.toolResultsTotalMaxChars = toolResultsTotalMaxChars;
  }

  public int getKeepRecentToolResults() {
    return keepRecentToolResults;
  }

  public void setKeepRecentToolResults(int keepRecentToolResults) {
    this.keepRecentToolResults = keepRecentToolResults;
  }

  public boolean isCompactOldToolResults() {
    return compactOldToolResults;
  }

  public void setCompactOldToolResults(boolean compactOldToolResults) {
    this.compactOldToolResults = compactOldToolResults;
  }

  public int getInputTokenBudget() {
    return inputTokenBudget;
  }

  public void setInputTokenBudget(int inputTokenBudget) {
    this.inputTokenBudget = inputTokenBudget;
  }

  public int getMaxMessageGroups() {
    return maxMessageGroups;
  }

  public void setMaxMessageGroups(int maxMessageGroups) {
    this.maxMessageGroups = maxMessageGroups;
  }

  public int getSnipKeepHeadGroups() {
    return snipKeepHeadGroups;
  }

  public void setSnipKeepHeadGroups(int snipKeepHeadGroups) {
    this.snipKeepHeadGroups = snipKeepHeadGroups;
  }

  public int getSnipKeepTailGroups() {
    return snipKeepTailGroups;
  }

  public void setSnipKeepTailGroups(int snipKeepTailGroups) {
    this.snipKeepTailGroups = snipKeepTailGroups;
  }

  public boolean isGroupAwareSnipEnabled() {
    return groupAwareSnipEnabled;
  }

  public void setGroupAwareSnipEnabled(boolean groupAwareSnipEnabled) {
    this.groupAwareSnipEnabled = groupAwareSnipEnabled;
  }

  public boolean isLlmCompactEnabled() {
    return llmCompactEnabled;
  }

  public void setLlmCompactEnabled(boolean llmCompactEnabled) {
    this.llmCompactEnabled = llmCompactEnabled;
  }
}
