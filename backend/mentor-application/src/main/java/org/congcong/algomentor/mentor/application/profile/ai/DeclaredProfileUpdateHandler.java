package org.congcong.algomentor.mentor.application.profile.ai;

import org.congcong.algomentor.mentor.application.profile.tool.DeclaredProfileUpdateRequest;
import org.congcong.algomentor.mentor.application.profile.tool.DeclaredProfileUpdateResult;

/** 用户自述更新工具调用的应用层边界，身份和父 run 均由调用端受信上下文提供。 */
@FunctionalInterface
public interface DeclaredProfileUpdateHandler {

  DeclaredProfileUpdateResult update(
      long userId,
      DeclaredProfileUpdateRequest request,
      long parentRunDbId,
      int parentStepIndex);
}
