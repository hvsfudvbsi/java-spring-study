package com.study.network.protocol;

import com.study.network.protocol.TcpStateMachine.TcpEvent;
import com.study.network.protocol.TcpStateMachine.TcpState;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * TCP 状态机测试：验证三次握手、四次挥手、同时关闭和非法转换拒绝。
 */
class TcpStateMachineTest {

    // ---- 三次握手 ----

    @Test
    @DisplayName("客户端三次握手：CLOSED -> SYN_SENT -> ESTABLISHED")
    void clientHandshake() {
        TcpStateMachine client = new TcpStateMachine(TcpState.CLOSED);

        assertEquals(TcpState.SYN_SENT, client.apply(TcpEvent.SEND_SYN));
        assertEquals(TcpState.ESTABLISHED, client.apply(TcpEvent.RECV_SYN_ACK));
        assertEquals(TcpState.ESTABLISHED, client.state());
    }

    @Test
    @DisplayName("服务端三次握手：CLOSED -> LISTEN -> SYN_RECEIVED -> ESTABLISHED")
    void serverHandshake() {
        TcpStateMachine server = new TcpStateMachine(TcpState.CLOSED);

        assertEquals(TcpState.LISTEN, server.apply(TcpEvent.LISTEN));
        assertEquals(TcpState.SYN_RECEIVED, server.apply(TcpEvent.RECV_SYN));
        assertEquals(TcpState.ESTABLISHED, server.apply(TcpEvent.RECV_ACK));
    }

    @Test
    @DisplayName("完整握手助手方法：客户端和服务端都到达 ESTABLISHED")
    void handshakeHelpers() {
        assertEquals(TcpState.ESTABLISHED, TcpStateMachine.clientHandshake());
        assertEquals(TcpState.ESTABLISHED, TcpStateMachine.serverHandshake());
    }

    // ---- 四次挥手 ----

    @Test
    @DisplayName("主动关闭方四次挥手：ESTABLISHED -> FIN_WAIT_1 -> FIN_WAIT_2 -> TIME_WAIT -> CLOSED")
    void activeClose() {
        TcpStateMachine closer = new TcpStateMachine(TcpState.ESTABLISHED);

        assertEquals(TcpState.FIN_WAIT_1, closer.apply(TcpEvent.SEND_FIN));
        assertEquals(TcpState.FIN_WAIT_2, closer.apply(TcpEvent.RECV_ACK));
        assertEquals(TcpState.TIME_WAIT, closer.apply(TcpEvent.RECV_FIN));
        assertEquals(TcpState.CLOSED, closer.apply(TcpEvent.TIMEOUT_2MSL));
    }

    @Test
    @DisplayName("被动关闭方四次挥手：ESTABLISHED -> CLOSE_WAIT -> LAST_ACK -> CLOSED")
    void passiveClose() {
        TcpStateMachine peer = new TcpStateMachine(TcpState.ESTABLISHED);

        assertEquals(TcpState.CLOSE_WAIT, peer.apply(TcpEvent.RECV_FIN));
        assertEquals(TcpState.LAST_ACK, peer.apply(TcpEvent.SEND_FIN));
        assertEquals(TcpState.CLOSED, peer.apply(TcpEvent.RECV_ACK));
    }

    @Test
    @DisplayName("完整挥手助手方法：主动方经过 TIME_WAIT 到 CLOSED，被动方直达 CLOSED")
    void closeHelpers() {
        assertEquals(TcpState.CLOSED, TcpStateMachine.activeClose());
        assertEquals(TcpState.CLOSED, TcpStateMachine.passiveClose());
    }

    @Test
    @DisplayName("TIME_WAIT 只属于主动关闭方：被动方无 TIME_WAIT 状态")
    void timeWaitOnlyForActiveCloser() {
        TcpStateMachine passive = new TcpStateMachine(TcpState.ESTABLISHED);
        passive.apply(TcpEvent.RECV_FIN); // CLOSE_WAIT
        passive.apply(TcpEvent.SEND_FIN); // LAST_ACK
        assertEquals(TcpState.LAST_ACK, passive.state());

        // 被动方不能进入 TIME_WAIT（FIN_WAIT_1 才是入口）
        assertThrows(IllegalStateException.class,
                () -> passive.apply(TcpEvent.RECV_FIN));
    }

    // ---- 同时关闭 ----

    @Test
    @DisplayName("同时关闭：双方都发 FIN 时经 CLOSING 进入 TIME_WAIT")
    void simultaneousClose() {
        TcpStateMachine closer = new TcpStateMachine(TcpState.ESTABLISHED);
        closer.apply(TcpEvent.SEND_FIN); // FIN_WAIT_1

        // 对端也发了 FIN（而不是 ACK）-> CLOSING
        assertEquals(TcpState.CLOSING, closer.apply(TcpEvent.RECV_FIN));
        // 收到对端对自己 FIN 的 ACK -> TIME_WAIT
        assertEquals(TcpState.TIME_WAIT, closer.apply(TcpEvent.RECV_ACK));
        assertEquals(TcpState.CLOSED, closer.apply(TcpEvent.TIMEOUT_2MSL));
    }

    // ---- 非法转换 ----

    @Test
    @DisplayName("非法转换被拒绝：ESTABLISHED 不能直接再收 SYN")
    void illegalTransitionThrows() {
        TcpStateMachine established = new TcpStateMachine(TcpState.ESTABLISHED);

        assertThrows(IllegalStateException.class, () -> established.apply(TcpEvent.RECV_SYN));
        assertThrows(IllegalStateException.class, () -> established.apply(TcpEvent.LISTEN));
        assertThrows(IllegalStateException.class, () -> established.apply(TcpEvent.SEND_SYN));
    }

    @Test
    @DisplayName("非法转换被拒绝：TIME_WAIT 只能超时关闭，不能收 ACK 或再发 FIN")
    void timeWaitRestrictsTransitions() {
        TcpStateMachine closer = new TcpStateMachine(TcpState.ESTABLISHED);
        closer.apply(TcpEvent.SEND_FIN);
        closer.apply(TcpEvent.RECV_ACK);
        closer.apply(TcpEvent.RECV_FIN); // TIME_WAIT
        assertEquals(TcpState.TIME_WAIT, closer.state());

        assertThrows(IllegalStateException.class, () -> closer.apply(TcpEvent.RECV_ACK));
        assertThrows(IllegalStateException.class, () -> closer.apply(TcpEvent.SEND_FIN));
        assertEquals(TcpState.CLOSED, closer.apply(TcpEvent.TIMEOUT_2MSL));
    }

    @Test
    @DisplayName("非法转换被拒绝：CLOSED 不能直接收 FIN（必须先建立连接）")
    void closedCannotReceiveFin() {
        TcpStateMachine closed = new TcpStateMachine(TcpState.CLOSED);
        assertThrows(IllegalStateException.class, () -> closed.apply(TcpEvent.RECV_FIN));
        assertThrows(IllegalStateException.class, () -> closed.apply(TcpEvent.RECV_ACK));
        assertThrows(IllegalStateException.class, () -> closed.apply(TcpEvent.RECV_SYN_ACK));
    }

    @Test
    @DisplayName("共 11 个 TCP 状态（RFC 793 完整集合）")
    void hasAllElevenStates() {
        assertEquals(11, TcpStateMachine.allStates().size());
    }

    // ---- RST 连接重置 ----

    @Test
    @DisplayName("端口未监听：SYN_SENT 收到 RST 直接 CLOSED（Connection refused）")
    void rstDuringSynSent() {
        TcpStateMachine client = new TcpStateMachine(TcpState.SYN_SENT);
        assertEquals(TcpState.CLOSED, client.apply(TcpEvent.RECV_RST));
    }

    @Test
    @DisplayName("对端强杀：ESTABLISHED 收到 RST 直接 CLOSED（Connection reset by peer）")
    void rstDuringEstablished() {
        TcpStateMachine established = new TcpStateMachine(TcpState.ESTABLISHED);
        assertEquals(TcpState.CLOSED, established.apply(TcpEvent.RECV_RST));
    }

    @Test
    @DisplayName("挥手中途收到 RST：FIN_WAIT_1 / FIN_WAIT_2 / CLOSE_WAIT / LAST_ACK 都直接 CLOSED")
    void rstDuringClose() {
        TcpStateMachine fw1 = new TcpStateMachine(TcpState.FIN_WAIT_1);
        assertEquals(TcpState.CLOSED, fw1.apply(TcpEvent.RECV_RST));
        TcpStateMachine fw2 = new TcpStateMachine(TcpState.FIN_WAIT_2);
        assertEquals(TcpState.CLOSED, fw2.apply(TcpEvent.RECV_RST));
        TcpStateMachine cw = new TcpStateMachine(TcpState.CLOSE_WAIT);
        assertEquals(TcpState.CLOSED, cw.apply(TcpEvent.RECV_RST));
        TcpStateMachine la = new TcpStateMachine(TcpState.LAST_ACK);
        assertEquals(TcpState.CLOSED, la.apply(TcpEvent.RECV_RST));
    }

    @Test
    @DisplayName("LISTEN 收到 RST 丢弃并继续监听，不影响已有连接")
    void rstDuringListenIgnored() {
        TcpStateMachine server = new TcpStateMachine(TcpState.LISTEN);
        assertEquals(TcpState.LISTEN, server.apply(TcpEvent.RECV_RST));
    }

    @Test
    @DisplayName("TIME_WAIT 不受 RST 影响：2MSL 固定等待，RST 不能提前结束")
    void rstCannotShortenTimeWait() {
        TcpStateMachine closer = new TcpStateMachine(TcpState.TIME_WAIT);
        assertThrows(IllegalStateException.class, () -> closer.apply(TcpEvent.RECV_RST));
        // 只能等 2MSL 超时后关闭
        assertEquals(TcpState.CLOSED, closer.apply(TcpEvent.TIMEOUT_2MSL));
    }

    // ---- 半开连接检测（SYN 重传超时） ----

    @Test
    @DisplayName("客户端半开连接：SYN 重传 2 次仍等待，第 3 次 RTO 放弃 -> CLOSED（Connection timed out）")
    void clientRetransmitThenGiveUp() {
        TcpStateMachine client = new TcpStateMachine(TcpState.SYN_SENT);

        assertEquals(TcpState.SYN_SENT, client.apply(TcpEvent.RETRANSMIT_TIMEOUT));
        assertEquals(1, client.retransmitCount(), "第 1 次 RTO：重发 SYN，继续等待");
        assertEquals(TcpState.SYN_SENT, client.apply(TcpEvent.RETRANSMIT_TIMEOUT));
        assertEquals(2, client.retransmitCount());

        // 重试耗尽：放弃连接
        assertEquals(TcpState.CLOSED, client.apply(TcpEvent.RETRANSMIT_TIMEOUT));
        assertEquals(0, client.retransmitCount(), "放弃后计数清零");
    }

    @Test
    @DisplayName("服务端半开连接：SYN_RECEIVED 重传 SYN+ACK 耗尽后回到 LISTEN 继续监听")
    void serverRetransmitThenGiveUp() {
        TcpStateMachine server = new TcpStateMachine(TcpState.SYN_RECEIVED);
        for (int i = 0; i < TcpStateMachine.MAX_RETRANSMITS - 1; i++) {
            assertEquals(TcpState.SYN_RECEIVED, server.apply(TcpEvent.RETRANSMIT_TIMEOUT));
        }
        assertEquals(TcpState.LISTEN, server.apply(TcpEvent.RETRANSMIT_TIMEOUT),
                "服务端重试耗尽后回到 LISTEN，不占用 TCB");
        assertEquals(0, server.retransmitCount());
    }

    @Test
    @DisplayName("握手成功重置重试计数：SYN_SENT 收到 SYN+ACK 后 RTO 不再适用")
    void retransmitCountResetsOnHandshakeSuccess() {
        TcpStateMachine client = new TcpStateMachine(TcpState.SYN_SENT);
        client.apply(TcpEvent.RETRANSMIT_TIMEOUT);
        assertEquals(1, client.retransmitCount());

        assertEquals(TcpState.ESTABLISHED, client.apply(TcpEvent.RECV_SYN_ACK));
        assertEquals(0, client.retransmitCount(), "握手成功计数清零");
        // ESTABLISHED 不是等待握手应答状态，RTO 事件非法
        assertThrows(IllegalStateException.class, () -> client.apply(TcpEvent.RETRANSMIT_TIMEOUT));
    }

    @Test
    @DisplayName("放弃后重新建连：重试计数从头算，仍可正常握手")
    void retransmitResetsAfterGiveUp() {
        TcpStateMachine client = new TcpStateMachine(TcpState.SYN_SENT);
        for (int i = 0; i < TcpStateMachine.MAX_RETRANSMITS; i++) {
            client.apply(TcpEvent.RETRANSMIT_TIMEOUT); // -> CLOSED，计数清零
        }
        assertEquals(TcpState.CLOSED, client.state());

        client.apply(TcpEvent.SEND_SYN); // 重新建连
        assertEquals(TcpState.SYN_SENT, client.state());
        assertEquals(0, client.retransmitCount());
        assertEquals(TcpState.SYN_SENT, client.apply(TcpEvent.RETRANSMIT_TIMEOUT));
        assertEquals(1, client.retransmitCount(), "新连接的重试从头计数");
    }

    @Test
    @DisplayName("RTO 只在等待握手应答的状态合法：ESTABLISHED/CLOSED/LISTEN 都拒绝")
    void retransmitIllegalOutsideWaitingStates() {
        TcpStateMachine established = new TcpStateMachine(TcpState.ESTABLISHED);
        assertThrows(IllegalStateException.class,
                () -> established.apply(TcpEvent.RETRANSMIT_TIMEOUT));
        TcpStateMachine closed = new TcpStateMachine(TcpState.CLOSED);
        assertThrows(IllegalStateException.class,
                () -> closed.apply(TcpEvent.RETRANSMIT_TIMEOUT));
        TcpStateMachine listen = new TcpStateMachine(TcpState.LISTEN);
        assertThrows(IllegalStateException.class,
                () -> listen.apply(TcpEvent.RETRANSMIT_TIMEOUT));
    }

    // ---- keep-alive 假死检测 ----

    @Test
    @DisplayName("keep-alive 探测：前 2 次无响应仍 ESTABLISHED，计数递增")
    void probeFailuresKeepConnection() {
        TcpStateMachine conn = new TcpStateMachine(TcpState.ESTABLISHED);
        assertEquals(0, conn.probeFailCount());

        assertEquals(TcpState.ESTABLISHED, conn.apply(TcpEvent.PROBE_TIMEOUT));
        assertEquals(1, conn.probeFailCount(), "第 1 次探测无响应");
        assertEquals(TcpState.ESTABLISHED, conn.apply(TcpEvent.PROBE_TIMEOUT));
        assertEquals(2, conn.probeFailCount());
    }

    @Test
    @DisplayName("连续 3 次探测无响应判定假死：ESTABLISHED -> CLOSED，计数清零")
    void probeFailuresGiveUp() {
        TcpStateMachine conn = new TcpStateMachine(TcpState.ESTABLISHED);
        for (int i = 0; i < TcpStateMachine.MAX_PROBES - 1; i++) {
            conn.apply(TcpEvent.PROBE_TIMEOUT);
        }
        assertEquals(TcpState.CLOSED, conn.apply(TcpEvent.PROBE_TIMEOUT),
                "连续 3 次无响应，判定对端假死");
        assertEquals(0, conn.probeFailCount(), "判定假死后计数清零");
    }

    @Test
    @DisplayName("收到对端报文重置探测计数：2 次无响应后收到 FIN，计数清零")
    void peerResponseResetsProbeCount() {
        TcpStateMachine conn = new TcpStateMachine(TcpState.ESTABLISHED);
        conn.apply(TcpEvent.PROBE_TIMEOUT);
        conn.apply(TcpEvent.PROBE_TIMEOUT);
        assertEquals(2, conn.probeFailCount());

        // 对端发来 FIN（说明还活着），计数清零并进入关闭流程
        assertEquals(TcpState.CLOSE_WAIT, conn.apply(TcpEvent.RECV_FIN));
        assertEquals(0, conn.probeFailCount(), "收到对端报文说明对端存活，探测计数清零");
    }

    @Test
    @DisplayName("PROBE_TIMEOUT 只在 ESTABLISHED 合法：未建立连接/已关闭都拒绝")
    void probeIllegalOutsideEstablished() {
        assertThrows(IllegalStateException.class,
                () -> new TcpStateMachine(TcpState.SYN_SENT).apply(TcpEvent.PROBE_TIMEOUT));
        assertThrows(IllegalStateException.class,
                () -> new TcpStateMachine(TcpState.LISTEN).apply(TcpEvent.PROBE_TIMEOUT));
        assertThrows(IllegalStateException.class,
                () -> new TcpStateMachine(TcpState.CLOSED).apply(TcpEvent.PROBE_TIMEOUT));
        assertThrows(IllegalStateException.class,
                () -> new TcpStateMachine(TcpState.TIME_WAIT).apply(TcpEvent.PROBE_TIMEOUT));
    }

    @Test
    @DisplayName("假死后重新建连：握手成功进入 ESTABLISHED，探测计数从头算")
    void probeCountResetsOnNewConnection() {
        TcpStateMachine client = new TcpStateMachine(TcpState.ESTABLISHED);
        for (int i = 0; i < TcpStateMachine.MAX_PROBES; i++) {
            client.apply(TcpEvent.PROBE_TIMEOUT); // -> CLOSED
        }
        assertEquals(TcpState.CLOSED, client.state());

        client.apply(TcpEvent.SEND_SYN);
        client.apply(TcpEvent.RECV_SYN_ACK); // 握手成功 -> ESTABLISHED
        assertEquals(TcpState.ESTABLISHED, client.state());
        assertEquals(0, client.probeFailCount(), "新连接的探测计数从头算");
        assertEquals(TcpState.ESTABLISHED, client.apply(TcpEvent.PROBE_TIMEOUT));
        assertEquals(1, client.probeFailCount());
    }
}
