package com.study.bc.gm;

import java.nio.charset.StandardCharsets;
import java.util.HexFormat;

import org.bouncycastle.crypto.AsymmetricCipherKeyPair;
import org.bouncycastle.crypto.params.ECPrivateKeyParameters;
import org.bouncycastle.crypto.params.ECPublicKeyParameters;

import com.study.bc.asymmetric.Sm2Demo;
import com.study.bc.hash.HashDemo;
import com.study.bc.symmetric.Sm4Demo;

/**
 * 国密专题：SM2 + SM3 + SM4 组合应用（"数字信封" 场景）。
 *
 * <p>完整流程：
 * <ol>
 *   <li>SM4 对称加密大段数据（快）；</li>
 *   <li>SM2 非对称加密 SM4 密钥（解决密钥分发，慢但只加密短密钥）；</li>
 *   <li>SM3 计算数据摘要（完整性校验）；</li>
 *   <li>SM2 对摘要签名（认证 + 防抵赖）。</li>
 * </ol>
 * 收方用 SM2 私钥解出 SM4 密钥 → SM4 解密数据 → SM3 重算摘要并验签，全链路闭环。
 *
 * <p>适用场景：国密合规的数据传输方案（标准报文、等保/密评场景）——
 * 对称加密大块数据、非对称安全分发密钥、摘要 + 签名保证完整性与不可否认。
 */
public final class GmDemo {

    private static final HexFormat HEX = HexFormat.of();

    private GmDemo() {
    }

    /** 演示入口。 */
    public static void demo() {
        AsymmetricCipherKeyPair pair = Sm2Demo.generateKeyPair();
        ECPublicKeyParameters pub = (ECPublicKeyParameters) pair.getPublic();
        ECPrivateKeyParameters priv = (ECPrivateKeyParameters) pair.getPrivate();

        String msg = "国密数字信封：SM4 加密数据 + SM2 加密密钥 + SM3 摘要 + SM2 签名";
        byte[] data = msg.getBytes(StandardCharsets.UTF_8);
        byte[] sm4Key = Sm4Demo.randomKey();

        System.out.println("国密组合应用（数字信封）");
        System.out.println("SM4 密钥(16 字节): " + HEX.formatHex(sm4Key));
        System.out.println();

        // 1) SM4 加密数据
        byte[] cipher = Sm4Demo.gcmEncrypt(sm4Key, data);
        System.out.println("1) SM4-GCM 加密数据 -> 密文 " + cipher.length + " 字节");

        // 2) SM2 加密 SM4 密钥
        byte[] wrappedKey = Sm2Demo.encrypt(pub, sm4Key);
        System.out.println("2) SM2 加密 SM4 密钥 -> 信封密钥 " + wrappedKey.length + " 字节");

        // 3) SM3 摘要
        byte[] digest = HashDemo.hash("SM3", cipher);
        System.out.println("3) SM3 摘要: " + HEX.formatHex(digest));

        // 4) SM2 签名
        byte[] signature = Sm2Demo.sign(priv, digest);
        System.out.println("4) SM2 签名: " + HEX.formatHex(signature).substring(0, 48) + "... (" + signature.length + " 字节)");
        System.out.println();

        // 收方开拆
        byte[] decryptedKey = Sm2Demo.decrypt(priv, wrappedKey);
        byte[] plain = Sm4Demo.gcmDecrypt(decryptedKey, cipher);
        byte[] reDigest = HashDemo.hash("SM3", cipher);
        System.out.println("开拆验证:");
        System.out.println("  SM2 解出密钥一致: " + java.util.Arrays.equals(decryptedKey, sm4Key));
        System.out.println("  SM4 解出明文一致: " + new String(plain, StandardCharsets.UTF_8).equals(msg));
        System.out.println("  SM3 摘要一致    : " + java.util.Arrays.equals(digest, reDigest));
        System.out.println("  SM2 验签通过    : " + Sm2Demo.verify(pub, digest, signature));
        System.out.println();
    }
}
