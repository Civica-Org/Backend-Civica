package cm.civica.backend.exception;

import org.springframework.http.HttpStatus;

/**
 * Exception métier de base pour CIVICA.
 * Capturée par GlobalExceptionHandler pour retourner une réponse JSON propre.
 */
public class BusinessException extends RuntimeException {

    private final HttpStatus status;
    private final java.util.Map<String, Object> data;

    public BusinessException(String message, HttpStatus status) {
        super(message);
        this.status = status;
        this.data = null;
    }

    public BusinessException(String message, HttpStatus status, java.util.Map<String, Object> data) {
        super(message);
        this.status = status;
        this.data = data;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public java.util.Map<String, Object> getData() {
        return data;
    }

    /** 400 Bad Request */
    public static BusinessException badRequest(String message) {
        return new BusinessException(message, HttpStatus.BAD_REQUEST);
    }

    /** 401 Unauthorized */
    public static BusinessException unauthorized(String message) {
        return new BusinessException(message, HttpStatus.UNAUTHORIZED);
    }

    /** 403 Forbidden */
    public static BusinessException forbidden(String message) {
        return new BusinessException(message, HttpStatus.FORBIDDEN);
    }

    /** 404 Not Found */
    public static BusinessException notFound(String message) {
        return new BusinessException(message, HttpStatus.NOT_FOUND);
    }

    /** 409 Conflict */
    public static BusinessException conflict(String message) {
        return new BusinessException(message, HttpStatus.CONFLICT);
    }

    /** 500 Internal Server Error */
    public static BusinessException internalServerError(String message) {
        return new BusinessException(message, HttpStatus.INTERNAL_SERVER_ERROR);
    }
}

