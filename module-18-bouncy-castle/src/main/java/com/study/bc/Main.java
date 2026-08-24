package com.study.bc;

import com.study.bc.BcSupport;
import com.study.bc.asymmetric.EccDemo;
import com.study.bc.asymmetric.RsaDemo;
import com.study.bc.asymmetric.Sm2Demo;
import com.study.bc.gm.GmDemo;
import com.study.bc.hash.HashDemo;
import com.study.bc.key.KeyAgreementDemo;
import com.study.bc.key.KeyManagementDemo;
import com.study.bc.mac.CmacDemo;
import com.study.bc.mac.HmacDemo;
import com.study.bc.signature.SignatureDemo;
import com.study.bc.symmetric.AesDemo;
import com.study.bc.symmetric.DesDemo;
import com.study.bc.symmetric.Sm4Demo;

/**
 * Bouncy Castle 密码学模块总演示入口。
 *
 * <p>运行（在仓库根目录）：
 * <pre>
 * mvn compile exec:java -pl module-18-bouncy-castle -Dexec.mainClass=com.study.bc.Main
 * </pre>
 *
 * <p>各小节：
 * <ol>
 *   <li>哈希：MD5/SHA-1/SHA-256/SHA-3/SM3 + 加盐</li>
 *   <li>对称加密：AES(ECB/CBC/GCM/CTR)、DES/3DES、SM4</li>
 *   <li>非对称加密：RSA(PKCS1/OAEP)、ECC、SM2</li>
 *   <li>MAC：HMAC、CMAC</li>
 *   <li>数字签名：RSA-SHA256/ECDSA/DSA/Ed25519/SM3withSM2</li>
 *   <li>密钥：PEM/DER/Base64 编码转换、DH/ECDH 协商</li>
 *   <li>国密专题：SM2+SM3+SM4 数字信封</li>
 * </ol>
 */
public final class Main {

    static {
        BcSupport.register();
    }

    private Main() {
    }

    public static void main(String[] args) {
        System.out.println("==========================================");
        System.out.println(" Bouncy Castle 密码学演示 (bcprov-jdk18on "
                + java.security.Security.getProvider("BC").getVersion() + ")");
        System.out.println("==========================================");

        System.out.println("\n[1] 哈希算法");
        HashDemo.demo();

        System.out.println("[2] 对称加密");
        AesDemo.demo();
        DesDemo.demo();
        Sm4Demo.demo();

        System.out.println("[3] 非对称加密");
        RsaDemo.demo();
        EccDemo.demo();
        Sm2Demo.demo();

        System.out.println("[4] MAC 消息认证码");
        HmacDemo.demo();
        CmacDemo.demo();

        System.out.println("[5] 数字签名");
        SignatureDemo.demo();

        System.out.println("[6] 密钥管理与协商");
        KeyManagementDemo.demo();
        KeyAgreementDemo.demo();

        System.out.println("[7] 国密专题");
        GmDemo.demo();

        System.out.println("==========================================");
        System.out.println(" 演示结束：全部算法往返验证通过（见各节输出）");
        System.out.println("==========================================");
    }
}
