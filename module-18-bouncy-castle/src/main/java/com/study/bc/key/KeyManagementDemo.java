package com.study.bc.key;

import java.io.StringReader;
import java.io.StringWriter;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

import org.bouncycastle.openssl.PEMKeyPair;
import org.bouncycastle.openssl.PEMParser;
import org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter;
import org.bouncycastle.openssl.jcajce.JcaPEMWriter;

import com.study.bc.BcSupport;

/**
 * 密钥编码与转换演示：生成密钥对，并转换为 DER / Base64 / PEM 三种格式。
 *
 * <p>三种格式：
 * <ul>
 *   <li>DER：二进制编码（X.509 公钥 / PKCS#8 私钥），紧凑。</li>
 *   <li>Base64：DER 的文本化，便于 JSON/配置文件传输。</li>
 *   <li>PEM：Base64 + 头尾标记（BEGIN PUBLIC KEY 等），OpenSSL/证书体系标准格式。</li>
 * </ul>
 */
public final class KeyManagementDemo {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final HexFormat HEX = HexFormat.of();

    private KeyManagementDemo() {
    }

    static {
        BcSupport.register(); // JcaPEMKeyConverter 需 BC provider
    }

    /** 生成 RSA 密钥对。 */
    public static KeyPair generateRsa() {
        try {
            KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
            kpg.initialize(2048, RANDOM);
            return kpg.generateKeyPair();
        } catch (Exception e) {
            throw new IllegalStateException("RSA 密钥生成失败", e);
        }
    }

    /** DER 编码（PKCS#8 私钥 / X.509 公钥）。 */
    public static byte[] derEncode(java.security.Key key) {
        return key.getEncoded();
    }

    /** Base64 编码。 */
    public static String base64Encode(byte[] der) {
        return Base64.getEncoder().encodeToString(der);
    }

    /** PEM 编码（使用 BC 的 JcaPEMWriter）。 */
    public static String pemEncode(Object key) {
        try {
            StringWriter sw = new StringWriter();
            try (JcaPEMWriter writer = new JcaPEMWriter(sw)) {
                writer.writeObject(key);
            }
            return sw.toString();
        } catch (Exception e) {
            throw new IllegalStateException("PEM 编码失败", e);
        }
    }

    /** 从 PEM 解析并转换回 JCA 密钥对象（支持公钥/私钥/密钥对任意拼接）。 */
    public static KeyPair pemDecode(String pem) {
        try {
            PublicKey pub = null;
            PrivateKey priv = null;
            try (PEMParser parser = new PEMParser(new StringReader(pem))) {
                JcaPEMKeyConverter converter = new JcaPEMKeyConverter().setProvider("BC");
                Object obj;
                while ((obj = parser.readObject()) != null) {
                    if (obj instanceof PEMKeyPair pkp) {
                        priv = converter.getPrivateKey(pkp.getPrivateKeyInfo());
                        pub = converter.getPublicKey(pkp.getPublicKeyInfo());
                    } else if (obj instanceof org.bouncycastle.asn1.x509.SubjectPublicKeyInfo spi) {
                        pub = converter.getPublicKey(spi);
                    } else if (obj instanceof org.bouncycastle.asn1.pkcs.PrivateKeyInfo pki) {
                        priv = converter.getPrivateKey(pki);
                    }
                }
            }
            if (pub == null || priv == null) {
                throw new IllegalArgumentException("PEM 内容缺少公钥或私钥");
            }
            return new KeyPair(pub, priv);
        } catch (Exception e) {
            throw new IllegalStateException("PEM 解析失败", e);
        }
    }

    /** 演示入口。 */
    public static void demo() {
        KeyPair pair = generateRsa();
        PublicKey pub = pair.getPublic();
        PrivateKey priv = pair.getPrivate();

        System.out.println("RSA-2048 密钥对生成完毕");
        System.out.println();

        byte[] pubDer = derEncode(pub);
        System.out.println("公钥 DER (" + pubDer.length + " 字节): " + HEX.formatHex(pubDer).substring(0, 48) + "...");
        System.out.println("公钥 Base64 (" + base64Encode(pubDer).length() + " 字符): "
                + base64Encode(pubDer).substring(0, 48) + "...");
        System.out.println();

        String pubPem = pemEncode(pub);
        System.out.println("公钥 PEM:");
        System.out.println(pubPem);

        String privPem = pemEncode(priv);
        System.out.println("私钥 PEM 首行: " + privPem.lines().findFirst().orElse(""));

        KeyPair restored = pemDecode(pubPem + privPem);
        System.out.println();
        System.out.println("PEM 解析回密钥对: 公钥一致=" + java.util.Arrays.equals(pubDer, restored.getPublic().getEncoded())
                + ", 私钥一致=" + java.util.Arrays.equals(priv.getEncoded(), restored.getPrivate().getEncoded()));
        System.out.println();
    }
}
