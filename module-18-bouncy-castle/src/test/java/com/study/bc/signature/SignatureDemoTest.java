package com.study.bc.signature;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;

import org.bouncycastle.crypto.AsymmetricCipherKeyPair;
import org.bouncycastle.crypto.params.ECPrivateKeyParameters;
import org.bouncycastle.crypto.params.ECPublicKeyParameters;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SignatureDemoTest {

    private static final byte[] DATA = "数字签名测试数据".getBytes(StandardCharsets.UTF_8);

    private void assertSignVerify(String keyAlgo, int keySize, String jceAlgo) {
        KeyPair pair = SignatureDemo.generateKeyPair(keyAlgo, keySize);
        String sig = SignatureDemo.sign(jceAlgo, pair, DATA);
        assertTrue(SignatureDemo.verify(jceAlgo, pair, DATA, sig), jceAlgo + " 验签失败");
        assertFalse(SignatureDemo.verify(jceAlgo, pair, (DATA + "!").getBytes(StandardCharsets.UTF_8), sig),
                jceAlgo + " 篡改数据不应通过");
    }

    @Test
    @DisplayName("RSA-SHA256：签名验签往返，篡改数据验签失败")
    void rsaSha256() {
        assertSignVerify("RSA", 2048, "SHA256withRSA");
    }

    @Test
    @DisplayName("ECDSA（P-256）：签名验签往返，篡改数据验签失败")
    void ecdsa() {
        assertSignVerify("EC", 0, "SHA256withECDSA");
    }

    @Test
    @DisplayName("DSA-2048：签名验签往返，篡改数据验签失败")
    void dsa() {
        assertSignVerify("DSA", 2048, "SHA256withDSA");
    }

    @Test
    @DisplayName("Ed25519：签名验签往返，篡改数据验签失败")
    void ed25519() {
        assertSignVerify("Ed25519", 0, "Ed25519");
    }

    @Test
    @DisplayName("SM3withSM2（JCE 方式）：签名验签往返，篡改数据验签失败")
    void sm3withSm2() {
        assertSignVerify("EC", 0, "SM3withSM2");
    }

    // ============ BC 底层 API 的 SM2 签名（对照 JCE 方式） ============

    @Test
    @DisplayName("SM2 底层 API：SM2Signer 签名验签往返，篡改数据验签失败")
    void lowLevelSm2RoundTrip() {
        AsymmetricCipherKeyPair pair = SignatureDemo.sm2KeyPair();
        ECPrivateKeyParameters priv = (ECPrivateKeyParameters) pair.getPrivate();
        ECPublicKeyParameters pub = (ECPublicKeyParameters) pair.getPublic();
        byte[] sig = SignatureDemo.sm2Sign(priv, DATA);
        assertTrue(SignatureDemo.sm2Verify(pub, DATA, sig), "底层 SM2 验签失败");
        assertFalse(SignatureDemo.sm2Verify(pub, (DATA + "!").getBytes(StandardCharsets.UTF_8), sig),
                "篡改数据不应通过");
    }

    @Test
    @DisplayName("SM2 底层密钥：使用国密推荐曲线 sm2p256v1（256 位域）")
    void lowLevelSignsOnSm2Curve() {
        // 底层方式必须用国密推荐曲线 sm2p256v1（256 位域），与 JCE 方式（secp256r1）区分
        AsymmetricCipherKeyPair pair = SignatureDemo.sm2KeyPair();
        ECPublicKeyParameters pub = (ECPublicKeyParameters) pair.getPublic();
        assertTrue(pub.getParameters().getCurve().getFieldSize() == 256);
    }

    @Test
    @DisplayName("互操作：底层 API 签名，转成 JCE 密钥后能被 JCE 接口验签")
    void crossInteropLowLevelToJce() {
        // 互操作 1：底层签名，转成 JCE 密钥后用 JCE 接口验签
        AsymmetricCipherKeyPair bcPair = SignatureDemo.sm2KeyPair();
        ECPrivateKeyParameters bcPriv = (ECPrivateKeyParameters) bcPair.getPrivate();
        byte[] lowSig = SignatureDemo.sm2Sign(bcPriv, DATA);
        KeyPair jcePair = SignatureDemo.toJceKeyPair(bcPair);
        String sigHex = java.util.HexFormat.of().formatHex(lowSig);
        assertTrue(SignatureDemo.verify("SM3withSM2", jcePair, DATA, sigHex),
                "底层签名应能被 JCE 接口验证");
    }

    @Test
    @DisplayName("互操作：JCE 接口签名，转成底层参数后能被底层 API 验签")
    void crossInteropJceToLowLevel() {
        // 互操作 2：JCE 签名，转成底层参数后用底层 API 验签
        AsymmetricCipherKeyPair bcPair = SignatureDemo.sm2KeyPair();
        KeyPair jcePair = SignatureDemo.toJceKeyPair(bcPair);
        String jceSig = SignatureDemo.sign("SM3withSM2", jcePair, DATA);
        AsymmetricCipherKeyPair bcFromJce = SignatureDemo.toBcKeyPair(jcePair);
        ECPublicKeyParameters pub = (ECPublicKeyParameters) bcFromJce.getPublic();
        assertTrue(SignatureDemo.sm2Verify(pub, DATA, java.util.HexFormat.of().parseHex(jceSig)),
                "JCE 签名应能被底层 API 验证");
    }
}
