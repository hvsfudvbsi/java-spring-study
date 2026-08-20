package com.study.network;

import com.study.network.packet.EthernetFrame;
import com.study.network.packet.IcmpHeader;
import com.study.network.packet.IpHeader;
import com.study.network.packet.PacketParser;
import com.study.network.packet.TcpHeader;
import com.study.network.packet.UdpHeader;
import com.study.network.protocol.TcpStateMachine;
import com.study.network.protocol.TransportProtocol;
import com.study.network.socket.TcpStickyPacketDemo;

/**
 * module-15-network 总入口：一次运行展示所有核心知识点。
 *
 * 运行：mvn compile exec:java -pl module-15-network -Dexec.mainClass=com.study.network.Main
 *
 * 展示内容：
 *   1. TCP/UDP 协议对比表
 *   2. TCP 首部 20 字节编码与解析（含数据偏移/标志位）
 *   3. UDP 首部 8 字节编码与解析
 *   4. IPv4 首部编码与解析（版本/IHL/地址）
 *   5. 以太网帧头 14 字节
 *   5.1 ICMP 首部（ping 请求，类型/代码/校验和）
 *   6. 完整报文分层解析（以太网 -> IP -> TCP/UDP/ICMP）
 *   7. TCP 三次握手/四次挥手状态机演示
 *   8. TCP 粘包 vs UDP 有边界演示
 */
public class Main {

    public static void main(String[] args) throws Exception {
        // 1. TCP vs UDP 对比
        TransportProtocol.printComparison();
        System.out.println();

        // 2. TCP 首部：模拟一次握手报文（SYN, seq=1000）
        TcpHeader syn = new TcpHeader(12345, 80, 1000, 0,
                5, false, true, false, false, false,
                65535, 0, 0);
        byte[] tcpBytes = syn.encode();
        System.out.println("TCP SYN 报文 " + tcpBytes.length + " 字节: " + syn);
        TcpHeader parsedTcp = TcpHeader.parse(tcpBytes);
        System.out.println("解析回: " + parsedTcp);
        System.out.println();

        // 3. UDP 首部：DNS 查询 53 端口
        UdpHeader udp = new UdpHeader(53000, 53, 8 + 12, 0);
        byte[] udpBytes = udp.encode();
        System.out.println("UDP 报文 " + udpBytes.length + " 字节: " + udp);
        System.out.println("解析回: " + UdpHeader.parse(udpBytes));
        System.out.println();

        // 4. IPv4 首部
        IpHeader ip = new IpHeader(4, 5, 20 + tcpBytes.length, 1,
                64, IpHeader.PROTOCOL_TCP, 0,
                IpHeader.parseIp("192.168.1.10"), IpHeader.parseIp("93.184.216.34"));
        byte[] ipBytes = ip.encode();
        System.out.println("IP 首部 " + ipBytes.length + " 字节: " + ip);
        System.out.println("解析回: " + IpHeader.parse(ipBytes));
        System.out.println();

        // 5. 以太网帧头
        EthernetFrame frame = new EthernetFrame(
                EthernetFrame.parseMac("FF:FF:FF:FF:FF:FF"),
                EthernetFrame.parseMac("AA:BB:CC:DD:EE:FF"),
                EthernetFrame.ETHERTYPE_IPV4);
        System.out.println("以太网帧头 " + frame.encode().length + " 字节: " + frame);
        System.out.println();

        // 5.1 ICMP 首部：ping 请求（类型 8）
        IcmpHeader icmp = new IcmpHeader(IcmpHeader.TYPE_ECHO_REQUEST, 0,
                0xABCD, 0x0001, 1);
        byte[] icmpBytes = icmp.encode();
        System.out.println("ICMP 首部 " + icmpBytes.length + " 字节: " + icmp);
        System.out.println("解析回: " + IcmpHeader.parse(icmpBytes));
        System.out.println();

        // 6. 完整报文分层解析：以太网(14) + IP(20) + TCP(20) + 负载
        byte[] full = concat(frame.encode(), ipBytes, tcpBytes, "GET / HTTP/1.1".getBytes());
        PacketParser.ParsedPacket parsed = PacketParser.parse(full);
        System.out.println("完整报文 " + full.length + " 字节，分层解析:");
        System.out.println("  第 1 层 以太网: " + parsed.ethernet());
        System.out.println("  第 2 层 IP:     " + parsed.ip());
        System.out.println("  第 3 层 传输:   " + parsed.transport());
        System.out.println("  负载 " + parsed.payloadLength() + " 字节");
        System.out.println();

        // 7. TCP 三次握手 / 四次挥手状态机
        TcpStateMachine.printHandshakeDemo();
        System.out.println();

        // 8. TCP 粘包 vs UDP 有边界
        TcpStickyPacketDemo.main(new String[]{});
    }

    private static byte[] concat(byte[]... arrays) {
        int total = 0;
        for (byte[] array : arrays) {
            total += array.length;
        }
        byte[] result = new byte[total];
        int offset = 0;
        for (byte[] array : arrays) {
            System.arraycopy(array, 0, result, offset, array.length);
            offset += array.length;
        }
        return result;
    }
}
