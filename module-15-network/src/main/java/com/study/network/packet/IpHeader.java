package com.study.network.packet;

import java.util.ArrayList;
import java.util.List;

/**
 * IPv4 首部（最小 20 字节，不含选项）——网络层报文头。
 *
 * <pre>
 *   0                   1                   2                   3
 *   0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1
 *  +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 *  |版本(4)| IHL(4)|   服务类型    |          总长度 (16)           |
 *  +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 *  |          标识 (16)            |标志(3)|     片偏移 (13)       |
 *  +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 *  |  TTL(8)  |  协议(8)  |       首部校验和 (16)                  |
 *  +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 *  |                        源 IP 地址 (32)                        |
 *  +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 *  |                      目的 IP 地址 (32)                        |
 *  +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 * </pre>
 *
 * 关键位字段：
 * - 版本（4 bit）：IPv4 = 4
 * - IHL（Internet Header Length，4 bit）：首部长度 ÷ 4，最小 5 → 20 字节
 * - 总长度（16 bit）：IP 首部 + 上层数据总长，最大 65535 字节
 * - 标识（16 bit）| 标志(3 bit) | 片偏移(13 bit)：分片三件套（见下）
 * - 协议（8 bit）：6=TCP，17=UDP，1=ICMP
 * - TTL（8 bit）：每过一个路由器减 1，减到 0 丢弃（防止环路）
 *
 * 分片三件套（IP 分片）：
 * - 标识 identification：同一数据报的所有分片共享同一个标识，接收方据此重组。
 * - 标志 flags：bit0 保留(恒 0)；DF（Don't Fragment）= 0x2，置 1 表示不许分片；
 *   MF（More Fragments）= 0x1，置 1 表示后面还有分片，最后一个分片 MF=0。
 * - 片偏移 fragmentOffset：本分片在原数据报中的偏移，**单位是 8 字节**（13 bit 最多表示 8191×8 ≈ 64KB，正好覆盖最大 IP 报文）。
 *
 * 什么时候需要分片：IP 报文超过链路 MTU（如以太网 1500 字节）时，路由器会把报文切成多个分片
 * 分别转发，接收方按「标识 + 片偏移 + MF」重组。由于分片重组开销大、易受攻击（分片炸弹），
 * 现代 TCP 一般用 Path MTU Discovery 找到不触发分片的最大报文（DF=1），把分片问题留在传输层解决。
 *
 * 本类演示 IPv4 首部编码与解析，重点看版本/IHL 挤在同一字节的位提取，以及分片字段的打包。
 */
public class IpHeader {

    /** 无选项时 IPv4 首部固定 20 字节 */
    public static final int FIXED_HEADER_LENGTH = 20;

    /** 协议号：ICMP（网络层控制报文，如 ping） */
    public static final int PROTOCOL_ICMP = 1;
    /** 协议号：TCP */
    public static final int PROTOCOL_TCP = 6;
    /** 协议号：UDP */
    public static final int PROTOCOL_UDP = 17;

    /** 分片标志：DF（Don't Fragment，禁止分片） */
    public static final int FLAG_DF = 0x2;
    /** 分片标志：MF（More Fragments，后面还有分片） */
    public static final int FLAG_MF = 0x1;

    private final int version;            // 4 bit（IPv4 = 4）
    private final int ihl;                // 4 bit：首部长度 ÷ 4
    private final int totalLength;        // 16 bit：首部 + 数据
    private final int identification;     // 16 bit 标识（同一数据报分片共享）
    private final int flags;              // 3 bit 分片标志（DF=0x2, MF=0x1）
    private final int fragmentOffset;     // 13 bit 片偏移（单位 8 字节，0~8191）
    private final int ttl;                // 8 bit 生存时间
    private final int protocol;           // 8 bit 上层协议（6=TCP, 17=UDP）
    private final int checksum;           // 16 bit 首部校验和
    private final int sourceIp;           // 32 bit 源地址
    private final int destinationIp;      // 32 bit 目的地址

    /** 不带分片信息（flags=0、fragmentOffset=0），等价于「未分片报文」。 */
    public IpHeader(int version, int ihl, int totalLength, int identification,
                    int ttl, int protocol, int checksum,
                    int sourceIp, int destinationIp) {
        this(version, ihl, totalLength, identification, 0, 0,
                ttl, protocol, checksum, sourceIp, destinationIp);
    }

    /** 完整构造：含分片标志与片偏移。 */
    public IpHeader(int version, int ihl, int totalLength, int identification,
                    int flags, int fragmentOffset,
                    int ttl, int protocol, int checksum,
                    int sourceIp, int destinationIp) {
        if (flags < 0 || flags > 0x7) {
            throw new IllegalArgumentException("分片标志只占 3 bit（0~7）: " + flags);
        }
        if (fragmentOffset < 0 || fragmentOffset > 0x1FFF) {
            throw new IllegalArgumentException("片偏移只占 13 bit（0~8191，单位 8 字节）: " + fragmentOffset);
        }
        this.version = version;
        this.ihl = ihl;
        this.totalLength = totalLength;
        this.identification = identification;
        this.flags = flags;
        this.fragmentOffset = fragmentOffset;
        this.ttl = ttl;
        this.protocol = protocol;
        this.checksum = checksum;
        this.sourceIp = sourceIp;
        this.destinationIp = destinationIp;
    }

    /** 返回设置分片信息后的一份拷贝（不可变风格，不修改原对象）。 */
    public IpHeader withFragmentation(int flags, int fragmentOffset) {
        return new IpHeader(version, ihl, totalLength, identification,
                flags, fragmentOffset, ttl, protocol, checksum,
                sourceIp, destinationIp);
    }

    /** 编码为 20 字节（网络字节序：大端） */
    public byte[] encode() {
        byte[] bytes = new byte[FIXED_HEADER_LENGTH];
        // 第 0 字节：版本(4 bit) | IHL(4 bit)
        bytes[0] = (byte) (((version & 0x0F) << 4) | (ihl & 0x0F));
        bytes[1] = 0; // 服务类型（简化）
        writeShort(bytes, 2, totalLength);
        writeShort(bytes, 4, identification);
        // 第 6、7 字节：标志(3 bit) | 片偏移(13 bit)
        writeShort(bytes, 6, ((flags & 0x7) << 13) | (fragmentOffset & 0x1FFF));
        bytes[8] = (byte) ttl;
        bytes[9] = (byte) protocol;
        writeShort(bytes, 10, checksum);
        writeInt(bytes, 12, sourceIp);
        writeInt(bytes, 16, destinationIp);
        return bytes;
    }

    /** 从字节解析 IPv4 首部（演示位提取） */
    public static IpHeader parse(byte[] bytes) {
        return parse(bytes, 0);
    }

    /**
     * 从字节数组的指定偏移处解析 IPv4 首部（用于完整报文分层解析）。
     *
     * @param bytes  完整报文（如以太网帧 + IP + TCP + 负载）
     * @param offset IP 首部起始位置
     */
    public static IpHeader parse(byte[] bytes, int offset) {
        if (bytes.length - offset < FIXED_HEADER_LENGTH) {
            throw new IllegalArgumentException(
                    "IPv4 首部至少 20 字节，偏移 " + offset + " 后剩余 "
                            + (bytes.length - offset));
        }
        int version = (bytes[offset] >> 4) & 0x0F;
        int ihl = bytes[offset] & 0x0F;
        int totalLength = readShort(bytes, offset + 2);
        int identification = readShort(bytes, offset + 4);
        int fragment = readShort(bytes, offset + 6);
        int flags = (fragment >> 13) & 0x7;
        int fragmentOffset = fragment & 0x1FFF;
        int ttl = bytes[offset + 8] & 0xFF;
        int protocol = bytes[offset + 9] & 0xFF;
        int checksum = readShort(bytes, offset + 10);
        int sourceIp = (int) readInt(bytes, offset + 12);
        int destinationIp = (int) readInt(bytes, offset + 16);
        return new IpHeader(version, ihl, totalLength, identification,
                flags, fragmentOffset, ttl, protocol, checksum, sourceIp, destinationIp);
    }

    /** IP 首部实际长度 = IHL × 4 */
    public int headerLength() {
        return ihl * 4;
    }

    /**
     * 分片重组（IPv4 接收端）：同一标识（identification）的多个分片，按片偏移（×8 字节）
     * 拼接回原数据报。返回重组后的**数据总长度**（不含 IP 首部）。
     * 分片偏移不连续（中间缺片）或标识不一致时抛异常——接收端遇到这两种情况只能丢弃。
     * 演示场景：3000 字节数据、MTU=1500 时分 3 片（1480 + 1480 + 40），偏移 0 / 185 / 370。
     *
     * @param fragments 同一数据报的所有分片（顺序可乱，内部按片偏移排序）
     */
    public static int reassembledDataLength(List<IpHeader> fragments) {
        if (fragments == null || fragments.isEmpty()) {
            throw new IllegalArgumentException("至少需要一个分片");
        }
        int id = fragments.get(0).identification();
        List<IpHeader> sorted = new ArrayList<>(fragments);
        sorted.sort(java.util.Comparator.comparingInt(IpHeader::fragmentOffset));
        int total = 0;
        for (IpHeader fragment : sorted) {
            if (fragment.identification() != id) {
                throw new IllegalArgumentException("分片标识不一致: " + fragment.identification()
                        + " != " + id + "（不同数据报的分片不能混在一起重组）");
            }
            if (fragment.fragmentOffset() != total / 8) {
                throw new IllegalArgumentException("分片偏移不连续: 期望 " + total / 8
                        + "（已收 " + total + " 字节），实际 " + fragment.fragmentOffset()
                        + "（中间缺片，重组失败）");
            }
            total += fragment.totalLength() - fragment.headerLength(); // 本片数据长度
        }
        return total;
    }

    /** 上层数据长度 = 总长度 - 首部长度 */
    public int payloadLength() {
        return totalLength - headerLength();
    }

    /** 把 32 bit 整数格式化为点分十进制（如 192.168.1.1） */
    public static String toIpString(int ip) {
        return ((ip >>> 24) & 0xFF) + "." + ((ip >>> 16) & 0xFF) + "."
                + ((ip >>> 8) & 0xFF) + "." + (ip & 0xFF);
    }

    /** 把点分十进制解析为 32 bit 整数 */
    public static int parseIp(String ip) {
        String[] parts = ip.split("\\.");
        if (parts.length != 4) {
            throw new IllegalArgumentException("非法 IP: " + ip);
        }
        int result = 0;
        for (int i = 0; i < 4; i++) {
            int value = Integer.parseInt(parts[i]);
            if (value < 0 || value > 255) {
                throw new IllegalArgumentException("IP 每段必须在 0~255: " + ip);
            }
            result = (result << 8) | value;
        }
        return result;
    }

    private static void writeShort(byte[] bytes, int offset, int value) {
        bytes[offset] = (byte) ((value >> 8) & 0xFF);
        bytes[offset + 1] = (byte) (value & 0xFF);
    }

    private static void writeInt(byte[] bytes, int offset, int value) {
        bytes[offset] = (byte) ((value >> 24) & 0xFF);
        bytes[offset + 1] = (byte) ((value >> 16) & 0xFF);
        bytes[offset + 2] = (byte) ((value >> 8) & 0xFF);
        bytes[offset + 3] = (byte) (value & 0xFF);
    }

    private static int readShort(byte[] bytes, int offset) {
        return ((bytes[offset] & 0xFF) << 8) | (bytes[offset + 1] & 0xFF);
    }

    private static long readInt(byte[] bytes, int offset) {
        return ((long) (bytes[offset] & 0xFF) << 24)
                | ((bytes[offset + 1] & 0xFF) << 16)
                | ((bytes[offset + 2] & 0xFF) << 8)
                | (bytes[offset + 3] & 0xFF);
    }

    public int version() {
        return version;
    }

    public int ihl() {
        return ihl;
    }

    public int totalLength() {
        return totalLength;
    }

    public int identification() {
        return identification;
    }

    public int flags() {
        return flags;
    }

    public int fragmentOffset() {
        return fragmentOffset;
    }

    /** 分片标志可读描述（DF/MF/DF|MF/无），便于日志与演示。 */
    public String flagsDescription() {
        StringBuilder sb = new StringBuilder();
        if ((flags & FLAG_DF) != 0) {
            sb.append("DF");
        }
        if ((flags & FLAG_MF) != 0) {
            sb.append(sb.isEmpty() ? "" : "|").append("MF");
        }
        return sb.isEmpty() ? "无" : sb.toString();
    }

    public int ttl() {
        return ttl;
    }

    public int protocol() {
        return protocol;
    }

    public int checksum() {
        return checksum;
    }

    public int sourceIp() {
        return sourceIp;
    }

    public int destinationIp() {
        return destinationIp;
    }

    @Override
    public String toString() {
        return "IpHeader{" +
                "version=" + version +
                ", ihl=" + ihl + " (" + headerLength() + " 字节)" +
                ", totalLength=" + totalLength +
                ", identification=" + identification
                + ", flags[" + flagsDescription() + "]"
                + ", fragmentOffset=" + fragmentOffset + " (×8=" + (fragmentOffset * 8) + " 字节)"
                + ", protocol=" + protocol +
                ", ttl=" + ttl +
                ", source=" + toIpString(sourceIp) +
                ", destination=" + toIpString(destinationIp) +
                '}';
    }
}
