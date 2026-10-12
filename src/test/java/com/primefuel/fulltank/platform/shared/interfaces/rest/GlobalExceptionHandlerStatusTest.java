package com.primefuel.fulltank.platform.shared.interfaces.rest;

import static org.assertj.core.api.Assertions.assertThat;

import com.primefuel.fulltank.platform.shared.interfaces.rest.resources.ErrorResource;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

class GlobalExceptionHandlerStatusTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void duplicateInsertOnAnyRouteIsAConflictWithStandardBody() {
        var response = handler.handleDataIntegrity(
                new DataIntegrityViolationException("uk_payments_active_order"),
                new MockHttpServletRequest("POST", "/api/payments"));
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(((ErrorResource) response.getBody()).code()).isEqualTo("RESOURCE_CONFLICT");
    }

    @Test
    void routingErrorsKeepTheirStatusWithStandardBody() {
        var method = handler.handleMethodNotSupported(
                new HttpRequestMethodNotSupportedException("PUT", List.of("GET")));
        assertThat(method.getStatusCode()).isEqualTo(HttpStatus.METHOD_NOT_ALLOWED);
        assertThat(method.getHeaders().getAllow()).contains(HttpMethod.GET);
        assertThat(method.getBody().code()).isEqualTo("METHOD_NOT_ALLOWED");

        var media = handler.handleMediaTypeNotSupported(
                new HttpMediaTypeNotSupportedException(MediaType.TEXT_PLAIN, List.of(MediaType.APPLICATION_JSON)));
        assertThat(media.getStatusCode()).isEqualTo(HttpStatus.UNSUPPORTED_MEDIA_TYPE);

        var missing = handler.handleNoResource(new NoResourceFoundException(HttpMethod.GET, "/v3/api-docs", "v3/api-docs"));
        assertThat(missing.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void responseStatusExceptionKeepsItsStatus() {
        assertThat(handler.handleResponseStatusException(new ResponseStatusException(HttpStatus.NOT_FOUND))
                .getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(handler.handleResponseStatusException(new ResponseStatusException(HttpStatus.FORBIDDEN))
                .getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(handler.handleResponseStatusException(new ResponseStatusException(HttpStatus.BAD_REQUEST, "x"))
                .getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }
}
