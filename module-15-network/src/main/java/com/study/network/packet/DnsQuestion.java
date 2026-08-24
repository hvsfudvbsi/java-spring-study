package com.study.network.packet;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

/**
 * DNS 查询记录（Question）——头部 12 字节之后的第一个可变长部分。
 *
 * <pre>
 *  +---------------------+----------------+---------+
 *  |  QNAME（域名，变长）  | QTYPE (16)     | QCLASS  |
 *  +---------------------+----------------+---------+
 * </pre>
 *
 * QNAME 用**标签编码**表示域名：每个标签 = 1 字节长度 + 标签内容，最后以 0x00 结尾。
 * <pre>
 *   www.example.com  ->  03 77 77 77  07 65 78 61 6d 70 6c 65  03 63 6f 6d  00
 *                        [3]www      [7]example              [3]com    [结束]
 * </pre>
 * 约束：单个标签最长 63 字节、完整域名最长 255 字节（标签是 ASCII 字符）。
 *
 * QTYPE（查询类型）：1=A（IPv4 地址）、2=NS（域名服务器）、5=CNAME（别名）、
 * 15=MX（邮件交换）、28=AAAA（IPv6 地址）、255=ANY。
 * QCLASS（查询类）：1=IN（Internet，几乎所有查询都用它）。
 *
 * 面试常问：解析响应里的域名时会出现**压缩指针**——回答记录里重复出现查询中的域名，
 * 为了省空间不重写整个名字，而是写一个 0xC0 开头的 2 字节指针指向报文前面出现过的
 * 名字位置。查询（Question）里不会出现压缩指针，本类解析到 0xC0 会明确拒绝并提示。
 */
public class DnsQuestion {

    /** 查询类型：A（IPv4 地址） */
    public static final int QTYPE_A = 1;
    /** 查询类型：NS（域名服务器） */
    public static final int QTYPE_NS = 2;
    /** 查询类型：CNAME（别名） */
    public static final int QTYPE_CNAME = 5;
    /** 查询类型：MX（邮件交换） */
    public static final int QTYPE_MX = 15;
    /** 查询类型：AAAA（IPv6 地址） */
    public static final int QTYPE_AAAA = 28;
    /** 查询类型：ANY（所有记录） */
    public static final int QTYPE_ANY = 255;

    /** 查询类：IN（Internet） */
    public static final int QCLASS_IN = 1;

    /** 单个标签最大长度（1 字节长度字段能表示 0~63） */
    public static final int MAX_LABEL_LENGTH = 63;
    /** 完整域名最大长度（含长度字节与结束符的编码上限） */
    public static final int MAX_NAME_LENGTH = 255;

    private final String name;   // 点分域名，如 www.example.com
    private final int qtype;     // 16 bit 查询类型
    private final int qclass;    // 16 bit 查询类

    public DnsQuestion(String name, int qtype, int qclass) {
        if (name == null || name.isEmpty()) {
            throw new IllegalArgumentException("域名不能为空");
        }
        for (String label : name.split("\\.")) {
            if (label.isEmpty()) {
                throw new IllegalArgumentException("域名存在空标签（如连续两个点）: " + name);
            }
            if (label.length() > MAX_LABEL_LENGTH) {
                throw new IllegalArgumentException("单个标签最长 " + MAX_LABEL_LENGTH
                        + " 字节: " + label);
            }
        }
        if (encodeName(name).length > MAX_NAME_LENGTH) {
            throw new IllegalArgumentException("完整域名编码后最长 " + MAX_NAME_LENGTH + " 字节: " + name);
        }
        this.name = name;
        this.qtype = qtype;
        this.qclass = qclass;
    }

    /** 编码为完整查询记录：QNAME（标签编码）+ QTYPE + QCLASS。 */
    public byte[] encode() {
        byte[] nameBytes = encodeName(name);
        byte[] bytes = new byte[nameBytes.length + 4];
        System.arraycopy(nameBytes, 0, bytes, 0, nameBytes.length);
        writeShort(bytes, nameBytes.length, qtype);
        writeShort(bytes, nameBytes.length + 2, qclass);
        return bytes;
    }

    /**
     * 把点分域名编码为 DNS 标签序列：
     * www.example.com -> [3]www [7]example [3]com [0]（共 17 字节，含结尾 0x00）。
     */
    public static byte[] encodeName(String name) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (String label : name.split("\\.")) {
            if (label.isEmpty() || label.length() > MAX_LABEL_LENGTH) {
                throw new IllegalArgumentException("非法标签（空或超过 " + MAX_LABEL_LENGTH + " 字节）: " + label);
            }
            out.write(label.length());
            out.writeBytes(label.getBytes(StandardCharsets.US_ASCII));
        }
        out.write(0); // 根标签：域名结束
        return out.toByteArray();
    }

    /** 解析结果：记录本身 + 该记录占用的字节数（用于连续解析多条 Question）。 */
    public record ParsedQuestion(DnsQuestion question, int bytesConsumed) {
    }

    /** 从字节数组指定偏移处解析一条查询记录。 */
    public static ParsedQuestion parseAt(byte[] bytes, int offset) {
        ParsedName parsedName = parseName(bytes, offset);
        int pos = offset + parsedName.bytesConsumed();
        if (pos + 4 > bytes.length) {
            throw new IllegalArgumentException("QTYPE/QCLASS 不足 4 字节");
        }
        int qtype = readShort(bytes, pos);
        int qclass = readShort(bytes, pos + 2);
        return new ParsedQuestion(
                new DnsQuestion(parsedName.name(), qtype, qclass),
                parsedName.bytesConsumed() + 4);
    }

    /** 从字节数组解析单条查询记录（默认从偏移 0 开始，用于独立测试）。 */
    public static DnsQuestion parse(byte[] bytes) {
        return parseAt(bytes, 0).question();
    }

    /** 把标签序列解码回点分域名（只读名字本身，不要求后面有 QTYPE/QCLASS）。 */
    public static String decodeName(byte[] bytes) {
        return parseName(bytes, 0).name();
    }

    /** 标签解析结果：域名 + 编码占用的字节数（含结束符 0x00）。 */
    private record ParsedName(String name, int bytesConsumed) {
    }

    /** 从偏移处逐个解析标签直到结束符 0x00，返回域名与占用字节数。 */
    private static ParsedName parseName(byte[] bytes, int offset) {
        int pos = offset;
        StringBuilder name = new StringBuilder();
        while (true) {
            if (pos >= bytes.length) {
                throw new IllegalArgumentException("域名标签未以 0x00 结束（报文被截断）");
            }
            int len = bytes[pos] & 0xFF;
            if (len == 0) {
                pos++; // 结束符
                break;
            }
            if ((len & 0xC0) == 0xC0) {
                throw new IllegalArgumentException("遇到压缩指针 0x"
                        + Integer.toHexString(len) + "——压缩指针只出现在响应（Answer）中，"
                        + "查询（Question）不应包含，本模块暂不解析");
            }
            if (len > MAX_LABEL_LENGTH) {
                throw new IllegalArgumentException("标签长度字段非法（>63）: " + len);
            }
            if (pos + 1 + len > bytes.length) {
                throw new IllegalArgumentException("标签内容超出报文长度");
            }
            if (name.length() > 0) {
                name.append('.');
            }
            name.append(new String(bytes, pos + 1, len, StandardCharsets.US_ASCII));
            pos += 1 + len;
        }
        return new ParsedName(name.toString(), pos - offset);
    }

    /** 查询类型可读描述。 */
    public String qtypeName() {
        return switch (qtype) {
            case QTYPE_A -> "A";
            case QTYPE_NS -> "NS";
            case QTYPE_CNAME -> "CNAME";
            case QTYPE_MX -> "MX";
            case QTYPE_AAAA -> "AAAA";
            case QTYPE_ANY -> "ANY";
            default -> "未知类型 " + qtype;
        };
    }

    /** 查询类可读描述。 */
    public String qclassName() {
        return qclass == QCLASS_IN ? "IN" : "未知类 " + qclass;
    }

    public String name() {
        return name;
    }

    public int qtype() {
        return qtype;
    }

    public int qclass() {
        return qclass;
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
        return "DnsQuestion{" + name + ", type=" + qtypeName() + "(" + qtype
                + "), class=" + qclassName() + "(" + qclass + ")}";
    }
}
