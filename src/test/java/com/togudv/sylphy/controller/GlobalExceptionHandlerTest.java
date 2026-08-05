package com.togudv.sylphy.controller;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;

import java.util.NoSuchElementException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void notFound_mapsTo404ProblemDetail() {
        ResponseEntity<ProblemDetail> response =
                handler.handleNotFound(new NoSuchElementException("Reminder no encontrado: 9"));
        ProblemDetail body = response.getBody();
        assertNotNull(body);

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertEquals(404, body.getStatus());
        assertEquals("Reminder no encontrado: 9", body.getDetail());
    }

    @Test
    void illegalArgument_mapsTo400ProblemDetail() {
        ResponseEntity<ProblemDetail> response =
                handler.handleIllegalArgument(new IllegalArgumentException("config invalida"));
        ProblemDetail body = response.getBody();
        assertNotNull(body);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("config invalida", body.getDetail());
    }

    @Test
    void validation_mapsFieldErrorsToProperty() {
        MethodArgumentNotValidException e = mock(MethodArgumentNotValidException.class);
        org.springframework.validation.BindingResult binding = mock(org.springframework.validation.BindingResult.class);
        org.springframework.validation.FieldError fieldError =
                new org.springframework.validation.FieldError("obj", "name", "no debe estar vacío");
        when(e.getBindingResult()).thenReturn(binding);
        when(binding.getFieldErrors()).thenReturn(java.util.List.of(fieldError));

        ResponseEntity<ProblemDetail> response = handler.handleValidation(e);
        ProblemDetail body = response.getBody();
        assertNotNull(body);
        java.util.Map<String, Object> properties = body.getProperties();
        assertNotNull(properties);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        @SuppressWarnings("unchecked")
        java.util.List<String> errors =
                (java.util.List<String>) properties.get("errors");
        assertEquals(java.util.List.of("name: no debe estar vacío"), errors);
    }

    @Test
    void unreadable_mapsTo400ProblemDetail() {
        HttpMessageNotReadableException e = mock(HttpMessageNotReadableException.class);
        when(e.getMessage()).thenReturn("cuerpo roto");

        ResponseEntity<ProblemDetail> response = handler.handleUnreadable(e);
        ProblemDetail body = response.getBody();
        assertNotNull(body);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("Solicitud invalida", body.getTitle());
    }

    @Test
    void generic_mapsTo500ProblemDetail() {
        ResponseEntity<ProblemDetail> response =
                handler.handleGeneric(new RuntimeException("boom"));
        ProblemDetail body = response.getBody();
        assertNotNull(body);

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertEquals(500, body.getStatus());
        assertTrue(body.getDetail().contains("Error interno"));
    }
}
