package com.study.network.packet;

/**
 * 完整报文分层解析器：模拟抓包工具（如 Wireshark）从原始字节流中逐层解析。
 *
 * 数据封装顺序（发送方）：
 * <pre>
 *   应用数据 -> TCP/UDP/ICMP 首部 -> IP 首部 -> 以太网帧头 -> 网线
 *   （ARP 例外：直接封装在以太网帧里，不经过 IP 层）
 * </pre>
 * 数据解封装顺序（接收方）：
 * <pre>
 *   网线 -> 以太网帧头 -> （按 EtherType 分派）-> IP 首部 -> TCP/UDP/ICMP 首部 -> 应用数据
 *                     \-> ARP 报文（不经过 IP）
 * </pre>
 *
 * 本类演示"从最外层剥到最内层"的解析过程：先看以太网帧头的 EtherType 决定下一层是谁，
 * 再按 IP 首部的协议号决定传输层是谁，配合 TcpHeader/UdpHeader/IcmpHeader/ArpHeader 理解
 * 每一层的首部字段和首部长度。
 */
public class PacketParser {

    /** 解析结果：包含各层首部与负载长度。ARP 帧的 ip() 为 null，ARP 报文放在 transport()。 */
    public record ParsedPacket(EthernetFrame ethernet, IpHeader ip,
                               Object transport, int payloadLength) {

        public boolean isTcp() {
            return transport instanceof TcpHeader;
        }

        public boolean isUdp() {
            return transport instanceof UdpHeader;
        }

        public boolean isIcmp() {
            return transport instanceof IcmpHeader;
        }

        public boolean isArp() {
            return transport instanceof ArpHeader;
        }
    }

    /**
     * 把以太网帧头 + 网络层 + 传输层 + 负载 解析为分层结构。
     * 偏移量依次累加，模拟真实网络中逐层剥离的过程。
     * 第 1 层之后先看 EtherType 分派：0x0800=IPv4（再按协议号分派 TCP/UDP/ICMP）、
     * 0x0806=ARP（直接解析，不经过 IP 层）。
     */
    public static ParsedPacket parse(byte[] frame) {
        // 第 1 层：以太网帧头（14 字节）
        EthernetFrame ethernet = EthernetFrame.parse(frame);
        int offset = EthernetFrame.HEADER_LENGTH;

        // 第 2 层：按 EtherType 分派（模拟真实网卡/抓包工具的协议识别）
        if (ethernet.etherType() == EthernetFrame.ETHERTYPE_ARP) {
            ArpHeader arp = ArpHeader.parse(frame, offset);
            return new ParsedPacket(ethernet, null, arp,
                    frame.length - offset - ArpHeader.HEADER_LENGTH);
        }
        if (ethernet.etherType() != EthernetFrame.ETHERTYPE_IPV4) {
            throw new IllegalArgumentException("不支持的 EtherType: 0x"
                    + Integer.toHexString(ethernet.etherType()));
        }

        // 第 2 层：IP 首部（默认 20 字节，IHL 决定实际长度）
        IpHeader ip = IpHeader.parse(frame, offset);
        offset += ip.headerLength();

        // 第 3 层：按协议号分派到 TCP / UDP / ICMP
        Object transport;
        int payloadLength;
        if (ip.protocol() == IpHeader.PROTOCOL_TCP) {
            TcpHeader tcp = TcpHeader.parse(frame, offset);
            transport = tcp;
            payloadLength = frame.length - offset - tcp.headerLength();
        } else if (ip.protocol() == IpHeader.PROTOCOL_UDP) {
            UdpHeader udp = UdpHeader.parse(frame, offset);
            transport = udp;
            payloadLength = udp.payloadLength();
        } else if (ip.protocol() == IpHeader.PROTOCOL_ICMP) {
            // ICMP 首部固定 8 字节；负载是诊断数据（如 ping 的时间戳）
            IcmpHeader icmp = IcmpHeader.parse(frame, offset);
            transport = icmp;
            payloadLength = frame.length - offset - IcmpHeader.HEADER_LENGTH;
        } else {
            throw new IllegalArgumentException("不支持的协议号: " + ip.protocol());
        }

        return new ParsedPacket(ethernet, ip, transport, payloadLength);
    }
}
