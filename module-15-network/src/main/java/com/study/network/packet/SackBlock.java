package com.study.network.packet;

/**
 * SACK 块——TCP 选择性确认（Selective Acknowledgment，RFC 2018）里的一个乱序接收区间。
 *
 * 场景：接收方收到乱序的段（如 1000~2000 先到、3000~4000 也到了，中间 2000~3000 丢了），
 * 无法用普通 ACK 序号表达「我已经收到哪些不连续的区域」，于是用 SACK 选项带上一组区间。
 * 发送方据此**只重传丢失的段**（而不是像传统 Go-Back-N 那样从丢包处全部重传），
 * 这就是快重传/快恢复的精细化版本，高带宽高延迟链路（如跨洋专线）收益巨大。
 *
 * 一个块是**两个 32 位序号**（共 8 字节）：
 * <pre>
 *   left edge  (4 字节): 块内第一个字节的序号（区间左闭）
 *   right edge (4 字节): 块内最后一个字节的序号 + 1（区间右开）
 * </pre>
 * 区间是右开的：`[leftEdge, rightEdge)`，即右边界指向块外第一个字节。
 * 序号是 32 位循环的（4GB 后回绕到 0），所以边界按模 2^32 比较——
 * 左边界等于右边界表示空块（非法），允许出现「跨越回绕点」的块（如 [0xFFFFFFF0, 0x00000010)）。
 *
 * 注意（面试常问）：SACK 只告诉发送方「哪些到了」，**不告诉发送方哪些丢了**——
 * 丢失区间要靠对 SACK 块之间的空隙推断；且 SACK 不能替代 ACK，
 * 累计确认仍然由 ACK 序号负责（SACK 选项总是和 ACK 一起出现）。
 */
public record SackBlock(long leftEdge, long rightEdge) {

    public SackBlock {
        if (leftEdge < 0 || leftEdge > 0xFFFF_FFFFL
                || rightEdge < 0 || rightEdge > 0xFFFF_FFFFL) {
            throw new IllegalArgumentException("SACK 块边界是 32 位序号（0~4294967295）: ["
                    + leftEdge + ", " + rightEdge + ")");
        }
        if (leftEdge == rightEdge) {
            throw new IllegalArgumentException("SACK 块不能为空：左右边界相等表示 0 长度区间 ["
                    + leftEdge + ", " + rightEdge + ")");
        }
    }

    /** 区间长度（按 32 位模 2^32 计算，正确处理序号回绕）。 */
    public long size() {
        return (rightEdge - leftEdge) & 0xFFFF_FFFFL;
    }

    /** 是否包含序号 seq（按 32 位模 2^32 判断，正确处理回绕块）。 */
    public boolean contains(long seq) {
        long distance = (seq - leftEdge) & 0xFFFF_FFFFL;
        return distance < size();
    }

    @Override
    public String toString() {
        return "[" + leftEdge + ", " + rightEdge + ")";
    }
}
