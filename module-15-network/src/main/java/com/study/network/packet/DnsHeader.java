package com.study.network.packet;

/**
 * DNS 报文头部（固定 12 字节）——应用层协议 DNS 的第一个字段，与 ICMP/ARP 一样可独立编解码。
 *
 * <pre>
 *   0                   1                   2                   3
 *   0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1
 *  +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 *  |        事务 ID (16)           |           标志 (16)            |
 *  +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 *  |      问题数 QDCOUNT (16)      |      回答数 ANCOUNT (16)       |
 *  +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 *  |   授权记录数 NSCOUNT (16)     |   附加记录数 ARCOUNT (16)       |
 *  +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 * </pre>
 *
 * 标志字段（16 bit，面试常问）：
 * <pre>
 *  +--+--+--+--+--+--+--+--+--+--+--+--+--+--+--+--+
 *  |QR|   Opcode  |AA|TC|RD|RA|   Z    |   RCODE   |
 *  +--+--+--+--+--+--+--+--+--+--+--+--+--+--+--+--+
 * </pre>
 * - QR（1 bit）：0 = 查询，1 = 响应
 * - Opcode（4 bit）：0 = 标准查询（QUERY）
 * - AA（1 bit）：权威回答（应答来自该域名的权威服务器）
 * - TC（1 bit）：截断（响应太长放不进一个 UDP 数据报，需要走 TCP 重查）
 * - RD（1 bit）：期望递归（客户端希望本地 DNS 帮它查到底）
 * - RA（1 bit）：可递归（服务器支持递归查询）
 * - Z（3 bit）：保留，恒 0
 * - RCODE（4 bit）：响应码（0=NOERROR，3=NXDOMAIN 域名不存在，5=REFUSED）
 *
 * 头部之后是可变长的 Question/Answer 记录（见 {@link DnsQuestion}），
 * 头部里的 QDCOUNT/ANCOUNT 告诉解析器每种记录各有多少条。
 *
 * DNS 运行在 **UDP 53 端口**（快：一个数据报就装下一个查询；应答超过 512 字节
 * 或需要可靠传输时切到 TCP 53）。查询：客户端发 Query，服务器回 Response，
 * 事务 ID 用于把响应和请求配对（ID 不匹配的响应直接丢弃，防伪造）。
 */
public class DnsHeader {

    /** DNS 头部固定 12 字节 */
    public static final int HEADER_LENGTH = 12;

    /** 标志位：QR（0=查询，1=响应） */
    private static final int FLAG_QR = 0x8000;
    /** 标志位：Opcode 占 bit 11~14 */
    private static final int OPCODE_MASK = 0x7800;
    /** 标志位：AA（权威回答） */
    private static final int FLAG_AA = 0x0400;
    /** 标志位：TC（截断） */
    private static final int FLAG_TC = 0x0200;
    /** 标志位：RD（期望递归） */
    private static final int FLAG_RD = 0x0100;
    /** 标志位：RA（可递归） */
    private static final int FLAG_RA = 0x0080;
    /** 标志位：RCODE 占 bit 0~3 */
    private static final int RCODE_MASK = 0x000F;

    private final int id;                    // 16 bit 事务 ID（配对请求与响应）
    private final boolean response;          // QR：true = 响应
    private final int opcode;                // 4 bit：0 = 标准查询
    private final boolean authoritative;     // AA：权威回答
    private final boolean truncated;         // TC：截断
    private final boolean recursionDesired;  // RD：期望递归
    private final boolean recursionAvailable;// RA：可递归
    private final int rcode;                 // 4 bit：响应码
    private final int questionCount;         // QDCOUNT：问题记录数
    private final int answerCount;           // ANCOUNT：回答记录数
    private final int authorityCount;        // NSCOUNT：授权记录数
    private final int additionalCount;       // ARCOUNT：附加记录数

    /** 完整构造（12 个字段对应头部 12 字节）。 */
    public DnsHeader(int id, boolean response, int opcode,
                     boolean authoritative, boolean truncated,
                     boolean recursionDesired, boolean recursionAvailable,
                     int rcode, int questionCount, int answerCount,
                     int authorityCount, int additionalCount) {
        if (opcode < 0 || opcode > 0xF) {
            throw new IllegalArgumentException("Opcode 只占 4 bit（0~15）: " + opcode);
        }
        if (rcode < 0 || rcode > 0xF) {
            throw new IllegalArgumentException("RCODE 只占 4 bit（0~15）: " + rcode);
        }
        this.id = id;
        this.response = response;
        this.opcode = opcode;
        this.authoritative = authoritative;
        this.truncated = truncated;
        this.recursionDesired = recursionDesired;
        this.recursionAvailable = recursionAvailable;
        this.rcode = rcode;
        this.questionCount = questionCount;
        this.answerCount = answerCount;
        this.authorityCount = authorityCount;
        this.additionalCount = additionalCount;
    }

    /** 典型客户端查询：QR=0、标准查询、RD 按需设置，回答/授权/附加记录数都为 0。 */
    public static DnsHeader query(int id, boolean recursionDesired, int questionCount) {
        return new DnsHeader(id, false, 0, false, false,
                recursionDesired, false, 0,
                questionCount, 0, 0, 0);
    }

    /** 典型服务器响应：QR=1、RA 置位、按需填 rcode 与回答数。 */
    public static DnsHeader response(int id, boolean recursionAvailable, int rcode,
                                     int questionCount, int answerCount) {
        return new DnsHeader(id, true, 0, false, false,
                false, recursionAvailable, rcode,
                questionCount, answerCount, 0, 0);
    }

    /** 编码为 12 字节（网络字节序：大端）。 */
    public byte[] encode() {
        byte[] bytes = new byte[HEADER_LENGTH];
        writeShort(bytes, 0, id);
        int flags = 0;
        if (response) {
            flags |= FLAG_QR;
        }
        flags |= (opcode & 0xF) << 11;
        if (authoritative) {
            flags |= FLAG_AA;
        }
        if (truncated) {
            flags |= FLAG_TC;
        }
        if (recursionDesired) {
            flags |= FLAG_RD;
        }
        if (recursionAvailable) {
            flags |= FLAG_RA;
        }
        flags |= rcode & 0xF;
        writeShort(bytes, 2, flags);
        writeShort(bytes, 4, questionCount);
        writeShort(bytes, 6, answerCount);
        writeShort(bytes, 8, authorityCount);
        writeShort(bytes, 10, additionalCount);
        return bytes;
    }

    /** 从 12 字节解析 DNS 头部（默认从偏移 0 开始）。 */
    public static DnsHeader parse(byte[] bytes) {
        return parse(bytes, 0);
    }

    /** 从字节数组的指定偏移处解析 DNS 头部。 */
    public static DnsHeader parse(byte[] bytes, int offset) {
        if (bytes.length - offset < HEADER_LENGTH) {
            throw new IllegalArgumentException(
                    "DNS 头部固定 12 字节，偏移 " + offset + " 后剩余 "
                            + (bytes.length - offset));
        }
        int id = readShort(bytes, offset);
        int flags = readShort(bytes, offset + 2);
        return new DnsHeader(
                id,
                (flags & FLAG_QR) != 0,
                (flags & OPCODE_MASK) >> 11,
                (flags & FLAG_AA) != 0,
                (flags & FLAG_TC) != 0,
                (flags & FLAG_RD) != 0,
                (flags & FLAG_RA) != 0,
                flags & RCODE_MASK,
                readShort(bytes, offset + 4),
                readShort(bytes, offset + 6),
                readShort(bytes, offset + 8),
                readShort(bytes, offset + 10));
    }

    /** 拷贝并更换事务 ID（不可变风格）。演示用途：DNS 防伪造——响应必须回同一个 ID 才能配对，
     *  换个 ID 再解码，就能模拟「ID 不匹配的响应被丢弃」。 */
    public DnsHeader withId(int newId) {
        return new DnsHeader(newId, response, opcode, authoritative, truncated,
                recursionDesired, recursionAvailable, rcode,
                questionCount, answerCount, authorityCount, additionalCount);
    }

    /** 响应码可读描述（常见值）。 */
    public String rcodeName() {
        return switch (rcode) {
            case 0 -> "NOERROR（无错误）";
            case 1 -> "FORMERR（格式错误）";
            case 2 -> "SERVFAIL（服务器故障）";
            case 3 -> "NXDOMAIN（域名不存在）";
            case 4 -> "NOTIMP（不支持的操作）";
            case 5 -> "REFUSED（拒绝）";
            default -> "未知响应码 " + rcode;
        };
    }

    public int id() {
        return id;
    }

    public boolean response() {
        return response;
    }

    public int opcode() {
        return opcode;
    }

    public boolean authoritative() {
        return authoritative;
    }

    public boolean truncated() {
        return truncated;
    }

    public boolean recursionDesired() {
        return recursionDesired;
    }

    public boolean recursionAvailable() {
        return recursionAvailable;
    }

    public int rcode() {
        return rcode;
    }

    public int questionCount() {
        return questionCount;
    }

    public int answerCount() {
        return answerCount;
    }

    public int authorityCount() {
        return authorityCount;
    }

    public int additionalCount() {
        return additionalCount;
    }

    private static void writeShort(byte[] bytes, int offset, int value) {
        bytes[offset] = (byte) ((value >> 8) & 0xFF);
        bytes[offset + 1] = (byte) (value & 0xFF);
    }

    private static int readShort(byte[] bytes, int offset) {
        return ((bytes[offset] & 0xFF) << 8) | (bytes[offset + 1] & 0xFF);
    }

    @Override
    public String toString() {
        return "DnsHeader{id=0x" + Integer.toHexString(id)
                + ", " + (response ? "Response" : "Query")
                + ", opcode=" + opcode
                + (authoritative ? ", AA" : "")
                + (truncated ? ", TC" : "")
                + (recursionDesired ? ", RD" : "")
                + (recursionAvailable ? ", RA" : "")
                + ", rcode=" + rcode + " (" + rcodeName() + ")"
                + ", QDCOUNT=" + questionCount
                + ", ANCOUNT=" + answerCount
                + ", NSCOUNT=" + authorityCount
                + ", ARCOUNT=" + additionalCount
                + '}';
    }
}
