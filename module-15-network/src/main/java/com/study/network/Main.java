package com.study.network;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.study.network.ip.SubnetCalculator;
import com.study.network.packet.ArpHeader;
import com.study.network.packet.Checksums;
import com.study.network.packet.DnsHeader;
import com.study.network.packet.DnsQuestion;
import com.study.network.packet.EthernetFrame;
import com.study.network.packet.HttpRequest;
import com.study.network.packet.HttpResponse;
import com.study.network.packet.IcmpHeader;
import com.study.network.packet.IpHeader;
import com.study.network.packet.PacketParser;
import com.study.network.packet.SackBlock;
import com.study.network.packet.TcpHeader;
import com.study.network.packet.TcpOption;
import com.study.network.packet.UdpHeader;
import com.study.network.protocol.TcpCongestionControl;
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
 *   2.1 TCP 校验和（伪首部 + 首部 + 数据）
 *   2.2 TCP 选项（SYN 携带 MSS=1460，数据偏移自动变大）
 *   2.3 SACK 块（乱序确认区间，丢包只重传丢失段）
 *   3. UDP 首部 8 字节编码与解析（含 UDP 校验和）
 *   4. IPv4 首部编码与解析（版本/IHL/地址）
 *   4.1 IP 分片字段（标识/标志 MF/片偏移）
 *   5. 以太网帧头 14 字节
 *   5.1 ICMP 首部（ping 请求，类型/代码/校验和）
 *   6. 完整报文分层解析（以太网 -> IP -> TCP/UDP/ICMP）
 *   6.1 ARP 报文解析（EtherType=0x0806，不经过 IP 层）
 *   6.2 DNS 查询报文（12 字节头部 + QNAME 标签编码的查询记录）
 *   6.3 HTTP 请求/响应报文解析（应用层收尾：请求行 + 状态行 + 头部）
 *   7. TCP 状态机演示：三次握手/四次挥手 + RST 连接重置
 *      + 半开连接检测（SYN 重传超时）+ keep-alive 假死检测（探测超时）
 *   8. TCP 粘包 vs UDP 有边界演示
 *   9. IP 子网划分/CIDR 计算演示
 *   10. TCP 拥塞控制（慢启动/拥塞避免/超时/快重传）演示
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

        // 2.1 TCP 校验和：必须计算，覆盖「伪首部(源/目的 IP + 协议号 + TCP 长度) + 首部 + 数据」
        int srcIp = IpHeader.parseIp("192.168.1.10");
        int dstIp = IpHeader.parseIp("93.184.216.34");
        byte[] payload = "GET / HTTP/1.1".getBytes();
        int tcpChecksum = syn.computeChecksum(srcIp, dstIp, payload);
        System.out.println("TCP 校验和（伪首部 + SYN + 数据 " + payload.length + " 字节）: 0x"
                + String.format("%04X", tcpChecksum));
        TcpHeader synWithChecksum = syn.withValidChecksum(srcIp, dstIp, payload);
        System.out.println("校验通过（整体反码和为 0xFFFF）: "
                + Checksums.verifyTransport(srcIp, dstIp, IpHeader.PROTOCOL_TCP,
                synWithChecksum.headerLength() + payload.length,
                synWithChecksum.segment(payload)));

        // 2.2 TCP 选项：SYN 携带 MSS=1460（三次握手里协商最大报文段，双方取小）
        TcpHeader synWithMss = syn.withOptions(List.of(TcpOption.mss(1460)));
        System.out.println("TCP SYN+MSS 首部 " + synWithMss.headerLength() + " 字节: " + synWithMss);

        TcpHeader parsedOptions = TcpHeader.parse(synWithMss.encode());
        System.out.println("解析回: dataOffset=" + parsedOptions.dataOffset()
                + ", 选项=" + parsedOptions.options());

        // 2.3 SACK 块：乱序确认（丢包时只重传丢失段，不用全部重传）
        // 场景：发送序号 1000~4000，接收方只收到 1000~2000 与 3000~4000，中间 2000~3000 丢失
        List<TcpOption> withSack = List.of(
                TcpOption.sackPermitted(),
                TcpOption.sack(new SackBlock(3000, 4000), new SackBlock(1000, 2000)));
        TcpHeader sackHeader = syn.withOptions(withSack);
        System.out.println("TCP SYN+SACK 首部 " + sackHeader.headerLength() + " 字节: " + sackHeader);
        List<TcpOption> parsedSack = TcpHeader.parse(sackHeader.encode()).options();
        TcpOption sackOption = parsedSack.get(1); // 第一个是 SACK-Permitted
        List<SackBlock> received = sackOption.sackBlocks();
        System.out.println("解析回: 选项=" + parsedSack + ", SACK 块=" + received);
        // 丢包区间推断：发送范围 − 已确认块 = 需要重传的段（SackBlock.gaps）
        List<SackBlock> gaps = SackBlock.gaps(1000, 4000, received);
        System.out.println("丢包推断: 发送 [1000, 4000) − SACK 块 → 需要重传 " + gaps
                + "（只重传丢失段，不用全部重发）");

        // 2.4 ECN 三次握手：SYN+ECE+CWR（显式拥塞通知，RFC 3168）
        TcpHeader synEcn = new TcpHeader(12345, 80, 1000, 0,
                5, false, true, false, false, false, false, true, true, 65535, 0, 0);
        System.out.println("ECN 握手① SYN+ECE+CWR: " + synEcn);
        TcpHeader synAckEcn = new TcpHeader(80, 12345, 0, 1001,
                5, true, true, false, false, false, false, false, true, 65535, 0, 0);
        System.out.println("ECN 握手② SYN+ACK+ECE: " + synAckEcn);
        TcpHeader ackEcn = new TcpHeader(12345, 80, 1001, 0,
                5, true, false, false, false, false, false, false, false, 65535, 0, 0);
        System.out.println("ECN 握手③ ACK（双方确认支持 ECN，中间路由器可打 CE 标记）: " + ackEcn);
        System.out.println();

        // 3. UDP 首部：DNS 查询 53 端口（含 UDP 校验和，IPv4 下可选）
        UdpHeader udp = new UdpHeader(53000, 53, 8 + 12, 0);
        byte[] udpBytes = udp.encode();
        System.out.println("UDP 报文 " + udpBytes.length + " 字节: " + udp);
        System.out.println("解析回: " + UdpHeader.parse(udpBytes));
        int udpChecksum = udp.computeChecksum(srcIp, dstIp, new byte[12]);
        System.out.println("UDP 校验和（伪首部 + 首部 + 12 字节数据）: 0x"
                + String.format("%04X", udpChecksum));
        System.out.println();

        // 4. IPv4 首部
        IpHeader ip = new IpHeader(4, 5, 20 + tcpBytes.length, 1,
                64, IpHeader.PROTOCOL_TCP, 0,
                IpHeader.parseIp("192.168.1.10"), IpHeader.parseIp("93.184.216.34"));
        byte[] ipBytes = ip.encode();
        System.out.println("IP 首部 " + ipBytes.length + " 字节: " + ip);
        System.out.println("解析回: " + IpHeader.parse(ipBytes));

        // 4.1 IP 分片：MF=1、片偏移 185（= 1480 字节 ÷ 8 字节单位），同一标识 0x1234
        IpHeader frag = ip.withFragmentation(IpHeader.FLAG_MF, 185);
        System.out.println("IP 分片首部: " + frag);
        System.out.println("解析回: " + IpHeader.parse(frag.encode()));
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

        // 6.1 ARP：广播询问「谁是 192.168.1.1」（目标 MAC 全 0，EtherType=0x0806）
        ArpHeader arpRequest = new ArpHeader(
                ArpHeader.HARDWARE_ETHERNET, ArpHeader.PROTOCOL_IPV4, 6, 4,
                ArpHeader.OPCODE_REQUEST,
                EthernetFrame.parseMac("AA:BB:CC:DD:EE:FF"), IpHeader.parseIp("192.168.1.10"),
                EthernetFrame.parseMac("00:00:00:00:00:00"), IpHeader.parseIp("192.168.1.1"));
        EthernetFrame arpEth = new EthernetFrame(
                EthernetFrame.parseMac("FF:FF:FF:FF:FF:FF"), // 广播地址
                EthernetFrame.parseMac("AA:BB:CC:DD:EE:FF"),
                EthernetFrame.ETHERTYPE_ARP);
        byte[] arpBytes = concat(arpEth.encode(), arpRequest.encode());
        PacketParser.ParsedPacket parsedArp = PacketParser.parse(arpBytes);
        System.out.println("ARP 帧 " + arpBytes.length + " 字节，分层解析:");
        System.out.println("  第 1 层 以太网: " + parsedArp.ethernet());
        System.out.println("  第 2 层 ARP:   " + parsedArp.transport()
                + "（isArp=" + parsedArp.isArp() + ", ip=" + parsedArp.ip() + "）");
        System.out.println();

        // 6.2 DNS：UDP 53 端口上的域名查询（头部 12 字节 + 查询记录）
        DnsHeader dnsHeader = DnsHeader.query(0x1234, true, 1); // id=0x1234, RD, 1 个问题
        DnsQuestion dnsQuestion = new DnsQuestion("www.example.com",
                DnsQuestion.QTYPE_A, DnsQuestion.QCLASS_IN);
        byte[] dnsBytes = concat(dnsHeader.encode(), dnsQuestion.encode());
        System.out.println("DNS 查询报文 " + dnsBytes.length + " 字节:");
        System.out.println("  头部: " + DnsHeader.parse(dnsBytes));
        System.out.println("  标签编码: " + hex(DnsQuestion.encodeName("www.example.com")));
        DnsQuestion.ParsedQuestion parsedDns = DnsQuestion.parseAt(dnsBytes, DnsHeader.HEADER_LENGTH);
        System.out.println("  查询: " + parsedDns.question()
                + "（记录占 " + parsedDns.bytesConsumed() + " 字节）");
        System.out.println();

        // 6.3 HTTP：应用层请求/响应报文（请求行 + 状态行 + 头部，行结束符 CRLF）
        Map<String, List<String>> reqHeaders = new LinkedHashMap<>();
        reqHeaders.put("Host", List.of("www.example.com"));
        reqHeaders.put("User-Agent", List.of("study-client/1.0"));
        reqHeaders.put("Connection", List.of("keep-alive"));
        HttpRequest httpRequest = new HttpRequest("GET", "/index.html", "HTTP/1.1",
                reqHeaders, "");
        String httpReqText = httpRequest.encode();
        System.out.println("HTTP 请求报文（" + httpReqText.length() + " 字符）:");
        System.out.println(httpReqText.replace("\r\n", "⏎\n"));
        System.out.println("解析回: " + HttpRequest.parse(httpReqText));

        Map<String, List<String>> respHeaders = new LinkedHashMap<>();
        respHeaders.put("Content-Type", List.of("text/html; charset=utf-8"));
        respHeaders.put("Content-Length", List.of("13"));
        HttpResponse httpResponse = new HttpResponse("HTTP/1.1", 200, "OK",
                respHeaders, "<h1>Hello</h1>");
        String httpRespText = httpResponse.encode();
        System.out.println("HTTP 响应报文:");
        System.out.println(httpRespText.replace("\r\n", "⏎\n"));
        System.out.println("解析回: " + HttpResponse.parse(httpRespText));
        System.out.println("状态码速查: 404=" + HttpResponse.reason(404)
                + ", 500=" + HttpResponse.reason(500)
                + ", 301=" + HttpResponse.reason(301));
        System.out.println();

        // 7. TCP 状态机：三次握手/四次挥手 + RST 连接重置 + 半开连接检测（SYN 重传超时）
        TcpStateMachine.printHandshakeDemo();
        TcpStateMachine.printRstDemo();
        TcpStateMachine.printHalfOpenDemo();
        TcpStateMachine.printKeepAliveDemo();
        System.out.println();

        // 8. TCP 粘包 vs UDP 有边界
        TcpStickyPacketDemo.main(new String[]{});
        System.out.println();

        // 9. IP 子网划分/CIDR 计算
        SubnetCalculator.printSubnetDemo();

        // 10. TCP 拥塞控制（慢启动/拥塞避免/超时/快重传/快恢复）
        TcpCongestionControl.printDemo();
    }

    private static String hex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02X ", b & 0xFF));
        }
        return sb.toString().trim();
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
