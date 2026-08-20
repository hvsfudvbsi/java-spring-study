package com.study.network.socket.framed;

import com.study.network.socket.framed.FrameCodec.FrameDecoder;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 帧编解码器单元测试：长度头编码、粘包一次多帧、拆包等待补齐。
 */
class FrameCodecTest {

    @Test
    @DisplayName("编码：帧 = [4 字节长度头][UTF-8 内容]")
    void encodeWritesLengthHeader() {
        String message = "长度头协议";
        byte[] frame = FrameCodec.encode(message);

        byte[] content = message.getBytes(StandardCharsets.UTF_8);
        assertEquals(4 + content.length, frame.length);
        assertEquals(content.length, FrameCodec.readLengthHeader(frame));
        assertEquals(message, FrameCodec.readContent(frame));
    }

    @Test
    @DisplayName("编码：中文多字节时长度头按字节数而非字符数")
    void encodeLengthCountsBytes() {
        String chinese = "中文消息"; // 每个汉字 3 字节
        byte[] frame = FrameCodec.encode(chinese);
        assertEquals(chinese.getBytes(StandardCharsets.UTF_8).length,
                FrameCodec.readLengthHeader(frame));
    }

    @Test
    @DisplayName("解码：完整单帧一次产出")
    void decodeSingleFrame() {
        FrameDecoder decoder = new FrameDecoder();
        List<String> frames = decoder.decode(FrameCodec.encode("hello"));

        assertEquals(List.of("hello"), frames);
    }

    @Test
    @DisplayName("解码粘包：一批数据含多帧，一次全部产出")
    void decodeStickyPackets() {
        FrameDecoder decoder = new FrameDecoder();
        byte[] frame1 = FrameCodec.encode("消息一");
        byte[] frame2 = FrameCodec.encode("消息二");
        byte[] frame3 = FrameCodec.encode("消息三");

        // 三帧拼接后一次到达（粘包）
        byte[] all = concat(frame1, frame2, frame3);
        List<String> frames = decoder.decode(all);

        assertEquals(List.of("消息一", "消息二", "消息三"), frames);
    }

    @Test
    @DisplayName("解码拆包：半帧到达不产出，补齐后产出完整帧")
    void decodeSplitPacket() {
        FrameDecoder decoder = new FrameDecoder();
        byte[] frame = FrameCodec.encode("拆包测试消息");

        // 先到一半：不产出，等待补齐
        int half = frame.length / 2;
        List<String> firstBatch = decoder.decode(Arrays.copyOf(frame, half));
        assertTrue(firstBatch.isEmpty(), "半帧不应产出消息");

        // 补齐剩余：产出完整帧
        List<String> secondBatch = decoder.decode(
                Arrays.copyOfRange(frame, half, frame.length));
        assertEquals(List.of("拆包测试消息"), secondBatch);
    }

    @Test
    @DisplayName("解码：长度头本身被拆成两批到达也能正确处理")
    void decodeSplitLengthHeader() {
        FrameDecoder decoder = new FrameDecoder();
        byte[] frame = FrameCodec.encode("头部被拆");

        // 第一批只到 2 字节（长度头 4 字节都没凑齐）
        List<String> first = decoder.decode(Arrays.copyOf(frame, 2));
        assertTrue(first.isEmpty(), "长度头不足不应产出");

        // 第二批补上剩余全部
        List<String> second = decoder.decode(Arrays.copyOfRange(frame, 2, frame.length));
        assertEquals(List.of("头部被拆"), second);
    }

    @Test
    @DisplayName("解码：拆包 + 粘包混合，分批到达仍能正确拆帧")
    void decodeMixedSplitAndSticky() {
        FrameDecoder decoder = new FrameDecoder();
        byte[] frameA = FrameCodec.encode("AAA");
        byte[] frameB = FrameCodec.encode("BBBBBBBB");

        // 第一批：A 的完整帧 + B 的一半（粘包 + 拆包混合）
        int firstLen = frameA.length + frameB.length / 2;
        byte[] first = concat(frameA, Arrays.copyOf(frameB, frameB.length / 2));
        assertEquals(List.of("AAA"), decoder.decode(first));

        // 第二批：B 的剩余一半
        byte[] rest = Arrays.copyOfRange(frameB, frameB.length / 2, frameB.length);
        assertEquals(List.of("BBBBBBBB"), decoder.decode(rest));
    }

    @Test
    @DisplayName("解码：连续解码累积正确，解码器有状态但无泄漏")
    void decoderKeepsStateAcrossBatches() {
        FrameDecoder decoder = new FrameDecoder();
        byte[] frame1 = FrameCodec.encode("第一帧");
        byte[] frame2 = FrameCodec.encode("第二帧");

        // 分批喂：第一批是 frame1 的一半，第二批是 frame1 剩余 + frame2 全部
        List<String> batch1 = decoder.decode(Arrays.copyOf(frame1, 4));
        assertTrue(batch1.isEmpty());

        byte[] rest1 = Arrays.copyOfRange(frame1, 4, frame1.length);
        List<String> batch2 = decoder.decode(concat(rest1, frame2));
        assertEquals(List.of("第一帧", "第二帧"), batch2);
    }

    @Test
    @DisplayName("解码：非法超长帧长度被拒绝")
    void decodeRejectsOversizedFrame() {
        FrameDecoder decoder = new FrameDecoder();
        // 长度头声明 1MB，超过 MAX_FRAME_LENGTH
        byte[] malicious = new byte[]{(byte) 0x00, (byte) 0x10, (byte) 0x00, (byte) 0x00};
        assertThrows(IllegalArgumentException.class, () -> decoder.decode(malicious));
    }

    private byte[] concat(byte[]... arrays) {
        int total = 0;
        for (byte[] array : arrays) {
            total += array.length;
        }
        byte[] result = new byte[total];
        int offset = 0;
        for (byte[] array : arrays) {
            System.arraycopy(array, 0, result, offset, array.length);
            offset += array.length;
        }
        return result;
    }
}
