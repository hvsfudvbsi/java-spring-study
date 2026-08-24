package com.study.network.packet;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

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
 * 丢失区间要靠 {@link #gaps} 对 SACK 块之间的空隙推断；且 SACK 不能替代 ACK，
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

    /**
     * 丢包区间推断：在发送范围 [sentStart, sentEnd) 内，找出**没有被任何 SACK 块覆盖**的区间。
     *
     * 场景：发送方知道自己发了哪些序号（发送窗口），接收方的 SACK 块告知哪些已到达，
     * 两者之差就是**需要重传的段**——发送方据此只重传丢失部分，而不是全部重发。
     *
     * 算法（提示：先排序、再合并、后求补）：
     * 1. 以 sentStart 为原点把每个块线性化到 [0, 发送范围长度) 坐标系（模 2^32），
     *    跨回绕点的块拆成两段——发送范围本身也可以跨回绕点；
     * 2. 块按左边界排序，重叠/相邻的合并成一个覆盖区间；
     * 3. 在发送范围内扫描覆盖区间的**补集**，即空隙（需要重传的段），转回绝对序号。
     *
     * 处理细节：
     * - 块乱序传入、块与块重叠，都不会产生虚假空隙（排序 + 合并保证）；
     * - 发送范围之外的块（之前窗口或更远的段）对当前窗口无影响，自然被忽略；
     * - 返回的空隙按左边界升序，且不会超出发送范围。
     *
     * @param sentStart 发送范围左边界（含，32 位序号）
     * @param sentEnd   发送范围右边界（不含，32 位序号）；等于 sentStart 视为空范围
     * @param blocks    接收方回报的 SACK 块（可乱序、可重叠、可在发送范围外）
     * @return 需要重传的区间列表（按左边界升序）；无空隙时为空列表
     * @throws IllegalArgumentException 发送范围边界超出 32 位序号范围
     */
    public static List<SackBlock> gaps(long sentStart, long sentEnd, List<SackBlock> blocks) {
        if (sentStart < 0 || sentStart > 0xFFFF_FFFFL || sentEnd < 0 || sentEnd > 0xFFFF_FFFFL) {
            throw new IllegalArgumentException("发送范围边界必须是 32 位序号（0~4294967295）: ["
                    + sentStart + ", " + sentEnd + ")");
        }
        long sentSize = (sentEnd - sentStart) & 0xFFFF_FFFFL;
        if (sentSize == 0) {
            return List.of(); // 空发送范围：没有可推断的丢包
        }
        List<SackBlock> result = new ArrayList<>();
        if (blocks == null || blocks.isEmpty()) {
            result.add(new SackBlock(sentStart, sentEnd)); // 什么都没收到：整个范围都要重传
            return result;
        }

        // 1. 线性化：以 sentStart 为原点，把块映射到 [0, sentSize) 坐标系；
        //    块在原点坐标系里「回绕」（right' < left'）时拆成两段
        List<long[]> covered = new ArrayList<>(); // 每项 {起点, 终点}（线性坐标）
        for (SackBlock block : blocks) {
            long left = (block.leftEdge() - sentStart) & 0xFFFF_FFFFL;
            long right = (block.rightEdge() - sentStart) & 0xFFFF_FFFFL;
            if (left < right) {
                covered.add(new long[]{left, right});
            } else {
                covered.add(new long[]{left, sentSize});
                covered.add(new long[]{0, right});
            }
        }

        // 2. 按起点排序，重叠/相邻区间合并
        covered.sort(Comparator.comparingLong(interval -> interval[0]));
        List<long[]> merged = new ArrayList<>();
        for (long[] interval : covered) {
            if (merged.isEmpty() || interval[0] > merged.get(merged.size() - 1)[1]) {
                merged.add(interval);
            } else {
                long[] last = merged.get(merged.size() - 1);
                last[1] = Math.max(last[1], interval[1]);
            }
        }

        // 3. 在 [0, sentSize) 内扫描补集（空隙），转回绝对序号
        long cursor = 0; // 已被覆盖到的位置
        for (long[] interval : merged) {
            if (interval[0] > cursor) {
                result.add(new SackBlock((cursor + sentStart) & 0xFFFF_FFFFL,
                        (Math.min(interval[0], sentSize) + sentStart) & 0xFFFF_FFFFL));
            }
            cursor = Math.max(cursor, interval[1]);
            if (cursor >= sentSize) {
                return result;
            }
        }
        if (cursor < sentSize) {
            result.add(new SackBlock((cursor + sentStart) & 0xFFFF_FFFFL, sentEnd));
        }
        return result;
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
