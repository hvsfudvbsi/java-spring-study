package com.study.designpattern.structural;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * 组合模式（Composite）用例（常用 + 不常用）
 *
 * 把对象组织成树形结构，让"单个对象"和"对象组合"使用一致接口（部分-整体）。
 * 适用：文件系统、菜单、组织架构、UI 组件树。
 *
 * 两种设计：
 *   透明组合（推荐演示）：叶子与容器实现同一接口，客户端无差别调用
 *   安全组合        ：只有容器有 add/remove（类型安全，但客户端要判断类型）
 */
public class CompositeDemo {

    /** 统一接口：叶子（文件）和容器（目录）都能 getSize / getName */
    public interface FileSystemNode {
        String getName();

        long size();
    }

    /** 叶子节点：文件 */
    public static final class FileNode implements FileSystemNode {
        private final String name;
        private final long size;

        public FileNode(String name, long size) {
            this.name = name;
            this.size = size;
        }

        public String getName() {
            return name;
        }

        public long size() {
            return size;
        }

        @Override
        public String toString() {
            return name + "(" + size + "B)";
        }
    }

    /** 容器节点：目录（递归包含子节点） */
    public static final class DirectoryNode implements FileSystemNode {
        private final String name;
        private final List<FileSystemNode> children = new ArrayList<>();

        public DirectoryNode(String name) {
            this.name = name;
        }

        public void add(FileSystemNode node) {
            children.add(node);
        }

        public void remove(FileSystemNode node) {
            children.remove(node);
        }

        public List<FileSystemNode> children() {
            return List.copyOf(children);
        }

        public String getName() {
            return name;
        }

        /** 递归求和：目录大小 = 所有子节点之和 */
        public long size() {
            return children.stream().mapToLong(FileSystemNode::size).sum();
        }

        /** 不常用：Stream 递归拍平所有子孙节点（含自己） */
        public List<FileSystemNode> flatten() {
            List<FileSystemNode> result = new ArrayList<>();
            result.add(this);
            children.stream()
                    .flatMap(node -> node instanceof DirectoryNode dir
                            ? dir.flatten().stream()
                            : Stream.of(node))
                    .forEach(result::add);
            return result;
        }

        /** 打印树结构（递归） */
        public void printTree(String indent) {
            System.out.println(indent + "[目录] " + name + "（合计 " + size() + "B）");
            children.forEach(child -> {
                if (child instanceof DirectoryNode dir) {
                    dir.printTree(indent + "  ");
                } else {
                    System.out.println(indent + "  " + child);
                }
            });
        }
    }

    public static void main(String[] args) {
        System.out.println("========== 组合：常用写法（文件系统树） ==========");
        DirectoryNode root = new DirectoryNode("root");
        DirectoryNode src = new DirectoryNode("src");
        DirectoryNode docs = new DirectoryNode("docs");
        root.add(src);
        root.add(docs);
        src.add(new FileNode("Main.java", 1024));
        src.add(new FileNode("Util.java", 512));
        docs.add(new FileNode("readme.md", 2048));
        docs.add(new FileNode("guide.md", 4096));
        root.add(new FileNode("pom.xml", 128));

        root.printTree("");
        System.out.println("  目录总大小（递归求和）= " + root.size() + "B");
        System.out.println("  src 目录大小 = " + src.size() + "B");

        System.out.println();
        System.out.println("========== 组合：不常用写法（Stream 拍平） ==========");
        List<FileSystemNode> all = root.flatten();
        long leafCount = all.stream().filter(n -> !(n instanceof DirectoryNode)).count();
        System.out.println("  flatten 全部节点数 = " + all.size() + "（叶子 " + leafCount + " 个）");
    }
}
