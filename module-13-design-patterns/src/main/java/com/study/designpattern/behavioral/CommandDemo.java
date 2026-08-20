package com.study.designpattern.behavioral;

import java.util.List;
import java.util.Stack;

/**
 * 命令模式（Command）用例（常用 + 不常用）
 *
 * 把"请求"封装成对象，从而支持参数化、排队、记录日志、撤销重做。
 * 适用：遥控器按钮、编辑器撤销、事务（命令日志可重放）。
 *
 * 角色：命令(Command) / 接收者(Receiver，真正干活) / 调用者(Invoker，触发) / 客户端(组装)
 */
public class CommandDemo {

    // ---------- 接收者：电灯 ----------
    public static final class Light {
        private boolean on;

        public void on() {
            on = true;
            System.out.println("    电灯打开 💡");
        }

        public void off() {
            on = false;
            System.out.println("    电灯关闭 🌑");
        }

        public boolean isOn() {
            return on;
        }
    }

    // ---------- 命令接口 ----------
    public interface Command {
        void execute();

        void undo();
    }

    // ---------- 具体命令 ----------
    public static final class LightOnCommand implements Command {
        private final Light light;

        public LightOnCommand(Light light) {
            this.light = light;
        }

        @Override
        public void execute() {
            light.on();
        }

        @Override
        public void undo() {
            light.off();
        }
    }

    public static final class LightOffCommand implements Command {
        private final Light light;

        public LightOffCommand(Light light) {
            this.light = light;
        }

        @Override
        public void execute() {
            light.off();
        }

        @Override
        public void undo() {
            light.on();
        }
    }

    /** 不常用：系统命令（通用的"执行/撤销"文本命令，用于宏命令） */
    public static final class SystemCommand implements Command {
        private final String action;
        private final String undoAction;

        public SystemCommand(String action, String undoAction) {
            this.action = action;
            this.undoAction = undoAction;
        }

        @Override
        public void execute() {
            System.out.println("    " + action);
        }

        @Override
        public void undo() {
            System.out.println("    " + undoAction);
        }
    }

    /** 不常用：宏命令（一次执行/撤销多个命令） */
    public static final class MacroCommand implements Command {
        private final List<Command> commands;

        public MacroCommand(List<Command> commands) {
            this.commands = commands;
        }

        @Override
        public void execute() {
            commands.forEach(Command::execute);
        }

        @Override
        public void undo() {
            // 逆序撤销
            for (int i = commands.size() - 1; i >= 0; i--) {
                commands.get(i).undo();
            }
        }
    }

    // ---------- 调用者：遥控器（支持撤销栈） ----------
    public static final class RemoteControl {
        private Command slot;
        private final Stack<Command> history = new Stack<>();

        public void setCommand(Command command) {
            this.slot = command;
        }

        public void pressButton() {
            slot.execute();
            history.push(slot);
        }

        public void pressUndo() {
            if (!history.isEmpty()) {
                System.out.println("  [撤销]");
                history.pop().undo();
            }
        }
    }

    /** 不常用：函数式命令（Runnable 直接当命令，连命令类都不用写） */
    public static final class SimpleExecutor {
        private final Stack<Runnable> history = new Stack<>();

        public void run(Runnable command) {
            command.run();
            history.push(command);
        }

        public int historySize() {
            return history.size();
        }
    }

    public static void main(String[] args) {
        System.out.println("========== 命令：常用写法（遥控器 + 撤销） ==========");
        Light light = new Light();
        RemoteControl remote = new RemoteControl();

        remote.setCommand(new LightOnCommand(light));
        remote.pressButton();
        remote.setCommand(new LightOffCommand(light));
        remote.pressButton();
        remote.pressUndo();    // 撤销关灯 -> 灯又亮了

        System.out.println();
        System.out.println("========== 命令：不常用写法 ==========");
        // 宏命令：一键"回家模式"（灯 + 空调 + 窗帘，一次执行、一次全部撤销）
        MacroCommand homeMode = new MacroCommand(List.of(
                new LightOnCommand(light),
                new SystemCommand("空调打开 26°C", "空调关闭"),
                new SystemCommand("窗帘关闭", "窗帘打开")));
        RemoteControl home = new RemoteControl();
        home.setCommand(homeMode);
        home.pressButton();
        home.pressUndo();   // 一键全部撤销（逆序）

        // 函数式命令
        SimpleExecutor executor = new SimpleExecutor();
        executor.run(() -> System.out.println("    函数式命令执行"));
        System.out.println("  函数式命令历史条数 = " + executor.historySize());
    }
}
