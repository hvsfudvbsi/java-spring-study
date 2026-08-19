package com.study.mvc.exception;

/**
 * 自定义业务异常：业务逻辑"预期内"的失败用自定义异常表达，
 * 由全局异常处理器统一转换为 HTTP 响应（404）。
 *
 * 最佳实践：
 *   - 业务异常继承 RuntimeException，避免方法签名到处 throws
 *   - 不要用 Exception/Throwable 表达业务失败
 */
public class UserNotFoundException extends RuntimeException {

    public UserNotFoundException(Long id) {
        super("用户不存在: id=" + id);
    }
}
