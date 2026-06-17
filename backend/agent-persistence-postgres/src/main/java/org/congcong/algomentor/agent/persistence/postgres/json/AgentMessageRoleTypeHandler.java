package org.congcong.algomentor.agent.persistence.postgres.json;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Locale;
import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.MappedTypes;
import org.congcong.algomentor.agent.core.runtime.model.AgentMessage;

@MappedTypes(AgentMessage.Role.class)
public class AgentMessageRoleTypeHandler extends BaseTypeHandler<AgentMessage.Role> {

  @Override
  public void setNonNullParameter(
      PreparedStatement ps,
      int index,
      AgentMessage.Role parameter,
      JdbcType jdbcType
  ) throws SQLException {
    ps.setString(index, parameter.name().toLowerCase(Locale.ROOT));
  }

  @Override
  public AgentMessage.Role getNullableResult(ResultSet rs, String columnName) throws SQLException {
    return parse(rs.getString(columnName));
  }

  @Override
  public AgentMessage.Role getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
    return parse(rs.getString(columnIndex));
  }

  @Override
  public AgentMessage.Role getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
    return parse(cs.getString(columnIndex));
  }

  private AgentMessage.Role parse(String value) {
    if (value == null || value.isBlank()) {
      return null;
    }
    return AgentMessage.Role.valueOf(value.toUpperCase(Locale.ROOT));
  }
}
