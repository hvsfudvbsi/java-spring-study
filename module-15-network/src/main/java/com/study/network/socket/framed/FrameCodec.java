package com.study.network.socket.framed;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * 帧编解码器：`[4 字节长度头][UTF-8 内容]` 协议。
 *
 * 为什么需要它：TCP 是字节流，没有消息边界（粘包/拆包）。应用层必须自己定义帧格式。
 * 长度头方案是最通用的做法（Netty 的 LengthFieldBasedFrameDecoder 就是这个思路）：
 *
 * <pre>
 *   +----------------+-------------------------+
 *   | 长度头 (4 字节)  | 内容 (UTF-8, 长度头决定) |
 *   +----------------+-------------------------+
 * </pre>
 *
 * 编码（发送方）：String -> [长度][内容]
 * 解码（接收方）：字节流 -> 按长度头切出完整帧，不足则等待下一批数据
 *
 * FrameDecoder 内部维护累积缓冲，正确处理三种情况：
 * - 粘包：一批数据含多帧 -> 一次产出多帧
 * - 拆包：半帧到达 -> 不产出，等下一批补齐
 * - 长度头本身被拆开 -> 等够 4 字节再读
 */
public class FrameCodec {

    /** 长度头占 4 字节 */
    public static final int LENGTH_HEADER_SIZE = 4;

    /** 单帧最大长度：防止恶意超长帧耗尽内存 */
    public static final int MAX_FRAME_LENGTH = 64 * 1024;

    private FrameCodec() {
    }

    /** 编码：String -> [4 字节长度][UTF-8 内容] */
    public static byte[] encode(String message) {
        byte[] content = message.getBytes(StandardCharsets.UTF_8);
        if (content.length > MAX_FRAME_LENGTH) {
            throw new IllegalArgumentException(
                    "帧超长: " + content.length + " > " + MAX_FRAME_LENGTH);
        }
        byte[] frame = new byte[LENGTH_HEADER_SIZE + content.length];
        // 大端写长度头
        frame[0] = (byte) ((content.length >> 24) & 0xFF);
        frame[1] = (byte) ((content.length >> 16) & 0xFF);
        frame[2] = (byte) ((content.length >> 8) & 0xFF);
        frame[3] = (byte) (content.length & 0xFF);
        System.arraycopy(content, 0, frame, LENGTH_HEADER_SIZE, content.length);
        return frame;
    }

    /** 读取帧长度（用于测试/日志） */
    public static int readLengthHeader(byte[] frame) {
        return ((frame[0] & 0xFF) << 24)
                | ((frame[1] & 0xFF) << 16)
                | ((frame[2] & 0xFF) << 8)
                | (frame[3] & 0xFF);
    }

    /** 提取帧内容（用于测试/日志） */
    public static String readContent(byte[] frame) {
        int length = readLengthHeader(frame);
        return new String(frame, LENGTH_HEADER_SIZE, length, StandardCharsets.UTF_8);
    }

    /**
     * 解码器：累积字节流，按长度头切出完整帧。
     * 每批数据调用一次 decode，返回本批产出的完整帧（可能 0 个或多个）。
     */
    public static class FrameDecoder {

        /** 累积缓冲：存放尚未凑成完整帧的字节 */
        private final ByteArrayOutputStream buffer = new ByteArrayOutputStream();

        /** 喂入一批字节，返回产出的完整帧列表（粘包时一次多帧，半帧时为空） */
        public List<String> decode(byte[] chunk) {
            buffer.write(chunk, 0, chunk.length);
            List<String> frames = new ArrayList<>();
            byte[] all = buffer.toByteArray();
            int offset = 0;

            while (true) {
                // 1. 长度头不足 4 字节：等待下一批
                if (all.length - offset < LENGTH_HEADER_SIZE) {
                    break;
                }
                // 2. 读长度头
                int length = ((all[offset] & 0xFF) << 24)
                        | ((all[offset + 1] & 0xFF) << 16)
                        | ((all[offset + 2] & 0xFF) << 8)
                        | (all[offset + 3] & 0xFF);
                if (length < 0 || length > MAX_FRAME_LENGTH) {
                    throw new IllegalArgumentException("非法帧长度: " + length);
                }
                // 3. 内容不足：整帧还没到齐，等待下一批（不能消费）
                if (all.length - offset - LENGTH_HEADER_SIZE < length) {
                    break;
                }
                // 4. 完整帧：切出来
                String content = new String(all, offset + LENGTH_HEADER_SIZE,
                        length, StandardCharsets.UTF_8);
                frames.add(content);
                offset += LENGTH_HEADER_SIZE + length;
            }

            // 保留未消费的剩余字节（半帧），下次继续
            if (offset > 0) {
                byte[] remaining = new byte[all.length - offset];
                System.arraycopy(all, offset, remaining, 0, remaining.length);
                buffer.reset();
                buffer.write(remaining, 0, remaining.length);
            }
            return frames;
        }
    }
}
