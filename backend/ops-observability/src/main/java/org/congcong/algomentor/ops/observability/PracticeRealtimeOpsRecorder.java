package org.congcong.algomentor.ops.observability;

import java.time.Duration;

/** Practice Chat Redis Stream 实时通道的最小可观测性端口。 */
public interface PracticeRealtimeOpsRecorder {

  void redisOperation(
      PracticeRealtimeOperation operation,
      PracticeRealtimeOutcome outcome,
      Duration duration
  );
}
