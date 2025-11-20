package com.pagosyradicacion.backend.carga;

import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.sql.SQLException;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.server.ResponseStatusException;

@ControllerAdvice
public class GlobalCargaExceptionHandler {

  private static final Logger log = LoggerFactory.getLogger(GlobalCargaExceptionHandler.class);

  private ResponseEntity<Map<String, Object>> body(HttpStatus status, String message) {
    Map<String, Object> map = new HashMap<>();
    map.put("ok", false);
    map.put("message", message);
    return ResponseEntity.status(status).body(map);
  }

  @ExceptionHandler(DuplicateRecordException.class)
  public ResponseEntity<Map<String, Object>> handleDuplicate(DuplicateRecordException ex) {
    log.warn("DuplicateRecordException: {}", ex.getMessage());
    return body(HttpStatus.CONFLICT, ex.getMessage()); // 409
  }

  @ExceptionHandler(BusinessValidationException.class)
  public ResponseEntity<Map<String, Object>> handleValidation(BusinessValidationException ex) {
    log.warn("BusinessValidationException: {}", ex.getMessage());
    return body(HttpStatus.UNPROCESSABLE_ENTITY, ex.getMessage()); // 422
  }

  @ExceptionHandler(MaxUploadSizeExceededException.class)
  public ResponseEntity<Map<String, Object>> handleMaxUpload(MaxUploadSizeExceededException ex) {
    log.warn("MaxUploadSizeExceededException: {}", ex.getMessage());
    return body(HttpStatus.PAYLOAD_TOO_LARGE, "El archivo supera el tamaño permitido"); // 413
  }

  @ExceptionHandler(ResponseStatusException.class)
  public ResponseEntity<Map<String, Object>> handleRse(ResponseStatusException ex) {
    // Respeta el status si alguien lanza RSE explícitamente
    log.warn("ResponseStatusException: status={} reason={}", ex.getStatusCode(), ex.getReason());
    return body(ex.getStatusCode() instanceof HttpStatus hs ? hs : HttpStatus.BAD_REQUEST,
        ex.getReason() != null ? ex.getReason() : ex.getMessage());
  }

  @ExceptionHandler(DataAccessException.class)
  public ResponseEntity<Map<String, Object>> handleData(DataAccessException ex) {
    log.error("DataAccessException durante carga", ex);
    // Construye respuesta con detalles útiles del error SQL
    Map<String, Object> map = new HashMap<>();
    map.put("ok", false);
    map.put("message", "Error de base de datos durante la carga");

    Throwable cause = ex.getMostSpecificCause();
    // Intento anticipado de mapear errores comunes de SQL Server
    if (cause instanceof SQLException sqlEx0) {
      ResponseEntity<Map<String, Object>> pre = handleSqlErrors(sqlEx0);
      if (pre != null) return pre;
    }
    if (cause instanceof SQLException sqlEx) {
      map.put("sql_state", sqlEx.getSQLState());
      map.put("error_code", sqlEx.getErrorCode());
      map.put("sql_error", safe(sqlEx.getMessage()));
    } else if (cause != null) {
      map.put("detail", safe(cause.getMessage()));
    } else {
      map.put("detail", safe(ex.getMessage()));
    }

    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(map);
  }

  private static String safe(String s) { return s == null ? "" : s; }

  private ResponseEntity<Map<String, Object>> handleSqlErrors(SQLException sqlEx) {
    int code = sqlEx.getErrorCode();
    Map<String, Object> map = new HashMap<>();
    map.put("ok", false);
    map.put("sql_state", sqlEx.getSQLState());
    map.put("error_code", code);
    map.put("sql_error", safe(sqlEx.getMessage()));

    if (code == 2628) { // truncamiento
      String col = extractBetween(sqlEx.getMessage(), "column '", "'");
      String truncated = extractBetween(sqlEx.getMessage(), "Truncated value: '", "'");
      String userMsg = "El valor excede la longitud permitida" +
          (col != null ? (" en la columna '" + col + "'") : "") +
          ". Revisa que el encabezado y el separador del CSV sean consistentes (usa ';').";
      map.put("message", userMsg);
      if (truncated != null) map.put("truncated_example", truncated);
      return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(map);
    }
    if (code == 1101) { // espacio insuficiente
      map.put("message", "Espacio insuficiente en SQL Server (filegroup PRIMARY). Libera espacio o habilita crecimiento del archivo.");
      return ResponseEntity.status(HttpStatus.INSUFFICIENT_STORAGE).body(map);
    }
    return null; // no mapeado
  }

  private static String extractBetween(String text, String start, String end) {
    if (text == null) return null;
    try {
      int i = text.indexOf(start);
      if (i < 0) return null;
      i += start.length();
      int j = text.indexOf(end, i);
      if (j < 0) return null;
      return text.substring(i, j);
    } catch (Exception ignore) {
      return null;
    }
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<Map<String, Object>> handleGeneric(Exception ex) {
    log.error("Excepción no controlada durante carga", ex);
    return body(HttpStatus.INTERNAL_SERVER_ERROR, "Error durante la carga: " + ex.getMessage());
  }
}
