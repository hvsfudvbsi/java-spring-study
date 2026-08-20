package com.study.network.packet;

/**
 * 完整报文分层解析器：模拟抓包工具（如 Wireshark）从原始字节流中逐层解析。
 *
 * 数据封装顺序（发送方）：
 * <pre>
 *   应用数据 -> TCP/UDP 首部 -> IP 首部 -> 以太网帧头 -> 网线
 * </pre>
 * 数据解封装顺序（接收方）：
 * <pre>
 *   网线 -> 以太网帧头 -> IP 首部 -> TCP/UDP 首部 -> 应用数据
 * </pre>
 *
 * 本类演示"从最外层剥到最内层"的解析过程，配合 TcpHeader/IpHeader 等理解
 * 每一层的首部字段和首部长度。
 */
public class PacketParser {

    /** 解析结果：包含各层首部与负载长度 */
    public record ParsedPacket(EthernetFrame ethernet, IpHeader ip,
                               Object transport, int payloadLength) {

        public boolean isTcp() {
            return transport instanceof TcpHeader;
        }

        public boolean isUdp() {
            return transport instanceof UdpHeader;
        }
    }

    /**
     * 把以太网帧头 + IP 首部 + 传输层首部 + 负载 解析为分层结构。
     * 偏移量依次累加，模拟真实网络中逐层剥离的过程。
     */
    public static ParsedPacket parse(byte[] frame) {
        // 第 1 层：以太网帧头（14 字节）
        EthernetFrame ethernet = EthernetFrame.parse(frame);
        int offset = EthernetFrame.HEADER_LENGTH;

        // 第 2 层：IP 首部（默认 20 字节，IHL 决定实际长度）
        IpHeader ip = IpHeader.parse(frame, offset);
        offset += ip.headerLength();

        // 第 3 层：按协议号分派到 TCP 或 UDP
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
        } else {
            throw new IllegalArgumentException("不支持的协议号: " + ip.protocol());
        }

        return new ParsedPacket(ethernet, ip, transport, payloadLength);
    }
}
