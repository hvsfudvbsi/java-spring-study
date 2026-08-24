package com.study.network.packet;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * TCP 选项（Options）——20 字节固定首部之后的变长部分，面试必问「数据偏移」的意义就在这里。
 *
 * 为什么需要选项：基础首部只够表达端口/序号/窗口，TCP 的很多能力（协商最大报文段、
 * 窗口缩放、选择性确认、时间戳）必须额外协商，这些参数就放在选项里。
 * 选项区域长度 = 数据偏移 × 4 - 20 字节，不足 4 的倍数时用 NOP 填充。
 *
 * 选项通用格式：`[Kind(1)][Length(1)][Value...]`，Length 包含 Kind 和 Length 本身。
 * 两个特殊选项只有 1 字节、没有 Length 字段：
 * - EOL（0x00）：End of Option List，选项列表结束
 * - NOP（0x01）：No Operation，1 字节填充（用于把后续选项对齐到 4 字节边界）
 *
 * 常见选项（面试常问）：
 * | Kind | 选项 | 长度 | 作用 |
 * |------|------|------|------|
 * | 2 | MSS | 4 | 协商最大报文段（双方取小），SYN 里必带，如 1460 = 1500(MTU) - 20(IP) - 20(TCP) |
 * | 3 | Window Scale | 3 | 窗口缩放：窗口字段 16 bit 不够用，左移 N 位把窗口放大到最大 1GB |
 * | 4 | SACK-Permitted | 2 | 声明支持选择性确认（丢包时只重传丢失段，不用全部重传） |
 * | 5 | SACK | 变长 | 告知对端哪些段已收到（乱序到达时用） |
 * | 8 | Timestamp | 10 | 时间戳：计算 RTT、防序号回绕（PAWS） |
 *
 * 本类实现选项的构造（工厂方法）、编码（NOP 对齐）与解析（按 Kind/Length 迭代）。
 */
public record TcpOption(int kind, int length, byte[] value) {

    /** 选项 Kind：EOL（选项列表结束） */
    public static final int KIND_EOL = 0;
    /** 选项 Kind：NOP（1 字节填充） */
    public static final int KIND_NOP = 1;
    /** 选项 Kind：MSS（最大报文段） */
    public static final int KIND_MSS = 2;
    /** 选项 Kind：Window Scale（窗口缩放） */
    public static final int KIND_WINDOW_SCALE = 3;
    /** 选项 Kind：SACK-Permitted（选择性确认声明） */
    public static final int KIND_SACK_PERMITTED = 4;
    /** 选项 Kind：SACK（选择性确认块） */
    public static final int KIND_SACK = 5;
    /** 选项 Kind：Timestamp（时间戳） */
    public static final int KIND_TIMESTAMP = 8;

    /**
     * 构造并校验：Kind 0/1 是 1 字节选项（无 Length 字段、无 Value）；
     * 其余选项 Length = 2 + Value 长度（Length 包含 Kind 和 Length 本身）。
     */
    public TcpOption {
        if (kind < 0 || kind > 0xFF) {
            throw new IllegalArgumentException("选项 Kind 只占 1 字节（0~255）: " + kind);
        }
        if (kind == KIND_EOL || kind == KIND_NOP) {
            if (length != 1 || value.length != 0) {
                throw new IllegalArgumentException("EOL/NOP 是 1 字节选项，不应带 Length/Value");
            }
        } else {
            if (length != 2 + value.length) {
                throw new IllegalArgumentException("选项 Length 必须 = 2 + Value 长度（kind=" + kind
                        + ", length=" + length + ", value=" + value.length + "）");
            }
        }
    }

    /** MSS 选项：协商最大报文段，如 1460 = 1500(MTU) - 20(IP) - 20(TCP)。 */
    public static TcpOption mss(int mss) {
        byte[] value = new byte[]{(byte) (mss >> 8), (byte) mss};
        return new TcpOption(KIND_MSS, 2 + value.length, value);
    }

    /** Window Scale 选项：窗口字段左移 shift 位放大（0~14）。 */
    public static TcpOption windowScale(int shift) {
        return new TcpOption(KIND_WINDOW_SCALE, 3, new byte[]{(byte) shift});
    }

    /** SACK-Permitted 选项：声明支持选择性确认（无 Value，Length=2）。 */
    public static TcpOption sackPermitted() {
        return new TcpOption(KIND_SACK_PERMITTED, 2, new byte[0]);
    }

    /** Timestamp 选项：TSval（发送方时间戳）+ TSecr（回显对端上次时间戳）。 */
    public static TcpOption timestamp(long tsval, long tsecr) {
        byte[] value = new byte[8];
        value[0] = (byte) (tsval >> 24);
        value[1] = (byte) (tsval >> 16);
        value[2] = (byte) (tsval >> 8);
        value[3] = (byte) tsval;
        value[4] = (byte) (tsecr >> 24);
        value[5] = (byte) (tsecr >> 16);
        value[6] = (byte) (tsecr >> 8);
        value[7] = (byte) tsecr;
        return new TcpOption(KIND_TIMESTAMP, 2 + value.length, value);
    }

    /** NOP 填充选项（1 字节）。 */
    public static TcpOption noOp() {
        return new TcpOption(KIND_NOP, 1, new byte[0]);
    }

    /** EOL 结束选项（1 字节）。 */
    public static TcpOption endOfList() {
        return new TcpOption(KIND_EOL, 1, new byte[0]);
    }

    /** 选项总长（含 Kind/Length），对齐到 4 字节倍数（不足用 NOP 填充）。 */
    public static int totalLength(List<TcpOption> options) {
        int sum = 0;
        for (TcpOption option : options) {
            sum += option.length();
        }
        return (sum + 3) & ~3;
    }

    /** 编码选项块：选项依次拼接，末尾用 NOP 填充到 4 字节倍数。 */
    public static byte[] encode(List<TcpOption> options) {
        int total = 0;
        for (TcpOption option : options) {
            total += option.length();
        }
        int padded = (total + 3) & ~3;
        byte[] out = new byte[padded];
        int pos = 0;
        for (TcpOption option : options) {
            out[pos++] = (byte) option.kind();
            if (option.kind() != KIND_EOL && option.kind() != KIND_NOP) {
                out[pos++] = (byte) option.length();
            }
            System.arraycopy(option.value(), 0, out, pos, option.value().length);
            pos += option.value().length;
        }
        // 剩余空间用 NOP 填充到 4 字节边界
        while (pos < padded) {
            out[pos++] = (byte) KIND_NOP;
        }
        return out;
    }

    /**
     * 解析选项块（数据偏移之后的区域）。
     *
     * @param block  完整报文
     * @param offset 选项块起始位置（20 字节固定首部之后）
     * @param length 选项块长度（数据偏移 × 4 - 20）
     * @return 解析出的选项列表（NOP 填充被忽略，EOL 提前结束）
     */
    public static List<TcpOption> parse(byte[] block, int offset, int length) {
        List<TcpOption> options = new ArrayList<>();
        int pos = offset;
        int end = offset + length;
        while (pos < end) {
            int kind = block[pos] & 0xFF;
            if (kind == KIND_EOL) {
                break; // 选项列表结束
            }
            if (kind == KIND_NOP) {
                pos++; // 填充字节，跳过
                continue;
            }
            if (pos + 1 >= end) {
                throw new IllegalArgumentException("选项缺少 Length 字节（报文被截断）");
            }
            int optLength = block[pos + 1] & 0xFF;
            if (optLength < 2 || pos + optLength > end) {
                throw new IllegalArgumentException("非法选项长度: " + optLength);
            }
            byte[] value = Arrays.copyOfRange(block, pos + 2, pos + optLength);
            options.add(new TcpOption(kind, optLength, value));
            pos += optLength;
        }
        return options;
    }

    /** 选项 Kind 可读描述。 */
    public String kindName() {
        return switch (kind) {
            case KIND_EOL -> "EOL";
            case KIND_NOP -> "NOP";
            case KIND_MSS -> "MSS";
            case KIND_WINDOW_SCALE -> "Window Scale";
            case KIND_SACK_PERMITTED -> "SACK-Permitted";
            case KIND_SACK -> "SACK";
            case KIND_TIMESTAMP -> "Timestamp";
            default -> "未知选项 " + kind;
        };
    }

    /** 2 字节大端 Value 的值（MSS 用）。 */
    public int shortValue() {
        return ((value[0] & 0xFF) << 8) | (value[1] & 0xFF);
    }

    /** 1 字节 Value 的值（Window Scale 用）。 */
    public int byteValue() {
        return value[0] & 0xFF;
    }

    @Override
    public String toString() {
        return "TcpOption{" + kindName() + "(kind=" + kind + ", len=" + length + ")}";
    }
}
