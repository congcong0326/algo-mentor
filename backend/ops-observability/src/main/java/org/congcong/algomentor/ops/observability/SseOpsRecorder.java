package org.congcong.algomentor.ops.observability;

public interface SseOpsRecorder {

  void opened(SseStreamType streamType);

  void completed(SseStreamType streamType);

  void failed(SseStreamType streamType, SseFailureType failureType);

  void timeout(SseStreamType streamType);

  void clientDisconnected(SseStreamType streamType);

}
