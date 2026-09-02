package com.study.concurrency.tps;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.random.RandomGenerator;
import java.util.random.RandomGeneratorFactory;

/**
 * 零拷贝演示：FileChannel.transferTo 与「read+write 用户态中转」的对比。
 *
 * <p>学习目标：普通文件拷贝是「磁盘 → 内核缓冲区 → 用户态 byte[] → 内核缓冲区 → 磁盘」，
 * 数据在用户态/内核态之间复制了多遍；而 transferTo 走的是内核态 DMA 通道，
 * 数据不经过用户态缓冲区，省掉多次上下文切换与拷贝，吞吐更高、CPU 占用更低。
 *
 * <p>零拷贝不止文件：Kafka 高吞吐的 sendfile、Netty 的 FileRegion、SocketChannel.transferFrom
 * 都是同一思想的工程化。关键限制：文件大小超过 2GB 需要分多次 transferTo（每次最大 2GB-1）。
 *
 * <p>运行入口：{@link #demo()}（在临时目录生成文件并对比耗时/校验一致性）。
 */
public final class ZeroCopyDemo {

    private ZeroCopyDemo() {
    }

    /**
     * 普通拷贝：逐块 read 进用户态 byte[] 再 write，模拟传统 I/O 路径。
     * 使用缓冲流减少系统调用次数，但数据仍要进出用户态缓冲区。
     */
    public static long copyWithReadWrite(Path from, Path to) throws IOException {
        long bytes = 0;
        try (InputStream in = Files.newInputStream(from, StandardOpenOption.READ);
             OutputStream out = Files.newOutputStream(to,
                     StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING)) {
            byte[] buf = new byte[64 * 1024];
            int n;
            while ((n = in.read(buf)) != -1) {
                out.write(buf, 0, n);
                bytes += n;
            }
        }
        return bytes;
    }

    /**
     * 零拷贝：transferTo 让内核直接把文件数据搬运到目标通道，用户态全程不碰数据。
     *
     * <p>为什么循环调用：单次 transferTo 上限 2GB-1，大文件必须分段；返回值 0 表示底层已达 EOF。
     */
    public static long copyWithTransferTo(Path from, Path to) throws IOException {
        long bytes = 0;
        try (var in = java.nio.channels.FileChannel.open(from, StandardOpenOption.READ);
             var out = java.nio.channels.FileChannel.open(to,
                     StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING)) {
            long position = 0;
            long size = in.size();
            while (position < size) {
                position += in.transferTo(position, size - position, out);
            }
            return position;
        }
    }

    /**
     * 压测对比：生成随机文件 → 两种方式各拷一遍 → 校验内容必须完全一致 → 打印耗时与吞吐。
     * 数据量、磁盘介质不同结果会有波动，重点看「两种方式结果一致」和「transferTo 至少不慢于传统拷贝」。
     */
    public static void demo() throws IOException {
        Path dir = Files.createTempDirectory("zerocopy-demo");
        try {
            Path src = dir.resolve("src.bin");
            writeRandomFile(src, 8 * 1024 * 1024); // 8MB 随机数据

            Path viaIo = dir.resolve("via-io.bin");
            Path viaZc = dir.resolve("via-zc.bin");

            long t0 = System.nanoTime();
            long ioBytes = copyWithReadWrite(src, viaIo);
            long t1 = System.nanoTime();
            long zcBytes = copyWithTransferTo(src, viaZc);
            long t2 = System.nanoTime();

            double ioMs = (t1 - t0) / 1_000_000.0;
            double zcMs = (t2 - t1) / 1_000_000.0;
            long mismatch = Files.mismatch(src, viaIo); // -1 表示完全一致
            long mismatchZc = Files.mismatch(src, viaZc);

            System.out.printf("  文件大小: %.1f MB%n", src.toFile().length() / (1024.0 * 1024));
            System.out.printf("  传统 read/write: %.1f ms，已拷贝 %d 字节%n", ioMs, ioBytes);
            System.out.printf("  transferTo 零拷贝: %.1f ms，已拷贝 %d 字节%n", zcMs, zcBytes);
            System.out.printf("  两份副本内容一致=%b（mismatch 位置: 传统=%d 零拷贝=%d）%n",
                    mismatch == -1 && mismatchZc == -1, mismatch, mismatchZc);
            System.out.printf("  零拷贝提速 %.1f 倍（本机磁盘/缓存情况不同会有波动）%n",
                    ioMs / Math.max(zcMs, 0.0001));
        } finally {
            // 演示与测试都只写临时目录，用后即删，不留垃圾文件
            try (var stream = Files.walk(dir)) {
                stream.sorted(java.util.Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
            }
        }
    }

    /** 生成指定字节数的随机文件，模拟真实数据（压缩率低、不可预测）。 */
    static void writeRandomFile(Path path, long size) throws IOException {
        RandomGenerator random = RandomGeneratorFactory.getDefault().create(42);
        try (var out = Files.newOutputStream(path, StandardOpenOption.CREATE, StandardOpenOption.WRITE)) {
            byte[] buf = new byte[64 * 1024];
            long written = 0;
            while (written < size) {
                long chunk = Math.min(buf.length, size - written);
                random.nextBytes(buf);
                out.write(buf, 0, (int) chunk);
                written += chunk;
            }
        }
    }
}