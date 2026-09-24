package com.yche.springmind.common.exception;

import com.yche.springmind.common.api.ApiResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

/**
 * 集中捕获控制器异常并转换为一致、安全且便于前端处理的 API 响应。
 *
 * <p>用于把业务失败表达为明确的异常语义，并由全局异常处理器转换为统一 API 响应。</p>
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 完成 {@code handleBusinessException} 对应的处理。
     *
     * @param exception 方法参数 {@code exception}
     * @return 方法执行结果，具体结构由返回类型 {@code ResponseEntity&lt;ApiResponse&lt;Void&gt;&gt;} 表示
     */
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusinessException(BusinessException exception) {
        HttpStatus status = switch (exception.getMessage()) {
            case "GLOBAL_BUSY", "USER_BUSY", "CONNECTION_BUSY", "SESSION_BUSY" -> HttpStatus.TOO_MANY_REQUESTS;
            default -> HttpStatus.BAD_REQUEST;
        };
        ResponseEntity.BodyBuilder response = ResponseEntity.status(status);
        if (status == HttpStatus.TOO_MANY_REQUESTS) {
            response.header(HttpHeaders.RETRY_AFTER, "1");
        }
        return response.body(new ApiResponse<>(false, null, exception.getMessage()));
    }

    /**
     * 完成 {@code handleForbiddenException} 对应的处理。
     * <p>
     * 实现要点：校验群组成员关系和角色权限。
     *
     * @param exception 方法参数 {@code exception}
     * @return 方法执行结果，具体结构由返回类型 {@code ApiResponse&lt;Void&gt;} 表示
     */
    @ExceptionHandler(ForbiddenException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public ApiResponse<Void> handleForbiddenException(ForbiddenException exception) {
        return new ApiResponse<>(false, null, exception.getMessage());
    }

    /**
     * 完成 {@code handleUnauthorizedException} 对应的处理。
     *
     * @param exception 方法参数 {@code exception}
     * @return 方法执行结果，具体结构由返回类型 {@code ApiResponse&lt;Void&gt;} 表示
     */
    @ExceptionHandler(UnauthorizedException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public ApiResponse<Void> handleUnauthorizedException(UnauthorizedException exception) {
        return new ApiResponse<>(false, null, exception.getMessage());
    }

    /**
     * 完成 {@code handleMethodArgumentNotValidException} 对应的处理。
     *
     * @param exception 方法参数 {@code exception}
     * @return 方法执行结果，具体结构由返回类型 {@code ApiResponse&lt;Void&gt;} 表示
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiResponse<Void> handleMethodArgumentNotValidException(
            MethodArgumentNotValidException exception
    ) {
        String message = exception.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(FieldError::getDefaultMessage)
                .orElse("请求参数校验失败");
        return new ApiResponse<>(false, null, message);
    }

    /**
     * 完成 {@code handleHttpMessageNotReadableException} 对应的处理。
     *
     * @param exception 方法参数 {@code exception}
     * @return 方法执行结果，具体结构由返回类型 {@code ApiResponse&lt;Void&gt;} 表示
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiResponse<Void> handleHttpMessageNotReadableException(
            HttpMessageNotReadableException exception
    ) {
        return new ApiResponse<>(false, null, "请求体格式非法");
    }

    /**
     * 完成 {@code handleMaxUploadSizeExceededException} 对应的处理。
     *
     * @param exception 方法参数 {@code exception}
     * @return 方法执行结果，具体结构由返回类型 {@code ApiResponse&lt;Void&gt;} 表示
     */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    @ResponseStatus(HttpStatus.PAYLOAD_TOO_LARGE)
    public ApiResponse<Void> handleMaxUploadSizeExceededException(
            MaxUploadSizeExceededException exception
    ) {
        return new ApiResponse<>(false, null, "上传文件超过大小限制");
    }
}
