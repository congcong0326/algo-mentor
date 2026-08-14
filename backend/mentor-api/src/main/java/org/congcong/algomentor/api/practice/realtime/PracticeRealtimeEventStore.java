package org.congcong.algomentor.api.practice.realtime;

import java.util.List;
import org.congcong.algomentor.mentor.application.practice.PracticeChatRunEventSubscriber;

/** Practice Chat 临时实时事件日志端口；失败只能降级实时体验。 */
public interface PracticeRealtimeEventStore extends PracticeChatRunEventSubscriber.EventStore {

  List<PracticeRealtimeEvent> readAfter(String runUuid, String after, boolean block);

  default boolean available() {
    return true;
  }
}
