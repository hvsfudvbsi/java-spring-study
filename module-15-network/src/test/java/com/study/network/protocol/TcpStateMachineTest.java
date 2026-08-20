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
}
