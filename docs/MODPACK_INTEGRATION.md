# Modpack Integration / 整合包集成

## English

RSE is included in the user's Minecraft 1.21.1 NeoForge survival-modpack family as the engineering and automation layer.

- Minecraft: 1.21.1
- NeoForge: 21.1.249+
- Java: 21
- RSE baseline: 1.0.20-alpha
- Required platform: JEI, Jade, GeckoLib, Cloth Config, and Fusion
- The pack must use the same compatible dependency versions on both client and server where the metadata requires both sides.
- RSE's existing CI workflow produces a verified JAR and SHA-256 file under the `rse-verified-<commit>` artifact name. Packagers should use the JAR from a successful run and retain its checksum with the pack build record.

RSE remains a separate mod JAR. It is not shaded into another mod, and its engineering simulation stays server-authoritative.

## 中文

RSE 作为用户 Minecraft 1.21.1 NeoForge 生存整合包中的工程与自动化层。

- Minecraft：1.21.1
- NeoForge：21.1.249+
- Java：21
- RSE 基线：1.0.20-alpha
- 必需平台依赖：JEI、Jade、GeckoLib、Cloth Config 和 Fusion
- 客户端与服务器必须使用符合模组元数据要求的兼容依赖版本。
- RSE 现有 CI 工作流会生成经过构建验证的 JAR 和 SHA-256 校验文件，产物名称为 `rse-verified-<commit>`。打包时应使用成功构建的 JAR，并将校验值保存在整合包构建记录中。

RSE 保持独立 JAR，不会被嵌入其他模组；工程模拟仍由服务器权威管理。
