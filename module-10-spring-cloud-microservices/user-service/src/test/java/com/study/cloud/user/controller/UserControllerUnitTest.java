package com.study.cloud.user.controller;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** 用户微服务 Controller 纯单元测试，不启动 Eureka。 */
class UserControllerUnitTest {

    @Test
    void getUserShouldBuildUserResponseFromId() {
        Map<String, Object> result = new UserController().getUser(7L);

        assertThat(result)
                .containsEntry("id", 7L)
                .containsEntry("name", "用户7")
                .containsEntry("email", "user7@example.com")
                .containsEntry("from", "user-service");
    }
}
