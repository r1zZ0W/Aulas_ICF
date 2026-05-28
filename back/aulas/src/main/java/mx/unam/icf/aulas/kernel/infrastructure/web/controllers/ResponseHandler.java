package mx.unam.icf.aulas.kernel.infrastructure.web.controllers;

import mx.unam.icf.aulas.kernel.infrastructure.web.responses.ApiResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/**
 * Response modifier to keep a consistent API response format.
 * It provides helpers to wrap successful responses in {@link ApiResponse}.
 */
public interface ResponseHandler {

    /**
     * Builds a successful HTTP 200 response containing a data payload.
     *
     * @param data the payload to include in the API response body
     * @return a {@link ResponseEntity} with status 200 and a successful {@link ApiResponse}
     * @param <R> the payload type
     */
    default <R> ResponseEntity<ApiResponse<R>> ok(R data) {
        return ResponseEntity.ok(ApiResponse.success(data));
    }

    /**
     * Builds a successful HTTP 200 response with a custom message and no payload.
     *
     * @param message the success message to include in the API response body
     * @return a {@link ResponseEntity} with status 200 and a successful {@link ApiResponse}
     */
    default ResponseEntity<ApiResponse<Void>> ok(String message) {
        return ResponseEntity.ok(ApiResponse.successMessage(message));
    }

    /**
     * Build a successful HTTP 200 response with a custom message and a data payload.
     * @param message the success message to include in the API response body.
     * @param data the payload to include in the API response body.
     * @return a {@link ResponseEntity} with status 200 and a successful {@link ApiResponse}
     * @param <R> the payload type.
     */
    default <R> ResponseEntity<ApiResponse<R>> ok(String message, R data) {
        return  ResponseEntity.ok(ApiResponse.success(message, data));
    }

    /**
     * Builds a successful HTTP 201 response containing a data payload.
     *
     * @param data the created resource payload
     * @return a {@link ResponseEntity} with status 201 and a successful {@link ApiResponse}
     * @param <R> the payload type
     */
    default <R> ResponseEntity<ApiResponse<R>> created(R data) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(data));
    }

    /**
     * Builds a successful HTTP 201 response containing a message instead of a payload.
     *
     * @param message the message that we want to retrieve in the API response body
     * @return a {@link ResponseEntity} with status 201 and a successful {@link ApiResponse}
     */
    default ResponseEntity<ApiResponse<Void>> created() {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.successMessage("Resource data created successfully"));
    }
}
