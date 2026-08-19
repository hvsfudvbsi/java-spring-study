package com.study.designpattern.behavioral;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 策略模式（Strategy）用例（常用 + 不常用）
 *
 * 定义一族算法并封装，使它们可以互相替换 —— 把"变化的算法"抽出来。
 * 适用：支付方式、排序算法、优惠计算、压缩格式。
 *
 * JDK 例子：Comparator 就是最经典的策略接口。
 * Java 8 之后：策略接口可以直接用 lambda/方法引用，连策略实现类都不用写。
 */
public class StrategyDemo {

    // ---------- 策略接口 ----------
    public interface PaymentStrategy {
        String pay(double amount);
    }

    // ---------- 具体策略 ----------
    public static final class WeChatPay implements PaymentStrategy {
        public String pay(double amount) {
            return "微信支付 ¥" + amount + "（优惠 2 元）";
        }
    }

    public static final class AlipayPay implements PaymentStrategy {
        public String pay(double amount) {
            return "支付宝支付 ¥" + amount + "（花呗分期可选）";
        }
    }

    public static final class CreditCardPay implements PaymentStrategy {
        public String pay(double amount) {
            return "信用卡支付 ¥" + amount + "（积分抵现）";
        }
    }

    // ---------- 上下文：持有策略，运行时切换 ----------
    public static final class PaymentContext {
        private PaymentStrategy strategy;

        public PaymentContext(PaymentStrategy strategy) {
            this.strategy = strategy;
        }

        public void setStrategy(PaymentStrategy strategy) {
            this.strategy = strategy;   // 运行期换策略
        }

        public String pay(double amount) {
            return strategy.pay(amount);
        }
    }

    /** 不常用：枚举策略（把一组相关策略收敛进枚举，每个常量一个算法） */
    public enum SortStrategy {
        QUICK_SORT {
            public List<Integer> sort(List<Integer> data) {
                List<Integer> copy = new ArrayList<>(data);
                quickSort(copy, 0, copy.size() - 1);
                return copy;
            }
        },
        BUBBLE_SORT {
            public List<Integer> sort(List<Integer> data) {
                List<Integer> copy = new ArrayList<>(data);
                for (int i = 0; i < copy.size(); i++) {
                    for (int j = 0; j < copy.size() - 1 - i; j++) {
                        if (copy.get(j) > copy.get(j + 1)) {
                            int tmp = copy.get(j);
                            copy.set(j, copy.get(j + 1));
                            copy.set(j + 1, tmp);
                        }
                    }
                }
                return copy;
            }
        };

        public abstract List<Integer> sort(List<Integer> data);

        private static void quickSort(List<Integer> list, int low, int high) {
            if (low >= high) {
                return;
            }
            int pivot = list.get(high);
            int i = low - 1;
            for (int j = low; j < high; j++) {
                if (list.get(j) < pivot) {
                    i++;
                    int tmp = list.get(i);
                    list.set(i, list.get(j));
                    list.set(j, tmp);
                }
            }
            int tmp = list.get(i + 1);
            list.set(i + 1, list.get(high));
            list.set(high, tmp);
            quickSort(list, low, i);
            quickSort(list, i + 2, high);
        }
    }

    public static void main(String[] args) {
        System.out.println("========== 策略：常用写法（支付方式） ==========");
        PaymentContext context = new PaymentContext(new WeChatPay());
        System.out.println("  " + context.pay(100));
        context.setStrategy(new AlipayPay());        // 运行期切换策略
        System.out.println("  " + context.pay(200));
        context.setStrategy(new CreditCardPay());
        System.out.println("  " + context.pay(300));

        System.out.println();
        System.out.println("========== 策略：不常用写法 ==========");
        // 函数式策略：直接传 lambda，无需实现类
        PaymentContext lambdaContext = new PaymentContext(amount -> "现金支付 ¥" + amount);
        System.out.println("  " + lambdaContext.pay(50));

        // Comparator 是 JDK 内置策略
        List<String> names = new ArrayList<>(List.of("bob", "alice", "charlie"));
        names.sort(Comparator.comparing(String::length));   // 按长度排序策略
        System.out.println("  Comparator 策略（按长度）: " + names);

        // 枚举策略
        List<Integer> data = new ArrayList<>(List.of(5, 3, 8, 1));
        System.out.println("  枚举策略 QUICK_SORT : " + SortStrategy.QUICK_SORT.sort(data));
        System.out.println("  枚举策略 BUBBLE_SORT: " + SortStrategy.BUBBLE_SORT.sort(data));
    }
}
