package com.study.mvc.model;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 请求/响应模型：record + Bean Validation 注解
 *
 * 校验注解（jakarta.validation.constraints，即 JSR-380）：
 *   @NotBlank      字符串非空（去空格后）
 *   @NotNull       非 null
 *   @Size          长度范围
 *   @Min / @Max    数值范围
 *   @Email         邮箱格式
 *   @Pattern       正则匹配
 *
 * 注意：校验注解只有配合 @Valid（方法参数）或 @Validated（类级别）才生效。
 * 分组校验：@Validated(UpdateGroup.class) 可以按场景启用不同规则。
 */
public record User(
        Long id,

        @NotBlank(message = "姓名不能为空")
        @Size(min = 2, max = 20, message = "姓名长度必须在 2-20 之间")
        String name,

        @NotBlank(message = "邮箱不能为空")
        @Email(message = "邮箱格式不正确")
        String email,

        @NotNull(message = "年龄不能为空")
        @Min(value = 1, message = "年龄最小为 1")
        @Max(value = 150, message = "年龄最大为 150")
        Integer age,

        @Pattern(regexp = "^1[3-9]\\d{9}$", message = "手机号格式不正确")
        String phone
) {
}
