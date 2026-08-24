package com.study.network.protocol;

import com.study.network.protocol.TcpCongestionControl.Phase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

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

    // ---- 构造参数校验 ----

    @Test
    @DisplayName("非法构造参数被拒绝：cwnd/ssthresh/rwnd 必须为正")
    void invalidConstructorRejected() {
        assertThrows(IllegalArgumentException.class, () -> new TcpCongestionControl(0, 16, 64));
        assertThrows(IllegalArgumentException.class, () -> new TcpCongestionControl(1, 0, 64));
        assertThrows(IllegalArgumentException.class, () -> new TcpCongestionControl(1, 16, 0));
    }
}
