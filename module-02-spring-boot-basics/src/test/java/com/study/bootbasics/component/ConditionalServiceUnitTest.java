package com.study.bootbasics.component;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** 条件 Bean 创建后的业务功能单元测试。 */
class ConditionalServiceUnitTest {

    @Test
    @DisplayName("条件化功能开启时 featureInfo 返回启用说明")
    void featureInfoShouldDescribeEnabledFeature() {
        assertThat(new ConditionalService().featureInfo())
                .isEqualTo("条件化功能已开启");
    }
}
