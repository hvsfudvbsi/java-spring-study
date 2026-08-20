package com.study.network.packet;

/**
 * ICMP 首部（固定 8 字节）——网络层控制报文，不是传输层协议。
 *
 * <pre>
 *   0                   1                   2                   3
 *   0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1
 *  +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 *  |     类型 (8)   |     代码 (8)   |         校验和 (16)          |
 *  +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 *  |     标识 (16)  |           序号 (16)                          |
 *  +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 * </pre>
 *
 * 常见类型（ping 即 ICMP Echo）：
 * - 0：Echo Reply（回显应答，ping 成功返回）
 * - 3：Destination Unreachable（目的不可达）
 * - 8：Echo Request（回显请求，ping 发起）
 * - 11：Time Exceeded（TTL 耗尽，traceroute 利用它）
 *
 * 关键理解（面试）：
 * - ICMP 封装在 IP 数据报里（IP 协议号 = 1），但它**不是传输层协议**：
 *   没有端口号，不承载应用数据，只是网络层的控制/诊断报文。
 * - ping 用 ICMP Echo（类型 8 请求 / 类型 0 回复），traceroute 用 Time Exceeded（类型 11）。
 *
 * 本类演示 8 字节 ICMP 首部的编码与解析。
 */
public class IcmpHeader {

    /** ICMP 首部固定 8 字节 */
    public static final int HEADER_LENGTH = 8;

    /** 类型：Echo 回复（ping 成功） */
    public static final int TYPE_ECHO_REPLY = 0;
    /** 类型：目的不可达 */
    public static final int TYPE_DESTINATION_UNREACHABLE = 3;
    /** 类型：Echo 请求（ping 发起） */
    public static final int TYPE_ECHO_REQUEST = 8;
    /** 类型：超时（TTL 耗尽） */
    public static final int TYPE_TIME_EXCEEDED = 11;

    private final int type;        // 8 bit
    private final int code;        // 8 bit（Echo 报文通常为 0）
    private final int checksum;    // 16 bit
    private final int identifier;  // 16 bit（Echo 报文标识进程）
    private final int sequence;    // 16 bit（Echo 报文序号）

    public IcmpHeader(int type, int code, int checksum, int identifier, int sequence) {
        this.type = type;
        this.code = code;
        this.checksum = checksum;
        this.identifier = identifier;
        this.sequence = sequence;
    }

    /** 编码为 8 字节（网络字节序：大端） */
    public byte[] encode() {
        byte[] bytes = new byte[HEADER_LENGTH];
        bytes[0] = (byte) type;
        bytes[1] = (byte) code;
        writeShort(bytes, 2, checksum);
        writeShort(bytes, 4, identifier);
        writeShort(bytes, 6, sequence);
        return bytes;
    }

    /** 从字节解析 ICMP 首部 */
    public static IcmpHeader parse(byte[] bytes) {
        return parse(bytes, 0);
    }

    /**
     * 从字节数组的指定偏移处解析 ICMP 首部（用于完整报文分层解析）。
     *
     * @param bytes  完整报文（如以太网帧 + IP + ICMP + 数据）
     * @param offset ICMP 首部起始位置
     */
    public static IcmpHeader parse(byte[] bytes, int offset) {
        if (bytes.length - offset < HEADER_LENGTH) {
            throw new IllegalArgumentException(
                    "ICMP 首部固定 8 字节，偏移 " + offset + " 后剩余 "
                            + (bytes.length - offset));
        }
        return new IcmpHeader(
                bytes[offset] & 0xFF,
                bytes[offset + 1] & 0xFF,
                readShort(bytes, offset + 2),
                readShort(bytes, offset + 4),
                readShort(bytes, offset + 6));
    }

    /** 类型名称（便于阅读） */
    public String typeName() {
        return switch (type) {
            case TYPE_ECHO_REPLY -> "Echo Reply (ping 成功)";
            case TYPE_DESTINATION_UNREACHABLE -> "Destination Unreachable";
            case TYPE_ECHO_REQUEST -> "Echo Request (ping 请求)";
            case TYPE_TIME_EXCEEDED -> "Time Exceeded (TTL 耗尽)";
            default -> "未知类型 " + type;
        };
    }

    private static void writeShort(byte[] bytes, int offset, int value) {
        bytes[offset] = (byte) ((value >> 8) & 0xFF);
        bytes[offset + 1] = (byte) (value & 0xFF);
    }

    private static int readShort(byte[] bytes, int offset) {
        return ((bytes[offset] & 0xFF) << 8) | (bytes[offset + 1] & 0xFF);
    }

    public int type() {
        return type;
    }

    public int code() {
        return code;
    }

    public int checksum() {
        return checksum;
    }

    public int identifier() {
        return identifier;
    }

    public int sequence() {
        return sequence;
    }

    @Override
    public String toString() {
        return "IcmpHeader{" +
                "type=" + type + " (" + typeName() + ")" +
                ", code=" + code +
                ", checksum=" + checksum +
                ", identifier=" + identifier +
                ", sequence=" + sequence +
                '}';
    }
}
