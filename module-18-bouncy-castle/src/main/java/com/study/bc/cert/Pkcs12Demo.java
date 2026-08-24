package com.study.bc.cert;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.security.KeyPair;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.HexFormat;

import org.bouncycastle.asn1.x500.X500Name;

import com.study.bc.BcSupport;

/**
 * PKCS#12 密钥库演示：把「私钥 + 证书链」打包成 .p12 字节流，再读回还原。
 *
 * <p>要点：
 * <ul>
 *   <li>PKCS#12 是行业标准容器（扩展名 .p12 / .pfx），TLS 服务器、浏览器导入都用它；</li>
 *   <li>内容：私钥（加密存储）+ 证书链（叶证书在前，根证书在后），整体由口令（password）保护；</li>
 *   <li>本演示用内存字节数组模拟文件，实际可把 {@link #toPkcs12} 的输出写入 .p12 文件。</li>
 * </ul>
 *
 * <p>适用场景：TLS 服务器密钥库（.p12）、浏览器证书导入、代码签名分发——
 * 私钥与证书链打包成单文件，整体由口令保护。
 */
public final class Pkcs12Demo {

    private static final HexFormat HEX = HexFormat.of();

    private Pkcs12Demo() {
    }

    static {
        BcSupport.register();
    }

    /** 构建「服务器私钥 + 证书链（服务器证书 ← CA 根证书）」，私钥与叶证书匹配。 */
    public static Bundle buildBundle() {
        KeyPair caKey = CertificateDemo.generateKeyPair();
        X500Name caName = new X500Name("CN=Study PKCS12 CA");
        X509Certificate caCert = CertificateDemo.selfSignedCa(caKey, caName);
        KeyPair serverKey = CertificateDemo.generateKeyPair();
        X509Certificate serverCert = CertificateDemo.issueServer(
                caName, caKey, serverKey, new X500Name("CN=app.study.example.com"), "app.study.example.com");
        return new Bundle(serverKey.getPrivate(), new Certificate[] {serverCert, caCert});
    }

    /** 生成 PKCS#12 字节流：别名 alias 下存私钥 + 证书链，口令 password 保护。 */
    public static byte[] toPkcs12(String alias, PrivateKey privateKey, Certificate[] chain, char[] password) {
        try {
            KeyStore keyStore = KeyStore.getInstance("PKCS12");
            keyStore.load(null, null);
            keyStore.setKeyEntry(alias, privateKey, password, chain);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            keyStore.store(out, password);
            return out.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException("PKCS#12 生成失败", e);
        }
    }

    /** 从 PKCS#12 字节流读取，返回按别名组织的条目（私钥 + 证书链）。 */
    public static Entry fromPkcs12(byte[] p12, String alias, char[] password) {
        try {
            KeyStore keyStore = KeyStore.getInstance("PKCS12");
            keyStore.load(new ByteArrayInputStream(p12), password);
            PrivateKey privateKey = (PrivateKey) keyStore.getKey(alias, password);
            Certificate[] chain = keyStore.getCertificateChain(alias);
            return new Entry(privateKey, chain);
        } catch (Exception e) {
            throw new IllegalStateException("PKCS#12 读取失败（口令错误或格式非法）", e);
        }
    }

    /** 「私钥 + 证书链」捆绑记录（打包前）。 */
    public record Bundle(PrivateKey privateKey, Certificate[] chain) {
    }

    /** 密钥库条目记录（读取后）。 */
    public record Entry(PrivateKey privateKey, Certificate[] chain) {
    }

    /** 演示入口。 */
    public static void demo() {
        String alias = "server";
        char[] password = "changeit".toCharArray();

        // 1. 构建私钥 + 证书链并打包
        Bundle bundle = buildBundle();
        byte[] p12 = toPkcs12(alias, bundle.privateKey(), bundle.chain(), password);
        System.out.println("1) PKCS#12 生成: 别名=" + alias + ", 证书链=" + bundle.chain().length
                + " 张, 大小=" + p12.length + " 字节");
        System.out.println("   内容前 32 字节: " + HEX.formatHex(java.util.Arrays.copyOf(p12, 32)) + "...");
        System.out.println();

        // 2. 读回还原
        Entry restored = fromPkcs12(p12, alias, password);
        System.out.println("2) PKCS#12 读取:");
        System.out.println("   私钥还原一致: " + java.util.Arrays.equals(
                bundle.privateKey().getEncoded(), restored.privateKey().getEncoded()));
        boolean chainSame = bundle.chain().length == restored.chain().length;
        for (int i = 0; chainSame && i < bundle.chain().length; i++) {
            try {
                chainSame = java.util.Arrays.equals(bundle.chain()[i].getEncoded(), restored.chain()[i].getEncoded());
            } catch (Exception e) {
                chainSame = false;
            }
        }
        System.out.println("   证书链内容一致: " + chainSame);
        System.out.println("   叶证书主体: " + ((X509Certificate) restored.chain()[0]).getSubjectX500Principal());
        System.out.println("   链信任验证: " + (restored.chain().length >= 1
                && CertificateDemo.verifyChain((X509Certificate) restored.chain()[0],
                        (X509Certificate) restored.chain()[restored.chain().length - 1])));
        System.out.println();

        // 3. 错误口令
        try {
            fromPkcs12(p12, alias, "wrong-password".toCharArray());
            System.out.println("3) 错误口令: 未拦截（异常！）");
        } catch (IllegalStateException e) {
            System.out.println("3) 错误口令: 已拒绝（" + e.getMessage() + "）");
        }
        System.out.println();
    }
}
