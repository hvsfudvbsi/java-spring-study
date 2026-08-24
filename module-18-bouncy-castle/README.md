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
| `signature` | `SignatureDemo` | RSA-SHA256 / ECDSA / DSA / Ed25519 / **SM3withSM2**（JCE 与 BC 底层 API 双实现 + 互操作） |
| `key` | `KeyManagementDemo` | 密钥生成、DER / Base64 / PEM 编码与解析还原 |
| `key` | `KeyAgreementDemo` | DH-2048 / ECDH(P-256) 密钥协商 |
| `gm` | `GmDemo` | 国密专题：SM2+SM3+SM4 **数字信封**全链路 |
| `cert` | `CertificateDemo` | X.509 证书：CA 自签名根证书、签发服务器证书、信任链/有效期/签名验证 |
| `cert` | `Pkcs12Demo` | PKCS#12 密钥库：私钥+证书链打包（.p12 字节流）、读回还原、口令保护 |
| `cms` | `CmsDemo` | CMS（RFC 5652）：数字信封 EnvelopedData（AES-CBC + RSA 密钥封装）、PKCS#7 签名（**attach 内嵌 / detach 分离**）、**PEM 导出 .p7m/.p7s/.p7e + openssl 命令行验证** |
| `p10` | `CsrDemo` | PKCS#10（P10）：**CSR 构建**（Subject+公钥+SAN 扩展）、验签、**CA 基于 CSR 签发证书** |

测试：`src/test/java/com/study/bc/**` 共 **87 个**（每个算法往返、篡改检测、错钥/错数据拒绝、已知向量、证书链/密钥库、SM2 底层/互操作、CBC 块翻转、CMS 信封与签名、PKCS#10 CSR、PEM 导出往返）。

## 二、运行方式

```bash
# 运行全部演示（10 个小节）
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
| CBC | 密文链式异或，有扩散 | 需随机 IV，PKCS7 填充；**无认证，可被块翻转攻击** |
| CTR(SIC) | 流密码化，无填充 | 密文长度=明文长度；**IV 不得重复** |
| GCM | 认证加密 AEAD，密文+标签 | 现代首选；标签校验失败=篡改 |

**CBC 密文块翻转攻击**（`AesDemo.cbcBitFlip`，见演示）：

- 原理：解密 `P[i] = D(C[i]) XOR C[i-1]`，翻转 `C[i-1]` 的某字节会让 `P[i]` 对应字节
  异或同样的 delta——**攻击者能精确改写目标字节**（如把 `role=0` 改成 `role=1`、金额 1000 改 9000）；
- 副作用：被翻转的 `C[i-1]` 自身解出的前一块变乱码（所以目标字段要放在后一块）；
  翻转 IV 只影响第一块、无副作用；PKCS7 填充仍可能校验通过（静默篡改）；
- 对策：用 GCM 等 AEAD 模式——同样翻转会被认证标签直接拒绝（演示中对照验证）。

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

**SM2 签名双实现对照**（`SignatureDemo`）：

- **JCE 方式**：`Signature.getInstance("SM3withSM2")`，经 BC provider（演示用 secp256r1 密钥）。
- **BC 底层 API**：`SM2Signer` + `SM3Digest` 直接驱动算法核心，密钥用国密推荐曲线 **sm2p256v1**（`GMNamedCurves`）。
- **互操作验证**：两种方式签名格式兼容（都是 DER 编码 (r, s)，默认用户 ID `1234567812345678` 一致），
  底层签名可转 JCE 密钥验签、JCE 签名可转底层参数验签（`toJceKeyPair`/`toBcKeyPair` 双向转换：
  `EC5Util.convertToSpec`/`convertPoint` + `PrivateKeyInfoFactory`/`ECUtil.generatePublicKeyParameter`）。

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

### 11. X.509 证书与 PKCS#12

- **签发**：`JcaX509v3CertificateBuilder`（bcpkix）构建证书——Subject/Issuer DN、序列号、有效期、
  BasicConstraints（CA 标志）、SAN 域名扩展，`JcaContentSignerBuilder("SHA256withRSA")` 签名，
  `JcaX509CertificateConverter` 转 JCA 证书。
- **验证三件事**：① 签名校验（用**签发者**公钥验签——服务器证书的签名者是 CA，不是它自己）；
  ② 有效期 `checkValidity()`；③ 信任链 `CertPathValidator` + TrustAnchor（PKIX 算法）。
- **PKCS#12**：行业标准容器（.p12/.pfx），TLS 服务器与浏览器导入都用它；`KeyStore.getInstance("PKCS12")`
  + `setKeyEntry(alias, 私钥, 口令, 证书链)` 打包，读回时按别名取私钥与证书链，整体由口令保护。
- 注意：签发服务器证书时 Issuer DN 应直接复用 CA 证书的原始 Subject（X500Name 对象），
  避免「DN 字符串 → 再解析」往返改变编码导致 PKIX 链匹配失败。

### 12. CMS 数字信封与 PKCS#7 签名（`cms/CmsDemo`）

- **SignedData（PKCS#7 签名）**：`CMSSignedDataGenerator` + `JcaSignerInfoGeneratorBuilder`。
  **attach（附件）**：`generate(data, true)` 把原文内嵌进签名（.p7m，验证无需另带原文）；
  **detach（分离）**：`generate(data, false)` 只给签名（.p7s/.sig，原文另行传输，验证时
  `new CMSSignedData(原文, 签名)` 关联）。验签用 `SignerInformation.verify(JcaSimpleSignerInfoVerifierBuilder)`。
- **EnvelopedData（数字信封）**：`CMSEnvelopedDataGenerator` 随机生成对称密钥（AES-128-CBC）加密数据，
  再用收件人 RSA 公钥封装该对称密钥（key transport），收件人私钥
  `JceKeyTransEnvelopedRecipient` 解封——与国密 `GmDemo` 的 SM2+SM3+SM4 信封是同一思想，这里是标准 CMS 格式。
  （内容加密用 CBC 而非 GCM：GCM 的 AEAD 参数在 openssl smime -decrypt 下报 cipher parameter error，CBC 是 S/MIME 常规、互操作最稳。）
- 适用：S/MIME 邮件签名/加密、PDF/代码签名（attach）、软件发布签名（detach .sig）、数据安全交换。

**PEM 导出 + openssl 命令行验证**（demo 第 4/5 节，环境有 openssl 时自动执行）：

- 导出到 `target/cms/`：`signed-attached.p7m`（attach，原文内嵌）、`signed-detached.p7s`（detach）、
  `enveloped.p7e`（信封），以及配套的 `original.txt`、`signer.pem`、`recipient.pem`、`recipient-key.pem`（PKCS#8）。
- 对应 openssl 命令（自签名证书用 `-noverify` 跳过证书链，签名本身仍会验证）：

```bash
# attach 验签（内容内嵌，无需 -content）
openssl smime -verify -in target/cms/signed-attached.p7m -inform PEM -certfile target/cms/signer.pem -noverify -out /dev/null
# detach 验签（必须 -content 指定原文）
openssl smime -verify -in target/cms/signed-detached.p7s -inform PEM -content target/cms/original.txt -certfile target/cms/signer.pem -noverify -out /dev/null
# 信封解密（收件人私钥）
openssl smime -decrypt -in target/cms/enveloped.p7e -inform PEM -recip target/cms/recipient.pem -inkey target/cms/recipient-key.pem -out target/cms/decrypted.txt
```

运行 `Main` 第 [9] 节会自动执行这三条命令并打印结果（attach/detach 均 Verification successful、
解密内容与原文一致），证明 BC 生成的 CMS 与 openssl 双向互操作。

### 13. PKCS#10（CSR / P10）与证书签发（`p10/CsrDemo`）

真实 PKI 的「申请证书」一环（对应 openssl `req -new` 与 `x509 -req`）：

```
申请人: 自持私钥 -> 私钥签名「Subject DN + 公钥 + SAN 扩展」-> 生成 CSR（P10）
CA:     用 CSR 内嵌公钥验签（证明申请人持有私钥）-> 用 CSR 的 Subject/公钥签发 X.509 证书
```

- **CSR 构建**：`JcaPKCS10CertificationRequestBuilder(X500Name, 公钥)` + `JcaContentSignerBuilder` 签名；
  扩展放在 **extensionRequest 属性**（PKCS#9 OID `1.2.840.113549.1.9.14`）里：
  `ExtensionsGenerator` 生成后 `addAttribute(PKCSObjectIdentifiers.pkcs_9_at_extensionRequest, exts)`，
  读取用 `csr.getRequestedExtensions()`。
- **验签**：`csr.isSignatureValid(JcaContentVerifierProviderBuilder)`——用 CSR 内嵌公钥验签，
  证明申请人**确实持有对应私钥**（换公钥/篡改字节都验不过）。
- **证书签发**：CA 用 `JcaX509v3CertificateBuilder` 取 CSR 的 Subject/公钥（沿用请求的 SAN 扩展）
  签发证书返回申请人。私钥全程不出申请人本机。

## 四、动手练习

1. ~~给 `AesDemo` 增加 **CBC 密文块翻转攻击演示**~~（已完成：`cbcBitFlip` 方法 + demo 演示 role=0→1 与 GCM 对照）→ 延伸：把攻击封装成 `CbcBitFlipAttack` 工具类，支持按**明文偏移**定位翻转（而非手算块/字节索引）。
2. 给 `HashDemo` 增加 **SHA-512/224、SHAKE128** 等 SHA-3 家族变体。
3. 给 `RsaDemo` 增加 **PKCS#1 v1.5 填充预言机（Bleichenbacher）模拟**：随机填充解密失败时观察行为差异。
4. 用 `KeyManagementDemo` 生成密钥对并导出 PEM，用 **openssl 命令行**解析对比（`openssl pkey -in key.pem -text`）。
5. 给 `Sm2Demo` 增加 **C1C2C3 模式**（与 C1C3C2 对比）与**不同用户 ID 验签失败**测试。
6. 用 `GmDemo` 思路实现 **SM2 + SM4 的「先签名后加密」** 完整报文协议（可参考 SM2 标准报文格式）。
7. 给 `CertificateDemo` 增加 **二级 CA 链**：根 CA 签发中间 CA，中间 CA 再签发服务器证书（验证时需构建完整证书链而非单 TrustAnchor）。
8. 给 `Pkcs12Demo` 增加**写文件版本**：把 `toPkcs12` 输出写入 `.p12` 文件，再用 `keytool -list` 或 openssl 命令行读取验证。
9. 给 `CertificateDemo` 增加 **证书吊销（CRL/OCSP）** 演示：签发吊销列表并让 PKIX 验证拒绝已吊销证书。
10. 给 `SignatureDemo` 的底层 SM2 签名增加 **原始 (r, s) 输出**（非 DER 包装）：用 `SM2Signer` 的 `PlainDERTBCObject`/手工拆分，对比 JCE 输出格式差异。
11. 给 `CsrDemo` 增加 **PEM 输出版本**：CSR 与签发的证书导出 PEM（`BEGIN CERTIFICATE REQUEST`），用 `openssl req -verify -in csr.pem` 与 `openssl x509 -text -in cert.pem` 命令行对照验证。
12. 给 `CmsDemo` 增加 **CMS 国密版**：用 SM2/SM3/SM4 构造 SignedData/EnvelopedData（对照 `GmDemo` 的手工信封，验证与标准 CMS 格式的异同）。

## 五、算法使用场景（选型指南）

> 选型三步：① 明确要解决的问题（机密性 / 完整性 / 认证 / 不可否认）；② 看合规约束（是否要求国密 SM2/SM3/SM4）；③ 看运行环境（是否有 AES 硬件加速、密钥长度限制等）。
> 独立速查文档见 [docs/crypto-cheatsheet.md](../../docs/crypto-cheatsheet.md)（含 HMAC vs CMAC 对照与一分钟决策表）。

| 算法 | 适用场景 | 典型应用 |
|---|---|---|
| MD5 / SHA-1 | 仅做非安全校验/兼容旧系统（已可碰撞，**禁用于安全场景**） | 文件校验、旧协议兼容 |
| SHA-256 | 通用完整性校验、签名前摘要 | 文件/镜像校验、Git、HTTPS 证书摘要、区块链 |
| SHA-3(256) | 新一代标准（Keccak），结构独立于 SHA-2，抗长度扩展 | 新系统选型、对未来哈希攻击的防御纵深 |
| SM3 | 国密标准摘要（256 位），中国金融/政务合规 | 国密 TLS、SM2 签名配套、密评/等保合规系统 |
| AES-GCM | **认证加密首选**：机密性 + 完整性一次完成 | TLS 1.2/1.3、现代 API/数据库字段加密 |
| AES-CBC | 仅遗留：无认证，须另配 MAC 或防块翻转 | 旧文件加密格式（配合 HMAC）、遗留系统 |
| AES-CTR | 流式加密、可并行计算；**IV 严禁重复** | 磁盘/存储加密（配合 HMAC）、高速数据流 |
| AES-ECB | 不应用于加密（泄露明文统计信息） | 仅教学对照 |
| DES / 3DES | 遗留兼容（3DES 仍在部分支付/EMV 使用，正逐步退役） | 旧金融系统、读卡器兼容 |
| SM4 | 国密对称加密标准（128 位分组/密钥） | 国密 TLS 数据加密、金融数据加密、无线局域网 |
| RSA | 短数据加解密、签名、证书体系 | HTTPS 证书、密钥封装（key transport）、代码签名 |
| ECC（ECDH/ECDSA） | 同安全性下密钥更短（256 位 ≈ RSA-3072），移动/IoT 友好 | 区块链、EC 证书（TLS）、物联网设备 |
| SM2 | 国密非对称（对标 ECDSA/ECIES，曲线 sm2p256v1） | 国密证书、金融身份认证、电子签章（GB/T 32918） |
| HMAC | 共享密钥下的消息认证/完整性，抗长度扩展 | API 签名（HMAC-SHA256）、JWT(HS256)、HOTP/TOTP 动态口令 |
| CMAC | 基于分组密码的 MAC：已有 AES 硬件/智能卡、无哈希依赖 | 嵌入式/智能卡、NIST SP 800-38B、受限设备消息认证 |
| DSA | 遗留政府签名标准 | 旧系统、合规遗留 |
| Ed25519 | 现代签名：快、密钥小、抗侧信道、实现不易出错 | SSH 密钥、区块链、新协议签名 |
| DH / ECDH | 双方协商共享秘密（密钥不落地传输） | TLS 密钥交换（DHE/ECDHE）、端到端加密（Signal） |
| SM2+SM3+SM4 信封 | 对称加密大块数据 + 非对称安全分发密钥 + 签名认证 | 国密标准报文、数据安全传输合规方案 |
| X.509 证书 | 把公钥绑定到身份并构建信任链 | HTTPS 服务器证书、代码签名、S/MIME 邮件 |
| PKCS#12 | 私钥 + 证书链的打包容器（.p12/.pfx） | TLS 服务器密钥库、浏览器证书导入、代码签名分发 |
| CMS SignedData（PKCS#7） | 内容签名：attach 内嵌原文 / detach 分离签名 | S/MIME 邮件签名、PDF/代码签名、软件发布 .sig |
| CMS EnvelopedData | 数字信封：对称加密数据 + 公钥封装对称密钥 | S/MIME 邮件加密、数据安全交换 |
| PKCS#10（CSR/P10） | 证书申请：私钥自证持有 + CA 签发证书 | 证书申请自动化、企业 PKI 签发流程 |

### HMAC vs CMAC 怎么选

- **HMAC**：哈希 + 密钥（`H(key⊕opad ‖ H(key⊕ipad ‖ msg))`）。软件实现简单、平台普遍支持，标签长度 = 哈希输出（32/64 字节）。适合 **API 签名、JWT、动态口令**等网络/服务端场景（密钥是共享秘密，双端都能算）。
- **CMAC**：分组密码（AES）构造的 MAC（NIST SP 800-38B），标签长度 = 分组大小（16 字节）。适合**已有 AES 硬件加速 / 智能卡 / 嵌入式**等受限环境——不引入哈希依赖，加解密与认证可用同一个分组密码原语。

## 六、测试用例与守护场景

> 85 个测试按包分组，每一行回答「这个用例在守护什么场景」。

### hash — 哈希（6 个）

| 测试 | 守护场景 |
|---|---|
| 已知向量：空串 MD5/SHA-1/SHA-256 与标准值一致 | 算法实现与 RFC 标准对齐，防回归 |
| 摘要长度：16/20/32 字节 | 下游按长度分配存储/比对（数据库字段、协议字段） |
| 确定性：同输入同摘要、不同输入不同 | 哈希基本性质：校验一致性与区分性 |
| 未知算法快速失败 | 配置错误立即暴露，不静默出错 |
| 加盐哈希：正确/错误密码 | **密码存储**：库泄露后加盐哈希难以批量逆推 |
| 加盐随机性：同密码两次结果不同 | 防止相同密码哈希相同被识别（防撞库） |

### symmetric — AES（14 个）

| 测试 | 守护场景 |
|---|---|
| ECB/CBC/CTR/GCM 往返 | 四种模式加解密正确性 |
| CBC：相同明文两次密文不同 | IV 随机 → 防重放/模式识别 |
| CTR：非 16 倍数明文无需填充 | 流模式特性：任意长度数据流 |
| GCM 篡改拒绝 | AEAD 完整性：密文被改立即拒绝（生产首选） |
| CBC 错钥拒绝 | 密钥不匹配快速失败，不静默解出乱码 |
| 块翻转：role=0→1 | **CBC 无认证可被精确篡改**：生产禁用裸 CBC |
| 块翻转副作用：前块变乱码 | 理解攻击可行性边界（目标字段放后一块） |
| 翻转 IV 只影响第一块 | IV 属于密文一部分，需一并保护/认证 |
| GCM 对照：同样翻转被拒 | 选型依据：AEAD 自带防篡改 |
| 越界：块/字节索引非法拒绝 | 攻击工具类健壮性（防御非法输入） |
| 非法格式：非 [IV‖16 倍数] 密文拒绝 | 防御畸形密文输入 |

### symmetric — DES/3DES（3 个）

| 测试 | 守护场景 |
|---|---|
| DES 8 字节密钥往返 | 遗留 56 位密钥算法正确性（仅兼容） |
| 3DES 24 字节密钥往返 | 3DES（112 位有效）支付/遗留兼容 |
| 错钥拒绝 | 密钥不匹配快速失败 |

### symmetric — SM4（6 个）

| 测试 | 守护场景 |
|---|---|
| 密钥必须 16 字节 | 国密标准固定 128 位密钥 |
| ECB/CBC/GCM 往返 | 国密合规系统数据加密正确性 |
| GCM 篡改拒绝 | 国密 AEAD 防篡改 |
| CBC 错钥拒绝 | 密钥不匹配快速失败 |

### asymmetric — RSA（6 个）

| 测试 | 守护场景 |
|---|---|
| 模数 2048 位 | 密钥强度底线（现代要求 ≥2048） |
| PKCS#1 v1.5 往返 | 遗留填充兼容（了解 Bleichenbacher 风险） |
| OAEP 往返 | 推荐填充正确性 |
| OAEP 随机化 | 相同明文两次密文不同，防重放 |
| 错私钥拒绝 | 密钥不匹配快速失败 |
| 超长明文（>190 字节）拒绝 | 填充开销上限：大数据须分段或用混合加密 |

### asymmetric — ECC（4 个）

| 测试 | 守护场景 |
|---|---|
| ECDH 共享秘密一致 | 协商正确性（双方算出相同密钥） |
| ECDSA 往返 | 签名正确性 |
| ECDSA 篡改拒绝 | 完整性 + 认证 |
| ECDSA 错公钥拒绝 | 防伪冒：他人公钥无法验签 |

### asymmetric — SM2（4 个）

| 测试 | 守护场景 |
|---|---|
| 加解密往返 | 国密非对称（C1C3C2）正确性 |
| 加密随机性（C1 随机点） | 相同明文两次密文不同，防重放 |
| 签名往返 | SM3withSM2 签名正确性 |
| 篡改拒绝 | 数据被改验签失败 |

### mac — HMAC/CMAC（7 个）

| 测试 | 守护场景 |
|---|---|
| HMAC 标签长度 16/32/64 | 与所选哈希输出一致，下游存储规划 |
| HMAC 确定性 / 错钥 | API 签名：同输入同 MAC、密钥不匹配认证失败 |
| HMAC 未知算法快速失败 | 配置错误立即暴露 |
| CMAC 标签 16 字节 | 受限设备/智能卡：短标签、无需哈希依赖 |
| CMAC 确定性 / 错钥 | 分组密码 MAC 的认证一致性 |

### signature — 数字签名（9 个）

| 测试 | 守护场景 |
|---|---|
| RSA/ECDSA/DSA/Ed25519/SM3withSM2 往返+篡改 | 五种签名算法正确性（完整性+认证+不可否认） |
| SM2 底层 API 往返 | BC 底层实现正确性（不依赖 JCE） |
| sm2p256v1 曲线断言 | 国密推荐曲线参数正确 |
| 底层↔JCE 双向互操作 | 两种实现签名格式兼容（DER (r,s)），可跨实现验签 |

### key — 密钥编码与协商（5 个）

| 测试 | 守护场景 |
|---|---|
| PEM 往返字节级一致 | 与 OpenSSL/证书体系互操作 |
| PEM BEGIN/END 标记 | 第三方工具可识别 |
| DER/Base64 一致 | Base64 是 DER 的文本化，可无损互转（JSON 传输） |
| DH/ECDH 协商一致 | 密钥协商正确性（共享秘密相同） |

### cert — 证书与 PKCS#12（8 个）

| 测试 | 守护场景 |
|---|---|
| CA 自签名（Subject=Issuer） | 根证书自验签正确性 |
| 服务器证书签发（SAN） | 链式签发：用 CA 公钥验签、域名扩展正确 |
| 信任链通过 / 无关 CA 拒绝 | **PKIX 验证**：只信任受信根签发的链 |
| 过期证书拒绝 | 有效期检查（checkValidity） |
| 无关公钥验签失败 | 防伪冒：必须用签发者公钥验签 |
| PKCS#12 往返 | 密钥库打包/读回字节级一致 |
| PKCS#12 读回链验证 | 打包的证书链仍可信 |
| PKCS#12 错误口令拒绝 | 密钥库口令保护有效 |

### gm — 国密信封（1 个）

| 测试 | 守护场景 |
|---|---|
| 信封冒烟：SM2+SM3+SM4 全链路 | 国密合规方案基线：签名+加密+摘要组合正确 |

### cms — CMS 数字信封与 PKCS#7 签名（9 个）

| 测试 | 守护场景 |
|---|---|
| attach 往返 / 内嵌内容可提取 / 篡改签名失败 | 附件签名：原文内嵌、验证防篡改 |
| detach 往返（带原文验证）/ 篡改原文失败 / 错公钥失败 / 签名比 attach 短 | 分离签名：原文另行传输、验签必须带原文 |
| 信封往返（私钥解封一致）/ 错误私钥拒绝 | 数字信封：只有收件人私钥能解封（密钥封装安全） |
| PEM 往返（BEGIN/END + base64 还原 DER）/ 导出文件重解析并验证（.p7m/.p7s/.p7e + 配套证书密钥） | PEM 是 openssl 可读的互操作格式，导出内容无损 |

### p10 — PKCS#10 CSR 与签发（5 个）

| 测试 | 守护场景 |
|---|---|
| Subject/公钥/SAN 读取、无 SAN 空列表 | CSR 构建内容正确（extensionRequest 属性） |
| 申请人公钥验签通过 / 他人公钥失败 / 篡改字节失败 | CSR 自证「确实持有私钥」 |
| CA 签发证书（Subject/公钥/SAN 沿用、CA 验签、有效期） | 证书签发流程端到端 |

## 七、验证

- `mvn test -pl module-18-bouncy-castle`：87 个测试全绿。
- 全量 `mvn clean verify`：BUILD SUCCESS、0 checkstyle 违规。
- 实际运行 `Main`：10 个小节全部往返验证通过（哈希向量、AES 四模式、SM2/SM3/SM4 信封、签名五种、PEM 还原、DH/ECDH 协商一致、CA 签发与信任链、PKCS#12 打包还原、CMS 信封与 attach/detach 签名、CSR 构建验签与证书签发）；[9] 节自动执行 openssl 三条命令全部成功（attach/detach 验签 Verification successful、信封解密内容一致）。
