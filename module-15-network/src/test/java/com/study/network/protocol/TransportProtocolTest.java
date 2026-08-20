package com.study.network.protocol;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * TCP/UDP 属性对比测试：验证枚举中各协议的关键特性与首部长度。
 */
class TransportProtocolTest {

    @Test
    @DisplayName("TCP 面向连接可靠有序，首部 20 字节")
    void tcpCharacteristics() {
        TransportProtocol tcp = TransportProtocol.TCP;
        assertTrue(tcp.connection().contains("面向连接"));
        assertTrue(tcp.reliability().contains("可靠"));
        assertTrue(tcp.ordering().contains("有序"));
        assertTrue(tcp.messageBoundary().contains("字节流"));
        assertTrue(tcp.headerOverhead().contains("20"));
        assertEquals(20, tcp.headerLength());
        assertEquals(6, tcp.protocolNumber());
    }

    @Test
    @DisplayName("UDP 无连接不可靠无边界，首部固定 8 字节")
    void udpCharacteristics() {
        TransportProtocol udp = TransportProtocol.UDP;
        assertTrue(udp.connection().contains("无连接"));
        assertTrue(udp.reliability().contains("不可靠"));
        assertTrue(udp.ordering().contains("乱序"));
        assertTrue(udp.messageBoundary().contains("数据报"));
        assertTrue(udp.headerOverhead().contains("8"));
        assertEquals(8, udp.headerLength());
        assertEquals(17, udp.protocolNumber());
    }

    @Test
    @DisplayName("TCP 首部开销大于 UDP：20 vs 8 字节")
    void tcpHeaderIsLargerThanUdp() {
        assertTrue(TransportProtocol.TCP.headerLength() > TransportProtocol.UDP.headerLength());
    }

    @Test
    @DisplayName("按协议号反查：6=TCP、17=UDP，未知协议号抛异常")
    void fromProtocolNumber() {
        assertEquals(TransportProtocol.TCP, TransportProtocol.fromProtocolNumber(6));
        assertEquals(TransportProtocol.UDP, TransportProtocol.fromProtocolNumber(17));
        assertThrows(IllegalArgumentException.class,
                () -> TransportProtocol.fromProtocolNumber(99));
    }
}
