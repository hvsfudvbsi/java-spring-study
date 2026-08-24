package com.study.network.protocol;

import com.study.network.packet.SackBlock;
import com.study.network.protocol.TcpCongestionControl.Phase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * TCP 拥塞控制测试：验证慢启动、拥塞避免、超时、快重传/快恢复与有效窗口。
 */
class TcpCongestionControlTest {

    // ---- 初始状态与慢启动 ----

    @Test
    @DisplayName("初始状态：cwnd=1、ssthresh=16、慢启动")
    void initialState() {
        TcpCongestionControl tcp = TcpCongestionControl.standard(64);
        assertEquals(1, tcp.cwnd());
        assertEquals(16, tcp.ssthresh());
        assertEquals(Phase.SLOW_START, tcp.phase());
        assertEquals(0, tcp.duplicateAckCount());
    }

    @Test
    @DisplayName("慢启动指数增长：cwnd 每 RTT 翻倍 1->2->4->8->16")
    void slowStartDoubles() {
        TcpCongestionControl tcp = TcpCongestionControl.standard(64);
        tcp.onRttAcknowledged();
        assertEquals(2, tcp.cwnd());
        assertEquals(Phase.SLOW_START, tcp.phase());
        tcp.onRttAcknowledged();
        assertEquals(4, tcp.cwnd());
        tcp.onRttAcknowledged();
        assertEquals(8, tcp.cwnd());
        tcp.onRttAcknowledged();
        assertEquals(16, tcp.cwnd());
    }

    @Test
    @DisplayName("cwnd 达到 ssthresh 后转入拥塞避免，且不越过 ssthresh")
    void slowStartStopsAtSsthresh() {
        TcpCongestionControl tcp = TcpCongestionControl.standard(64);
        // 4 个 RTT 后 cwnd=16 == ssthresh，应转入拥塞避免而不是翻倍到 32
        for (int i = 0; i < 4; i++) {
            tcp.onRttAcknowledged();
        }
        assertEquals(16, tcp.cwnd());
        assertEquals(Phase.CONGESTION_AVOIDANCE, tcp.phase());
    }

    @Test
    @DisplayName("拥塞避免线性增长：cwnd 每 RTT 只 +1")
    void congestionAvoidanceLinear() {
        TcpCongestionControl tcp = TcpCongestionControl.standard(64);
        for (int i = 0; i < 4; i++) {
            tcp.onRttAcknowledged(); // 到 cwnd=16, CA
        }
        tcp.onRttAcknowledged();
        assertEquals(17, tcp.cwnd());
        tcp.onRttAcknowledged();
        assertEquals(18, tcp.cwnd());
    }

    // ---- 超时 ----

    @Test
    @DisplayName("超时：ssthresh 减半、cwnd 归 1、重新慢启动")
    void timeoutResets() {
        TcpCongestionControl tcp = TcpCongestionControl.standard(64);
        for (int i = 0; i < 4; i++) {
            tcp.onRttAcknowledged(); // cwnd=16, CA
        }
        tcp.onRttAcknowledged();     // cwnd=17
        tcp.onTimeout();
        assertEquals(1, tcp.cwnd());
        assertEquals(17 / 2, tcp.ssthresh()); // 8
        assertEquals(Phase.SLOW_START, tcp.phase());
        assertEquals(0, tcp.duplicateAckCount());
    }

    @Test
    @DisplayName("超时后 ssthresh 有下限 2：cwnd 很小减半不会变成 0 或 1")
    void timeoutSsthreshFloor() {
        TcpCongestionControl tcp = new TcpCongestionControl(3, 16, 64);
        tcp.onTimeout();
        assertEquals(2, tcp.ssthresh());
        assertEquals(1, tcp.cwnd());
    }

    // ---- 快重传 / 快恢复 ----

    @Test
    @DisplayName("3 个重复 ACK 触发快重传：ssthresh=cwnd/2、cwnd=ssthresh+3、进入快恢复")
    void duplicateAcksTriggerFastRetransmit() {
        TcpCongestionControl tcp = TcpCongestionControl.standard(64);
        for (int i = 0; i < 4; i++) {
            tcp.onRttAcknowledged(); // cwnd=16, CA
        }
        tcp.onDuplicateAck();
        tcp.onDuplicateAck();
        assertEquals(2, tcp.duplicateAckCount());
        assertEquals(Phase.CONGESTION_AVOIDANCE, tcp.phase()); // 未到 3 个，不触发
        tcp.onDuplicateAck();
        assertEquals(8, tcp.ssthresh());      // 16 / 2
        assertEquals(11, tcp.cwnd());         // 8 + 3（Reno 惯例）
        assertEquals(Phase.FAST_RECOVERY, tcp.phase());
    }

    @Test
    @DisplayName("快恢复期间每多收一个重复 ACK，cwnd 临时 +1")
    void fastRecoveryInflatesOnDupAck() {
        TcpCongestionControl tcp = TcpCongestionControl.standard(64);
        for (int i = 0; i < 4; i++) {
            tcp.onRttAcknowledged(); // cwnd=16, CA
        }
        for (int i = 0; i < 3; i++) {
            tcp.onDuplicateAck();    // 进入快恢复，cwnd=11
        }
        tcp.onDuplicateAck();
        assertEquals(12, tcp.cwnd());
    }

    @Test
    @DisplayName("收到新 ACK 退出快恢复：cwnd 收敛回 ssthresh、进入拥塞避免、重复计数清零")
    void newAckExitsFastRecovery() {
        TcpCongestionControl tcp = TcpCongestionControl.standard(64);
        for (int i = 0; i < 4; i++) {
            tcp.onRttAcknowledged(); // cwnd=16, CA
        }
        for (int i = 0; i < 3; i++) {
            tcp.onDuplicateAck();    // 快恢复，cwnd=11
        }
        tcp.onNewAck();
        assertEquals(8, tcp.cwnd());          // 回到 ssthresh
        assertEquals(Phase.CONGESTION_AVOIDANCE, tcp.phase());
        assertEquals(0, tcp.duplicateAckCount());
    }

    @Test
    @DisplayName("新 ACK 重置重复 ACK 计数但不改变 cwnd（非快恢复阶段）")
    void newAckResetsCountOutsideFastRecovery() {
        TcpCongestionControl tcp = TcpCongestionControl.standard(64);
        tcp.onDuplicateAck();
        tcp.onDuplicateAck();
        assertEquals(2, tcp.duplicateAckCount());
        tcp.onNewAck();
        assertEquals(0, tcp.duplicateAckCount());
        assertEquals(1, tcp.cwnd());
        assertEquals(Phase.SLOW_START, tcp.phase());
    }

    // ---- 有效窗口：min(cwnd, rwnd) ----

    @Test
    @DisplayName("有效窗口 = min(cwnd, rwnd)：rwnd 充足时等于 cwnd")
    void effectiveWindowWithAmpleRwnd() {
        TcpCongestionControl tcp = TcpCongestionControl.standard(64);
        assertEquals(1, tcp.effectiveWindow());
        tcp.onRttAcknowledged();
        assertEquals(2, tcp.effectiveWindow());
        assertEquals(tcp.cwnd(), tcp.effectiveWindow());
    }

    @Test
    @DisplayName("有效窗口受 rwnd 限制：cwnd 超过接收窗口时只发 rwnd 那么多")
    void effectiveWindowCappedByRwnd() {
        TcpCongestionControl tcp = new TcpCongestionControl(1, 16, 4);
        for (int i = 0; i < 3; i++) {
            tcp.onRttAcknowledged(); // cwnd=8 > rwnd=4
        }
        assertEquals(8, tcp.cwnd());
        assertEquals(4, tcp.effectiveWindow()); // min(8, 4) = 4
    }

    // ---- SACK 精细化重传 ----

    @Test
    @DisplayName("SACK 接入：3 个重复 ACK + SACK 块，精确算出需重传的段（块空隙）")
    void duplicateAckWithSackFindsLostSegments() {
        TcpCongestionControl tcp = TcpCongestionControl.standard(64);
        for (int i = 0; i < 4; i++) {
            tcp.onRttAcknowledged(); // cwnd=16, CA
        }
        // 发送 1000~4000，只收到两端：[3000,4000) 与 [1000,2000)，中间 [2000,3000) 丢失
        List<SackBlock> received = List.of(new SackBlock(3000, 4000), new SackBlock(1000, 2000));
        List<SackBlock> lost = null;
        for (int i = 0; i < 3; i++) {
            lost = tcp.onDuplicateAckWithSack(1000, 4000, received);
        }
        assertEquals(List.of(new SackBlock(2000, 3000)), lost,
                "只重传块之间的空隙，不是整个窗口");
        assertEquals(Phase.FAST_RECOVERY, tcp.phase(), "窗口调整与普通重复 ACK 一致");
        assertEquals(8, tcp.ssthresh());
        assertEquals(11, tcp.cwnd());
    }

    @Test
    @DisplayName("SACK 全覆盖时无丢失段：返回空列表，不触发额外重传")
    void duplicateAckWithSackFullyCovered() {
        TcpCongestionControl tcp = TcpCongestionControl.standard(64);
        List<SackBlock> lost = tcp.onDuplicateAckWithSack(0, 1000,
                List.of(new SackBlock(0, 1000)));
        assertEquals(List.of(), lost, "发送范围全被 SACK 覆盖，没有需要重传的段");
    }

    // ---- ssthresh 手动设置 ----

    @Test
    @DisplayName("手动调低 ssthresh：cwnd 超过新阈值时立即收敛并转入拥塞避免")
    void setSsthreshCapsCwnd() {
        TcpCongestionControl tcp = TcpCongestionControl.standard(64);
        tcp.onRttAcknowledged(); // cwnd=2
        tcp.onRttAcknowledged(); // cwnd=4
        tcp.setSsthresh(2);      // 人为调低阈值
        assertEquals(2, tcp.cwnd(), "cwnd 收敛到新阈值");
        assertEquals(Phase.CONGESTION_AVOIDANCE, tcp.phase(), "慢启动提前结束");
        tcp.onRttAcknowledged();
        assertEquals(3, tcp.cwnd(), "之后进入拥塞避免线性增长");
    }

    @Test
    @DisplayName("setSsthresh 非法参数被拒绝")
    void setSsthreshRejectsInvalid() {
        TcpCongestionControl tcp = TcpCongestionControl.standard(64);
        assertThrows(IllegalArgumentException.class, () -> tcp.setSsthresh(0));
    }

    // ---- 慢启动阈值翻倍（加速恢复） ----

    @Test
    @DisplayName("超时后阈值翻倍：ssthresh 每 RTT 恢复翻倍，最多回到初始阈值")
    void ssthreshRecoveryDoublesBack() {
        TcpCongestionControl tcp = TcpCongestionControl.standard(64);
        for (int i = 0; i < 4; i++) {
            tcp.onRttAcknowledged(); // cwnd=16, CA
        }
        tcp.onRttAcknowledged();     // cwnd=17
        tcp.onTimeout();             // ssthresh=8, cwnd=1
        assertEquals(8, tcp.ssthresh());
        tcp.onSsthreshRecovery();    // 8 -> 16（恢复翻倍）
        assertEquals(16, tcp.ssthresh(), "翻倍回初始阈值");
        tcp.onSsthreshRecovery();    // min(32, 16) = 16，封顶
        assertEquals(16, tcp.ssthresh(), "不超过初始阈值");
    }

    @Test
    @DisplayName("阈值翻倍配合慢启动：恢复期 cwnd 可继续翻倍到更高阈值再转拥塞避免")
    void ssthreshRecoveryWithSlowStart() {
        TcpCongestionControl tcp = TcpCongestionControl.standard(64);
        for (int i = 0; i < 4; i++) {
            tcp.onRttAcknowledged(); // cwnd=16, CA
        }
        tcp.onRttAcknowledged();     // cwnd=17
        tcp.onTimeout();             // ssthresh=8
        tcp.onSsthreshRecovery();    // ssthresh=16
        // 慢启动从 cwnd=1 翻倍：2,4,8,16 -> 到 16 转拥塞避免
        for (int i = 0; i < 4; i++) {
            tcp.onRttAcknowledged();
        }
        assertEquals(16, tcp.cwnd());
        assertEquals(Phase.CONGESTION_AVOIDANCE, tcp.phase());
    }

    // ---- 构造参数校验 ----

    @Test
    @DisplayName("非法构造参数被拒绝：cwnd/ssthresh/rwnd 必须为正")
    void invalidConstructorRejected() {
        assertThrows(IllegalArgumentException.class, () -> new TcpCongestionControl(0, 16, 64));
        assertThrows(IllegalArgumentException.class, () -> new TcpCongestionControl(1, 0, 64));
        assertThrows(IllegalArgumentException.class, () -> new TcpCongestionControl(1, 16, 0));
    }
}
