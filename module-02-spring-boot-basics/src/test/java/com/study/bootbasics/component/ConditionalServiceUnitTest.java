package com.study.bootbasics.component;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** 条件 Bean 创建后的业务功能单元测试。 */
class ConditionalServiceUnitTest {

    @Test
    void featureInfoShouldDescribeEnabledFeature() {
        assertThat(new ConditionalService().featureInfo())
                .isEqualTo("条件化功能已开启");
    }
}
