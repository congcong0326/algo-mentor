package org.congcong.algomentor.auth.session;

import java.util.List;

/** 面向认证控制链的按用户 Session 查询端口。 */
public interface AuthSessionRepository {

  List<AuthSessionRecord> findByUserId(long userId);
}
