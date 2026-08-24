package com.study.network.protocol;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;

/**
 * TCP 状态机模拟——三次握手与四次挥手的核心。
 *
 * RFC 793 定义了 11 个状态，本类用"状态 + 事件 -> 新状态"的转换表模拟：
 *
 * <pre>
 * 三次握手（建立连接）：
 *   客户端: CLOSED --SEND_SYN--> SYN_SENT --RECV_SYN_ACK--> ESTABLISHED
 *   服务端: CLOSED --LISTEN--> LISTEN --RECV_SYN--> SYN_RECEIVED --RECV_ACK--> ESTABLISHED
 *
 * 四次挥手（主动关闭方，如客户端）：
 *   ESTABLISHED --SEND_FIN--> FIN_WAIT_1 --RECV_ACK--> FIN_WAIT_2
 *   --RECV_FIN--> TIME_WAIT --TIMEOUT_2MSL--> CLOSED
 *
 * 四次挥手（被动关闭方，如服务端）：
 *   ESTABLISHED --RECV_FIN--> CLOSE_WAIT --SEND_FIN--> LAST_ACK --RECV_ACK--> CLOSED
 *
 * 同时关闭（双方同时发 FIN）：
 *   FIN_WAIT_1 --RECV_FIN--> CLOSING --RECV_ACK--> TIME_WAIT --TIMEOUT_2MSL--> CLOSED
 * </pre>
 *
 * RST（连接重置）——为什么 TCP 需要它（面试常问）：
 * - 端口未监听：客户端 SYN 到达无监听端口，内核直接回 RST，客户端 SYN_SENT + RST -> CLOSED，
 *   这就是最常见的 "Connection refused（连接被拒绝）"。
 * - 连接被对端强杀：进程崩溃/重启、程序异常退出时对端收 RST，ESTABLISHED + RST -> CLOSED，
 *   即 "Connection reset by peer"。
 * - 半开连接（对端已消失但本端不知情）收到数据时回 RST，通知对端"这条连接已经不存在了"。
 * - LISTEN 状态下收到 RST 通常直接丢弃（继续监听），不影响已有连接。
 * - TIME_WAIT 是终态前的固定等待（2MSL），不受 RST 影响、不能提前结束。
 *
 * 半开连接检测（Half-open）——对端消失（崩溃/断电/网络断开）但本端没收到 FIN/RST，
 * 连接资源（TCB、端口）被白白占用。检测分两个阶段（面试常问）：
 * - 建连阶段（本类新增 RETRANSMIT_TIMEOUT 事件）：SYN 发出后等待 SYN+ACK，RTO 超时
 *   按**指数退避**重发 SYN（1s、2s、4s…），重试耗尽即放弃：客户端 SYN_SENT -> CLOSED，
 *   服务端 SYN_RECEIVED -> LISTEN（Linux 默认重发 6 次约 127s 后报 Connection timed out）。
 * - 建连之后（本类新增 PROBE_TIMEOUT 事件）：**keep-alive 假死检测**——连接空闲一段时间后
 *   发探测包，连续 N 次无响应（Linux 默认 9 次）判定对端假死 -> CLOSED；
 *   收到对端任何报文（数据/ACK）都会重置探测计数（说明对端还活着）。
 *   网络层没有心跳协议，TCP 探测就是最朴素的"对方还在吗"；应用层心跳见 module-11 的 IdleStateHandler。
 *
 * 学习点：
 * - 状态不是随便跳的：非法转换（如 ESTABLISHED 直接收到 SYN）抛 IllegalStateException。
 * - TIME_WAIT 是主动关闭方专有：等待 2 倍报文最大生存时间（2MSL），确保最后一个 ACK 到达。
 * - 被动关闭方没有 FIN_WAIT/TIME_WAIT，只有 CLOSE_WAIT -> LAST_ACK。
 */
public class TcpStateMachine {

    /** TCP 11 个状态（RFC 793） */
    public enum TcpState {
        CLOSED,          // 初始：无连接
        LISTEN,          // 服务端监听，等待 SYN
        SYN_SENT,        // 客户端已发 SYN，等待 SYN+ACK
        SYN_RECEIVED,    // 服务端已收 SYN 并回 SYN+ACK，等待 ACK
        ESTABLISHED,     // 连接建立，可传输数据
        FIN_WAIT_1,      // 主动关闭：已发 FIN，等待 ACK 或 FIN
        FIN_WAIT_2,      // 主动关闭：已收对端 ACK，等待对端 FIN
        CLOSING,         // 同时关闭：双方都发了 FIN，等待对端 ACK
        TIME_WAIT,       // 主动关闭：已收对端 FIN 并发 ACK，等待 2MSL 后关闭
        CLOSE_WAIT,      // 被动关闭：已收对端 FIN 并回 ACK，等待本端应用关闭
        LAST_ACK         // 被动关闭：应用已关闭、已发 FIN，等待对端 ACK
    }

    /** 触发状态转换的事件 */
    public enum TcpEvent {
        LISTEN,          // 服务端进入监听
        SEND_SYN,        // 发送 SYN（主动打开连接）
        RECV_SYN,        // 收到 SYN
        RECV_SYN_ACK,    // 收到 SYN+ACK
        RECV_ACK,        // 收到 ACK
        SEND_FIN,        // 发送 FIN（主动关闭）
        RECV_FIN,        // 收到 FIN
        RECV_RST,        // 收到 RST（连接重置：端口未监听/对端强杀/半开连接）
        RETRANSMIT_TIMEOUT, // 重传超时（RTO）：等待握手应答超时，指数退避重发 SYN/SYN+ACK
        PROBE_TIMEOUT,   // keep-alive 探测超时：连接空闲后探测对端，连续 N 次无响应判定假死
        TIMEOUT_2MSL     // TIME_WAIT 超时（2 倍报文最大生存时间）
    }

    /** 握手重试上限：重发 SYN/SYN+ACK 达到该次数仍无应答则放弃连接 */
    public static final int MAX_RETRANSMITS = 3;

    /** keep-alive 探测上限：连续 N 次探测无响应判定对端假死（Linux 默认 9 次，这里简化为 3） */
    public static final int MAX_PROBES = 3;

    private TcpState state;
    private int retransmitCount; // 当前等待握手应答期间的 RTO 重试次数
    private int probeFailCount;  // 连续 keep-alive 探测无响应次数

    public TcpStateMachine(TcpState initialState) {
        this.state = initialState;
    }

    public TcpState state() {
        return state;
    }

    /** 当前握手重试次数（0 = 未发生过 RTO）。 */
    public int retransmitCount() {
        return retransmitCount;
    }

    /** 当前连续 keep-alive 探测无响应次数（0 = 对端正常/从未探测）。 */
    public int probeFailCount() {
        return probeFailCount;
    }

    /**
     * 应用一个事件并返回新状态；非法转换抛 IllegalStateException。
     * 状态机保证：任何状态只能按 TCP 协议规定的方式跳转。
     */
    public TcpState apply(TcpEvent event) {
        if (event == TcpEvent.RETRANSMIT_TIMEOUT) {
            return handleRetransmitTimeout();
        }
        if (event == TcpEvent.PROBE_TIMEOUT) {
            return handleProbeTimeout();
        }
        TcpState next = transition(state, event);
        if (next == null) {
            throw new IllegalStateException(
                    "非法转换: " + state + " + " + event + "（TCP 协议不允许）");
        }
        this.state = next;
        // 重新发起握手 / 握手成功 / 连接结束：重试计数清零，下一次 RTO 从头算
        if (event == TcpEvent.SEND_SYN || event == TcpEvent.RECV_SYN
                || next == TcpState.ESTABLISHED || next == TcpState.CLOSED) {
            retransmitCount = 0;
        }
        // keep-alive 探测计数：进入 ESTABLISHED（握手成功）或收到对端任何报文时清零
        if (next == TcpState.ESTABLISHED || isPeerResponse(event)) {
            probeFailCount = 0;
        }
        return next;
    }

    /**
     * 半开连接检测：等待握手应答（SYN_SENT / SYN_RECEIVED）时 RTO 超时。
     * 未到重试上限：状态不变（指数退避重发 SYN/SYN+ACK），重试计数 +1；
     * 达到上限：放弃连接——客户端 SYN_SENT -> CLOSED，服务端 SYN_RECEIVED -> LISTEN（继续监听）。
     */
    private TcpState handleRetransmitTimeout() {
        if (state != TcpState.SYN_SENT && state != TcpState.SYN_RECEIVED) {
            throw new IllegalStateException(
                    "RTO 重传只发生在等待握手应答的状态（SYN_SENT/SYN_RECEIVED），当前: " + state);
        }
        retransmitCount++;
        if (retransmitCount >= MAX_RETRANSMITS) {
            retransmitCount = 0;
            this.state = state == TcpState.SYN_SENT ? TcpState.CLOSED : TcpState.LISTEN;
            return this.state;
        }
        return this.state; // 重发 SYN/SYN+ACK，继续等待（状态不变）
    }

    /**
     * keep-alive 假死检测：连接已建立（ESTABLISHED）后探测对端。
     * 未到上限：状态不变（继续发探测），探测失败计数 +1；
     * 连续 MAX_PROBES 次无响应：判定对端假死（进程崩溃/网络断开），直接 CLOSED 释放资源。
     * 真实 Linux 默认探测 9 次（每次间隔 75s），本类简化为 3 次；
     * 期间收到对端任何报文（RECV_*）都会把计数清零（对端还活着）。
     */
    private TcpState handleProbeTimeout() {
        if (state != TcpState.ESTABLISHED) {
            throw new IllegalStateException(
                    "keep-alive 探测只在 ESTABLISHED 状态进行（连接已建立才需要保活），当前: " + state);
        }
        probeFailCount++;
        if (probeFailCount >= MAX_PROBES) {
            probeFailCount = 0;
            this.state = TcpState.CLOSED;
            return TcpState.CLOSED;
        }
        return TcpState.ESTABLISHED; // 继续发探测，等待对端响应
    }

    /** 该事件是否表示"对端发来了报文"（说明对端还活着，keep-alive 计数清零）。 */
    private static boolean isPeerResponse(TcpEvent event) {
        return event == TcpEvent.RECV_SYN || event == TcpEvent.RECV_SYN_ACK
                || event == TcpEvent.RECV_ACK || event == TcpEvent.RECV_FIN
                || event == TcpEvent.RECV_RST;
    }

    /** 状态转换表：状态 x 事件 -> 新状态（null 表示非法） */
    private static final Map<TcpState, Map<TcpEvent, TcpState>> TRANSITIONS = buildTransitions();

    private static Map<TcpState, Map<TcpEvent, TcpState>> buildTransitions() {
        Map<TcpState, Map<TcpEvent, TcpState>> table = new EnumMap<>(TcpState.class);
        for (TcpState s : TcpState.values()) {
            table.put(s, new EnumMap<>(TcpEvent.class));
        }
        // 三次握手
        table.get(TcpState.CLOSED).put(TcpEvent.LISTEN, TcpState.LISTEN);
        table.get(TcpState.CLOSED).put(TcpEvent.SEND_SYN, TcpState.SYN_SENT);
        table.get(TcpState.LISTEN).put(TcpEvent.RECV_SYN, TcpState.SYN_RECEIVED);
        table.get(TcpState.SYN_SENT).put(TcpEvent.RECV_SYN_ACK, TcpState.ESTABLISHED);
        table.get(TcpState.SYN_RECEIVED).put(TcpEvent.RECV_ACK, TcpState.ESTABLISHED);
        // 主动关闭（四次挥手前半段）
        table.get(TcpState.ESTABLISHED).put(TcpEvent.SEND_FIN, TcpState.FIN_WAIT_1);
        table.get(TcpState.FIN_WAIT_1).put(TcpEvent.RECV_ACK, TcpState.FIN_WAIT_2);
        table.get(TcpState.FIN_WAIT_1).put(TcpEvent.RECV_FIN, TcpState.CLOSING);
        table.get(TcpState.FIN_WAIT_2).put(TcpEvent.RECV_FIN, TcpState.TIME_WAIT);
        table.get(TcpState.CLOSING).put(TcpEvent.RECV_ACK, TcpState.TIME_WAIT);
        table.get(TcpState.TIME_WAIT).put(TcpEvent.TIMEOUT_2MSL, TcpState.CLOSED);
        // 被动关闭（四次挥手后半段）
        table.get(TcpState.ESTABLISHED).put(TcpEvent.RECV_FIN, TcpState.CLOSE_WAIT);
        table.get(TcpState.CLOSE_WAIT).put(TcpEvent.SEND_FIN, TcpState.LAST_ACK);
        table.get(TcpState.LAST_ACK).put(TcpEvent.RECV_ACK, TcpState.CLOSED);
        // RST（连接重置）：端口未监听/对端强杀/半开连接 -> 直接 CLOSED
        // LISTEN 收到 RST 丢弃继续监听；TIME_WAIT 不受 RST 影响（2MSL 固定等待）
        table.get(TcpState.LISTEN).put(TcpEvent.RECV_RST, TcpState.LISTEN);         // 丢弃，继续监听
        table.get(TcpState.SYN_SENT).put(TcpEvent.RECV_RST, TcpState.CLOSED);       // Connection refused
        table.get(TcpState.SYN_RECEIVED).put(TcpEvent.RECV_RST, TcpState.CLOSED);   // 连接被重置
        table.get(TcpState.ESTABLISHED).put(TcpEvent.RECV_RST, TcpState.CLOSED);    // Connection reset by peer
        table.get(TcpState.FIN_WAIT_1).put(TcpEvent.RECV_RST, TcpState.CLOSED);
        table.get(TcpState.FIN_WAIT_2).put(TcpEvent.RECV_RST, TcpState.CLOSED);
        table.get(TcpState.CLOSING).put(TcpEvent.RECV_RST, TcpState.CLOSED);
        table.get(TcpState.CLOSE_WAIT).put(TcpEvent.RECV_RST, TcpState.CLOSED);
        table.get(TcpState.LAST_ACK).put(TcpEvent.RECV_RST, TcpState.CLOSED);
        return table;
    }

    private static TcpState transition(TcpState from, TcpEvent event) {
        return TRANSITIONS.get(from).get(event);
    }

    /** 客户端视角的三次握手完整流程（返回最终状态，供演示） */
    public static TcpState clientHandshake() {
        TcpStateMachine client = new TcpStateMachine(TcpState.CLOSED);
        client.apply(TcpEvent.SEND_SYN);
        client.apply(TcpEvent.RECV_SYN_ACK);
        return client.state();
    }

    /** 服务端视角的三次握手完整流程（返回最终状态，供演示） */
    public static TcpState serverHandshake() {
        TcpStateMachine server = new TcpStateMachine(TcpState.CLOSED);
        server.apply(TcpEvent.LISTEN);
        server.apply(TcpEvent.RECV_SYN);
        server.apply(TcpEvent.RECV_ACK);
        return server.state();
    }

    /** 主动关闭方视角的四次挥手完整流程（返回最终状态，供演示） */
    public static TcpState activeClose() {
        TcpStateMachine closer = new TcpStateMachine(TcpState.ESTABLISHED);
        closer.apply(TcpEvent.SEND_FIN);
        closer.apply(TcpEvent.RECV_ACK);
        closer.apply(TcpEvent.RECV_FIN);
        closer.apply(TcpEvent.TIMEOUT_2MSL);
        return closer.state();
    }

    /** 被动关闭方视角的四次挥手完整流程（返回最终状态，供演示） */
    public static TcpState passiveClose() {
        TcpStateMachine peer = new TcpStateMachine(TcpState.ESTABLISHED);
        peer.apply(TcpEvent.RECV_FIN);
        peer.apply(TcpEvent.SEND_FIN);
        peer.apply(TcpEvent.RECV_ACK);
        return peer.state();
    }

    /** 打印三次握手 + 四次挥手全过程（供 Main 演示） */
    public static void printHandshakeDemo() {
        System.out.println("================ TCP 状态机：三次握手 ================");
        // 客户端
        TcpStateMachine client = new TcpStateMachine(TcpState.CLOSED);
        System.out.println("  客户端 " + client.state() + " --SEND_SYN--> " + client.apply(TcpEvent.SEND_SYN));
        System.out.println("  客户端 " + client.state() + " --RECV_SYN_ACK--> " + client.apply(TcpEvent.RECV_SYN_ACK));
        // 服务端
        TcpStateMachine server = new TcpStateMachine(TcpState.CLOSED);
        System.out.println("  服务端 " + server.state() + " --LISTEN--> " + server.apply(TcpEvent.LISTEN));
        System.out.println("  服务端 " + server.state() + " --RECV_SYN--> " + server.apply(TcpEvent.RECV_SYN));
        System.out.println("  服务端 " + server.state() + " --RECV_ACK--> " + server.apply(TcpEvent.RECV_ACK));

        System.out.println();
        System.out.println("================ TCP 状态机：四次挥手 ================");
        TcpStateMachine closer = new TcpStateMachine(TcpState.ESTABLISHED);
        System.out.println("  主动方 " + closer.state() + " --SEND_FIN--> " + closer.apply(TcpEvent.SEND_FIN));
        System.out.println("  主动方 " + closer.state() + " --RECV_ACK--> " + closer.apply(TcpEvent.RECV_ACK));
        System.out.println("  主动方 " + closer.state() + " --RECV_FIN--> " + closer.apply(TcpEvent.RECV_FIN));
        System.out.println("  主动方 " + closer.state() + " --TIMEOUT_2MSL--> " + closer.apply(TcpEvent.TIMEOUT_2MSL));

        TcpStateMachine peer = new TcpStateMachine(TcpState.ESTABLISHED);
        System.out.println("  被动方 " + peer.state() + " --RECV_FIN--> " + peer.apply(TcpEvent.RECV_FIN));
        System.out.println("  被动方 " + peer.state() + " --SEND_FIN--> " + peer.apply(TcpEvent.SEND_FIN));
        System.out.println("  被动方 " + peer.state() + " --RECV_ACK--> " + peer.apply(TcpEvent.RECV_ACK));
    }

    /** 打印 keep-alive 假死检测演示（供 Main 调用）：连接空闲后连续探测无响应判定假死。 */
    public static void printKeepAliveDemo() {
        System.out.println("================ TCP 状态机：keep-alive 假死检测 ================");
        System.out.println("  场景：连接已建立但对端进程崩溃/网络断开——空闲后发探测包，连续无响应判定假死");
        TcpStateMachine conn = new TcpStateMachine(TcpState.ESTABLISHED);
        for (int i = 1; i <= MAX_PROBES; i++) {
            TcpState result = conn.apply(TcpEvent.PROBE_TIMEOUT);
            String note = result == TcpState.CLOSED
                    ? "（连续 " + MAX_PROBES + " 次探测无响应，判定对端假死，释放连接）"
                    : "（发送探测，对端无响应）";
            System.out.println("    第 " + i + " 次探测超时: " + result + note);
        }
        System.out.println();
    }

    /** 打印半开连接检测演示（供 Main 调用）：SYN 重传超时 + 指数退避，重试耗尽放弃。 */
    public static void printHalfOpenDemo() {
        System.out.println("================ TCP 状态机：半开连接检测（SYN 重传超时） ================");
        System.out.println("  场景：客户端发 SYN 后对端无响应（服务器宕机/网络断开）——RTO 指数退避重发");
        TcpStateMachine client = new TcpStateMachine(TcpState.SYN_SENT);
        int rtoSeconds = 1;
        for (int i = 1; i <= MAX_RETRANSMITS; i++) {
            TcpState result = client.apply(TcpEvent.RETRANSMIT_TIMEOUT);
            String note = result == TcpState.CLOSED
                    ? "（重试耗尽，放弃连接，报 Connection timed out）"
                    : "（重发 SYN，继续等待）";
            System.out.println("    RTO " + rtoSeconds + "s 超时: " + result + note);
            rtoSeconds *= 2; // 指数退避：1s -> 2s -> 4s
        }
        System.out.println();
    }

    /** 打印 RST 重置演示（供 Main 调用）：Connection refused 与 Connection reset by peer。 */
    public static void printRstDemo() {
        System.out.println("================ TCP 状态机：RST 连接重置 ================");
        // 场景 1：端口未监听 -> Connection refused
        TcpStateMachine refused = new TcpStateMachine(TcpState.SYN_SENT);
        System.out.println("  客户端 " + refused.state()
                + " --RECV_RST--> " + refused.apply(TcpEvent.RECV_RST)
                + "（端口未监听，内核回 RST -> Connection refused）");
        // 场景 2：对端强杀/进程崩溃 -> Connection reset by peer
        TcpStateMachine reset = new TcpStateMachine(TcpState.ESTABLISHED);
        System.out.println("  已连接 " + reset.state()
                + " --RECV_RST--> " + reset.apply(TcpEvent.RECV_RST)
                + "（对端异常退出 -> Connection reset by peer）");
        // 场景 3：LISTEN 收到 RST 直接丢弃，继续监听
        TcpStateMachine listen = new TcpStateMachine(TcpState.LISTEN);
        System.out.println("  服务端 " + listen.state()
                + " --RECV_RST--> " + listen.apply(TcpEvent.RECV_RST) + "（丢弃，继续监听）");
        System.out.println();
    }

    /** 打印所有状态名（供参考） */
    public static EnumSet<TcpState> allStates() {
        return EnumSet.allOf(TcpState.class);
    }
}
