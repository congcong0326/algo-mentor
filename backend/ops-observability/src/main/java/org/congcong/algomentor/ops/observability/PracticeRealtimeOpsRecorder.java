package org.congcong.algomentor.ops.observability;

import java.time.Duration;

/** Practice Chat Redis Stream 实时通道的最小可观测性端口。 */
public interface PracticeRealtimeOpsRecorder {

  void redisOperation(
      PracticeRealtimeOperation operation,
      PracticeRealtimeOutcome outcome,
      Duration duration
  );

  /** 记录公开 payload 的异步写入结果和已序列化字节数，不记录正文或 run 标识。 */
  void publicEventAppend(String eventName, int payloadBytes, PracticeRealtimeOutcome outcome);
}
