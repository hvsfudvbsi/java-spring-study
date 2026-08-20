package com.study.network.packet;

/**
 * 以太网帧头（14 字节）——数据链路层报文头。
 *
 * <pre>
 *   +------------------------+------------------------+------------------+
 *   | 目的 MAC (6 字节)       | 源 MAC (6 字节)         | 类型/长度 (2 字节) |
 *   +------------------------+------------------------+------------------+
 * </pre>
 *
 * - MAC 地址：48 bit（6 字节），形如 AA:BB:CC:DD:EE:FF
 * - 类型字段：0x0800 = IPv4，0x0806 = ARP，0x86DD = IPv6
 * - 帧头之后是网络层数据（如 IP 报文），帧尾还有 4 字节 FCS 校验（不在本类范围）
 *
 * 学习点：MAC 地址是"同一局域网内下一跳"的地址，IP 地址是"端到端"的地址。
 * 数据经过路由器时，每跳的 MAC 地址都会变，而 IP 地址不变——这就是封装/解封装。
 */
public class EthernetFrame {

    /** 以太网帧头固定 14 字节 */
    public static final int HEADER_LENGTH = 14;

    /** EtherType：IPv4 */
    public static final int ETHERTYPE_IPV4 = 0x0800;
    /** EtherType：ARP */
    public static final int ETHERTYPE_ARP = 0x0806;

    private final byte[] destinationMac; // 6 字节
    private final byte[] sourceMac;      // 6 字节
    private final int etherType;         // 2 字节

    public EthernetFrame(byte[] destinationMac, byte[] sourceMac, int etherType) {
        if (destinationMac.length != 6 || sourceMac.length != 6) {
            throw new IllegalArgumentException("MAC 地址必须 6 字节");
        }
        this.destinationMac = destinationMac;
        this.sourceMac = sourceMac;
        this.etherType = etherType;
    }

    /** 编码帧头为 14 字节 */
    public byte[] encode() {
        byte[] bytes = new byte[HEADER_LENGTH];
        System.arraycopy(destinationMac, 0, bytes, 0, 6);
        System.arraycopy(sourceMac, 0, bytes, 6, 6);
        bytes[12] = (byte) ((etherType >> 8) & 0xFF);
        bytes[13] = (byte) (etherType & 0xFF);
        return bytes;
    }

    /** 从字节解析帧头 */
    public static EthernetFrame parse(byte[] bytes) {
        if (bytes.length < HEADER_LENGTH) {
            throw new IllegalArgumentException(
                    "以太网帧头至少 14 字节，实际 " + bytes.length);
        }
        byte[] dest = new byte[6];
        byte[] source = new byte[6];
        System.arraycopy(bytes, 0, dest, 0, 6);
        System.arraycopy(bytes, 6, source, 0, 6);
        int etherType = ((bytes[12] & 0xFF) << 8) | (bytes[13] & 0xFF);
        return new EthernetFrame(dest, source, etherType);
    }

    /** 把 6 字节 MAC 格式化为 AA:BB:CC:DD:EE:FF */
    public static String toMacString(byte[] mac) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < mac.length; i++) {
            if (i > 0) {
                sb.append(':');
            }
            sb.append(String.format("%02X", mac[i] & 0xFF));
        }
        return sb.toString();
    }

    /** 把 AA:BB:CC:DD:EE:FF 解析为 6 字节 */
    public static byte[] parseMac(String mac) {
        String[] parts = mac.split(":");
        if (parts.length != 6) {
            throw new IllegalArgumentException("非法 MAC: " + mac);
        }
        byte[] result = new byte[6];
        for (int i = 0; i < 6; i++) {
            result[i] = (byte) Integer.parseInt(parts[i], 16);
        }
        return result;
    }

    public byte[] destinationMac() {
        return destinationMac;
    }

    public byte[] sourceMac() {
        return sourceMac;
    }

    public int etherType() {
        return etherType;
    }

    @Override
    public String toString() {
        return "EthernetFrame{" +
                "destinationMac=" + toMacString(destinationMac) +
                ", sourceMac=" + toMacString(sourceMac) +
                ", etherType=0x" + Integer.toHexString(etherType) +
                '}';
    }
}
