package com.study.bc.mac;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.HexFormat;

import org.bouncycastle.crypto.macs.CMac;
import org.bouncycastle.crypto.engines.AESEngine;
import org.bouncycastle.crypto.params.KeyParameter;

/**
 * CMAC（基于分组密码的消息认证码）演示。
 *
 * <p>CMAC = 用分组密码（如 AES）构造的 MAC，等价于 OMAC1（NIST SP 800-38B）。
 * 相比 HMAC 需要哈希函数，CMAC 只需一个分组密码原语，
 * 常用于嵌入式/受限环境与协议（如 SCTP、TLS 早期版本）。
 *
 * <p>适用场景：已有 AES 硬件加速的智能卡/嵌入式设备、不能引入哈希依赖的受限环境
 * （NIST SP 800-38B 协议）；标签更短（16 字节），适合带宽敏感场景。
 */
public final class CmacDemo {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final HexFormat HEX = HexFormat.of();

    private CmacDemo() {
    }

    /** 计算 AES-CMAC（默认 128 位标签）。 */
    public static byte[] cmac(byte[] key, byte[] data) {
        CMac mac = new CMac(new AESEngine(), 128);
        mac.init(new KeyParameter(key));
        mac.update(data, 0, data.length);
        byte[] out = new byte[mac.getMacSize()];
        mac.doFinal(out, 0);
        return out;
    }

    /** 演示入口。 */
    public static void demo() {
        byte[] key = new byte[16];
        RANDOM.nextBytes(key);
        String msg = "CMAC 消息认证码演示（基于 AES-128）";
        byte[] data = msg.getBytes(StandardCharsets.UTF_8);

        byte[] tag = cmac(key, data);
        byte[] wrongKey = new byte[16];
        RANDOM.nextBytes(wrongKey);
        byte[] wrongTag = cmac(wrongKey, data);

        System.out.println("密钥(16 字节): " + HEX.formatHex(key));
        System.out.println("消息: " + msg);
        System.out.println("AES-CMAC 标签(" + tag.length + " 字节): " + HEX.formatHex(tag));
        System.out.println("错钥标签一致: " + java.util.Arrays.equals(tag, wrongTag));
        System.out.println();
    }
}
