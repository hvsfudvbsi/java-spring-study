package com.study.network.packet;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 以太网帧头测试：14 字节固定长度、MAC 地址转换、EtherType。
 */
class EthernetFrameTest {

    @Test
    @DisplayName("以太网帧头往返：14 字节、MAC 与 EtherType 完整解析")
    void roundTrip() {
        EthernetFrame original = new EthernetFrame(
                EthernetFrame.parseMac("FF:FF:FF:FF:FF:FF"),
                EthernetFrame.parseMac("AA:BB:CC:DD:EE:FF"),
                EthernetFrame.ETHERTYPE_IPV4);

        byte[] bytes = original.encode();
        assertEquals(14, bytes.length, "以太网帧头固定 14 字节");

        EthernetFrame parsed = EthernetFrame.parse(bytes);
        assertEquals("FF:FF:FF:FF:FF:FF", EthernetFrame.toMacString(parsed.destinationMac()));
        assertEquals("AA:BB:CC:DD:EE:FF", EthernetFrame.toMacString(parsed.sourceMac()));
        assertEquals(0x0800, parsed.etherType(), "EtherType 0x0800 = IPv4");
    }

    @Test
    @DisplayName("EtherType 0x0806 表示 ARP 报文")
    void arpEtherType() {
        EthernetFrame frame = new EthernetFrame(
                EthernetFrame.parseMac("00:11:22:33:44:55"),
                EthernetFrame.parseMac("66:77:88:99:AA:BB"),
                EthernetFrame.ETHERTYPE_ARP);
        assertEquals(0x0806, EthernetFrame.parse(frame.encode()).etherType());
    }

    @Test
    @DisplayName("MAC 地址大小写不敏感且长度必须为 6 字节")
    void macParsing() {
        assertEquals("AA:BB:CC:DD:EE:FF",
                EthernetFrame.toMacString(EthernetFrame.parseMac("aa:bb:cc:dd:ee:ff")));
        assertThrows(IllegalArgumentException.class,
                () -> new EthernetFrame(new byte[5], new byte[6], 0x0800));
        assertThrows(IllegalArgumentException.class, () -> EthernetFrame.parseMac("AA:BB:CC"));
    }

    @Test
    @DisplayName("字节数不足 14 时解析抛 IllegalArgumentException")
    void parseRejectsShortBuffer() {
        assertThrows(IllegalArgumentException.class, () -> EthernetFrame.parse(new byte[13]));
    }
}
