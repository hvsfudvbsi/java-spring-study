package com.study.designpattern.structural;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;

/**
 * 适配器模式（Adapter）用例（常用 + 不常用）
 *
 * 把一个类的接口转换成客户端期望的另一个接口 —— "转接头"。
 * 适用：接入第三方/遗留系统（老接口改不动）、接口不兼容的系统整合。
 *
 * 两种实现：
 *   对象适配器（推荐）：组合被适配者，灵活、低耦合
 *   类适配器        ：继承被适配者，Java 单继承限制多，不推荐
 */
public class AdapterDemo {

    // ---------- 被适配者：国标插座（220V） ----------
    public static final class ChineseSocket {
        public String supply220V() {
            return "220V 交流电";
        }
    }

    // ---------- 目标接口：欧标插头 ----------
    public interface EuropeanPlug {
        String connect();
    }

    public static final class EuropeanPlugImpl implements EuropeanPlug {
        public String connect() {
            return "欧标插头已插入";
        }
    }

    /** 对象适配器：让"国标插座"能当"欧标插头"用 */
    public static final class SocketAdapter implements EuropeanPlug {
        private final ChineseSocket socket;   // 组合被适配者

        public SocketAdapter(ChineseSocket socket) {
            this.socket = socket;
        }

        @Override
        public String connect() {
            // 内部做转换，客户端无感知
            return "转换头把 [" + socket.supply220V() + "] 转为欧标接口";
        }
    }

    public static void main(String[] args) throws IOException {
        System.out.println("========== 适配器：常用写法（对象适配器） ==========");
        // 客户端只认识 EuropeanPlug，不感知底层是国标插座
        EuropeanPlug plug = new SocketAdapter(new ChineseSocket());
        System.out.println("  " + plug.connect());

        System.out.println();
        System.out.println("========== 适配器：不常用写法（JDK 内置适配器） ==========");
        // 数组 -> List
        String[] array = {"a", "b"};
        List<String> list = Arrays.asList(array);
        System.out.println("  Arrays.asList: 数组 -> List, " + list);

        // List -> Enumeration（遗留代码接口）
        Enumeration<String> enumeration = Collections.enumeration(list);
        System.out.println("  Collections.enumeration: List -> Enumeration, hasMoreElements="
                + enumeration.hasMoreElements());

        // 字节流 -> 字符流（InputStreamReader 是经典适配器）
        InputStream in = new ByteArrayInputStream("你好".getBytes(StandardCharsets.UTF_8));
        Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8);
        char[] buf = new char[2];
        int n = reader.read(buf);
        System.out.println("  InputStreamReader: InputStream -> Reader, 读到 " + new String(buf, 0, n));

        // 函数式适配：方法引用把已有方法"适配"成函数式接口（Comparator 等）
        List<Integer> nums = new ArrayList<>(List.of(3, 1, 2));
        nums.sort(Integer::compareTo);
        System.out.println("  方法引用适配 Comparator: " + nums);
    }
}
