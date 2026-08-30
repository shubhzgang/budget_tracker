package com.budget.tracker.exception;

import com.budget.tracker.payload.response.MessageResponse;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void handleRuntimeException_returnsBadRequestWithMessage() {
        org.springframework.http.ResponseEntity<?> response =
                handler.handleRuntimeException(new RuntimeException("boom"));

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("boom", ((MessageResponse) response.getBody()).getMessage());
    }

    @Test
    void handleIllegalArgumentException_returnsBadRequestWithMessage() {
        org.springframework.http.ResponseEntity<?> response =
                handler.handleIllegalArgumentException(new IllegalArgumentException("invalid amount"));

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("invalid amount", ((MessageResponse) response.getBody()).getMessage());
    }

    @Test
    void handleAuthenticationException_returnsUnauthorized() {
        org.springframework.http.ResponseEntity<?> response =
                handler.handleAuthenticationException(new AuthenticationException("bad credentials") {
                });

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        assertEquals("bad credentials", ((MessageResponse) response.getBody()).getMessage());
    }

    @Test
    void handleValidationException_concatenatesFieldErrors() {
        MethodArgumentNotValidException ex = new MethodArgumentNotValidException(dummyMethod(), bindingResult(
                new FieldError("obj", "name", "must not be blank"),
                new FieldError("obj", "amount", "must be greater than zero")));

        org.springframework.http.ResponseEntity<?> response = handler.handleValidationException(ex);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        String message = ((MessageResponse) response.getBody()).getMessage();
        assertTrue(message.contains("Validation failed:"));
        assertTrue(message.contains("name: must not be blank"));
        assertTrue(message.contains("amount: must be greater than zero"));
    }

    private MethodParameter dummyMethod() {
        try {
            Method method = GlobalExceptionHandlerTest.class.getDeclaredMethod("noopProbe");
            return new MethodParameter(method, -1);
        } catch (NoSuchMethodException e) {
            throw new IllegalStateException(e);
        }
    }

    private BeanPropertyBindingResult bindingResult(FieldError... errors) {
        BeanPropertyBindingResult result = new BeanPropertyBindingResult(new Object(), "obj");
        for (FieldError error : errors) {
            result.addError(error);
        }
        return result;
    }

    @SuppressWarnings("unused")
    private void noopProbe() {
    }
}
