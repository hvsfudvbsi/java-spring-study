package com.study.bc.gm;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 国密专题冒烟测试：验证演示流程不抛异常且关键断言全部通过。 */
class GmDemoTest {

    @Test
    @DisplayName("国密信封冒烟：演示完整执行，SM2/SM3/SM4 关键断言全部通过")
    void demoRunsClean() {
        // 捕获输出，验证演示完整执行
        PrintStream original = System.out;
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        System.setOut(new PrintStream(buffer, true, StandardCharsets.UTF_8));
        try {
            GmDemo.demo();
        } finally {
            System.setOut(original);
        }
        String output = buffer.toString(StandardCharsets.UTF_8);
        assertTrue(output.contains("SM2 解出密钥一致: true"));
        assertTrue(output.contains("SM4 解出明文一致: true"));
        assertTrue(output.contains("SM3 摘要一致    : true"));
        assertTrue(output.contains("SM2 验签通过    : true"));
    }
}
