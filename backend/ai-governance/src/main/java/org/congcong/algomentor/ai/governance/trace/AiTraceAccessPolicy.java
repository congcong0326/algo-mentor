package org.congcong.algomentor.ai.governance.trace;

import org.congcong.algomentor.ai.governance.model.AiActor;

public class AiTraceAccessPolicy {

  public void assertCanReadFullTrace(AiActor actor) {
    if (actor == null || !actor.authenticated() || !actor.admin()) {
      throw new AiTraceAccessDeniedException("Only administrators can read full AI trace content");
    }
  }
}
