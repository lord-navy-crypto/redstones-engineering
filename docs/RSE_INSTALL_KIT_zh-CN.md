# RSE 1.0.20-alpha / PR #229：安装与离线预检

适用 Minecraft **1.21.1**、NeoForge **21.1.249+（仅 21.1.x）**、Java **21**。

必须在同一个实例的 `mods/` 目录内安装 **RSE、Jade 15.10.6+（15.x）、GeckoLib 4.9.2+（4.x）、LDLib2 2.2.26+（2.x）**，均为适配 Minecraft 1.21.1 的 NeoForge 运行版。Fusion、JEI、Cloth Config 目前不是必装前置。

## 特别注意 LDLib2

LDLib2 官方发布同时列出 `ldlib2-neoforge-1.21.1-2.2.26-all.jar`（Modrinth/CurseForge 主文件）、`ldlib2-neoforge-1.21.1-2.2.26.jar`（补充文件），以及 `-sources.jar`。**不可单凭 `-all` 后缀判定是错误玩家文件**：以官方发布来源、正确的 NeoForge 元数据、内含 `ldlib2` 模组 ID、版本范围为判断依据。不要同时放入两种 LDLib2 JAR；`-sources.jar` 和 `-javadoc.jar` 不是运行包。

官方版本页：https://modrinth.com/mod/ldlib/version/mc1.21.1-2.2.26-neoforge

请从作者/官方分发页面取得依赖，不要把 Maven `:all` 构建坐标当作安装操作，也不要从不可信镜像下载。安装包**不重分发** Jade/GeckoLib/LDLib2。

## 使用方法

1. 从本包 `RSE_JAR/` 取出 RSE JAR，复制到你真正使用的 Minecraft **实例** `mods/` 文件夹，不要把整个 ZIP 放进 `mods/`。
2. 单独安装三项前置。
3. Windows 可运行 `check_rse_windows.bat`，macOS 可运行 `check_rse_macos.command`。需安装 Python 3.9+。多实例启动器建议在终端指定：`python3 rse_install_preflight.py --mods "实例路径/mods"`。
4. 预检脚本仅读取 `mods/`；不会联网、下载、移动、删除或修改模组。使用 `--report <路径>` 时才额外写出 JSON 报告。
5. `BUILD_INFO.json` 记录**PR 分支提交**、**CI 实际检出提交（PR 场景可能是测试合并提交）**与 RSE JAR 的 SHA-256；不要把两种提交混为一谈。

**边界：**预检通过只表示依赖元数据初查通过；并非 Minecraft 启动、联机 GUI、运行时 GameTest 已验收通过。