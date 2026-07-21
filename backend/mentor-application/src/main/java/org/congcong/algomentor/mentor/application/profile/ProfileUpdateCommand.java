package org.congcong.algomentor.mentor.application.profile;

/** 服务端受信的画像版本切换命令。 */
public record ProfileUpdateCommand(
    LearnerProfileIdentity identity,
    ProfileUpdateDecision decision,
    String expectedSnapshotToken,
    LearnerProfileOriginType originType,
    String modelProvider,
    String modelName,
    String promptVersion
) {
  public ProfileUpdateCommand {
    if (identity == null || decision == null || expectedSnapshotToken == null || expectedSnapshotToken.isBlank()
        || originType == null) {
      throw new IllegalArgumentException("Invalid profile update command");
    }
  }
}
