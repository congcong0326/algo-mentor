package org.congcong.algomentor.identity.group.relation;

/** 身份模块拥有的用户有效关系查询门面。 */
public interface UserRelationProvider {

  UserRelations getRelations(long userId);
}
