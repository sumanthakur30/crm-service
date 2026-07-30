package com.shopmanagement.crmservice.web;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

import com.shopmanagement.crmservice.entitlement.CrmEntitlementException;
import com.shopmanagement.crmservice.integration.NotificationDispatchException;
import com.shopmanagement.crmservice.integration.OrderDispatchException;

@RestControllerAdvice
public class ApiExceptionHandler {

  @ExceptionHandler(CrmEntitlementException.class)
  public ResponseEntity<Map<String, Object>> entitlement(CrmEntitlementException ex) {
    return ResponseEntity.status(HttpStatus.FORBIDDEN)
        .body(Map.of("message", ex.getMessage(), "code", "CRM_ENTITLEMENT_DENIED"));
  }

  @ExceptionHandler(NotificationDispatchException.class)
  public ResponseEntity<Map<String, Object>> notification(NotificationDispatchException ex) {
    return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
        .body(Map.of("message", ex.getMessage(), "code", "CRM_NOTIFICATION_FAILED"));
  }

  @ExceptionHandler(OrderDispatchException.class)
  public ResponseEntity<Map<String, Object>> order(OrderDispatchException ex) {
    return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
        .body(Map.of("message", ex.getMessage(), "code", "CRM_ORDER_FAILED"));
  }

  @ExceptionHandler(ResponseStatusException.class)
  public ResponseEntity<Map<String, Object>> status(ResponseStatusException ex) {
    return ResponseEntity.status(ex.getStatusCode())
        .body(Map.of("message", ex.getReason() == null ? ex.getMessage() : ex.getReason()));
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<Map<String, Object>> validation(MethodArgumentNotValidException ex) {
    String msg =
        ex.getBindingResult().getFieldErrors().stream()
            .findFirst()
            .map(err -> err.getField() + ": " + err.getDefaultMessage())
            .orElse("Validation failed");
    return ResponseEntity.badRequest().body(Map.of("message", msg));
  }

  @ExceptionHandler(IllegalStateException.class)
  public ResponseEntity<Map<String, Object>> illegalState(IllegalStateException ex) {
    return ResponseEntity.badRequest().body(Map.of("message", ex.getMessage()));
  }
}
