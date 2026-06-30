package org.congcong.algomentor.identity.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.congcong.algomentor.identity.controller.model.AdminUserStatusUpdateRequest;
import org.congcong.algomentor.identity.model.AuthRole;
import org.congcong.algomentor.identity.model.AuthUser;
import org.congcong.algomentor.identity.model.AuthUserStatus;
import org.congcong.algomentor.identity.model.IdentityUserPage;
import org.congcong.algomentor.identity.repository.IdentityUserRepository;
import org.congcong.algomentor.identity.service.IdentityUserErrorCode;
import org.congcong.algomentor.identity.service.IdentityUserManagementException;
import org.congcong.algomentor.identity.service.IdentityUserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class AdminUserControllerTest {

  private static final Instant CREATED_AT = Instant.parse("2026-06-30T01:00:00Z");
  private static final Instant UPDATED_AT = Instant.parse("2026-06-30T02:00:00Z");
  private static final Instant LAST_LOGIN_AT = Instant.parse("2026-06-30T03:00:00Z");
  private static final Instant DELETED_AT = Instant.parse("2026-06-30T04:00:00Z");

  private final ObjectMapper objectMapper = new ObjectMapper()
      .registerModule(new JavaTimeModule())
      .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
  private IdentityUserService service;
  private IdentityUserRepository repository;
  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    service = mock(IdentityUserService.class);
    repository = mock(IdentityUserRepository.class);
    mockMvc = MockMvcBuilders
        .standaloneSetup(new AdminUserController(service, repository))
        .setControllerAdvice(new AdminUserExceptionHandler())
        .setMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper))
        .build();
  }

  @Test
  void listUsersReturnsPagedResponse() throws Exception {
    AuthUser user = activeUser(42L);
    when(service.searchUsers(any())).thenReturn(new IdentityUserPage(List.of(user), 1, 2, 10));
    when(repository.findRoles(42L)).thenReturn(List.of(AuthRole.USER));

    mockMvc.perform(get("/api/admin/users")
            .param("page", "2")
            .param("pageSize", "10")
            .param("keyword", "alice")
            .param("status", "ACTIVE"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.items[0].id").value(42))
        .andExpect(jsonPath("$.data.items[0].roles[0]").value("USER"))
        .andExpect(jsonPath("$.data.total").value(1))
        .andExpect(jsonPath("$.data.page").value(2))
        .andExpect(jsonPath("$.data.pageSize").value(10));
  }

  @Test
  void malformedQueryStatusMapsTo400ContractError() throws Exception {
    mockMvc.perform(get("/api/admin/users")
            .param("page", "1")
            .param("pageSize", "20")
            .param("status", "BOGUS"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.error.code").value("USER_STATUS_INVALID"));
  }

  @Test
  void detailReturnsRolesAndDeleteFields() throws Exception {
    AuthUser user = deletedUser(42L, 7L);
    when(service.getUser(42L)).thenReturn(user);
    when(repository.findRoles(42L)).thenReturn(List.of(AuthRole.USER, AuthRole.ADMIN));

    mockMvc.perform(get("/api/admin/users/{userId}", 42L))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.id").value(42))
        .andExpect(jsonPath("$.data.emailNormalized").value("alice@example.com"))
        .andExpect(jsonPath("$.data.roles[0]").value("USER"))
        .andExpect(jsonPath("$.data.roles[1]").value("ADMIN"))
        .andExpect(jsonPath("$.data.deletedAt").value("2026-06-30T04:00:00Z"))
        .andExpect(jsonPath("$.data.deletedBy").value(7));
  }

  @Test
  void updateStatusDisablesUser() throws Exception {
    AuthUser disabled = user(42L, AuthUserStatus.DISABLED, null, null);
    when(service.updateStatus(42L, 99L, AuthUserStatus.DISABLED)).thenReturn(disabled);
    when(repository.findRoles(42L)).thenReturn(List.of(AuthRole.USER));

    mockMvc.perform(patch("/api/admin/users/{userId}/status", 42L)
            .principal(new TestingAuthenticationToken("99", "n/a"))
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsBytes(new AdminUserStatusUpdateRequest(AuthUserStatus.DISABLED))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.id").value(42))
        .andExpect(jsonPath("$.data.status").value("DISABLED"))
        .andExpect(jsonPath("$.data.roles[0]").value("USER"));

    verify(service).updateStatus(42L, 99L, AuthUserStatus.DISABLED);
  }

  @Test
  void deleteSoftDeletesUser() throws Exception {
    AuthUser deleted = deletedUser(42L, 99L);
    when(service.softDelete(42L, 99L)).thenReturn(deleted);
    when(repository.findRoles(42L)).thenReturn(List.of(AuthRole.USER));

    mockMvc.perform(delete("/api/admin/users/{userId}", 42L)
            .principal(new TestingAuthenticationToken("99", "n/a")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.status").value("DELETED"))
        .andExpect(jsonPath("$.data.deletedBy").value(99));

    verify(service).softDelete(42L, 99L);
  }

  @Test
  void serviceConflictMapsTo409() throws Exception {
    when(service.softDelete(42L, 99L)).thenThrow(new IdentityUserManagementException(
        IdentityUserErrorCode.USER_STATUS_CONFLICT,
        "用户状态冲突。"));

    mockMvc.perform(delete("/api/admin/users/{userId}", 42L)
            .principal(new TestingAuthenticationToken("99", "n/a")))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.error.code").value("USER_STATUS_CONFLICT"))
        .andExpect(jsonPath("$.error.message").value("用户状态冲突。"));
  }

  @Test
  void invalidStatusMapsTo400() throws Exception {
    when(service.updateStatus(42L, 99L, null)).thenThrow(new IdentityUserManagementException(
        IdentityUserErrorCode.USER_STATUS_INVALID,
        "用户状态不合法。"));

    mockMvc.perform(patch("/api/admin/users/{userId}/status", 42L)
            .principal(new TestingAuthenticationToken("99", "n/a"))
            .contentType(MediaType.APPLICATION_JSON)
            .content("{}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.error.code").value("USER_STATUS_INVALID"))
        .andExpect(jsonPath("$.error.message").value("用户状态不合法。"));
  }

  @Test
  void malformedJsonStatusMapsTo400ContractError() throws Exception {
    mockMvc.perform(patch("/api/admin/users/{userId}/status", 42L)
            .principal(new TestingAuthenticationToken("99", "n/a"))
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"status\":\"BOGUS\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.error.code").value("USER_STATUS_INVALID"));
  }

  private AuthUser activeUser(long id) {
    return user(id, AuthUserStatus.ACTIVE, null, null);
  }

  private AuthUser deletedUser(long id, long deletedBy) {
    return user(id, AuthUserStatus.DELETED, DELETED_AT, deletedBy);
  }

  private AuthUser user(long id, AuthUserStatus status, Instant deletedAt, Long deletedBy) {
    return new AuthUser(
        id,
        "alice@example.com",
        "alice@example.com",
        "Alice",
        "https://example.com/alice.png",
        status,
        CREATED_AT,
        UPDATED_AT,
        LAST_LOGIN_AT,
        deletedAt,
        deletedBy);
  }
}
