package com.study.network.packet;

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
 * - 协议（8 bit）：6=TCP，17=UDP，1=ICMP
 * - TTL（8 bit）：每过一个路由器减 1，减到 0 丢弃（防止环路）
 *
 * 本类演示 IPv4 首部编码与解析，重点看版本/IHL 挤在同一字节的位提取。
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

    private final int version;            // 4 bit（IPv4 = 4）
    private final int ihl;                // 4 bit：首部长度 ÷ 4
    private final int totalLength;        // 16 bit：首部 + 数据
    private final int identification;     // 16 bit 标识
    private final int ttl;                // 8 bit 生存时间
    private final int protocol;           // 8 bit 上层协议（6=TCP, 17=UDP）
    private final int checksum;           // 16 bit 首部校验和
    private final int sourceIp;           // 32 bit 源地址
    private final int destinationIp;      // 32 bit 目的地址

    public IpHeader(int version, int ihl, int totalLength, int identification,
                    int ttl, int protocol, int checksum,
                    int sourceIp, int destinationIp) {
        this.version = version;
        this.ihl = ihl;
        this.totalLength = totalLength;
        this.identification = identification;
        this.ttl = ttl;
        this.protocol = protocol;
        this.checksum = checksum;
        this.sourceIp = sourceIp;
        this.destinationIp = destinationIp;
    }

    /** 编码为 20 字节（网络字节序：大端） */
    public byte[] encode() {
        byte[] bytes = new byte[FIXED_HEADER_LENGTH];
        // 第 0 字节：版本(4 bit) | IHL(4 bit)
        bytes[0] = (byte) (((version & 0x0F) << 4) | (ihl & 0x0F));
        bytes[1] = 0; // 服务类型（简化）
        writeShort(bytes, 2, totalLength);
        writeShort(bytes, 4, identification);
        // 第 6、7 字节：标志(3 bit) | 片偏移(13 bit)，简化全 0
        writeShort(bytes, 6, 0);
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
        int ttl = bytes[offset + 8] & 0xFF;
        int protocol = bytes[offset + 9] & 0xFF;
        int checksum = readShort(bytes, offset + 10);
        int sourceIp = (int) readInt(bytes, offset + 12);
        int destinationIp = (int) readInt(bytes, offset + 16);
        return new IpHeader(version, ihl, totalLength, identification,
                ttl, protocol, checksum, sourceIp, destinationIp);
    }

    /** IP 首部实际长度 = IHL × 4 */
    public int headerLength() {
        return ihl * 4;
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
                ", protocol=" + protocol +
                ", ttl=" + ttl +
                ", source=" + toIpString(sourceIp) +
                ", destination=" + toIpString(destinationIp) +
                '}';
    }
}
