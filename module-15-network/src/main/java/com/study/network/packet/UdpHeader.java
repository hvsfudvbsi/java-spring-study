package com.study.network.packet;

/**
 * UDP 首部（固定 8 字节）——比 TCP 简单得多，这正是 UDP 的特点。
 *
 * <pre>
 *   0                   1                   2                   3
 *   0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1
 *  +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 *  |         源端口 (16)           |       目的端口 (16)            |
 *  +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 *  |           长度 (16)           |        校验和 (16)             |
 *  +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 * </pre>
 *
 * 对比 TCP（最小 20 字节 + 选项）：\n
 * - UDP 首部固定 8 字节，没有序号、确认号、标志位、窗口、紧急指针。\n * - 长度字段 = 首部 8 字节 + 数据长度，即整个 UDP 数据报长度。\n * - 没有可靠性机制：不保证送达、不保证顺序、不保证不重复 → 无连接、无握手。\n *
 * 本类演示 8 字节 UDP 首部的编码与解析，以及 UDP 校验和（伪首部 + 数据报，见 {@link Checksums}）。\n *
 * 注意：IPv4 下 UDP 校验和是**可选**的——置 0 表示未计算（本类保留该语义）；\n * IPv6 下强制要求。UDP 校验和的计算方法与 TCP 相同（伪首部 + 报文段）。\n */
public class UdpHeader {

    /** UDP 首部固定 8 字节 */
    public static final int HEADER_LENGTH = 8;

    private final int sourcePort;      // 16 bit
    private final int destinationPort; // 16 bit
    private final int length;          // 16 bit：首部 + 数据的总长度
    private final int checksum;        // 16 bit：可为 0（IPv4 下可选）

    public UdpHeader(int sourcePort, int destinationPort, int length, int checksum) {
        this.sourcePort = sourcePort;
        this.destinationPort = destinationPort;
        this.length = length;
        this.checksum = checksum;
    }

    /** 编码为 8 字节（网络字节序：大端） */
    public byte[] encode() {
        return encodeWithChecksum(checksum);
    }

    /** 用指定的校验和字段值编码首部（计算校验和时传 0）。 */
    private byte[] encodeWithChecksum(int checksumValue) {
        byte[] bytes = new byte[HEADER_LENGTH];
        writeShort(bytes, 0, sourcePort);
        writeShort(bytes, 2, destinationPort);
        writeShort(bytes, 4, length);
        writeShort(bytes, 6, checksumValue);
        return bytes;
    }

    /** 从 8 字节解析 UDP 首部 */
    public static UdpHeader parse(byte[] bytes) {
        return parse(bytes, 0);
    }

    /**
     * 从字节数组的指定偏移处解析 UDP 首部（用于完整报文分层解析）。
     *
     * @param bytes  完整报文（如以太网帧 + IP + UDP + 负载）
     * @param offset UDP 首部起始位置
     */
    public static UdpHeader parse(byte[] bytes, int offset) {
        if (bytes.length - offset < HEADER_LENGTH) {
            throw new IllegalArgumentException(
                    "UDP 首部固定 8 字节，偏移 " + offset + " 后剩余 "
                            + (bytes.length - offset));
        }
        return new UdpHeader(
                readShort(bytes, offset),
                readShort(bytes, offset + 2),
                readShort(bytes, offset + 4),
                readShort(bytes, offset + 6));
    }

    /** 数据长度 = 总长度 - 8 字节首部 */
    public int payloadLength() {
        return length - HEADER_LENGTH;
    }

    /**
     * 计算 UDP 校验和（IPv4 下可选，IPv6 下强制）：
     * 覆盖「伪首部(源/目的 IP + 协议号 17 + UDP 长度) + UDP 首部 + 数据」。
     * 计算时首部校验和字段置 0；数据为奇数个字节时末尾按 0 补齐（不参与传输）。
     *
     * @param sourceIp      源 IP（32 bit）
     * @param destinationIp 目的 IP（32 bit）
     * @param payload       UDP 数据；长度字段必须 = 8 + payload.length
     */
    public int computeChecksum(int sourceIp, int destinationIp, byte[] payload) {
        byte[] header = encodeWithChecksum(0);
        byte[] datagram = concat(header, payload);
        // 伪首部的长度字段取 UDP 首部里的长度字段（必须等于 8 + 数据长度）
        return Checksums.transportChecksum(sourceIp, destinationIp, IpHeader.PROTOCOL_UDP,
                length, datagram);
    }

    /** 返回校验和已填好的一份拷贝（IPv4 下可置 0 表示不校验）。 */
    public UdpHeader withValidChecksum(int sourceIp, int destinationIp, byte[] payload) {
        return new UdpHeader(sourcePort, destinationPort, length,
                computeChecksum(sourceIp, destinationIp, payload));
    }

    /** 完整 UDP 数据报 = 首部（含已填的校验和）+ 数据，用于发送或整体校验。 */
    public byte[] datagram(byte[] payload) {
        return concat(encode(), payload);
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

    private static void writeShort(byte[] bytes, int offset, int value) {
        bytes[offset] = (byte) ((value >> 8) & 0xFF);
        bytes[offset + 1] = (byte) (value & 0xFF);
    }

    private static int readShort(byte[] bytes, int offset) {
        return ((bytes[offset] & 0xFF) << 8) | (bytes[offset + 1] & 0xFF);
    }

    public int sourcePort() {
        return sourcePort;
    }

    public int destinationPort() {
        return destinationPort;
    }

    public int length() {
        return length;
    }

    public int checksum() {
        return checksum;
    }

    @Override
    public String toString() {
        return "UdpHeader{" +
                "sourcePort=" + sourcePort +
                ", destinationPort=" + destinationPort +
                ", length=" + length + " (数据 " + payloadLength() + " 字节)" +
                ", checksum=" + checksum +
                '}';
    }
}
