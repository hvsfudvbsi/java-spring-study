package com.study.bc;

import java.security.Security;

/**
 * Bouncy Castle provider 注册工具。
 *
 * <p>底层 API（org.bouncycastle.crypto.*）无需注册；但经 JCE 接口
 * （KeyPairGenerator/Signature/Cipher 的 "BC" provider 名）调用时，
 * 必须先执行 {@link #register()} 把 BC 注册为 JCA provider。
 * Main 与测试统一走本工具，避免重复。
 */
public final class BcSupport {

    private static boolean registered;

    private BcSupport() {
    }

    /** 注册 BC provider（幂等）。 */
    public static synchronized void register() {
        if (!registered) {
            if (Security.getProvider("BC") == null) {
                Security.addProvider(new org.bouncycastle.jce.provider.BouncyCastleProvider());
            }
            registered = true;
        }
    }
}
