package com.study.concurrency.tps;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ZeroCopyDemoTest {

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("传统 read/write 拷贝: 字节数与源一致，内容逐字节相同")
    void readWriteCopyPreservesContent() throws Exception {
        Path src = tempDir.resolve("src.bin");
        ZeroCopyDemo.writeRandomFile(src, 2 * 1024 * 1024);
        Path dst = tempDir.resolve("dst.bin");

        long bytes = ZeroCopyDemo.copyWithReadWrite(src, dst);
        assertEquals(src.toFile().length(), bytes);
        assertEquals(-1, Files.mismatch(src, dst), "-1 表示两份文件逐字节一致");
    }

    @Test
    @DisplayName("transferTo 零拷贝: 字节数与源一致，内容逐字节相同")
    void transferToCopyPreservesContent() throws Exception {
        Path src = tempDir.resolve("src.bin");
        ZeroCopyDemo.writeRandomFile(src, 2 * 1024 * 1024);
        Path dst = tempDir.resolve("dst.bin");

        long bytes = ZeroCopyDemo.copyWithTransferTo(src, dst);
        assertEquals(src.toFile().length(), bytes);
        assertEquals(-1, Files.mismatch(src, dst));
    }

    @Test
    @DisplayName("零拷贝不慢于传统拷贝: 8MB 文件 transferTo 耗时不超过 read/write 的 3 倍")
    void transferToNotSlower() throws Exception {
        Path src = tempDir.resolve("src.bin");
        ZeroCopyDemo.writeRandomFile(src, 8 * 1024 * 1024);

        long t0 = System.nanoTime();
        ZeroCopyDemo.copyWithReadWrite(src, tempDir.resolve("a.bin"));
        long t1 = System.nanoTime();
        ZeroCopyDemo.copyWithTransferTo(src, tempDir.resolve("b.bin"));
        long t2 = System.nanoTime();

        double ioMs = (t1 - t0) / 1_000_000.0;
        double zcMs = (t2 - t1) / 1_000_000.0;
        assertTrue(zcMs < ioMs * 3,
                "transferTo 不应显著慢于 read/write（io=" + ioMs + "ms, zc=" + zcMs + "ms）");
    }
}