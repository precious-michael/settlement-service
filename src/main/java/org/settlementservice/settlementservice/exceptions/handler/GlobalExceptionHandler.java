package org.settlementservice.settlementservice.exceptions.handler;

import lombok.extern.slf4j.Slf4j;
import org.settlementservice.settlementservice.dtos.response.SettlementServiceResponse;
import org.settlementservice.settlementservice.exceptions.DuplicateResourceException;
import org.settlementservice.settlementservice.exceptions.FileParseException;
import org.settlementservice.settlementservice.exceptions.ResourceNotFoundException;
import org.settlementservice.settlementservice.exceptions.SettlementServiceException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.NoHandlerFoundException;

import java.util.List;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(SettlementServiceException.class)
    public ResponseEntity<?> handleSettlementServiceException(SettlementServiceException ex) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(SettlementServiceResponse.builder()
                        .message(ex.getMessage())
                        .success(false)
                        .error("Internal Server Error")
                        .build());
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<?> handleNotFoundException(ResourceNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(SettlementServiceResponse.builder()
                        .message(ex.getMessage())
                        .success(false)
                        .error("Not Found")
                        .build());
    }

    @ExceptionHandler(DuplicateResourceException.class)
    public ResponseEntity<?> handleDuplicateResourceException(DuplicateResourceException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(SettlementServiceResponse.builder()
                        .message(ex.getMessage())
                        .success(false)
                        .error("Conflict")
                        .build());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<?> handleValidationException(MethodArgumentNotValidException ex) {
        List<String> details = ex.getBindingResult().getFieldErrors().stream()
                .map(fieldError -> fieldError.getField() + ": " + fieldError.getDefaultMessage())
                .toList();
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(SettlementServiceResponse.builder()
                        .message("Validation failed")
                        .success(false)
                        .error("Validation Failed")
                        .details(details)
                        .build());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<?> handleIllegalArgumentException(IllegalArgumentException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(SettlementServiceResponse.builder()
                        .message(ex.getMessage())
                        .success(false)
                        .error("Bad Request")
                        .build());
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<?> handleAuthenticationException(AuthenticationException ex) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(SettlementServiceResponse.builder()
                        .message(ex.getMessage())
                        .success(false)
                        .error("Unauthorized")
                        .build());
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<?> handleDataIntegrityViolationException(DataIntegrityViolationException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(SettlementServiceResponse.builder()
                        .message("This record cannot be modified because it is still referenced by other records")
                        .success(false)
                        .error("Conflict")
                        .build());
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<?> handleAccessDeniedException(AccessDeniedException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(SettlementServiceResponse.builder()
                        .message(ex.getMessage())
                        .success(false)
                        .error("You do not have permission")
                        .build());
    }

    @ExceptionHandler(NoHandlerFoundException.class)
    public ResponseEntity<?> handleNoHandlerFoundException(NoHandlerFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(SettlementServiceResponse.builder()
                        .message("The requested endpoint does not exist: " + ex.getRequestURL())
                        .success(false)
                        .error("Not Found")
                        .build());
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<?> handleMethodNotSupportedException(HttpRequestMethodNotSupportedException ex) {
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED)
                .body(SettlementServiceResponse.builder()
                        .message("HTTP method " + ex.getMethod() + " is not supported for this endpoint")
                        .success(false)
                        .error("Method Not Allowed")
                        .build());
    }

    @ExceptionHandler(FileParseException.class)
    public ResponseEntity<?> handleFileParseException(FileParseException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(SettlementServiceResponse.builder()
                        .message(ex.getMessage())
                        .success(false)
                        .error("File Parse Error")
                        .build());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<?> handleUnexpectedException(Exception ex) {
        log.error("Unhandled exception", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(SettlementServiceResponse.builder()
                        .message("Something went wrong")
                        .success(false)
                        .error("Internal Server Error")
                        .build());
    }
}