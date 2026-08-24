package com.study.network.packet;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * DNS 头部测试：验证 12 字节固定格式、标志位打包、计数与响应码。
 */
class DnsHeaderTest {

    @Test
    @DisplayName("查询头部往返：id=0x1234、RD 置位、QDCOUNT=1，编码后完整解析回来")
    void queryRoundTrip() {
        DnsHeader query = DnsHeader.query(0x1234, true, 1);

        byte[] bytes = query.encode();
        assertEquals(12, bytes.length, "DNS 头部固定 12 字节");

        DnsHeader parsed = DnsHeader.parse(bytes);
        assertEquals(0x1234, parsed.id());
        assertFalse(parsed.response(), "QR=0 表示查询");
        assertEquals(0, parsed.opcode(), "标准查询 opcode=0");
        assertTrue(parsed.recursionDesired(), "RD 应置位");
        assertFalse(parsed.recursionAvailable());
        assertFalse(parsed.truncated());
        assertEquals(0, parsed.rcode());
        assertEquals(1, parsed.questionCount());
        assertEquals(0, parsed.answerCount());
        assertEquals(0, parsed.authorityCount());
        assertEquals(0, parsed.additionalCount());
    }

    @Test
    @DisplayName("标志位字节布局：查询 id=0x1234 RD 置位 -> 12 34 01 00 ...")
    void flagBitLayout() {
        DnsHeader query = DnsHeader.query(0x1234, true, 1);
        byte[] bytes = query.encode();
        assertEquals(0x12, bytes[0] & 0xFF);
        assertEquals(0x34, bytes[1] & 0xFF);
        assertEquals(0x01, bytes[2] & 0xFF, "RD=0x0100 -> 高字节 0x01");
        assertEquals(0x00, bytes[3] & 0xFF);

        DnsHeader noRd = DnsHeader.query(0x0001, false, 1);
        assertEquals(0x00, noRd.encode()[2] & 0xFF, "不设 RD 时标志高字节为 0");
    }

    @Test
    @DisplayName("响应头部往返：QR=1、RA 置位、rcode=0、ANCOUNT=1")
    void responseRoundTrip() {
        DnsHeader response = DnsHeader.response(0x1234, true, 0, 1, 1);

        DnsHeader parsed = DnsHeader.parse(response.encode());
        assertTrue(parsed.response(), "QR=1 表示响应");
        assertTrue(parsed.recursionAvailable(), "RA 应置位");
        assertEquals(0, parsed.rcode());
        assertEquals("NOERROR（无错误）", parsed.rcodeName());
        assertEquals(1, parsed.questionCount());
        assertEquals(1, parsed.answerCount());
    }

    @Test
    @DisplayName("NXDOMAIN 响应：rcode=3 正确解析并给出可读描述")
    void nxdomainResponse() {
        DnsHeader response = DnsHeader.response(0xABCD, true, 3, 1, 0);
        assertEquals(3, DnsHeader.parse(response.encode()).rcode());
        assertEquals("NXDOMAIN（域名不存在）", DnsHeader.parse(response.encode()).rcodeName());
    }

    @Test
    @DisplayName("TC 截断标志：TC 置位时标志字节为 0x02，解析回来一致")
    void truncatedFlag() {
        DnsHeader truncated = new DnsHeader(1, true, 0, false, true,
                false, true, 0, 1, 0, 0, 0);
        byte[] bytes = truncated.encode();
        assertEquals(0x82, bytes[2] & 0xFF, "RA(0x80) | TC(0x02) = 0x82");
        DnsHeader parsed = DnsHeader.parse(bytes);
        assertTrue(parsed.truncated());
        assertTrue(parsed.recursionAvailable());
    }

    @Test
    @DisplayName("带偏移解析：从 12 字节之后的偏移处提取头部（查询报文整体场景）")
    void parseWithOffset() {
        DnsHeader query = DnsHeader.query(0x5678, true, 1);
        byte[] padded = new byte[4 + 12];
        System.arraycopy(query.encode(), 0, padded, 4, 12);

        DnsHeader parsed = DnsHeader.parse(padded, 4);
        assertEquals(0x5678, parsed.id());
        assertTrue(parsed.recursionDesired());
    }

    @Test
    @DisplayName("非法参数被拒绝：opcode/rcode 超出 4 bit")
    void invalidParametersRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> new DnsHeader(1, false, 16, false, false, false, false, 0, 0, 0, 0, 0));
        assertThrows(IllegalArgumentException.class,
                () -> new DnsHeader(1, false, 0, false, false, false, false, 16, 0, 0, 0, 0));
    }

    @Test
    @DisplayName("字节数不足 12 时解析抛 IllegalArgumentException")
    void parseRejectsShortBuffer() {
        assertThrows(IllegalArgumentException.class, () -> DnsHeader.parse(new byte[11]));
    }
}
