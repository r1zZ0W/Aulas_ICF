package mx.unam.icf.aulas.kernel.infrastructure.web.responses;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

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
     * Creates an error response with a custom error message.
     * @param message A custom error message indicating what went wrong.
     * @return An ApiResponse instance representing an error.
     * @param <T> The type of the data payload (usually null in case of error).
     */
    public static <T> ApiResponse<T> error(String message) {
        return ApiResponse.<T>builder()
                .error(true)
                .message(message)
                .data(null)
                .build();
    }

}
