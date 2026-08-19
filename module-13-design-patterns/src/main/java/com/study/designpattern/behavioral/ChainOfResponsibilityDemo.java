package com.study.designpattern.behavioral;

import java.util.List;
import java.util.function.Function;

/**
 * 责任链模式（Chain of Responsibility）用例（常用 + 不常用）
 *
 * 多个处理器串成链，请求沿链传递，直到有处理器处理（或全部不处理）。
 * 适用：审批流（按金额分级）、日志级别过滤、过滤器/拦截器（Servlet Filter、Spring MVC Interceptor）。
 *
 * 两种传递方式：
 *   中断式：某个处理器处理完就停止（审批）
 *   全链式：每个处理器都执行（过滤器，层层过滤后放行）
 */
public class ChainOfResponsibilityDemo {

    // ---------- 抽象处理器 ----------
    public abstract static class Approver {
        protected Approver next;

        /** 设置后继处理器，返回后继便于链式组装 */
        public Approver setNext(Approver next) {
            this.next = next;
            return next;
        }

        public abstract String approve(double amount);

        protected String passToNext(double amount) {
            return next == null ? "无人有权审批（驳回）" : next.approve(amount);
        }
    }

    // ---------- 具体处理器 ----------
    public static final class TeamLeader extends Approver {
        public String approve(double amount) {
            return amount <= 1000 ? "组长审批通过 ¥" + amount : passToNext(amount);
        }
    }

    public static final class Manager extends Approver {
        public String approve(double amount) {
            return amount <= 10000 ? "经理审批通过 ¥" + amount : passToNext(amount);
        }
    }

    public static final class Director extends Approver {
        public String approve(double amount) {
            return amount <= 100000 ? "总监审批通过 ¥" + amount : passToNext(amount);
        }
    }

    public static final class Ceo extends Approver {
        public String approve(double amount) {
            return "CEO 审批通过 ¥" + amount;
        }
    }

    /** 不常用：函数式责任链（List&lt;Function&gt; 组装，无需处理器类） */
    public static final class FunctionalChain {
        private final List<Function<Double, String>> handlers;

        public FunctionalChain(List<Function<Double, String>> handlers) {
            this.handlers = handlers;
        }

        public String handle(double amount) {
            for (Function<Double, String> handler : handlers) {
                String result = handler.apply(amount);
                if (result != null) {   // 返回 null 表示"不处理，交给下一个"
                    return result;
                }
            }
            return "无人处理";
        }
    }

    public static void main(String[] args) {
        System.out.println("========== 责任链：常用写法（审批流） ==========");
        // 组装链：组长 -> 经理 -> 总监 -> CEO
        Approver chain = new TeamLeader();
        chain.setNext(new Manager()).setNext(new Director()).setNext(new Ceo());

        for (double amount : new double[]{500, 5000, 50000, 500000}) {
            System.out.println("  报销 ¥" + amount + " -> " + chain.approve(amount));
        }

        System.out.println();
        System.out.println("========== 责任链：不常用写法（函数式链） ==========");
        FunctionalChain functional = new FunctionalChain(List.of(
                amount -> amount <= 1000 ? "组内报销 ¥" + amount : null,
                amount -> amount <= 10000 ? "部门报销 ¥" + amount : null,
                amount -> amount <= 100000 ? "公司报销 ¥" + amount : null));
        System.out.println("  ¥8000 -> " + functional.handle(8000));
        System.out.println("  ¥80000 -> " + functional.handle(80000));
        System.out.println("  ¥800000 -> " + functional.handle(800000));
    }
}
