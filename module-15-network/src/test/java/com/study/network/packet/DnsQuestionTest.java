package com.study.network.packet;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * DNS 查询记录测试：验证 QNAME 标签编码、QTYPE/QCLASS 与连续记录解析。
 */
class DnsQuestionTest {

    @Test
    @DisplayName("标签编码：www.example.com -> [3]www[7]example[3]com[0]")
    void labelEncoding() {
        byte[] encoded = DnsQuestion.encodeName("www.example.com");
        assertArrayEquals(
                new byte[]{3, 'w', 'w', 'w', 7, 'e', 'x', 'a', 'm', 'p', 'l', 'e',
                        3, 'c', 'o', 'm', 0},
                encoded);
        assertEquals(17, encoded.length, "3+1 + 7+1 + 3+1 + 1 = 17");
    }

    @Test
    @DisplayName("标签编码往返：任意多层域名编码后能解码回原样")
    void nameRoundTrip() {
        for (String name : new String[]{"example.com", "www.example.com", "a.b.c.d.e",
                "localhost", "mail.google.com"}) {
            assertEquals(name, DnsQuestion.decodeName(DnsQuestion.encodeName(name)));
        }
    }

    @Test
    @DisplayName("查询记录往返：A 记录、IN 类，编码后完整解析回来并给出可读描述")
    void questionRoundTrip() {
        DnsQuestion question = new DnsQuestion("www.example.com",
                DnsQuestion.QTYPE_A, DnsQuestion.QCLASS_IN);

        byte[] bytes = question.encode();
        DnsQuestion parsed = DnsQuestion.parse(bytes);
        assertEquals("www.example.com", parsed.name());
        assertEquals(DnsQuestion.QTYPE_A, parsed.qtype());
        assertEquals("A", parsed.qtypeName());
        assertEquals(DnsQuestion.QCLASS_IN, parsed.qclass());
        assertEquals("IN", parsed.qclassName());
        assertArrayEquals(bytes, parsed.encode());
    }

    @Test
    @DisplayName("AAAA/CNAME/MX 类型：qtypeName 描述正确")
    void otherTypes() {
        assertEquals("AAAA", new DnsQuestion("x.com", DnsQuestion.QTYPE_AAAA, 1).qtypeName());
        assertEquals("CNAME", new DnsQuestion("x.com", DnsQuestion.QTYPE_CNAME, 1).qtypeName());
        assertEquals("MX", new DnsQuestion("x.com", DnsQuestion.QTYPE_MX, 1).qtypeName());
    }

    @Test
    @DisplayName("带偏移解析：紧跟 12 字节头部之后，bytesConsumed 等于记录长度")
    void parseAtAfterHeader() {
        DnsHeader header = DnsHeader.query(0x1234, true, 1);
        DnsQuestion question = new DnsQuestion("www.example.com",
                DnsQuestion.QTYPE_A, DnsQuestion.QCLASS_IN);
        byte[] message = new byte[DnsHeader.HEADER_LENGTH + question.encode().length];
        System.arraycopy(header.encode(), 0, message, 0, DnsHeader.HEADER_LENGTH);
        System.arraycopy(question.encode(), 0, message, DnsHeader.HEADER_LENGTH,
                question.encode().length);

        DnsQuestion.ParsedQuestion parsed = DnsQuestion.parseAt(message, DnsHeader.HEADER_LENGTH);
        assertEquals("www.example.com", parsed.question().name());
        assertEquals(17 + 4, parsed.bytesConsumed(), "17 字节名字 + 4 字节类型/类");
        // 紧接其后的位置应能继续解析下一条（模拟 QDCOUNT>1）
        assertEquals(DnsHeader.HEADER_LENGTH + parsed.bytesConsumed(), message.length);
    }

    @Test
    @DisplayName("解析压缩指针被明确拒绝：0xC0 只出现在响应（Answer）中")
    void compressionPointerRejected() {
        byte[] bytes = new byte[]{(byte) 0xC0, 0x0C, 0x00, 0x01, 0x00, 0x01};
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> DnsQuestion.parse(bytes));
        assertTrue(e.getMessage().contains("压缩指针"));
    }

    @Test
    @DisplayName("非法域名被拒绝：空标签、超长标签（>63 字节）")
    void invalidNameRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> new DnsQuestion("", DnsQuestion.QTYPE_A, 1));
        assertThrows(IllegalArgumentException.class,
                () -> new DnsQuestion("a..b", DnsQuestion.QTYPE_A, 1));
        String tooLong = "a".repeat(64) + ".com";
        assertThrows(IllegalArgumentException.class,
                () -> new DnsQuestion(tooLong, DnsQuestion.QTYPE_A, 1));
        assertThrows(IllegalArgumentException.class,
                () -> DnsQuestion.encodeName("bad..label"));
    }

    @Test
    @DisplayName("标签未以 0x00 结束（报文被截断）时解析抛 IllegalArgumentException")
    void truncatedNameRejected() {
        // 只有标签没有结束符
        byte[] truncated = new byte[]{3, 'w', 'w', 'w'};
        assertThrows(IllegalArgumentException.class, () -> DnsQuestion.parse(truncated));
    }
}
