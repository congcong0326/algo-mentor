package org.congcong.algomentor.api.databasebackup.controller;

import java.util.Locale;
import org.congcong.algomentor.api.databasebackup.service.DatabaseBackupErrorCode;
import org.congcong.algomentor.api.databasebackup.service.DatabaseBackupException;
import org.congcong.algomentor.common.api.ApiErrorMessageResolver;
import org.congcong.algomentor.common.api.ApiErrorResponseFactory;
import org.congcong.algomentor.common.api.ApiResponse;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MultipartException;

@RestControllerAdvice(assignableTypes = AdminDatabaseBackupController.class)
@Order(Ordered.HIGHEST_PRECEDENCE)
public class DatabaseBackupExceptionHandler {

  private final ApiErrorResponseFactory responseFactory;

  public DatabaseBackupExceptionHandler(ObjectProvider<ApiErrorResponseFactory> responseFactoryProvider) {
    this.responseFactory = responseFactoryProvider.getIfAvailable(
        () -> new ApiErrorResponseFactory(new ApiErrorMessageResolver()));
  }

  @ExceptionHandler(DatabaseBackupException.class)
  public ResponseEntity<ApiResponse<Void>> databaseBackup(DatabaseBackupException exception, Locale locale) {
    return failure(status(exception.code()), exception.code(), exception.getMessage(), locale);
  }

  @ExceptionHandler(MultipartException.class)
  public ResponseEntity<ApiResponse<Void>> multipart(MultipartException exception, Locale locale) {
    return failure(
        HttpStatus.BAD_REQUEST,
        DatabaseBackupErrorCode.DATABASE_RESTORE_ARCHIVE_INVALID,
        "Backup archive upload is invalid.",
        locale);
  }

  private ResponseEntity<ApiResponse<Void>> failure(
      HttpStatus status,
      DatabaseBackupErrorCode code,
      String message,
      Locale locale
  ) {
    return ResponseEntity.status(status).body(responseFactory.failure(code.name(), message, locale));
  }

  private static HttpStatus status(DatabaseBackupErrorCode code) {
    return switch (code) {
      case DATABASE_BACKUP_BUSY -> HttpStatus.CONFLICT;
      case DATABASE_BACKUP_FAILED, DATABASE_RESTORE_FAILED -> HttpStatus.INTERNAL_SERVER_ERROR;
      case DATABASE_RESTORE_PRIVILEGE_REQUIRED -> HttpStatus.FORBIDDEN;
      case DATABASE_RESTORE_ARCHIVE_INVALID,
          DATABASE_RESTORE_CHECKSUM_MISMATCH,
          DATABASE_RESTORE_VERSION_MISMATCH,
          DATABASE_RESTORE_TABLE_SET_MISMATCH -> HttpStatus.BAD_REQUEST;
    };
  }
}
