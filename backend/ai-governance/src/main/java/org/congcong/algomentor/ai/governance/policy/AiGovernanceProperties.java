package org.congcong.algomentor.ai.governance.policy;

import java.time.Duration;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.EnumMap;
import org.congcong.algomentor.ai.governance.model.AiPurpose;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "algo-mentor.ai-governance")
public class AiGovernanceProperties {

  private boolean enabled = true;
  private ZoneId quotaZone = ZoneOffset.UTC;
  private Duration activeRunTtl = Duration.ofMinutes(30);
  private EnumMap<AiPurpose, PurposeProperties> purposes = defaultPurposeProperties();

  public boolean isEnabled() {
    return enabled;
  }

  public void setEnabled(boolean enabled) {
    this.enabled = enabled;
  }

  public ZoneId getQuotaZone() {
    return quotaZone;
  }

  public void setQuotaZone(ZoneId quotaZone) {
    this.quotaZone = quotaZone == null ? ZoneOffset.UTC : quotaZone;
  }

  public Duration getActiveRunTtl() {
    return activeRunTtl;
  }

  public void setActiveRunTtl(Duration activeRunTtl) {
    this.activeRunTtl = activeRunTtl == null ? Duration.ofMinutes(30) : activeRunTtl;
  }

  public EnumMap<AiPurpose, PurposeProperties> getPurposes() {
    return purposes;
  }

  public void setPurposes(EnumMap<AiPurpose, PurposeProperties> purposes) {
    EnumMap<AiPurpose, PurposeProperties> defaults = defaultPurposeProperties();
    if (purposes != null) {
      purposes.forEach((purpose, properties) -> {
        if (purpose != null && properties != null) {
          defaults.put(purpose, properties);
        }
      });
    }
    this.purposes = defaults;
  }

  private static EnumMap<AiPurpose, PurposeProperties> defaultPurposeProperties() {
    EnumMap<AiPurpose, PurposeProperties> defaults = new EnumMap<>(AiPurpose.class);
    defaults.put(AiPurpose.LEARNING_PLAN, new PurposeProperties(
        true, 50, 1, 32768, 4096, 12, false, true, true, false,
        null, null, "learning-plan-p0"));
    defaults.put(AiPurpose.PROBLEM_EXPLANATION, new PurposeProperties(
        true, 50, 1, 32768, 2048, 8, true, true, false, false,
        null, null, "problem-explanation-p0"));
    defaults.put(AiPurpose.LEARNING_CHAT, new PurposeProperties(
        true, 50, 1, 16384, 2048, 8, true, true, false, false,
        null, null, "learning-chat-p0"));
    return defaults;
  }

  public static class PurposeProperties {

    private boolean enabled;
    private int dailyRequestLimit;
    private int maxConcurrentRunsPerUser;
    private int maxRequestBytes;
    private int maxOutputTokens;
    private int maxSteps;
    private boolean streamingAllowed;
    private boolean toolsAllowed;
    private boolean structuredOutputRequired;
    private boolean adminOnly;
    private String defaultProvider;
    private String defaultModel;
    private String systemPolicyVersion;

    public PurposeProperties() {
    }

    public PurposeProperties(
        boolean enabled,
        int dailyRequestLimit,
        int maxConcurrentRunsPerUser,
        int maxRequestBytes,
        int maxOutputTokens,
        int maxSteps,
        boolean streamingAllowed,
        boolean toolsAllowed,
        boolean structuredOutputRequired,
        boolean adminOnly,
        String defaultProvider,
        String defaultModel,
        String systemPolicyVersion) {
      this.enabled = enabled;
      this.dailyRequestLimit = dailyRequestLimit;
      this.maxConcurrentRunsPerUser = maxConcurrentRunsPerUser;
      this.maxRequestBytes = maxRequestBytes;
      this.maxOutputTokens = maxOutputTokens;
      this.maxSteps = maxSteps;
      this.streamingAllowed = streamingAllowed;
      this.toolsAllowed = toolsAllowed;
      this.structuredOutputRequired = structuredOutputRequired;
      this.adminOnly = adminOnly;
      this.defaultProvider = defaultProvider;
      this.defaultModel = defaultModel;
      this.systemPolicyVersion = systemPolicyVersion;
    }

    public boolean isEnabled() {
      return enabled;
    }

    public void setEnabled(boolean enabled) {
      this.enabled = enabled;
    }

    public int getDailyRequestLimit() {
      return dailyRequestLimit;
    }

    public void setDailyRequestLimit(int dailyRequestLimit) {
      this.dailyRequestLimit = dailyRequestLimit;
    }

    public int getMaxConcurrentRunsPerUser() {
      return maxConcurrentRunsPerUser;
    }

    public void setMaxConcurrentRunsPerUser(int maxConcurrentRunsPerUser) {
      this.maxConcurrentRunsPerUser = maxConcurrentRunsPerUser;
    }

    public int getMaxRequestBytes() {
      return maxRequestBytes;
    }

    public void setMaxRequestBytes(int maxRequestBytes) {
      this.maxRequestBytes = maxRequestBytes;
    }

    public int getMaxOutputTokens() {
      return maxOutputTokens;
    }

    public void setMaxOutputTokens(int maxOutputTokens) {
      this.maxOutputTokens = maxOutputTokens;
    }

    public int getMaxSteps() {
      return maxSteps;
    }

    public void setMaxSteps(int maxSteps) {
      this.maxSteps = maxSteps;
    }

    public boolean isStreamingAllowed() {
      return streamingAllowed;
    }

    public void setStreamingAllowed(boolean streamingAllowed) {
      this.streamingAllowed = streamingAllowed;
    }

    public boolean isToolsAllowed() {
      return toolsAllowed;
    }

    public void setToolsAllowed(boolean toolsAllowed) {
      this.toolsAllowed = toolsAllowed;
    }

    public boolean isStructuredOutputRequired() {
      return structuredOutputRequired;
    }

    public void setStructuredOutputRequired(boolean structuredOutputRequired) {
      this.structuredOutputRequired = structuredOutputRequired;
    }

    public boolean isAdminOnly() {
      return adminOnly;
    }

    public void setAdminOnly(boolean adminOnly) {
      this.adminOnly = adminOnly;
    }

    public String getDefaultProvider() {
      return defaultProvider;
    }

    public void setDefaultProvider(String defaultProvider) {
      this.defaultProvider = defaultProvider;
    }

    public String getDefaultModel() {
      return defaultModel;
    }

    public void setDefaultModel(String defaultModel) {
      this.defaultModel = defaultModel;
    }

    public String getSystemPolicyVersion() {
      return systemPolicyVersion;
    }

    public void setSystemPolicyVersion(String systemPolicyVersion) {
      this.systemPolicyVersion = systemPolicyVersion;
    }

    AiPurposePolicy toPolicy() {
      return new AiPurposePolicy(
          enabled,
          dailyRequestLimit,
          maxConcurrentRunsPerUser,
          maxRequestBytes,
          maxOutputTokens,
          maxSteps,
          streamingAllowed,
          toolsAllowed,
          structuredOutputRequired,
          adminOnly,
          defaultProvider,
          defaultModel,
          systemPolicyVersion);
    }
  }
}
