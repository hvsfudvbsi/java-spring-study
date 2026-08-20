package com.study.network.tls;

import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.math.BigInteger;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.cert.X509Certificate;
import java.util.Date;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * 实操示例：纯 JDK（SSLSocket）亲眼观察一次 TLS 握手。
 *
 * 与 module-11 的 Netty 版 SslHandshakeDemo 对应，本模块不依赖 Netty，
 * 只用 JDK 原生 Socket：SSLServerSocket + SSLSocket + SSLContext。
 *
 * 功能：
 *   - 开启 JSSE 握手跟踪（javax.net.debug=ssl:handshake），打印 ClientHello → ServerHello
 *     → EncryptedExtensions → Certificate → CertificateVerify → Finished 每一步的真实报文；
 *   - 打印 TLS 1.3 握手步骤注解，便于对照输出逐条理解；
 *   - 握手完成后打印协商结果（协议版本、密码套件、服务端证书），并完成一次回显。
 *
 * 运行：
 *   mvn compile exec:java -pl module-15-network -Dexec.mainClass=com.study.network.tls.TlsHandshakeDemo
 */
public class TlsHandshakeDemo {

    public static void main(String[] args) throws Exception {
        // 必须在任何 JSSE 类初始化之前设置，才能输出每个握手报文的详细跟踪。
        // 想看密钥/随机数等更多细节可改为 "ssl:handshake:verbose"。
        System.setProperty("javax.net.debug", "ssl:handshake");
        printHandshakeGuide();
        System.out.println("======== 开始真实 TLS 握手（注意上方 ssl 开头的握手跟踪日志） ========\n");
        String summary = runDemo();
        System.out.println("\n======== 握手演示完成 ========");
        System.out.println(summary);
        System.exit(0); // 结束后退出，避免非守护线程阻止 JVM 退出
    }

    /**
     * 运行一次真实 TLS 握手（服务端 + 客户端同进程），返回协商结果与回显摘要。
     * 供 main 和测试复用；测试调用时不开启 javax.net.debug，避免污染其他测试输出。
     */
    public static String runDemo() throws Exception {
        // 1. 找一个空闲端口。
        int port;
        try (ServerSocket socket = new ServerSocket(0)) {
            port = socket.getLocalPort();
        }

        // 2. 生成自签名证书并构建服务端 SSLContext（学习用途；生产必须使用正式证书）。
        KeyPair keyPair = generateKeyPair();
        X509Certificate certificate = selfSignedCertificate(keyPair);
        SSLContext serverContext = serverSslContext(keyPair, certificate);

        // 3. 服务端：SSLServerSocket 接受一次连接，打印握手结果，读一行并回显。
        ExecutorService executor = Executors.newSingleThreadExecutor();
        Future<?> serverTask = executor.submit(() -> {
            try (SSLServerSocket serverSocket =
                         (SSLServerSocket) serverContext.getServerSocketFactory().createServerSocket(port)) {
                serverSocket.setSoTimeout(10_000); // 兜底：10 秒没客户端连接就退出，避免挂死
                try (SSLSocket socket = (SSLSocket) serverSocket.accept()) {
                    socket.startHandshake(); // 显式触发握手（不调用也会在首次 IO 时隐式握手）
                    SSLSession session = socket.getSession();
                    System.out.println("  [服务端] TLS 握手成功: 协议=" + session.getProtocol()
                            + ", 密码套件=" + session.getCipherSuite());
                    BufferedReader in = new BufferedReader(
                            new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
                    String line = in.readLine();
                    String response = "TLS echo: " + line;
                    OutputStream out = socket.getOutputStream();
                    out.write((response + System.lineSeparator()).getBytes(StandardCharsets.UTF_8));
                    out.flush();
                    // 等客户端主动关闭（读到 EOF）再关，避免服务端先关导致
                    // 客户端发送 close_notify 时写到已关闭的连接上报 Broken pipe。
                    while (in.readLine() != null) {
                        // 丢弃客户端后续数据（本演示客户端只发一行）
                    }
                }
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });

        // 4. 客户端：SSLSocket 连接，信任所有证书（学习用途），发送一行并读取回显。
        SSLContext clientContext = clientSslContext();
        String clientInfo;
        String echo;
        try (SSLSocket socket =
                     (SSLSocket) clientContext.getSocketFactory().createSocket("127.0.0.1", port)) {
            socket.setSoTimeout(8_000);
            socket.startHandshake();
            SSLSession session = socket.getSession();
            clientInfo = "协议=" + session.getProtocol()
                    + ", 密码套件=" + session.getCipherSuite()
                    + ", 服务端证书=" + peerSubject(session);
            OutputStream out = socket.getOutputStream();
            out.write(("handshake-demo" + System.lineSeparator()).getBytes(StandardCharsets.UTF_8));
            out.flush();
            echo = new BufferedReader(
                    new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8)).readLine();
        }
        serverTask.get(10, TimeUnit.SECONDS);
        executor.shutdownNow();
        return "协商结果: " + clientInfo + "\n回显: " + echo;
    }

    /** 生成 RSA 密钥对（2048 位）。 */
    private static KeyPair generateKeyPair() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        return generator.generateKeyPair();
    }

    /** 用 BouncyCastle 生成一张 CN=localhost 的自签名证书（有效期 1 年）。 */
    private static X509Certificate selfSignedCertificate(KeyPair keyPair) throws Exception {
        long now = System.currentTimeMillis();
        X500Name subject = new X500Name("CN=localhost");
        JcaX509v3CertificateBuilder builder = new JcaX509v3CertificateBuilder(
                subject, new BigInteger(Long.toString(now)),
                new Date(now - 86_400_000L), new Date(now + 365L * 86_400_000L),
                subject, keyPair.getPublic());
        ContentSigner signer = new JcaContentSignerBuilder("SHA256withRSA").build(keyPair.getPrivate());
        return new JcaX509CertificateConverter().getCertificate(builder.build(signer));
    }

    /** 服务端 SSLContext：把自签名证书和私钥装入 KeyStore，由 KeyManager 提供给握手。 */
    private static SSLContext serverSslContext(KeyPair keyPair, X509Certificate certificate) throws Exception {
        KeyStore keyStore = KeyStore.getInstance("PKCS12");
        keyStore.load(null, null);
        keyStore.setKeyEntry("server", keyPair.getPrivate(), new char[0],
                new java.security.cert.Certificate[]{certificate});
        KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        kmf.init(keyStore, new char[0]);
        SSLContext context = SSLContext.getInstance("TLS");
        context.init(kmf.getKeyManagers(), null, null);
        return context;
    }

    /** 客户端 SSLContext：信任所有证书（学习用途，不能用于生产）。 */
    private static SSLContext clientSslContext() throws Exception {
        TrustManager[] trustAll = new TrustManager[]{new X509TrustManager() {
            @Override
            public void checkClientTrusted(X509Certificate[] chain, String authType) {
            }

            @Override
            public void checkServerTrusted(X509Certificate[] chain, String authType) {
            }

            @Override
            public X509Certificate[] getAcceptedIssuers() {
                return new X509Certificate[0];
            }
        }};
        SSLContext context = SSLContext.getInstance("TLS");
        context.init(null, trustAll, null);
        return context;
    }

    /** 读取会话中的服务端证书主体。 */
    private static String peerSubject(SSLSession session) {
        try {
            java.security.cert.Certificate[] certs = session.getPeerCertificates();
            if (certs.length > 0 && certs[0] instanceof X509Certificate x509) {
                return x509.getSubjectX500Principal().getName();
            }
            return "无";
        } catch (Exception e) {
            return "无";
        }
    }

    /** 打印 TLS 1.3 握手步骤注解，与 javax.net.debug 输出逐条对应。 */
    private static void printHandshakeGuide() {
        System.out.println("======== TLS 1.3 握手步骤（下面将打印每一步的真实报文） ========");
        System.out.println("  1. ClientHello          客户端 → 服务器：客户端随机数、支持的 TLS 版本/密码套件、SNI");
        System.out.println("  2. ServerHello          服务器 → 客户端：选定版本/密码套件、服务器随机数");
        System.out.println("  3. EncryptedExtensions  服务器 → 客户端：扩展信息（此后流量加密）");
        System.out.println("  4. Certificate          服务器 → 客户端：服务器证书链（本示例为自签名）");
        System.out.println("  5. CertificateVerify    服务器 → 客户端：私钥签名，证明证书与私钥匹配");
        System.out.println("  6. Finished             服务器 → 客户端：握手消息完整性校验值");
        System.out.println("  7. Finished             客户端 → 服务器：客户端同样发送校验值");
        System.out.println("  8. Application Data     双向：此后业务数据全部加密传输");
        System.out.println();
    }
}
