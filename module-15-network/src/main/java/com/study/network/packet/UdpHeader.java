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
 * - UDP 首部固定 8 字节，没有序号、确认号、标志位、窗口、紧急指针。\n
 * - 长度字段 = 首部 8 字节 + 数据长度，即整个 UDP 数据报长度。\n
 * - 没有可靠性机制：不保证送达、不保证顺序、不保证不重复 → 无连接、无握手。\n
 *
 * 本类演示 8 字节 UDP 首部的编码与解析。\n
 */
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
        byte[] bytes = new byte[HEADER_LENGTH];
        writeShort(bytes, 0, sourcePort);
        writeShort(bytes, 2, destinationPort);
        writeShort(bytes, 4, length);
        writeShort(bytes, 6, checksum);
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
