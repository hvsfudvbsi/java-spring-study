package com.study.network.packet;

import java.util.Arrays;

/**
 * TCP 首部（最小 20 字节，不含选项）——计算机网络面试必考。
 *
 * TCP 首部布局（从低位字节到高位字节）：
 * <pre>
 *   0                   1                   2                   3
 *   0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1
 *  +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 *  |         源端口 (16)           |       目的端口 (16)            |
 *  +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 *  |                        序号 (32)                              |
 *  +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 *  |                       确认号 (32)                             |
 *  +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 *  | 数据偏移(4) | 保留(3) |N|C|E|U|A|P|R|S|F|    窗口大小 (16)    |
 *  +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 *  |      校验和 (16)              |       紧急指针 (16)            |
 *  +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 * </pre>
 *
 * 关键位字段（本类演示如何从字节中按位提取）：
 * - 数据偏移（Data Offset，4 bit）：TCP 首部长度 ÷ 4。最小 5 → 20 字节；
 *   有选项时更大（如 6 → 24 字节）。这就是"首部长度"的来历。
 * - 标志位（9 bit）：NS/CWR/ECE/URG/ACK/PSH/RST/SYN/FIN，
 *   其中 ACK/SYN/FIN 是三次握手、四次挥手的主角。
 * - 窗口大小（16 bit）：接收方还能收多少字节（流量控制）。
 *
 * 本类把首部编码成字节、再从字节解析，帮助你直观理解每个字段在报文中的位置。
 *
 * 标志位在报文中的精确位置（第 12 字节起 16 bit）：
 *   数据偏移(4) | 保留(3) | NS(1) | CWR(1) | ECE(1) | URG(1) | ACK(1) | PSH(1) | RST(1) | SYN(1) | FIN(1)
 * 本类实现经典 5 标志位（ACK/PSH/RST/SYN/FIN），其余标志保留为 0。
 *
 * TCP 校验和（见 {@link Checksums}）：**必须**计算，覆盖「伪首部(源/目的 IP + 协议号 + TCP 长度)
 * + TCP 首部 + 数据」。首部中的校验和字段在计算时置 0，算完再填回。
 */
public class TcpHeader {

    /** 无选项时 TCP 首部固定 20 字节 */
    public static final int FIXED_HEADER_LENGTH = 20;

    // ---- 前 4 字节：端口 ----
    private final int sourcePort;    // 16 bit
    private final int destinationPort; // 16 bit

    // ---- 中间 8 字节：序号与确认号 ----
    private final long sequenceNumber;   // 32 bit（无符号）
    private final long acknowledgmentNumber; // 32 bit（无符号）

    // ---- 数据偏移与标志 ----
    private final int dataOffset;  // 4 bit，单位 4 字节，例如 5 表示首部 20 字节
    private final boolean ack;     // ACK 确认标志
    private final boolean syn;     // SYN 同步标志（建立连接）
    private final boolean fin;     // FIN 结束标志（断开连接）
    private final boolean psh;     // PSH 推送标志
    private final boolean rst;     // RST 重置标志

    // ---- 后 4 字节 ----
    private final int windowSize;      // 16 bit 窗口
    private final int checksum;        // 16 bit 校验和
    private final int urgentPointer;   // 16 bit 紧急指针

    /** 构造并编码：用字段值生成 20 字节首部字节数组。 */
    public TcpHeader(int sourcePort, int destinationPort,
                     long sequenceNumber, long acknowledgmentNumber,
                     int dataOffset, boolean ack, boolean syn, boolean fin,
                     boolean psh, boolean rst, int windowSize,
                     int checksum, int urgentPointer) {
        this.sourcePort = sourcePort;
        this.destinationPort = destinationPort;
        this.sequenceNumber = sequenceNumber;
        this.acknowledgmentNumber = acknowledgmentNumber;
        this.dataOffset = dataOffset;
        this.ack = ack;
        this.syn = syn;
        this.fin = fin;
        this.psh = psh;
        this.rst = rst;
        this.windowSize = windowSize;
        this.checksum = checksum;
        this.urgentPointer = urgentPointer;
    }

    /**
     * 把首部字段编码为 20 字节字节数组（网络字节序：大端）。
     *
     * 位字段打包逻辑：
     * - 第 12 字节高 4 位 = dataOffset（首部长度 ÷ 4），低 4 位 = 保留(3) + NS(1)，填 0
     * - 第 13 字节 8 bit = CWR(1) ECE(1) URG(1) ACK(1) PSH(1) RST(1) SYN(1) FIN(1)
     *   本类只实现经典 5 标志（ACK/PSH/RST/SYN/FIN），掩码从 ACK=0x10 开始向下排。
     */
    public byte[] encode() {
        return encodeWithChecksum(checksum);
    }

    /** 用指定的校验和字段值编码首部（计算校验和时传 0，发送时传算好的值）。 */
    private byte[] encodeWithChecksum(int checksumValue) {
        byte[] bytes = new byte[FIXED_HEADER_LENGTH];
        // 源端口 / 目的端口
        writeShort(bytes, 0, sourcePort);
        writeShort(bytes, 2, destinationPort);
        // 序号 / 确认号（32 bit）
        writeInt(bytes, 4, sequenceNumber);
        writeInt(bytes, 8, acknowledgmentNumber);

        // 第 12 字节：数据偏移(4 bit) | 保留(3 bit) + NS(1 bit)=0
        bytes[12] = (byte) ((dataOffset & 0x0F) << 4);
        // 第 13 字节：标志位。ACK=0x10, PSH=0x08, RST=0x04, SYN=0x02, FIN=0x01
        int flags = 0;
        if (ack) flags |= 0x10;
        if (psh) flags |= 0x08;
        if (rst) flags |= 0x04;
        if (syn) flags |= 0x02;
        if (fin) flags |= 0x01;
        bytes[13] = (byte) flags;

        writeShort(bytes, 14, windowSize);
        writeShort(bytes, 16, checksumValue);
        writeShort(bytes, 18, urgentPointer);
        return bytes;
    }

    /**
     * 从字节数组解析 TCP 首部（逐字段 + 按位提取标志）。
     * 这是理解"报文里每个字节/每个 bit 的含义"的入口。
     */
    public static TcpHeader parse(byte[] bytes) {
        return parse(bytes, 0);
    }

    /**
     * 从字节数组的指定偏移处解析 TCP 首部（用于完整报文分层解析）。
     *
     * @param bytes  完整报文（如以太网帧 + IP + TCP + 负载）
     * @param offset TCP 首部起始位置
     */
    public static TcpHeader parse(byte[] bytes, int offset) {
        if (bytes.length - offset < FIXED_HEADER_LENGTH) {
            throw new IllegalArgumentException(
                    "TCP 首部至少 20 字节，偏移 " + offset + " 后剩余 "
                            + (bytes.length - offset));
        }
        int sourcePort = readShort(bytes, offset);
        int destinationPort = readShort(bytes, offset + 2);
        long seq = readInt(bytes, offset + 4);
        long ackNum = readInt(bytes, offset + 8);

        // 第 12 字节高 4 位是数据偏移（单位 4 字节）
        int dataOffset = (bytes[offset + 12] >> 4) & 0x0F;
        // 第 13 字节：低 6 位是标志（ACK/PSH/RST/SYN/FIN）
        int flags = bytes[offset + 13] & 0xFF;
        boolean ack = (flags & 0x10) != 0;
        boolean psh = (flags & 0x08) != 0;
        boolean rst = (flags & 0x04) != 0;
        boolean syn = (flags & 0x02) != 0;
        boolean fin = (flags & 0x01) != 0;

        int windowSize = readShort(bytes, offset + 14);
        int checksum = readShort(bytes, offset + 16);
        int urgentPointer = readShort(bytes, offset + 18);

        return new TcpHeader(sourcePort, destinationPort, seq, ackNum,
                dataOffset, ack, syn, fin, psh, rst,
                windowSize, checksum, urgentPointer);
    }

    /**
     * 计算 TCP 校验和（TCP 校验和**必须**计算，不可省略）：
     * 覆盖「伪首部(源/目的 IP + 协议号 6 + TCP 长度) + TCP 首部 + 数据」。
     * 计算时首部校验和字段置 0；数据为奇数个字节时末尾按 0 补齐（不参与传输）。
     *
     * @param sourceIp      源 IP（32 bit，来自 IP 首部）
     * @param destinationIp 目的 IP（32 bit）
     * @param payload       TCP 数据（可为空）
     */
    public int computeChecksum(int sourceIp, int destinationIp, byte[] payload) {
        byte[] header = encodeWithChecksum(0);
        byte[] segment = concat(header, payload);
        return Checksums.transportChecksum(sourceIp, destinationIp, IpHeader.PROTOCOL_TCP,
                headerLength() + payload.length, segment);
    }

    /** 返回校验和已填好的一份拷贝（校验和 = computeChecksum 的结果），可直接发送。 */
    public TcpHeader withValidChecksum(int sourceIp, int destinationIp, byte[] payload) {
        return new TcpHeader(sourcePort, destinationPort, sequenceNumber, acknowledgmentNumber,
                dataOffset, ack, syn, fin, psh, rst, windowSize,
                computeChecksum(sourceIp, destinationIp, payload), urgentPointer);
    }

    /** 完整 TCP 报文段 = 首部（含已填的校验和）+ 数据，用于发送或整体校验。 */
    public byte[] segment(byte[] payload) {
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

    /** 大端写 16 bit 无符号整数 */
    private static void writeShort(byte[] bytes, int offset, int value) {
        bytes[offset] = (byte) ((value >> 8) & 0xFF);
        bytes[offset + 1] = (byte) (value & 0xFF);
    }

    /** 大端写 32 bit 无符号整数 */
    private static void writeInt(byte[] bytes, int offset, long value) {
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

    public int headerLength() {
        return dataOffset * 4;
    }

    public int sourcePort() {
        return sourcePort;
    }

    public int destinationPort() {
        return destinationPort;
    }

    public long sequenceNumber() {
        return sequenceNumber;
    }

    public long acknowledgmentNumber() {
        return acknowledgmentNumber;
    }

    public int dataOffset() {
        return dataOffset;
    }

    public boolean ack() {
        return ack;
    }

    public boolean syn() {
        return syn;
    }

    public boolean fin() {
        return fin;
    }

    public boolean psh() {
        return psh;
    }

    public boolean rst() {
        return rst;
    }

    public int windowSize() {
        return windowSize;
    }

    public int checksum() {
        return checksum;
    }

    public int urgentPointer() {
        return urgentPointer;
    }

    @Override
    public String toString() {
        return "TcpHeader{" +
                "sourcePort=" + sourcePort +
                ", destinationPort=" + destinationPort +
                ", sequenceNumber=" + sequenceNumber +
                ", acknowledgmentNumber=" + acknowledgmentNumber +
                ", dataOffset=" + dataOffset + " (" + headerLength() + " 字节)" +
                ", flags[SYN=" + syn + ", ACK=" + ack + ", FIN=" + fin +
                ", PSH=" + psh + ", RST=" + rst + "]" +
                ", windowSize=" + windowSize +
                ", checksum=" + checksum +
                ", urgentPointer=" + urgentPointer +
                ", bytes=" + Arrays.toString(encode()) +
                '}';
    }
}
