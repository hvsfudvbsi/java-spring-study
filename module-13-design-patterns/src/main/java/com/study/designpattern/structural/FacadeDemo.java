package com.study.designpattern.structural;

/**
 * 外观模式（Facade）用例（常用 + 不常用）
 *
 * 为复杂子系统提供一个统一的高层接口 —— "前台接待"。
 * 适用：下单（库存+支付+物流）、编译（词法+语法+语义+生成）、SDK 封装。
 *
 * 与中介者的区别（面试高频）：
 *   外观   : 单向门面（客户端 -> 子系统），子系统之间互不知晓
 *   中介者 : 双向协调（同事之间通过中介者互相通信）
 */
public class FacadeDemo {

    // ---------- 子系统 1：库存 ----------
    public static final class InventoryService {
        public boolean checkStock(String sku, int count) {
            System.out.println("    库存服务: 校验 " + sku + " x" + count + " 有货");
            return true;
        }

        public void deduct(String sku, int count) {
            System.out.println("    库存服务: 扣减 " + sku + " x" + count);
        }
    }

    // ---------- 子系统 2：支付 ----------
    public static final class PaymentService {
        public boolean pay(String orderId, double amount) {
            System.out.println("    支付服务: 订单 " + orderId + " 支付 ¥" + amount + " 成功");
            return true;
        }
    }

    // ---------- 子系统 3：物流 ----------
    public static final class ShippingService {
        public void ship(String orderId, String address) {
            System.out.println("    物流服务: 订单 " + orderId + " 发货至 " + address);
        }
    }

    /** 外观：一键下单（客户端只依赖它，不接触三个子系统） */
    public static final class OrderFacade {
        private final InventoryService inventory = new InventoryService();
        private final PaymentService payment = new PaymentService();
        private final ShippingService shipping = new ShippingService();

        public String placeOrder(String sku, int count, double price, String address) {
            String orderId = "ORD-" + System.currentTimeMillis() % 100000;
            System.out.println("  [外观] 开始下单 " + orderId);
            if (!inventory.checkStock(sku, count)) {
                return "库存不足";
            }
            inventory.deduct(sku, count);
            if (!payment.pay(orderId, price * count)) {
                return "支付失败";
            }
            shipping.ship(orderId, address);
            System.out.println("  [外观] 下单完成");
            return orderId;
        }
    }

    /** 不常用：抽象门面接口（可替换实现 / 便于测试） */
    public interface OrderService {
        String placeOrder(String sku, int count, double price, String address);
    }

    public static final class OrderFacadeImpl implements OrderService {
        private final OrderFacade facade = new OrderFacade();

        @Override
        public String placeOrder(String sku, int count, double price, String address) {
            return facade.placeOrder(sku, count, price, address);
        }
    }

    public static void main(String[] args) {
        System.out.println("========== 外观：常用写法（一键下单） ==========");
        OrderFacade facade = new OrderFacade();
        String orderId = facade.placeOrder("SKU-001", 2, 99.5, "北京市朝阳区");
        System.out.println("  下单结果: " + orderId);

        System.out.println();
        System.out.println("========== 外观：不常用写法（抽象门面接口） ==========");
        OrderService service = new OrderFacadeImpl();
        System.out.println("  通过接口调用: " + service.placeOrder("SKU-002", 1, 10, "上海市浦东新区"));
    }
}
