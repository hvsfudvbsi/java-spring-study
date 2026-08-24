# module-18-bouncy-castle — Bouncy Castle 密码学

> 纯 Java 模块（不依赖 Spring），使用 **Bouncy Castle（bcprov-jdk18on + bcpkix-jdk18on 1.80）**
> 完成常见密码学原语与**国密 SM2/SM3/SM4** 的全套代码 + 测试 + 演示。

## 一、目录结构与算法清单

| 包 | 类 | 覆盖内容 |
|---|---|---|
| `hash` | `HashDemo` | MD5 / SHA-1 / SHA-256 / SHA-3(256) / **SM3** + 加盐哈希 |
| `symmetric` | `AesDemo` | AES-ECB / CBC / **GCM** / CTR（PKCS7 填充、IV、AEAD 标签） |
| `symmetric` | `DesDemo` | DES / 3DES（DESede）CBC |
| `symmetric` | `Sm4Demo` | 国密 SM4-ECB / CBC / GCM |
| `asymmetric` | `RsaDemo` | RSA-2048 PKCS#1 v1.5 / OAEP(SHA-256) |
| `asymmetric` | `EccDemo` | EC P-256 密钥生成、ECDH 协商、ECDSA 签名 |
| `asymmetric` | `Sm2Demo` | 国密 SM2 加解密（C1C3C2）、SM3withSM2 签名 |
| `mac` | `HmacDemo` | HMAC-MD5 / SHA-256 / SHA-512 |
| `mac` | `CmacDemo` | AES-CMAC（基于分组密码的 MAC） |
| `signature` | `SignatureDemo` | RSA-SHA256 / ECDSA / DSA / Ed25519 / **SM3withSM2** |
| `key` | `KeyManagementDemo` | 密钥生成、DER / Base64 / PEM 编码与解析还原 |
| `key` | `KeyAgreementDemo` | DH-2048 / ECDH(P-256) 密钥协商 |
| `gm` | `GmDemo` | 国密专题：SM2+SM3+SM4 **数字信封**全链路 |

测试：`src/test/java/com/study/bc/**` 共 **55 个**（每个算法往返、篡改检测、错钥/错数据拒绝、已知向量）。

## 二、运行方式

```bash
# 运行全部演示（7 个小节）
mvn compile exec:java -pl module-18-bouncy-castle -Dexec.mainClass=com.study.bc.Main

# 单跑某个小节（如国密专题）
mvn compile exec:java -pl module-18-bouncy-castle -Dexec.mainClass=com.study.bc.gm.GmDemo
```

## 三、核心概念

### 1. BC 的两种用法（务必区分）

- **底层 API**（`org.bouncycastle.crypto.*`）：`Digest` / `BlockCipher` / `HMac` / `SM2Engine` 等，
  不依赖 JCA，无需注册 provider，代码中大多演示用这一层（更能体现算法原理）。
- **JCE 接口 + BC provider**：`KeyPairGenerator.getInstance("EC", "BC")`、`Signature.getInstance("SM3withSM2", "BC")` 等，
  必须先注册：`Security.addProvider(new BouncyCastleProvider())`（本模块统一走 `BcSupport.register()`）。

### 2. 哈希与加盐

- 摘要长度：MD5=16、SHA-1=20、SHA-256/SHA3-256/SM3=32、SHA-512=64。
- **MD5/SHA-1 已不安全**（碰撞/长度扩展），仅作对比；密码存储用加盐 + 慢哈希。
- 加盐：16 字节随机盐拼在原文前，存储 `[盐 || 哈希]`，校验时用存储的盐重算并
  `MessageDigest.isEqual` **常数时间比较**。

### 3. 分组密码工作模式

| 模式 | 特点 | 使用注意 |
|---|---|---|
| ECB | 相同明文块→相同密文块，无扩散 | **弃用**（泄露明文统计信息） |
| CBC | 密文链式异或，有扩散 | 需随机 IV，PKCS7 填充 |
| CTR(SIC) | 流密码化，无填充 | 密文长度=明文长度；**IV 不得重复** |
| GCM | 认证加密 AEAD，密文+标签 | 现代首选；标签校验失败=篡改 |

### 4. RSA 填充

- RSA 是数学运算，**必须配填充**。PKCS#1 v1.5 有 Bleichenbacher 攻击，OAEP（RFC 8017）
  是推荐方案。明文上限 = 密钥字节数 − 填充开销（2048 位 + OAEP-SHA256 为 190 字节）。

### 5. SM2（GB/T 32918）

- 基于椭圆曲线（sm2p256v1），对标 ECDSA/ECIES；密钥 256 位。
- 加密输出 **C1C3C2**：C1=曲线点、C3=SM3 杂凑（完整性）、C2=密文。
- 签名 = SM3withSM2，用户身份 ID（默认 `1234567812345678`）参与签名。

### 6. MAC 两类

- **HMAC**：哈希 + 密钥（`H(key⊕opad || H(key⊕ipad || msg))`），抗长度扩展。
- **CMAC**：分组密码（如 AES）构造的 MAC（NIST SP 800-38B），只需一个密码原语。

### 7. 数字签名

私钥签名、公钥验签 → 完整性 + 认证 + 不可否认。本模块覆盖 RSA / ECDSA / DSA / Ed25519 /
国密 SM3withSM2 五种；Ed25519 与 SM3withSM2 需要 BC provider。

### 8. 密钥编码

- **DER**：二进制（X.509/PKCS#8），紧凑。
- **Base64**：DER 文本化，便于 JSON 传输。
- **PEM**：Base64 + `-----BEGIN/END-----` 标记，OpenSSL/证书体系标准；
  读写用 bcpkix 的 `JcaPEMWriter` / `PEMParser` + `JcaPEMKeyConverter`。

### 9. 密钥协商

- DH / ECDH：双方各自生成密钥对 → 交换公钥 → 各自算出**相同共享秘密**（可派生对称密钥）。
- 安全性依赖离散对数 / 椭圆曲线离散对数难题。演示 P-256 ECDH 共享秘密 32 字节。

### 10. 国密数字信封（组合应用）

```
发送方: SM4 加密数据 → SM2 加密 SM4 密钥 → SM3 摘要 → SM2 签名
接收方: SM2 解密钥 → SM4 解数据 → SM3 重算摘要 → SM2 验签
```
对称加密快（大块数据）、非对称加密安全（短密钥分发），各取所长。

## 四、动手练习

1. 给 `AesDemo` 增加 **CBC 密文块翻转攻击演示**：翻转前一块密文某字节，观察下一块明文对应字节被翻转（填充仍可能通过）。
2. 给 `HashDemo` 增加 **SHA-512/224、SHAKE128** 等 SHA-3 家族变体。
3. 给 `RsaDemo` 增加 **PKCS#1 v1.5 填充预言机（Bleichenbacher）模拟**：随机填充解密失败时观察行为差异。
4. 用 `KeyManagementDemo` 生成密钥对并导出 PEM，用 **openssl 命令行**解析对比（`openssl pkey -in key.pem -text`）。
5. 给 `Sm2Demo` 增加 **C1C2C3 模式**（与 C1C3C2 对比）与**不同用户 ID 验签失败**测试。
6. 用 `GmDemo` 思路实现 **SM2 + SM4 的「先签名后加密」** 完整报文协议（可参考 SM2 标准报文格式）。

## 五、验证

- `mvn test -pl module-18-bouncy-castle`：55 个测试全绿。
- 全量 `mvn clean verify`：BUILD SUCCESS、0 checkstyle 违规。
- 实际运行 `Main`：7 个小节全部往返验证通过（哈希向量、AES 四模式、SM2/SM3/SM4 信封、签名五种、PEM 还原、DH/ECDH 协商一致）。
