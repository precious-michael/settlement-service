package org.settlementservice.settlementservice.exceptions.handler;

import org.junit.jupiter.api.Test;
import org.settlementservice.settlementservice.dtos.response.SettlementServiceResponse;
import org.settlementservice.settlementservice.exceptions.DuplicateResourceException;
import org.settlementservice.settlementservice.exceptions.ResourceNotFoundException;
import org.settlementservice.settlementservice.exceptions.SettlementServiceException;
import org.springframework.core.MethodParameter;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void handleSettlementServiceException_returns500WithExceptionMessage() {
        ResponseEntity<?> response = handler.handleSettlementServiceException(
                new SettlementServiceException("something app-specific broke"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        SettlementServiceResponse<?> body = (SettlementServiceResponse<?>) response.getBody();
        assertThat(body.isSuccess()).isFalse();
        assertThat(body.getMessage()).isEqualTo("something app-specific broke");
        assertThat(body.getError()).isEqualTo("Internal Server Error");
    }

    @Test
    void handleNotFoundException_returns404WithTailoredMessage() {
        ResponseEntity<?> response = handler.handleNotFoundException(
                new ResourceNotFoundException("No account found with id 5"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        SettlementServiceResponse<?> body = (SettlementServiceResponse<?>) response.getBody();
        assertThat(body.getMessage()).isEqualTo("No account found with id 5");
        assertThat(body.getError()).isEqualTo("Not Found");
    }

    @Test
    void handleDuplicateResourceException_returns409WithTailoredMessage() {
        ResponseEntity<?> response = handler.handleDuplicateResourceException(
                new DuplicateResourceException("An account with account number 123 already exists"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        SettlementServiceResponse<?> body = (SettlementServiceResponse<?>) response.getBody();
        assertThat(body.getMessage()).isEqualTo("An account with account number 123 already exists");
        assertThat(body.getError()).isEqualTo("Conflict");
    }

    @Test
    void handleValidationException_returns400WithFieldDetails_andGenericTopLevelMessage() {
        BindException bindException = new BindException(new Object(), "loginRequest");
        bindException.addError(new FieldError("loginRequest", "username", "Username is required"));
        bindException.addError(new FieldError("loginRequest", "password", "Password is required"));
        MethodArgumentNotValidException ex = new MethodArgumentNotValidException(dummyMethodParameter(), bindException);

        ResponseEntity<?> response = handler.handleValidationException(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        SettlementServiceResponse<?> body = (SettlementServiceResponse<?>) response.getBody();
        // Regression: the top-level message must be a short, fixed string — MethodArgumentNotValidException's
        // own getMessage() is a multi-line framework dump, not something meant to be shown to a client.
        assertThat(body.getMessage()).isEqualTo("Validation failed");
        assertThat(body.getError()).isEqualTo("Validation Failed");
        assertThat(body.getDetails()).containsExactlyInAnyOrder(
                "username: Username is required", "password: Password is required");
    }

    @Test
    void handleIllegalArgumentException_returns400_notConflict() {
        // Regression: this used to incorrectly return 409 Conflict for a client input problem.
        ResponseEntity<?> response = handler.handleIllegalArgumentException(
                new IllegalArgumentException("Uploaded file is empty"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        SettlementServiceResponse<?> body = (SettlementServiceResponse<?>) response.getBody();
        assertThat(body.getMessage()).isEqualTo("Uploaded file is empty");
        assertThat(body.getError()).isEqualTo("Bad Request");
    }

    @Test
    void handleAuthenticationException_returns401WithExceptionMessage() {
        ResponseEntity<?> response = handler.handleAuthenticationException(new BadCredentialsException("Bad credentials"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        SettlementServiceResponse<?> body = (SettlementServiceResponse<?>) response.getBody();
        assertThat(body.getError()).isEqualTo("Unauthorized");
    }

    @Test
    void handleDataIntegrityViolationException_returns409WithGenericMessage_hidesRawSql() {
        // Regression: this used to echo ex.getMessage(), which leaked the raw SQL statement and
        // constraint/table names straight to the API client.
        DataIntegrityViolationException ex = new DataIntegrityViolationException(
                "could not execute statement [Cannot delete or update a parent row: a foreign key constraint "
                        + "fails (`settlement_service`.`bank_statements`, CONSTRAINT `fk_bank_statements_account`"
                        + " FOREIGN KEY (`account_id`) REFERENCES `accounts` (`id`))]; SQL [delete from accounts where id=?]");

        ResponseEntity<?> response = handler.handleDataIntegrityViolationException(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        SettlementServiceResponse<?> body = (SettlementServiceResponse<?>) response.getBody();
        assertThat(body.getMessage())
                .doesNotContain("SQL")
                .doesNotContain("fk_bank_statements_account")
                .isEqualTo("This record cannot be modified because it is still referenced by other records");
    }

    @Test
    void handleAccessDeniedException_returns403() {
        ResponseEntity<?> response = handler.handleAccessDeniedException(new AccessDeniedException("Access Denied"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        SettlementServiceResponse<?> body = (SettlementServiceResponse<?>) response.getBody();
        assertThat(body.getError()).isEqualTo("You do not have permission");
    }

    @Test
    void handleUnexpectedException_returns500WithGenericMessage_hidesRealExceptionMessage() {
        // Regression: this used to echo ex.getMessage() for genuinely unanticipated exceptions,
        // which can leak internal details (class names, driver-level text) to the client.
        ResponseEntity<?> response = handler.handleUnexpectedException(
                new NullPointerException("some.internal.package.Class.field was null"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        SettlementServiceResponse<?> body = (SettlementServiceResponse<?>) response.getBody();
        assertThat(body.getMessage())
                .isEqualTo("Something went wrong")
                .doesNotContain("some.internal.package");
    }

    private MethodParameter dummyMethodParameter() {
        try {
            Method method = getClass().getDeclaredMethod("dummyTarget", String.class);
            return new MethodParameter(method, 0);
        } catch (NoSuchMethodException e) {
            throw new IllegalStateException(e);
        }
    }

    @SuppressWarnings("unused")
    private void dummyTarget(String arg) {
    }
}
