package com.study.network.protocol;

import com.study.network.packet.SackBlock;

import java.util.List;

/**
 * TCP 拥塞控制模拟（TCP Reno）——面试必问：TCP 为什么既要有流量控制又要有拥塞控制？
 *
 * 两个窗口的区别（最容易混淆的点）：
 * - 流量控制 rwnd（接收窗口）：由**接收方**告知，表示接收方还剩多少缓冲区，
 *   防止发送方把接收方淹没（端到端、对端能力）。
 * - 拥塞控制 cwnd（拥塞窗口）：由**发送方自己维护**，表示当前网络（中间路由器队列）
 *   还能承受多少数据，防止发送方把网络打爆（网络路径承载能力）。
 * - 发送方实际能发的数据量 = min(cwnd, rwnd)，叫**有效窗口**。
 *
 * 拥塞窗口以 RTT（往返时间）为周期更新，单位为 MSS（最大报文段，即一个数据段）：
 *
 * <pre>
 * 慢启动 Slow Start（指数探测）:
 *   cwnd 从 1 开始，每个 RTT 翻倍：1 -> 2 -> 4 -> 8 -> 16 ...
 *   目的：快速摸清网络带宽。cwnd 达到 ssthresh 后转入拥塞避免。
 *
 * 拥塞避免 Congestion Avoidance（线性试探）:
 *   cwnd 每个 RTT 只 +1：16 -> 17 -> 18 ...
 *   目的：在接近瓶颈时缓慢增长，避免再次引发拥塞。
 *
 * 超时（RTO 计时器到期，最严重的拥塞信号）:
 *   ssthresh = cwnd / 2，cwnd 重置为 1，重新开始慢启动。
 *   因为超时意味着报文可能在网络中排队很久甚至被丢弃，网络可能已严重拥塞。
 *
 * 快重传 + 快恢复 Fast Retransmit / Fast Recovery（收到 3 个重复 ACK）:
 *   重复 ACK 说明只是乱序或单个丢包，网络还能正常通信，所以不必从头再来：
 *   ssthresh = cwnd / 2，cwnd = ssthresh + 3（Reno 惯例），进入快恢复；
 *   快恢复期间每多收一个重复 ACK，cwnd 临时 +1（补偿已发出的数据）；
 *   收到新 ACK（重传段被确认）后 cwnd 收敛回 ssthresh，进入拥塞避免。
 * </pre>
 *
 * 经典拥塞窗口曲线（TCP Reno，面试画图题）：
 * <pre>
 * cwnd
 *  |             ___
 *  |            /   \___/‾‾‾   拥塞避免（每 RTT +1）
 *  |           /    （超时 -> cwnd=1 重新慢启动）
 *  |     ___  /
 *  |    /   \/    （3 个重复 ACK -> 快重传/快恢复，cwnd=ssthresh 不归零）
 *  |   /  慢启动（每 RTT 翻倍）
 *  |  /
 *  +-----------------------> RTT
 * </pre>
 *
 * 本类用「状态 + 事件」模拟 cwnd 的演化，配合 TcpStateMachine（连接状态）
 * 可完整理解 TCP 的「建立连接 -> 传输（拥塞控制）-> 释放连接」全过程。
 */
public class TcpCongestionControl {

    /** 拥塞控制所处的阶段 */
    public enum Phase {
        /** 慢启动：cwnd 每 RTT 翻倍（指数增长，快速探测带宽） */
        SLOW_START,
        /** 拥塞避免：cwnd 每 RTT +1（线性增长，缓慢试探） */
        CONGESTION_AVOIDANCE,
        /** 快恢复：3 个重复 ACK 后，cwnd = ssthresh 不再归零 */
        FAST_RECOVERY
    }

    /** ssthresh 下限：cwnd 很小时减半不能小于 2（保证拥塞避免仍能增长） */
    private static final int MIN_SSTHRESH = 2;

    private int cwnd;              // 拥塞窗口（单位：MSS）
    private int ssthresh;          // 慢启动阈值（单位：MSS）
    private final int initialSsthresh; // 初始慢启动阈值（加速恢复时追回的上限）
    private final int rwnd;        // 接收窗口（单位：MSS，模拟中固定不变）
    private Phase phase;
    private int duplicateAckCount; // 连续重复 ACK 计数

    /**
     * @param initialCwnd    初始拥塞窗口（经典实现为 1 MSS）
     * @param initialSsthresh 初始慢启动阈值（经典实现为 16 MSS）
     * @param rwnd            接收窗口（由接收方通告，模拟中固定）
     */
    public TcpCongestionControl(int initialCwnd, int initialSsthresh, int rwnd) {
        if (initialCwnd < 1 || initialSsthresh < 1 || rwnd < 1) {
            throw new IllegalArgumentException("窗口必须为正整数: cwnd=" + initialCwnd
                    + ", ssthresh=" + initialSsthresh + ", rwnd=" + rwnd);
        }
        this.cwnd = initialCwnd;
        this.ssthresh = initialSsthresh;
        this.initialSsthresh = initialSsthresh;
        this.rwnd = rwnd;
        this.phase = Phase.SLOW_START;
    }

    /** 经典初始参数：cwnd=1，ssthresh=16（RFC 5681 建议的典型值）。 */
    public static TcpCongestionControl standard(int rwnd) {
        return new TcpCongestionControl(1, 16, rwnd);
    }

    /**
     * 一个 RTT 内所有数据段都收到确认（正常增长）。
     * - 慢启动：cwnd 翻倍；达到 ssthresh 后转入拥塞避免（不越过 ssthresh）。
     * - 拥塞避免：cwnd +1。
     */
    public void onRttAcknowledged() {
        if (phase == Phase.SLOW_START) {
            cwnd *= 2;
            if (cwnd >= ssthresh) {
                cwnd = ssthresh; // 到达阈值即转入拥塞避免，不再指数增长
                phase = Phase.CONGESTION_AVOIDANCE;
            }
        } else if (phase == Phase.CONGESTION_AVOIDANCE) {
            cwnd += 1;
        }
        // FAST_RECOVERY 期间按 RTT 推进不增长（靠重复 ACK 临时补偿 + 新 ACK 收敛）
    }

    /**
     * 超时（RTO 计时器到期）——最严重的拥塞信号，全盘重来：
     * ssthresh = max(cwnd/2, 2)，cwnd = 1，回到慢启动。
     */
    public void onTimeout() {
        ssthresh = Math.max(cwnd / 2, MIN_SSTHRESH);
        cwnd = 1;
        duplicateAckCount = 0;
        phase = Phase.SLOW_START;
    }

    /**
     * 收到一个重复 ACK（对同一个序号反复确认，说明后续数据段丢了或乱序）。
     * - 连续 3 个重复 ACK：触发快重传（重传丢失段）+ 进入快恢复：
     *   ssthresh = max(cwnd/2, 2)，cwnd = ssthresh + 3（Reno 惯例）。
     * - 快恢复期间每多收一个重复 ACK：cwnd 临时 +1（补偿已发到网络中的数据）。
     */
    public void onDuplicateAck() {
        duplicateAckCount++;
        if (duplicateAckCount == 3) {
            ssthresh = Math.max(cwnd / 2, MIN_SSTHRESH);
            cwnd = ssthresh + 3;
            phase = Phase.FAST_RECOVERY;
        } else if (phase == Phase.FAST_RECOVERY) {
            cwnd += 1;
        }
    }

    /**
     * 收到一个新 ACK（确认了重传的数据）——快恢复结束：
     * cwnd 收敛回 ssthresh，转入拥塞避免，重复 ACK 计数清零。
     */
    public void onNewAck() {
        if (phase == Phase.FAST_RECOVERY) {
            cwnd = ssthresh;
            phase = Phase.CONGESTION_AVOIDANCE;
        }
        duplicateAckCount = 0;
    }

    /**
     * 收到重复 ACK + SACK 块（乱序确认）：用 {@link SackBlock#gaps} 从发送范围内
     * 精确算出**需要重传的段**（块之间的空隙），而不是整窗口重传——
     * 这就是 SACK 对快重传的精细化：只补丢失的，不重发已收到的。
     * 拥塞窗口行为与 {@link #onDuplicateAck} 完全一致（第 3 个重复 ACK 进快恢复）。
     *
     * @param sentStart 发送范围左边界（32 位序号）
     * @param sentEnd   发送范围右边界（不含）
     * @param blocks    接收方回报的 SACK 块（乱序/可重叠/可在发送范围外）
     * @return 需要重传的段列表（SackBlock.gaps 的结果，升序）
     */
    public List<SackBlock> onDuplicateAckWithSack(long sentStart, long sentEnd,
                                                  List<SackBlock> blocks) {
        List<SackBlock> lostSegments = SackBlock.gaps(sentStart, sentEnd, blocks);
        onDuplicateAck(); // 窗口调整逻辑与普通重复 ACK 相同
        return lostSegments;
    }

    /**
     * 手动设置慢启动阈值（模拟丢包前人为调低阈值）：
     * 设置后若 cwnd 已超过新阈值，立即收敛到阈值并转入拥塞避免（慢启动提前结束）。
     */
    public void setSsthresh(int newSsthresh) {
        if (newSsthresh < 1) {
            throw new IllegalArgumentException("ssthresh 必须为正整数: " + newSsthresh);
        }
        this.ssthresh = newSsthresh;
        if (cwnd >= ssthresh) {
            cwnd = ssthresh;
            phase = Phase.CONGESTION_AVOIDANCE;
        }
    }

    /**
     * 慢启动阈值翻倍（加速恢复）：超时/快重传把 ssthresh 减半后，每 RTT 调用一次
     * 把 ssthresh 翻倍（最多恢复到初始阈值），配合慢启动的指数增长快速追回带宽——
     * 避免长期停留在减半后的低阈值、恢复过慢。
     */
    public void onSsthreshRecovery() {
        ssthresh = Math.min(ssthresh * 2, initialSsthresh);
        if (phase == Phase.SLOW_START && cwnd >= ssthresh) {
            cwnd = ssthresh;
            phase = Phase.CONGESTION_AVOIDANCE;
        }
    }

    /** 有效发送窗口 = min(cwnd, rwnd)：两者取小，同时受网络与接收方限制。 */
    public int effectiveWindow() {
        return Math.min(cwnd, rwnd);
    }

    public int cwnd() {
        return cwnd;
    }

    public int ssthresh() {
        return ssthresh;
    }

    public int rwnd() {
        return rwnd;
    }

    public Phase phase() {
        return phase;
    }

    public int duplicateAckCount() {
        return duplicateAckCount;
    }

    /** 打印拥塞控制全过程演示（供 Main 调用）：慢启动 -> 拥塞避免 -> 超时 -> 快重传。 */
    public static void printDemo() {
        System.out.println("================ TCP 拥塞控制（Reno）演示 ================");
        TcpCongestionControl tcp = standard(64);

        // 1. 慢启动：每 RTT 翻倍，直到达到 ssthresh=16
        System.out.println("  ① 慢启动（cwnd 每 RTT 翻倍，ssthresh=16）:");
        int rtt = 0;
        while (tcp.phase() == Phase.SLOW_START && rtt < 6) {
            rtt++;
            System.out.println("    RTT " + rtt + ": cwnd=" + tcp.cwnd()
                    + " -> " + tcp.cwnd() * 2);
            tcp.onRttAcknowledged();
        }
        System.out.println("    达到 ssthresh，转入拥塞避免（当前 cwnd=" + tcp.cwnd() + "）");

        // 2. 拥塞避免：每 RTT +1
        System.out.println("  ② 拥塞避免（cwnd 每 RTT +1）:");
        for (int i = 0; i < 3; i++) {
            tcp.onRttAcknowledged();
            System.out.println("    RTT: cwnd=" + tcp.cwnd() + ", ssthresh=" + tcp.ssthresh());
        }

        // 3. 超时：ssthresh 减半、cwnd 归 1
        int before = tcp.cwnd();
        tcp.onTimeout();
        System.out.println("  ③ 超时（cwnd 从 " + before + " -> 1，ssthresh -> " + tcp.ssthresh()
                + "，重新慢启动）");

        // 4. 重新慢启动 + 3 个重复 ACK -> 快重传/快恢复
        for (int i = 0; i < 3; i++) {
            tcp.onRttAcknowledged(); // 快速涨回 ssthresh 附近
        }
        int cwndBeforeDup = tcp.cwnd();
        System.out.println("  ④ 收到 3 个重复 ACK（cwnd=" + cwndBeforeDup + "）:");
        for (int i = 0; i < 3; i++) {
            tcp.onDuplicateAck();
        }
        System.out.println("    快重传触发: ssthresh=" + tcp.ssthresh()
                + ", cwnd=" + tcp.cwnd() + "（进入快恢复，不归零）");
        tcp.onDuplicateAck(); // 快恢复期间额外重复 ACK，临时 +1
        System.out.println("    快恢复期间再收 1 个重复 ACK: cwnd=" + tcp.cwnd());
        tcp.onNewAck();       // 重传段被确认，退出快恢复
        System.out.println("    收到新 ACK: cwnd 收敛回 ssthresh=" + tcp.cwnd()
                + "，进入拥塞避免");
        System.out.println();
    }
}
