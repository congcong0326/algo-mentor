package org.congcong.algomentor.api.databasebackup.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import org.congcong.algomentor.api.databasebackup.model.DatabaseRestoreResponse;
import org.congcong.algomentor.api.databasebackup.service.DatabaseBackupException;
import org.congcong.algomentor.api.databasebackup.service.DatabaseBackupService;
import org.congcong.algomentor.api.databasebackup.service.PreparedDatabaseBackup;
import org.congcong.algomentor.common.api.ApiResponse;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

@RestController
@ConditionalOnProperty(name = "spring.datasource.url")
@RequestMapping(DatabaseBackupApiContractConstants.ADMIN_DATABASE_BASE_PATH)
@PreAuthorize("hasRole('ADMIN')")
public class AdminDatabaseBackupController {

  private final DatabaseBackupService service;

  public AdminDatabaseBackupController(DatabaseBackupService service) {
    this.service = service;
  }

  @GetMapping(value = DatabaseBackupApiContractConstants.BACKUP_PATH,
      produces = DatabaseBackupApiContractConstants.ARCHIVE_MEDIA_TYPE)
  public ResponseEntity<StreamingResponseBody> backup(Authentication authentication) {
    PreparedDatabaseBackup prepared = service.prepareBackup(requireOperatorUserId(authentication));
    StreamingResponseBody body = output -> prepared.writeTo(output);
    return ResponseEntity.ok()
        .contentType(MediaType.APPLICATION_OCTET_STREAM)
        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + prepared.filename() + "\"")
        .header(HttpHeaders.CACHE_CONTROL, DatabaseBackupApiContractConstants.CACHE_CONTROL_NO_STORE)
        .body(body);
  }

  @PostMapping(value = DatabaseBackupApiContractConstants.RESTORE_PATH,
      consumes = DatabaseBackupApiContractConstants.MULTIPART_MEDIA_TYPE)
  public ApiResponse<DatabaseRestoreResponse> restore(
      Authentication authentication,
      HttpServletRequest request,
      @RequestParam(value = DatabaseBackupApiContractConstants.FILE_PART, required = false) MultipartFile file,
      @RequestParam(value = DatabaseBackupApiContractConstants.CONFIRMATION_PART, required = false) String confirmation
  ) throws IOException {
    DatabaseRestoreResponse response = service.restore(
        requireOperatorUserId(authentication),
        confirmation,
        file == null ? null : file.getInputStream(),
        file == null ? 0 : file.getSize());
    invalidateCurrentSession(request);
    return ApiResponse.success(response);
  }

  private static long requireOperatorUserId(Authentication authentication) {
    if (authentication == null || authentication.getName() == null || authentication.getName().isBlank()) {
      throw new DatabaseBackupException(
          org.congcong.algomentor.api.databasebackup.service.DatabaseBackupErrorCode.DATABASE_BACKUP_FAILED,
          "Unable to resolve authenticated administrator.");
    }
    try {
      long userId = Long.parseLong(authentication.getName());
      if (userId < 1) {
        throw new NumberFormatException("non-positive user id");
      }
      return userId;
    } catch (NumberFormatException exception) {
      throw new DatabaseBackupException(
          org.congcong.algomentor.api.databasebackup.service.DatabaseBackupErrorCode.DATABASE_BACKUP_FAILED,
          "Unable to resolve authenticated administrator.");
    }
  }

  private static void invalidateCurrentSession(HttpServletRequest request) {
    HttpSession session = request.getSession(false);
    if (session != null) {
      session.invalidate();
    }
  }
}
