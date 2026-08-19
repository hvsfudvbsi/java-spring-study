package com.study.designpattern;

import com.study.designpattern.behavioral.ChainOfResponsibilityDemo;
import com.study.designpattern.behavioral.CommandDemo;
import com.study.designpattern.behavioral.InterpreterDemo;
import com.study.designpattern.behavioral.IteratorDemo;
import com.study.designpattern.behavioral.MediatorDemo;
import com.study.designpattern.behavioral.MementoDemo;
import com.study.designpattern.behavioral.ObserverDemo;
import com.study.designpattern.behavioral.StateDemo;
import com.study.designpattern.behavioral.StrategyDemo;
import com.study.designpattern.behavioral.TemplateMethodDemo;
import com.study.designpattern.behavioral.VisitorDemo;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.ConcurrentModificationException;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 行为型模式验证：责任链分级、命令撤销、迭代器遍历、中介转发、
 * 备忘录恢复、观察者通知、状态机流转、策略切换、模板方法骨架、
 * 访问者双分派、解释器求值
 */
class BehavioralPatternsTest {

    @Test
    @DisplayName("责任链：按金额路由到对应审批级别")
    void chainRoutesByAmount() {
        ChainOfResponsibilityDemo.Approver chain = new ChainOfResponsibilityDemo.TeamLeader();
        chain.setNext(new ChainOfResponsibilityDemo.Manager())
                .setNext(new ChainOfResponsibilityDemo.Director())
                .setNext(new ChainOfResponsibilityDemo.Ceo());

        assertTrue(chain.approve(500).contains("组长"));
        assertTrue(chain.approve(5000).contains("经理"));
        assertTrue(chain.approve(50000).contains("总监"));
        assertTrue(chain.approve(500000).contains("CEO"));
    }

    @Test
    @DisplayName("命令：执行开灯后可撤销关灯")
    void commandExecuteAndUndo() {
        CommandDemo.Light light = new CommandDemo.Light();
        CommandDemo.Command on = new CommandDemo.LightOnCommand(light);
        on.execute();
        assertTrue(light.isOn());
        on.undo();
        assertFalse(light.isOn());
    }

    @Test
    @DisplayName("迭代器：自定义集合可遍历；倒序迭代器顺序正确；fail-fast 抛异常")
    void iteratorTraversal() {
        IteratorDemo.BookShelf shelf = new IteratorDemo.BookShelf();
        shelf.add(new IteratorDemo.Book("a", 1));
        shelf.add(new IteratorDemo.Book("b", 2));
        shelf.add(new IteratorDemo.Book("c", 3));

        int count = 0;
        for (IteratorDemo.Book ignored : shelf) {
            count++;
        }
        assertEquals(3, count);

        Iterator<IteratorDemo.Book> reverse = shelf.reverseIterator();
        assertEquals("c", reverse.next().title());
        assertEquals("b", reverse.next().title());
        assertEquals("a", reverse.next().title());

        // fail-fast：迭代中结构性修改抛 ConcurrentModificationException
        List<String> list = new ArrayList<>(List.of("a", "b"));
        assertThrows(ConcurrentModificationException.class, () -> {
            for (String s : list) {
                if (s.equals("a")) {
                    list.add("x");
                }
            }
        });
    }

    @Test
    @DisplayName("中介者：消息经聊天室转发给其他人，发送者自己不收")
    void mediatorRelaysMessage() {
        MediatorDemo.ChatRoom room = new MediatorDemo.ChatRoom();
        MediatorDemo.User alice = new MediatorDemo.User("Alice", room);
        MediatorDemo.User bob = new MediatorDemo.User("Bob", room);
        room.addUser(alice);
        room.addUser(bob);
        alice.send("你好");
        assertEquals(1, bob.inbox().size());
        assertTrue(bob.inbox().get(0).contains("你好"));
        assertEquals(0, alice.inbox().size());
    }

    @Test
    @DisplayName("备忘录：保存快照后可恢复之前内容")
    void mementoSaveAndRestore() {
        MementoDemo.Editor editor = new MementoDemo.Editor();
        editor.write("Hello");
        MementoDemo.EditorState snapshot = editor.save();
        editor.write(", World");
        assertEquals("Hello, World", editor.content());
        editor.restore(snapshot);
        assertEquals("Hello", editor.content());
    }

    @Test
    @DisplayName("观察者：注册后收到通知，取消注册后不再通知")
    void observerNotifies() {
        ObserverDemo.WeatherStation station = new ObserverDemo.WeatherStation();
        AtomicInteger count = new AtomicInteger();
        ObserverDemo.WeatherObserver counter = (t, h) -> count.incrementAndGet();

        station.register(counter);
        station.setMeasurements(25, 60);
        assertEquals(1, count.get());
        station.unregister(counter);
        station.setMeasurements(26, 58);
        assertEquals(1, count.get(), "取消注册后不再通知");
    }

    @Test
    @DisplayName("状态：合法流转正常，非法流转抛异常；枚举状态机转移表正确")
    void stateTransitions() {
        StateDemo.Order order = new StateDemo.Order();
        assertEquals("待支付", order.status());
        order.pay();
        assertEquals("已支付", order.status());
        order.ship();
        assertEquals("已发货", order.status());
        order.complete();
        assertEquals("已完成", order.status());
        assertThrows(IllegalStateException.class, order::ship);   // 终态不可再操作

        // 枚举 + 转移表
        assertEquals(StateDemo.OrderStatus.PAID,
                StateDemo.EnumStateMachine.next(
                        StateDemo.OrderStatus.PENDING,
                        StateDemo.OrderEvent.PAY));
        assertThrows(IllegalStateException.class, () ->
                StateDemo.EnumStateMachine.next(
                        StateDemo.OrderStatus.PENDING,
                        StateDemo.OrderEvent.SHIP));
    }

    @Test
    @DisplayName("策略：运行期切换策略；枚举策略排序正确")
    void strategySwitches() {
        StrategyDemo.PaymentContext context = new StrategyDemo.PaymentContext(
                amount -> "现金支付 ¥" + amount);
        assertTrue(context.pay(50).contains("现金"));
        context.setStrategy(new StrategyDemo.AlipayPay());
        assertTrue(context.pay(100).contains("支付宝"));

        assertEquals(List.of(1, 3, 5, 8),
                StrategyDemo.SortStrategy.QUICK_SORT.sort(new ArrayList<>(List.of(5, 3, 8, 1))));
        assertEquals(List.of(1, 3, 5, 8),
                StrategyDemo.SortStrategy.BUBBLE_SORT.sort(new ArrayList<>(List.of(5, 3, 8, 1))));
    }

    @Test
    @DisplayName("模板方法：骨架固定，钩子可跳过加料步骤")
    void templateMethod() {
        String coffee = new TemplateMethodDemo.Coffee().make();
        assertTrue(coffee.contains("烧水到 100°C"));
        assertTrue(coffee.contains("加糖和牛奶"));

        String plainTea = new TemplateMethodDemo.PlainTea().make();
        assertTrue(plainTea.contains("不加任何配料"));
        assertFalse(plainTea.contains("加柠檬"));
    }

    @Test
    @DisplayName("访问者：双分派计算各形状面积之和")
    void visitorDoubleDispatch() {
        VisitorDemo.AreaVisitor area = new VisitorDemo.AreaVisitor();
        List<VisitorDemo.Shape> shapes = List.of(
                new VisitorDemo.Circle(2),
                new VisitorDemo.Rectangle(3, 4),
                new VisitorDemo.Triangle(4, 5));
        shapes.forEach(s -> s.accept(area));
        assertEquals(Math.PI * 4 + 12 + 10, area.total(), 1e-9);
    }

    @Test
    @DisplayName("解释器：递归下降解析器遵循乘除优先、从左到右")
    void interpreterEvaluates() {
        assertEquals(7, new InterpreterDemo.ExpressionParser("1+2*3").parse().interpret());
        assertEquals(5, new InterpreterDemo.ExpressionParser("10-2-3").parse().interpret());
        assertEquals(25, new InterpreterDemo.ExpressionParser("2*3*4+1").parse().interpret());
        // 手工组装表达式树
        InterpreterDemo.Expression expr = new InterpreterDemo.AddExpr(
                new InterpreterDemo.NumberExpr(1),
                new InterpreterDemo.MultiplyExpr(new InterpreterDemo.NumberExpr(2), new InterpreterDemo.NumberExpr(3)));
        assertEquals(7, expr.interpret());
    }
}
