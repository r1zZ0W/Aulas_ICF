package mx.unam.icf.aulas.kernel.infrastructure.web.responses;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import mx.unam.icf.aulas.kernel.domain.exceptions.ErrorCode;

/**
 * This class serves with the purpose to unify the responses that the API gives to the client.
 * @param <T> It is a Generic that at compile time can determine the type that it passed right into it when they first call it.
 */
@Data @Builder
@AllArgsConstructor @NoArgsConstructor
public class ApiResponse<T> {
    private String message; // The message that will be displayed in the JSON.
    private T data; // The data that it will pass to the JSON, if none data is passed it will be null.
    private boolean error; // A flag to the JSON to determine if the response is an error or not.

    /**
     * Stable, machine-readable error identifier — see {@link ErrorCode}. Only present on
     * error responses; {@code @JsonInclude} keeps it out of success payloads so those keep
     * their exact current shape (including {@code "data": null}, which the frontend's Zod
     * response schemas already depend on).
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private ErrorCode code;

    /**
     * Creates a successful response with a default success message ("Operation successfully completed without any errors") and the provided data.
     * @param data The payload to include in the response.
     * @return An ApiResponse instance representing success.
     * @param <T> The type of the data payload.
     */
    public static <T> ApiResponse<T> success(T data) {
        return ApiResponse.<T>builder()
                .error(false)
                .message("Operation successfully completed without any errors.")
                .data(data)
                .build();
    }

    /**
     * Creates a successful response with a custom message and no data.
     * @param message A custom success message to be displayed.
     * @return An ApiResponse instance representing success.
     * @param <T> The type of the data payload.
     */
    public static <T> ApiResponse<T> successMessage(String message) {
        return ApiResponse.<T>builder()
                .error(false)
                .message(message)
                .data(null)
                .build();
    }

    /**
     * Creates a successful response with a custom message and the provided data.
     * @param message A custom success message to be displayed.
     * @param data The payload to include in the response.
     * @return An ApiResponse instance representing success.
     * @param <T> The type of the data payload.
     */
    public static <T> ApiResponse<T> success(String message, T data) {
        return ApiResponse.<T>builder()
                .error(false)
                .message(message)
                .data(data)
                .build();
    }

    /**
     * Creates an error response with a stable code and a debugging message, no data payload.
     * @param code The stable error identifier the client resolves to a user-facing message.
     * @param message A debugging message (logs only — never shown to the user as-is).
     * @return An ApiResponse instance representing an error.
     * @param <T> The type of the data payload (always null here).
     */
    public static <T> ApiResponse<T> error(ErrorCode code, String message) {
        return ApiResponse.<T>builder()
                .error(true)
                .code(code)
                .message(message)
                .data(null)
                .build();
    }

    /**
     * Creates an error response with a stable code, a debugging message, and a typed data
     * payload (e.g. {@code ConflictDetailDTO}, {@code ValidationErrorDTO}).
     * @param code The stable error identifier the client resolves to a user-facing message.
     * @param message A debugging message (logs only — never shown to the user as-is).
     * @param data The structured error payload to include in the response.
     * @return An ApiResponse instance representing an error.
     * @param <T> The type of the data payload.
     */
    public static <T> ApiResponse<T> error(ErrorCode code, String message, T data) {
        return ApiResponse.<T>builder()
                .error(true)
                .code(code)
                .message(message)
                .data(data)
                .build();
    }

}
