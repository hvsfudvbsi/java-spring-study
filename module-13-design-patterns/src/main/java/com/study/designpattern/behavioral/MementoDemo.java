package com.study.designpattern.behavioral;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * 备忘录模式（Memento）用例（常用 + 不常用）
 *
 * 在不破坏封装的前提下，捕获并保存对象的内部状态，以便之后恢复 —— 快照/存档。
 * 适用：文本编辑器撤销、游戏存档、事务回滚。
 *
 * 角色：发起人(Originator，要保存状态的对象) / 备忘录(Memento，状态快照) /
 *       管理者(Caretaker，存快照)
 * 黑箱封装：备忘录对发起人宽接口、对管理者窄接口（管理者只能存取，不能篡改内容）。
 */
public class MementoDemo {

    /** 备忘录：状态快照（record 天然不可变，管理者无法篡改内容） */
    public record EditorState(String content, int cursor) {
    }

    /** 发起人：文本编辑器 */
    public static final class Editor {
        private String content = "";
        private int cursor = 0;

        public void write(String text) {
            content += text;
            cursor = content.length();
        }

        public void moveCursor(int pos) {
            cursor = Math.max(0, Math.min(content.length(), pos));
        }

        public EditorState save() {
            return new EditorState(content, cursor);   // 生成快照
        }

        public void restore(EditorState state) {
            this.content = state.content();            // 从快照恢复
            this.cursor = state.cursor();
        }

        public String content() {
            return content;
        }

        public int cursor() {
            return cursor;
        }
    }

    /** 管理者：历史记录（存快照、取快照，不知道快照里是什么） */
    public static final class History {
        private final Deque<EditorState> states = new ArrayDeque<>();

        public void push(EditorState state) {
            states.push(state);
            if (states.size() > 10) {   // 只保留最近 10 个快照
                states.removeLast();
            }
        }

        public EditorState pop() {
            return states.isEmpty() ? null : states.pop();
        }

        public int size() {
            return states.size();
        }
    }

    /** 不常用：带版本号的快照（额外元信息，便于展示"历史版本"） */
    public record VersionedState(String version, String content, int cursor) {
    }

    public static void main(String[] args) {
        System.out.println("========== 备忘录：常用写法（编辑器撤销） ==========");
        Editor editor = new Editor();
        History history = new History();

        editor.write("Hello");
        history.push(editor.save());          // 快照1：Hello
        editor.write(", World");
        history.push(editor.save());          // 快照2：Hello, World
        editor.write("!!!");
        System.out.println("  当前内容: \"" + editor.content() + "\"（光标 " + editor.cursor() + "）");

        editor.restore(history.pop());        // 撤销一次
        System.out.println("  撤销1    : \"" + editor.content() + "\"");
        editor.restore(history.pop());        // 再撤销
        System.out.println("  撤销2    : \"" + editor.content() + "\"");
        System.out.println("  历史剩余快照数 = " + history.size());

        System.out.println();
        System.out.println("========== 备忘录：不常用写法（带版本号） ==========");
        VersionedState v1 = new VersionedState("v1", "第一版内容", 5);
        VersionedState v2 = new VersionedState("v2", "第二版内容", 5);
        System.out.println("  版本列表: " + v1.version() + " -> " + v2.version());
        System.out.println("  说明: 快照与命令模式组合可实现多级撤销/重做（编辑器/IDE 的 Ctrl+Z 即此类实现）");
    }
}
