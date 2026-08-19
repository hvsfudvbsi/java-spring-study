package com.study.designpattern.practice;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * 实操示例二：审批流引擎（组合 3 个设计模式）
 *
 * 场景：报销单提交后按金额走多级审批（组长/经理/总监/CEO），
 *       提交前自动拍快照，审批出错可一键回退。
 *
 * 用到的模式：
 *   责任链 : Approver 按金额分级审批，超限自动传给下一级
 *   状态   : 审批单状态机（草稿 -> 审批中 -> 通过/驳回）
 *   备忘录 : Snapshot 快照 + History 历史栈，支持多级回退
 */
public class ApprovalWorkflowDemo {

    // ================= 状态 =================

    public enum ApprovalStatus {DRAFT, IN_REVIEW, APPROVED, REJECTED}

    // ================= 备忘录 =================

    /** 备忘录：审批单快照（record 不可变） */
    public record Snapshot(ApprovalStatus status, String decidedBy) {
    }

    /** 管理者：快照栈（支持多级回退） */
    public static final class History {
        private final Deque<Snapshot> snapshots = new ArrayDeque<>();

        public void push(ApprovalRequest request) {
            snapshots.push(request.save());
        }

        /** 回退一步，返回是否成功 */
        public boolean rollback(ApprovalRequest request) {
            Snapshot snapshot = snapshots.poll();
            if (snapshot == null) {
                return false;
            }
            request.restore(snapshot);
            return true;
        }

        public int size() {
            return snapshots.size();
        }
    }

    // ================= 发起人：审批单 =================

    public static final class ApprovalRequest {
        private final String title;
        private final double amount;
        private ApprovalStatus status = ApprovalStatus.DRAFT;
        private String decidedBy = "-";

        public ApprovalRequest(String title, double amount) {
            this.title = title;
            this.amount = amount;
        }

        public String title() {
            return title;
        }

        public double amount() {
            return amount;
        }

        public ApprovalStatus status() {
            return status;
        }

        public String decidedBy() {
            return decidedBy;
        }

        public Snapshot save() {
            return new Snapshot(status, decidedBy);   // 生成快照
        }

        public void restore(Snapshot snapshot) {
            this.status = snapshot.status();          // 从快照恢复
            this.decidedBy = snapshot.decidedBy();
        }

        void markInReview() {
            status = ApprovalStatus.IN_REVIEW;
        }

        void approve(String approver) {
            status = ApprovalStatus.APPROVED;
            decidedBy = approver;
        }

        void reject(String approver) {
            status = ApprovalStatus.REJECTED;
            decidedBy = approver;
        }
    }

    // ================= 责任链：审批人 =================

    public abstract static class Approver {
        protected Approver next;

        public Approver setNext(Approver next) {
            this.next = next;
            return next;
        }

        /** 审批：能批就批，不能批传给下一个 */
        public final void handle(ApprovalRequest request) {
            if (canApprove(request.amount())) {
                request.approve(name());
            } else if (next != null) {
                next.handle(request);
            } else {
                request.reject("系统");
            }
        }

        protected abstract boolean canApprove(double amount);

        protected abstract String name();
    }

    public static final class TeamLeader extends Approver {
        protected boolean canApprove(double amount) {
            return amount <= 1000;
        }

        protected String name() {
            return "组长";
        }
    }

    public static final class Manager extends Approver {
        protected boolean canApprove(double amount) {
            return amount <= 10000;
        }

        protected String name() {
            return "经理";
        }
    }

    public static final class Director extends Approver {
        protected boolean canApprove(double amount) {
            return amount <= 100000;
        }

        protected String name() {
            return "总监";
        }
    }

    public static final class Ceo extends Approver {
        protected boolean canApprove(double amount) {
            return true;   // 兜底：都能批
        }

        protected String name() {
            return "CEO";
        }
    }

    // ================= 门面：审批工作流 =================

    public static final class ApprovalWorkflow {
        private final Approver chain;
        private final History history = new History();

        public ApprovalWorkflow() {
            chain = new TeamLeader();
            chain.setNext(new Manager()).setNext(new Director()).setNext(new Ceo());
        }

        /** 提交审批：先拍快照（备忘录），再走责任链 */
        public void submit(ApprovalRequest request) {
            history.push(request);              // 拍快照，可回退
            request.markInReview();             // 状态流转：DRAFT -> IN_REVIEW
            chain.handle(request);              // 责任链审批 -> APPROVED / REJECTED
        }

        /** 回退到上一个快照 */
        public boolean rollback(ApprovalRequest request) {
            return history.rollback(request);
        }

        public int snapshotCount() {
            return history.size();
        }
    }

    public static void main(String[] args) {
        System.out.println("========== 审批流引擎（责任链 + 状态 + 备忘录） ==========");
        ApprovalWorkflow workflow = new ApprovalWorkflow();

        // 不同金额走不同级别审批
        ApprovalRequest r1 = new ApprovalRequest("差旅报销", 500);
        ApprovalRequest r2 = new ApprovalRequest("服务器采购", 50000);
        ApprovalRequest r3 = new ApprovalRequest("年度预算", 500000);
        workflow.submit(r1);
        workflow.submit(r2);
        workflow.submit(r3);
        System.out.println("  " + r1.title() + " ¥" + r1.amount()
                + " -> " + r1.status() + "（" + r1.decidedBy() + "）");
        System.out.println("  " + r2.title() + " ¥" + r2.amount()
                + " -> " + r2.status() + "（" + r2.decidedBy() + "）");
        System.out.println("  " + r3.title() + " ¥" + r3.amount()
                + " -> " + r3.status() + "（" + r3.decidedBy() + "）");

        // 备忘录回退演示：提交后不满意，回退到提交前（DRAFT）
        ApprovalRequest r4 = new ApprovalRequest("临时采购", 800);
        workflow.submit(r4);
        System.out.println("  " + r4.title() + " 提交后: " + r4.status() + "（" + r4.decidedBy() + "）");
        boolean rolledBack = workflow.rollback(r4);
        System.out.println("  快照回退 " + (rolledBack ? "成功" : "失败")
                + " -> 恢复为 " + r4.status() + "（回退前快照数 " + workflow.snapshotCount() + "）");
    }
}
