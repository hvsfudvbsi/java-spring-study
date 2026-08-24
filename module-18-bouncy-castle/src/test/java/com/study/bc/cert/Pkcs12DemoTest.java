package com.study.bc.cert;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.security.cert.Certificate;
import java.security.cert.X509Certificate;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class Pkcs12DemoTest {

    private static Pkcs12Demo.Bundle bundle;
    private static byte[] p12;
    private static final String ALIAS = "server";
    private static final char[] PASSWORD = "changeit".toCharArray();

    @BeforeAll
    static void setUp() {
        bundle = Pkcs12Demo.buildBundle();
        p12 = Pkcs12Demo.toPkcs12(ALIAS, bundle.privateKey(), bundle.chain(), PASSWORD);
    }

    @Test
    @DisplayName("PKCS#12 往返：私钥与证书链打包后再读回，字节级一致")
    void roundTrip() throws Exception {
        Pkcs12Demo.Entry entry = Pkcs12Demo.fromPkcs12(p12, ALIAS, PASSWORD);
        assertArrayEquals(bundle.privateKey().getEncoded(), entry.privateKey().getEncoded());
        assertEquals(bundle.chain().length, entry.chain().length);
        for (int i = 0; i < bundle.chain().length; i++) {
            assertArrayEquals(bundle.chain()[i].getEncoded(), entry.chain()[i].getEncoded());
        }
    }

    @Test
    @DisplayName("PKCS#12 读回的证书链仍能通过信任链验证")
    void chainTrustedAfterRoundTrip() {
        Pkcs12Demo.Entry entry = Pkcs12Demo.fromPkcs12(p12, ALIAS, PASSWORD);
        Certificate[] chain = entry.chain();
        assertTrue(chain.length >= 2);
        assertTrue(CertificateDemo.verifyChain((X509Certificate) chain[0],
                (X509Certificate) chain[chain.length - 1]));
    }

    @Test
    @DisplayName("PKCS#12 错误口令：读取被拒绝")
    void wrongPasswordRejected() {
        assertThrows(IllegalStateException.class,
                () -> Pkcs12Demo.fromPkcs12(p12, ALIAS, "wrong-password".toCharArray()));
    }
}
