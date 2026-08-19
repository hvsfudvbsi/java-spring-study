package com.study.designpattern;

import com.study.designpattern.structural.AdapterDemo;
import com.study.designpattern.structural.BridgeDemo;
import com.study.designpattern.structural.CompositeDemo;
import com.study.designpattern.structural.DecoratorDemo;
import com.study.designpattern.structural.FacadeDemo;
import com.study.designpattern.structural.FlyweightDemo;
import com.study.designpattern.structural.ProxyDemo;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 结构型模式验证：适配转换、桥接组合、树递归求和、装饰叠加、
 * 外观封装、享元复用、代理拦截
 */
class StructuralPatternsTest {

    @Test
    @DisplayName("适配器：国标插座经转接头适配成欧标插头")
    void adapterConverts() {
        AdapterDemo.EuropeanPlug plug = new AdapterDemo.SocketAdapter(new AdapterDemo.ChineseSocket());
        assertTrue(plug.connect().contains("220V"));
        assertTrue(plug.connect().contains("欧标"));
    }

    @Test
    @DisplayName("桥接：同一形状可自由切换渲染器")
    void bridgeCombines() {
        BridgeDemo.Shape vectorCircle = new BridgeDemo.Circle(new BridgeDemo.VectorRenderer(), 5);
        BridgeDemo.Shape rasterCircle = new BridgeDemo.Circle(new BridgeDemo.RasterRenderer(), 5);
        assertTrue(vectorCircle.draw().contains("矢量"));
        assertTrue(rasterCircle.draw().contains("光栅"));
        assertEquals(vectorCircle.area(), rasterCircle.area(), 1e-9);
    }

    @Test
    @DisplayName("组合：目录大小递归求和正确")
    void compositeRecursiveSize() {
        CompositeDemo.DirectoryNode root = new CompositeDemo.DirectoryNode("root");
        CompositeDemo.DirectoryNode src = new CompositeDemo.DirectoryNode("src");
        root.add(src);
        src.add(new CompositeDemo.FileNode("a.java", 1024));
        src.add(new CompositeDemo.FileNode("b.java", 512));
        root.add(new CompositeDemo.FileNode("pom.xml", 128));
        assertEquals(1024 + 512 + 128, root.size());
        assertEquals(1024 + 512, src.size());
        // flatten 包含 root/src/两个文件/pom 共 5 个节点
        assertEquals(5, root.flatten().size());
    }

    @Test
    @DisplayName("装饰器：浓缩+牛奶+糖 价格与描述正确叠加")
    void decoratorStacks() {
        DecoratorDemo.Coffee coffee = new DecoratorDemo.SugarDecorator(
                new DecoratorDemo.MilkDecorator(new DecoratorDemo.Espresso()));
        assertEquals(15 + 3 + 2, coffee.cost());
        assertTrue(coffee.description().contains("牛奶"));
        assertTrue(coffee.description().contains("糖"));
    }

    @Test
    @DisplayName("外观：一键下单返回订单号，客户端不接触子系统")
    void facadePlacesOrder() {
        String orderId = new FacadeDemo.OrderFacade().placeOrder("SKU-1", 1, 10, "北京");
        assertTrue(orderId.startsWith("ORD-"));
        assertTrue(orderId.length() > 4);
    }

    @Test
    @DisplayName("享元：相同内部状态返回同一实例")
    void flyweightReuses() {
        FlyweightDemo.ChessPiece a = FlyweightDemo.ChessPieceFactory.get("兵", "黑");
        FlyweightDemo.ChessPiece b = FlyweightDemo.ChessPieceFactory.get("兵", "黑");
        assertSame(a, b);
        assertTrue(a != FlyweightDemo.ChessPieceFactory.get("兵", "白"));
    }

    @Test
    @DisplayName("代理：静态代理转发结果；保护代理拦截无权限操作；动态代理生效")
    void proxyIntercepts() {
        ProxyDemo.UserService real = new ProxyDemo.UserServiceImpl();
        ProxyDemo.UserService logProxy = new ProxyDemo.UserServiceLogProxy(real);
        assertTrue(logProxy.findUser("1").contains("张三"));

        // 保护代理：非管理员更新用户被拦截
        ProxyDemo.UserService guest = new ProxyDemo.AdminProxy(real, false);
        assertThrows(SecurityException.class, () -> guest.updateUser("1", "x"));
        // 管理员放行
        new ProxyDemo.AdminProxy(real, true).updateUser("1", "管理员");

        // JDK 动态代理
        ProxyDemo.UserService dynamic = (ProxyDemo.UserService) Proxy.newProxyInstance(
                ProxyDemo.UserService.class.getClassLoader(),
                new Class<?>[]{ProxyDemo.UserService.class},
                new ProxyDemo.TimerInvocationHandler(real));
        assertTrue(dynamic.findUser("2").contains("张三"));
    }
}
