package com.study.designpattern;

/**
 * 设计模式学习模块总入口
 *
 * 运行方式（IDEA 中右键 Run，或命令行）：
 *   mvn compile exec:java -pl module-13-design-patterns -Dexec.mainClass=com.study.designpattern.Main
 *
 * 本模块分三部分：
 *   1. 创建型模式（5 种）：单例 / 工厂方法 / 抽象工厂 / 建造者 / 原型
 *   2. 结构型模式（7 种）：适配器 / 桥接 / 组合 / 装饰器 / 外观 / 享元 / 代理
 *   3. 行为型模式（11 种）：责任链 / 命令 / 迭代器 / 中介者 / 备忘录 / 观察者 /
 *      状态 / 策略 / 模板方法 / 访问者 / 解释器
 * 每个用例类均包含"常用写法 + 不常用写法"。
 */
public class Main {

    public static void main(String[] args) throws Exception {
        System.out.println("========================================");
        System.out.println("  第一部分：创建型模式（5 种）");
        System.out.println("========================================");

        com.study.designpattern.creational.SingletonDemo.main(args);
        System.out.println();

        com.study.designpattern.creational.FactoryMethodDemo.main(args);
        System.out.println();

        com.study.designpattern.creational.AbstractFactoryDemo.main(args);
        System.out.println();

        com.study.designpattern.creational.BuilderDemo.main(args);
        System.out.println();

        com.study.designpattern.creational.PrototypeDemo.main(args);
        System.out.println();

        System.out.println("========================================");
        System.out.println("  第二部分：结构型模式（7 种）");
        System.out.println("========================================");

        com.study.designpattern.structural.AdapterDemo.main(args);
        System.out.println();

        com.study.designpattern.structural.BridgeDemo.main(args);
        System.out.println();

        com.study.designpattern.structural.CompositeDemo.main(args);
        System.out.println();

        com.study.designpattern.structural.DecoratorDemo.main(args);
        System.out.println();

        com.study.designpattern.structural.FacadeDemo.main(args);
        System.out.println();

        com.study.designpattern.structural.FlyweightDemo.main(args);
        System.out.println();

        com.study.designpattern.structural.ProxyDemo.main(args);
        System.out.println();

        System.out.println("========================================");
        System.out.println("  第三部分：行为型模式（11 种）");
        System.out.println("========================================");

        com.study.designpattern.behavioral.ChainOfResponsibilityDemo.main(args);
        System.out.println();

        com.study.designpattern.behavioral.CommandDemo.main(args);
        System.out.println();

        com.study.designpattern.behavioral.IteratorDemo.main(args);
        System.out.println();

        com.study.designpattern.behavioral.MediatorDemo.main(args);
        System.out.println();

        com.study.designpattern.behavioral.MementoDemo.main(args);
        System.out.println();

        com.study.designpattern.behavioral.ObserverDemo.main(args);
        System.out.println();

        com.study.designpattern.behavioral.StateDemo.main(args);
        System.out.println();

        com.study.designpattern.behavioral.StrategyDemo.main(args);
        System.out.println();

        com.study.designpattern.behavioral.TemplateMethodDemo.main(args);
        System.out.println();

        com.study.designpattern.behavioral.VisitorDemo.main(args);
        System.out.println();

        com.study.designpattern.behavioral.InterpreterDemo.main(args);
        System.out.println();

        System.out.println("========================================");
        System.out.println("  第四部分：完整实操示例（可单独运行）");
        System.out.println("========================================");
        System.out.println("  1. 在线商城下单 : com.study.designpattern.practice.OnlineOrderSystemDemo");
        System.out.println("  2. 审批流引擎   : com.study.designpattern.practice.ApprovalWorkflowDemo");
        System.out.println("  3. 文档导出中心 : com.study.designpattern.practice.DocumentExportDemo");
        System.out.println("  详情见 module-13-design-patterns/README.md");
    }
}
