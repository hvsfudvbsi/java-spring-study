package com.study.mvc.exception;

import java.time.LocalDateTime;

/**
 * 统一错误响应体：所有异常都转换为这个结构返回给前端
 *
 * 设计规范（REST API 最佳实践）：
 *   - 错误响应与业务响应结构分离
 *   - 包含：时间戳、HTTP 状态码、业务错误码、错误信息、可选详情
 */
public record ErrorResponse(
        LocalDateTime timestamp,
        int status,
        String error,
        String message,
        String path
) {

    public static ErrorResponse of(int status, String error, String message, String path) {
        return new ErrorResponse(LocalDateTime.now(), status, error, message, path);
    }
}
