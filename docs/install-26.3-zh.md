# Vitrail 26.3 NeoForge 非官方测试版

版本: 0.11.0-beta.neoforge26.3.3
移植修改日期: 2026-09-21
原作者及项目: avpbynf, https://github.com/avpbynf/Vitrail-Shaders

## 安装

1. 使用 Minecraft 26.3、NeoForge 26.3.0.0-beta 和 Java 25。
2. 安装适用于 26.3 的 Sodium NeoForge 0.9.2。
3. 将 vitrail-neoforge-0.11.0-beta.neoforge26.3.3+mc26.3.jar 放入该游戏实例的 mods 文件夹。
4. 游戏图形后端选择 Vulkan，按游戏提示重启。
5. 将光影包 ZIP 放入该实例的 shaderpacks 文件夹。
6. 进入世界后按 I，选择光影包，点击 Apply，然后点击 Done。按 R 可重载光影。

不要同时安装原版 Vitrail 与此移植版。发布目录内的源码 ZIP 不是模组文件。
本包不包含 Minecraft、NeoForge、Sodium 或光影包。

## 已测范围

测试环境为 RTX 5070 Ti、NeoForge 开发客户端。此前已测试 Complementary Reimagined r5.9.3。
已检查主世界地形、天空、水面反射、阴影、空手、手持剑、雨天、末地画面，
以及光影重载、资源重载、关闭后重新启用光影和维度切换。

这是非官方实验移植版，不是原作者发布的正式更新。
其他显卡、光影包、服务器、模组组合和长期稳定性尚未验证。
下界完成加载检查，但测试落点被熔岩遮挡，尚未完成完整画面检查。
尚未在独立启动器中验证最终 JAR；测试通过的是相同代码的开发客户端。
本次 BSL、Photon、iterationRP 的检查结果和未解决问题见 shaderpack-compatibility-26.3-zh.md。
详细移植记录见 port-26.3-status.md。

## 源码与构建

随附源码保留原项目许可与署名；不包含测试存档、游戏源码、依赖缓存或光影包。
原 README 说明上游版本，本移植版请以本页和状态记录为准。
使用 JDK 25 和 Gradle 9.6.1 执行:

```
gradle :common:compatibilityRegression :neoforge:build checkText
```

构建产物位于 neoforge/build/libs。仅验证 NeoForge 构建目标，未验证 Fabric。
