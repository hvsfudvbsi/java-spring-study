package com.study.designpattern;

import com.study.designpattern.practice.ApprovalWorkflowDemo;
import com.study.designpattern.practice.DocumentExportDemo;
import com.study.designpattern.practice.OnlineOrderSystemDemo;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 实操示例验证：
 *   1. 在线商城：工厂折扣、模板流程状态流转、观察者通知、非法转移拦截
 *   2. 审批流  ：责任链分级审批、备忘录快照回退
 *   3. 文档导出：适配器内容转换、享元字体复用、装饰器叠加、外观一键导出
 */
class PracticeTest {

    // ================= 在线商城下单系统 =================

    @Test
    @DisplayName("商城：工厂方法按类型创建订单，折扣正确（秒杀 5 折）")
    void orderFactoryDiscount() {
        OnlineOrderSystemDemo.Order flash = new OnlineOrderSystemDemo.FlashOrderFactory().create(80);
        assertEquals("秒杀订单", flash.typeName());
        assertEquals(40, flash.amount(), 1e-9);

        OnlineOrderSystemDemo.Order normal = new OnlineOrderSystemDemo.NormalOrderFactory().create(100);
        assertEquals(100, normal.amount(), 1e-9);
    }

    @Test
    @DisplayName("商城：模板流程 创建->支付->发货，状态机流转 + 观察者收到通知")
    void orderTemplateProcess() {
        List<OnlineOrderSystemDemo.OrderEvent> events = new ArrayList<>();
        OnlineOrderSystemDemo.OrderProcessor processor = new OnlineOrderSystemDemo.NormalOrderProcessor(
                List.of((order, event) -> events.add(event)),
                new OnlineOrderSystemDemo.WeChatStrategy());

        OnlineOrderSystemDemo.Order order = processor.process(100);
        assertEquals(OnlineOrderSystemDemo.OrderStatus.SHIPPED, order.status());
        assertEquals(List.of(OnlineOrderSystemDemo.OrderEvent.PAY, OnlineOrderSystemDemo.OrderEvent.SHIP),
                order.timeline());
        assertEquals(2, events.size(), "支付/发货各通知一次");

        // 正常收尾：发货 -> 完成
        order.apply(OnlineOrderSystemDemo.OrderEvent.COMPLETE);
        assertEquals(OnlineOrderSystemDemo.OrderStatus.COMPLETED, order.status());
        // 终态再发货 -> 非法转移
        assertThrows(IllegalStateException.class,
                () -> order.apply(OnlineOrderSystemDemo.OrderEvent.SHIP));
    }

    @Test
    @DisplayName("商城：秒杀钩子校验限购，超额被拦截")
    void flashOrderHookValidation() {
        OnlineOrderSystemDemo.OrderProcessor flash = new OnlineOrderSystemDemo.FlashOrderProcessor(
                List.of(), new OnlineOrderSystemDemo.AlipayStrategy());
        assertThrows(IllegalStateException.class, () -> flash.process(500));   // 实付 250 > 100
        assertEquals(OnlineOrderSystemDemo.OrderStatus.SHIPPED, flash.process(80).status());  // 实付 40 通过
    }

    // ================= 审批流引擎 =================

    @Test
    @DisplayName("审批流：责任链按金额分级审批")
    void workflowChainByAmount() {
        ApprovalWorkflowDemo.ApprovalWorkflow workflow = new ApprovalWorkflowDemo.ApprovalWorkflow();
        ApprovalWorkflowDemo.ApprovalRequest r1 = new ApprovalWorkflowDemo.ApprovalRequest("差旅", 500);
        ApprovalWorkflowDemo.ApprovalRequest r2 = new ApprovalWorkflowDemo.ApprovalRequest("采购", 500000);
        workflow.submit(r1);
        workflow.submit(r2);
        assertEquals(ApprovalWorkflowDemo.ApprovalStatus.APPROVED, r1.status());
        assertEquals("组长", r1.decidedBy());
        assertEquals("CEO", r2.decidedBy());
    }

    @Test
    @DisplayName("审批流：提交前快照，可回退到提交前状态")
    void workflowMementoRollback() {
        ApprovalWorkflowDemo.ApprovalWorkflow workflow = new ApprovalWorkflowDemo.ApprovalWorkflow();
        ApprovalWorkflowDemo.ApprovalRequest r = new ApprovalWorkflowDemo.ApprovalRequest("临时", 800);
        workflow.submit(r);
        assertEquals(ApprovalWorkflowDemo.ApprovalStatus.APPROVED, r.status());
        assertTrue(workflow.rollback(r), "回退应成功");
        assertEquals(ApprovalWorkflowDemo.ApprovalStatus.DRAFT, r.status(), "回退恢复为提交前状态");
    }

    // ================= 文档导出中心 =================

    @Test
    @DisplayName("导出：适配器把遗留系统数据转为统一接口")
    void exportAdapter() {
        DocumentExportDemo.DataSource source =
                new DocumentExportDemo.LegacyDataSourceAdapter(new DocumentExportDemo.LegacyDataSource());
        assertEquals("遗留系统数据（老接口）", source.content());
    }

    @Test
    @DisplayName("导出：享元字体复用同一实例")
    void exportFlyweight() {
        DocumentExportDemo.Font a = DocumentExportDemo.FontFactory.get("宋体", 12, "粗体");
        DocumentExportDemo.Font b = DocumentExportDemo.FontFactory.get("宋体", 12, "粗体");
        assertSame(a, b);
    }

    @Test
    @DisplayName("导出：装饰器叠加加密/压缩/水印，外观一键导出")
    void exportDecoratorAndFacade() {
        DocumentExportDemo.ExportFacade facade = new DocumentExportDemo.ExportFacade();
        DocumentExportDemo.DataSource source = new DocumentExportDemo.InternalDataSource("报表");
        DocumentExportDemo.Font font = DocumentExportDemo.FontFactory.get("宋体", 12, "常规");

        String plain = facade.exportPlain(source, font);
        assertTrue(plain.contains("报表"));

        String secure = facade.exportSecure(source, font);
        assertTrue(secure.contains("[已加密]"));
        assertTrue(secure.contains("[已压缩]"));
        assertTrue(secure.contains("[水印: 机密文件]"));
        assertFalse(plain.contains("[已加密]"), "普通导出不应带加密");
    }
}
