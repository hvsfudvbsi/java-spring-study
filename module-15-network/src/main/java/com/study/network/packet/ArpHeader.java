package com.study.network.packet;

/**
 * ARP 报文（固定 28 字节）——把 IP 地址解析成 MAC 地址的链路层协议。
 *
 * <pre>
 *   0                   1                   2                   3
 *   0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1
 *  +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 *  |        硬件类型 (16)          |       协议类型 (16)            |
 *  +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 *  |  硬件地址长度(8)  | 协议地址长度(8)|         操作码 (16)         |
 *  +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 *  |                   发送方硬件地址 (32/48)                      |
 *  +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 *  |                   发送方协议地址 (32)                         |
 *  +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 *  |                   目标硬件地址 (32/48)                       |
 *  +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 *  |                   目标协议地址 (32)                          |
 *  +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 * </pre>
 *
 * 典型取值（以太网 + IPv4）：硬件类型=1（以太网）、协议类型=0x0800（IPv4）、
 * 硬件地址长度=6（MAC）、协议地址长度=4（IP）、操作码 1=请求 2=回复。
 * 以太网 + IPv4 时固定 28 字节（2+2+1+1+2+6+4+6+4）。
 *
 * 工作过程（面试常问）：
 * - 主机要发数据给同一子网的 192.168.1.1，但只知道 IP 不知道 MAC（IP 地址端到端、MAC 地址逐跳）。
 * - 发送方广播 ARP 请求（操作码 1）：目标 MAC 填 FF:FF:FF:FF:FF:FF，问「谁是 192.168.1.1？」。
 * - 只有 IP 匹配的主机回 ARP 回复（操作码 2），告知自己的 MAC；其他主机丢弃。
 * - 主机把「IP -> MAC」存入 ARP 缓存（几分钟过期），之后直接单播发送，不必每次都广播。
 * - 免费 ARP（Gratuitous ARP）：主机主动广播自己的 IP->MAC 映射，用于 IP 冲突检测、故障切换。
 *
 * 关键理解：ARP 只工作在**同一子网内**（跨子网找网关 MAC）；ARP 报文直接封装在以太网帧里
 * （EtherType=0x0806），不经过 IP 层——所以 PacketParser 在 IP 之前先按 EtherType 分派。
 * 注意 ARP 协议类型字段是 0x0800（它解析的是 IP），而以太网帧头里的 EtherType 是 0x0806，
 * 两个字段不要混淆。
 */
public class ArpHeader {

    /** ARP 报文固定 28 字节（以太网 + IPv4） */
    public static final int HEADER_LENGTH = 28;

    /** 硬件类型：以太网 */
    public static final int HARDWARE_ETHERNET = 1;
    /** 协议类型：IPv4（表示「我要解析的地址是 IP 地址」） */
    public static final int PROTOCOL_IPV4 = 0x0800;

    /** 操作码：ARP 请求（广播询问） */
    public static final int OPCODE_REQUEST = 1;
    /** 操作码：ARP 回复（被询问方应答） */
    public static final int OPCODE_REPLY = 2;

    private final int hardwareType;     // 2 字节：1 = 以太网
    private final int protocolType;     // 2 字节：0x0800 = IPv4
    private final int hardwareSize;     // 1 字节：MAC 地址长度 6
    private final int protocolSize;     // 1 字节：IP 地址长度 4
    private final int opcode;           // 2 字节：1 = 请求，2 = 回复
    private final byte[] senderMac;     // 6 字节
    private final int senderIp;         // 4 字节
    private final byte[] targetMac;     // 6 字节（请求时通常全 0 或广播）
    private final int targetIp;         // 4 字节

    public ArpHeader(int hardwareType, int protocolType, int hardwareSize, int protocolSize,
                     int opcode, byte[] senderMac, int senderIp,
                     byte[] targetMac, int targetIp) {
        if (senderMac.length != 6 || targetMac.length != 6) {
            throw new IllegalArgumentException("ARP 的 MAC 地址必须 6 字节");
        }
        this.hardwareType = hardwareType;
        this.protocolType = protocolType;
        this.hardwareSize = hardwareSize;
        this.protocolSize = protocolSize;
        this.opcode = opcode;
        this.senderMac = senderMac;
        this.senderIp = senderIp;
        this.targetMac = targetMac;
        this.targetIp = targetIp;
    }

    /** 编码为 28 字节（网络字节序：大端）。 */
    public byte[] encode() {
        byte[] bytes = new byte[HEADER_LENGTH];
        writeShort(bytes, 0, hardwareType);
        writeShort(bytes, 2, protocolType);
        bytes[4] = (byte) hardwareSize;
        bytes[5] = (byte) protocolSize;
        writeShort(bytes, 6, opcode);
        System.arraycopy(senderMac, 0, bytes, 8, 6);
        writeInt(bytes, 14, senderIp);
        System.arraycopy(targetMac, 0, bytes, 18, 6);
        writeInt(bytes, 24, targetIp);
        return bytes;
    }

    /** 从字节解析 ARP 报文（默认从偏移 0 开始）。 */
    public static ArpHeader parse(byte[] bytes) {
        return parse(bytes, 0);
    }

    /**
     * 从字节数组的指定偏移处解析 ARP 报文。
     *
     * @param bytes  完整帧（以太网帧头 + ARP 报文）
     * @param offset ARP 报文起始位置（紧接 14 字节以太网帧头之后）
     */
    public static ArpHeader parse(byte[] bytes, int offset) {
        if (bytes.length - offset < HEADER_LENGTH) {
            throw new IllegalArgumentException(
                    "ARP 报文固定 28 字节，偏移 " + offset + " 后剩余 "
                            + (bytes.length - offset));
        }
        int hardwareType = readShort(bytes, offset);
        int protocolType = readShort(bytes, offset + 2);
        int hardwareSize = bytes[offset + 4] & 0xFF;
        int protocolSize = bytes[offset + 5] & 0xFF;
        int opcode = readShort(bytes, offset + 6);
        byte[] senderMac = new byte[6];
        byte[] targetMac = new byte[6];
        System.arraycopy(bytes, offset + 8, senderMac, 0, 6);
        int senderIp = (int) readInt(bytes, offset + 14);
        System.arraycopy(bytes, offset + 18, targetMac, 0, 6);
        int targetIp = (int) readInt(bytes, offset + 24);
        return new ArpHeader(hardwareType, protocolType, hardwareSize, protocolSize,
                opcode, senderMac, senderIp, targetMac, targetIp);
    }

    /** 操作码可读描述：请求/回复。 */
    public String opcodeName() {
        return switch (opcode) {
            case OPCODE_REQUEST -> "ARP Request（广播询问）";
            case OPCODE_REPLY -> "ARP Reply（应答）";
            default -> "未知操作码 " + opcode;
        };
    }

    public int hardwareType() {
        return hardwareType;
    }

    public int protocolType() {
        return protocolType;
    }

    public int hardwareSize() {
        return hardwareSize;
    }

    public int protocolSize() {
        return protocolSize;
    }

    public int opcode() {
        return opcode;
    }

    public byte[] senderMac() {
        return senderMac;
    }

    public int senderIp() {
        return senderIp;
    }

    public byte[] targetMac() {
        return targetMac;
    }

    public int targetIp() {
        return targetIp;
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

    @Override
    public String toString() {
        return "ArpHeader{" + opcodeName() +
                ", 硬件类型=" + hardwareType + ", 协议类型=0x"
                + Integer.toHexString(protocolType) +
                ", " + hardwareSize + "/" + protocolSize +
                " 字节, 发送方 " + EthernetFrame.toMacString(senderMac)
                + " -> " + IpHeader.toIpString(senderIp) +
                ", 目标 " + EthernetFrame.toMacString(targetMac)
                + " -> " + IpHeader.toIpString(targetIp) +
                '}';
    }
}
