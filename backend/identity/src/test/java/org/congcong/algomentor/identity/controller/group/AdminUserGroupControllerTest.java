package org.congcong.algomentor.identity.controller.group;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.time.Instant;
import java.util.List;
import org.congcong.algomentor.identity.group.model.UserGroup;
import org.congcong.algomentor.identity.group.model.UserGroupMemberPage;
import org.congcong.algomentor.identity.group.model.UserGroupPage;
import org.congcong.algomentor.identity.group.model.UserGroupStatus;
import org.congcong.algomentor.identity.group.service.UserGroupDeleteResult;
import org.congcong.algomentor.identity.group.service.UserGroupErrorCode;
import org.congcong.algomentor.identity.group.service.UserGroupManagementException;
import org.congcong.algomentor.identity.group.service.UserGroupMemberAddResult;
import org.congcong.algomentor.identity.group.service.UserGroupMemberAddStatus;
import org.congcong.algomentor.identity.group.service.UserGroupMemberBatchResult;
import org.congcong.algomentor.identity.group.service.UserGroupMemberRemovalResult;
import org.congcong.algomentor.identity.group.service.UserGroupService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class AdminUserGroupControllerTest {

  private static final Instant NOW = Instant.parse("2026-07-23T05:00:00Z");
  private final ObjectMapper objectMapper = new ObjectMapper()
      .registerModule(new JavaTimeModule())
      .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
  private UserGroupService service;
  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    service = mock(UserGroupService.class);
    mockMvc = MockMvcBuilders.standaloneSetup(new AdminUserGroupController(service))
        .setControllerAdvice(new AdminUserGroupExceptionHandler())
        .setMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper))
        .build();
  }

  @Test
  void listsGroupsWithActiveMemberCount() throws Exception {
    when(service.searchGroups(any())).thenReturn(new UserGroupPage(
        List.of(group(UserGroupStatus.ACTIVE, 3)), 1, 1, 20));

    mockMvc.perform(get("/api/admin/user-groups").param("page", "1").param("pageSize", "20"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.items[0].code").value("PRO"))
        .andExpect(jsonPath("$.data.items[0].activeMemberCount").value(3));
  }

  @Test
  void createsNormalizedGroupThroughAuthenticatedOperator() throws Exception {
    when(service.createGroup(" pro ", "专业会员", null, 99L)).thenReturn(group(UserGroupStatus.ACTIVE, 0));

    mockMvc.perform(post("/api/admin/user-groups")
            .principal(new TestingAuthenticationToken("99", "n/a"))
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"code\":\" pro \",\"name\":\"专业会员\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.id").value(8));
  }

  @Test
  void updatesWithoutAllowingCodeInContract() throws Exception {
    when(service.updateGroup(8L, "专业版", null, UserGroupStatus.DISABLED, 99L))
        .thenReturn(group(UserGroupStatus.DISABLED, 0));

    mockMvc.perform(patch("/api/admin/user-groups/{groupId}", 8L)
            .principal(new TestingAuthenticationToken("99", "n/a"))
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"name\":\"专业版\",\"status\":\"DISABLED\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.status").value("DISABLED"));
  }

  @Test
  void deleteReturnsCleanupCount() throws Exception {
    when(service.deleteGroup(8L, 99L)).thenReturn(new UserGroupDeleteResult(8L, true, 4));

    mockMvc.perform(delete("/api/admin/user-groups/{groupId}", 8L)
            .principal(new TestingAuthenticationToken("99", "n/a")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.deleted").value(true))
        .andExpect(jsonPath("$.data.removedMembershipCount").value(4));
  }

  @Test
  void addsMembersAndReturnsPerUserResults() throws Exception {
    when(service.addMembers(eqLong(8L), any(), any(), eqLong(99L))).thenReturn(
        UserGroupMemberBatchResult.from(List.of(
            new UserGroupMemberAddResult(42L, UserGroupMemberAddStatus.ADDED),
            new UserGroupMemberAddResult(43L, UserGroupMemberAddStatus.USER_DELETED))));

    mockMvc.perform(post("/api/admin/user-groups/{groupId}/members", 8L)
            .principal(new TestingAuthenticationToken("99", "n/a"))
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"userIds\":[42,43],\"expiresAt\":\"2026-12-31T23:59:59Z\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.addedCount").value(1))
        .andExpect(jsonPath("$.data.failedCount").value(1))
        .andExpect(jsonPath("$.data.results[1].status").value("USER_DELETED"));
  }

  @Test
  void removeMemberIsIdempotent() throws Exception {
    when(service.removeMember(8L, 42L, 99L))
        .thenReturn(new UserGroupMemberRemovalResult(8L, 42L, false));

    mockMvc.perform(delete("/api/admin/user-groups/{groupId}/members/{userId}", 8L, 42L)
            .principal(new TestingAuthenticationToken("99", "n/a")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.removed").value(false));

    verify(service).removeMember(8L, 42L, 99L);
  }

  @Test
  void activeDeleteConflictMapsToStableError() throws Exception {
    when(service.deleteGroup(8L, 99L)).thenThrow(new UserGroupManagementException(
        UserGroupErrorCode.USER_GROUP_DELETE_REQUIRES_DISABLED,
        "用户组必须先停用后才能删除。"));

    mockMvc.perform(delete("/api/admin/user-groups/{groupId}", 8L)
            .principal(new TestingAuthenticationToken("99", "n/a")))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.error.code").value("USER_GROUP_DELETE_REQUIRES_DISABLED"));
  }

  @Test
  void malformedStatusMapsToBadRequest() throws Exception {
    mockMvc.perform(patch("/api/admin/user-groups/{groupId}", 8L)
            .principal(new TestingAuthenticationToken("99", "n/a"))
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"name\":\"专业版\",\"status\":\"DEAD\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("REQUEST_BODY_INVALID"));
  }

  private UserGroup group(UserGroupStatus status, long activeMemberCount) {
    return new UserGroup(8L, "PRO", "专业会员", null, status, activeMemberCount, NOW, NOW, null, null);
  }

  private static long eqLong(long value) {
    return org.mockito.ArgumentMatchers.eq(value);
  }
}
