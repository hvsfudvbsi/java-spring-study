package com.study.network.protocol;

import com.study.network.packet.IpHeader;
import com.study.network.packet.TcpHeader;
import com.study.network.packet.UdpHeader;

/**
 * TCP 与 UDP 传输层协议对比——计算机网络最高频面试题。
 *
 * 一句话总结：TCP 是"可靠的、面向连接的字节流"，UDP 是"不可靠的、无连接的数据报"。
 */
public enum TransportProtocol {

    /**
     * 传输控制协议（Transmission Control Protocol）
     */
    TCP(
            "TCP",
            "面向连接：三次握手建立连接，四次挥手释放连接",
            "可靠：确认、重传、排序、去重、流量控制、拥塞控制",
            "有序：按发送顺序交付",
            "字节流：无消息边界，应用层自行定义帧格式（粘包拆包问题）",
            "最少 20 字节 + 选项",
            "文件传输(FTP/HTTP)、邮件(SMTP)、网页(HTTPS)、数据库",
            IpHeader.PROTOCOL_TCP
    ),

    /**
     * 用户数据报协议（User Datagram Protocol）
     */
    UDP(
            "UDP",
            "无连接：不需要握手，发完即走",
            "不可靠：不保证送达、顺序和去重（丢包不重传）",
            "无序：可能乱序到达",
            "数据报：一次 send 对应一次 receive，天然有消息边界",
            "固定 8 字节",
            "实时音视频(RTP)、DNS 查询、DHCP、游戏、NTP 时间同步",
            IpHeader.PROTOCOL_UDP
    );

    private final String name;
    /** 连接特性 */
    private final String connection;
    /** 可靠性 */
    private final String reliability;
    /** 有序性 */
    private final String ordering;
    /** 消息边界 */
    private final String messageBoundary;
    /** 首部开销 */
    private final String headerOverhead;
    /** 典型应用 */
    private final String typicalUses;
    /** IP 协议号 */
    private final int protocolNumber;

    TransportProtocol(String name, String connection, String reliability,
                      String ordering, String messageBoundary,
                      String headerOverhead, String typicalUses,
                      int protocolNumber) {
        this.name = name;
        this.connection = connection;
        this.reliability = reliability;
        this.ordering = ordering;
        this.messageBoundary = messageBoundary;
        this.headerOverhead = headerOverhead;
        this.typicalUses = typicalUses;
        this.protocolNumber = protocolNumber;
    }

    public String displayName() {
        return name;
    }

    public String connection() {
        return connection;
    }

    public String reliability() {
        return reliability;
    }

    public String ordering() {
        return ordering;
    }

    public String messageBoundary() {
        return messageBoundary;
    }

    public String headerOverhead() {
        return headerOverhead;
    }

    public String typicalUses() {
        return typicalUses;
    }

    public int protocolNumber() {
        return protocolNumber;
    }

    /** 按 IP 协议号反查协议（6=TCP, 17=UDP） */
    public static TransportProtocol fromProtocolNumber(int protocolNumber) {
        for (TransportProtocol p : values()) {
            if (p.protocolNumber == protocolNumber) {
                return p;
            }
        }
        throw new IllegalArgumentException("未知协议号: " + protocolNumber);
    }

    /**
     * 返回首部大小（字节）：
     * - TCP 无选项时 20 字节，dataOffset 可计算实际长度
     * - UDP 固定 8 字节
     */
    public int headerLength() {
        return switch (this) {
            case TCP -> TcpHeader.FIXED_HEADER_LENGTH;
            case UDP -> UdpHeader.HEADER_LENGTH;
        };
    }

    /** 打印完整对比表（供 Main 演示） */
    public static void printComparison() {
        System.out.println("================ TCP vs UDP ================");
        System.out.printf("%-8s | %-40s | %-40s%n", "特性", "TCP", "UDP");

        System.out.println("-".repeat(100));
        for (String[] row : new String[][]{
                {"连接方式", TCP.connection, UDP.connection},
                {"可靠性", TCP.reliability, UDP.reliability},
                {"有序性", TCP.ordering, UDP.ordering},
                {"消息边界", TCP.messageBoundary, UDP.messageBoundary},
                {"首部开销", TCP.headerOverhead, UDP.headerOverhead},
                {"典型应用", TCP.typicalUses, UDP.typicalUses},
        }) {
            System.out.printf("%-8s | %-40s | %-40s%n", row[0], row[1], row[2]);
        }
        System.out.println("首部长度对比: TCP " + TCP.headerLength()
                + " 字节 vs UDP " + UDP.headerLength() + " 字节");
    }
}
