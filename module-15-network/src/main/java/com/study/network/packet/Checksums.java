package com.study.network.packet;

/**
 * 校验和计算工具（反码和 / ones' complement sum）——IP、TCP、UDP 共用的底层算法（RFC 1071）。
 *
 * 算法三步：
 * 1. 把待校验数据按 16 bit 一组（大端）累加；
 * 2. 把进位（高 16 位）折叠回低 16 位，直到只剩 16 位；
 * 3. 取反码（~sum & 0xFFFF），结果就是校验和。
 *
 * 校验时把「数据 + 算出的校验和」整体再算一遍反码和，结果应为 0xFFFF（全 1），否则报文已损坏。
 *
 * <b>IP 首部校验和</b>：只覆盖 IP 首部本身（不含上层数据），计算时首部的校验和字段先置 0。
 *
 * <b>TCP/UDP 校验和</b>：覆盖「伪首部 + 报文段」，伪首部是 12 字节的虚拟头，不随报文传输：
 * <pre>
 *   0                   1                   2                   3
 *   +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 *   |                        源 IP 地址 (32)                        |
 *   +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 *   |                       目的 IP 地址 (32)                       |
 *   +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 *   |     0 (8)     | 协议号 (8)    |     TCP/UDP 长度 (16)          |
 *   +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 * </pre>
 *
 * 为什么 TCP/UDP 校验和要有伪首部（面试常问）：
 * 传输层只看到端口号，感知不到 IP 地址。若校验和不覆盖 IP 地址，
 * 报文被路由到错误主机时接收方无法发现。伪首部把 IP 地址「借」进校验范围
 * （协议号防止跨协议误判，长度字段与 TCP/UDP 首部里的一致），
 * 但只在计算/校验时临时拼出，不占报文长度、不参与传输。
 * 代价是：TCP/UDP 校验和无法只在本机独立计算，必须知道源/目的 IP。
 *
 * 注意（面试细节）：
 * - UDP 校验和在 IPv4 下是**可选**的（置 0 表示未计算，IPv6 下强制要求）；TCP 校验和**必须**计算。
 * - 数据为奇数个字节时，计算时末尾补一个 0x00（不参与传输）。
 * - 反码和有一个特例：结果 0x0000 会以 0xFFFF 传输（避免与「未计算」混淆）——
 *   本工具按 RFC 1071 实现，TCP/UDP 的校验和字段约定若计算结果为 0 则填 0xFFFF。
 */
public final class Checksums {

    private Checksums() {
    }

    /**
     * 反码和：16 bit 大端字累加并折叠进位，返回折叠后的 16 bit 和（0~0xFFFF）。
     * 奇数长度末尾按 0x00 补齐（RFC 1071：补齐字节不参与传输）。
     */
    public static int onesComplementSum(byte[] data) {
        return fold(sumWords(data));
    }

    /** 取反码：校验和 = ~反码和。 */
    public static int complement(int sum) {
        return (~sum) & 0xFFFF;
    }

    /** IP 首部校验和：只覆盖首部本身，调用前首部的校验和字段必须已置 0。 */
    public static int ipHeaderChecksum(byte[] header) {
        return complement(onesComplementSum(header));
    }

    /**
     * 传输层校验和（TCP/UDP）：伪首部(12 字节) + 完整报文段。
     *
     * @param sourceIp      源 IP（32 bit）
     * @param destinationIp 目的 IP（32 bit）
     * @param protocol      协议号（6=TCP，17=UDP）
     * @param segmentLength 伪首部的长度字段：TCP = 首部 + 数据总长；UDP = UDP 首部里的长度字段
     * @param segment       完整报文段（首部 + 数据），首部中的校验和字段必须已置 0
     */
    public static int transportChecksum(int sourceIp, int destinationIp, int protocol,
                                        int segmentLength, byte[] segment) {
        return complement(fold(sumWords(pseudoHeader(sourceIp, destinationIp, protocol, segmentLength))
                + sumWords(segment)));
    }

    /**
     * 校验「伪首部 + 含校验和的完整报文段」：整体反码和应为 0xFFFF（全 1），
     * 否则说明报文在传输中至少被改动了一个字节。这是抓包/排障时手工验证校验和的方法。
     */
    public static boolean verifyTransport(int sourceIp, int destinationIp, int protocol,
                                          int segmentLength, byte[] segment) {
        return fold(sumWords(pseudoHeader(sourceIp, destinationIp, protocol, segmentLength))
                + sumWords(segment)) == 0xFFFF;
    }

    /** 拼出 12 字节伪首部：源 IP + 目的 IP + 0 + 协议号 + 长度。 */
    private static byte[] pseudoHeader(int sourceIp, int destinationIp, int protocol, int length) {
        byte[] pseudo = new byte[12];
        writeInt(pseudo, 0, sourceIp);
        writeInt(pseudo, 4, destinationIp);
        pseudo[8] = 0;
        pseudo[9] = (byte) protocol;
        writeShort(pseudo, 10, length);
        return pseudo;
    }

    /** 按 16 bit 大端字累加（不折叠），奇数长度末尾补 0。 */
    private static long sumWords(byte[] data) {
        long sum = 0;
        for (int i = 0; i < data.length; i += 2) {
            int word = ((data[i] & 0xFF) << 8)
                    | (i + 1 < data.length ? (data[i + 1] & 0xFF) : 0);
            sum += word;
        }
        return sum;
    }

    /** 把进位折叠回低 16 位：0x1_xxxx -> 0xxxxx + 0x1，直到只剩 16 位。 */
    private static int fold(long sum) {
        while ((sum >> 16) != 0) {
            sum = (sum & 0xFFFF) + (sum >> 16);
        }
        return (int) sum;
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
}
