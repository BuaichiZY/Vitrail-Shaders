# 三款光影包兼容性检查

日期: 2026-09-21
模组: Vitrail 非官方 0.11.0-beta.neoforge26.3.3, Minecraft 26.3 NeoForge
环境: NeoForge 26.3.0.0-beta, Sodium 0.9.2, Java 25, Vulkan, RTX 5070 Ti。
测试使用独立开发客户端和独立存档。用户游戏目录中的三个原始 ZIP 未修改。

## 结果

| 光影包 | 结果 | 验证范围 |
| --- | --- | --- |
| BSL_v10.1.6.zip | 默认配置通过本次场景检查 | 主世界地形、树叶、水面、天空、阴影、第一人称空手; 修复后再次切换加载、热重载正常 |
| photon_v1.3b.zip | 默认配置通过; 彩色光照计算编译问题已修复 | 默认场景检查; COLORED_LIGHTS=true 时 shadowcomp 成功编译和调度; 光影热重载正常 |
| iterationRP Alpha 0.8.26 hotfix.zip | 本次主世界正午场景通过, 原先严重偏暗已修复 | 原始 ZIP 的地形、树叶、水面、天空和空手恢复正常可见亮度; 光影重载及资源重载通过 |

以上结果只覆盖所列场景和配置。Photon 彩色光照已验证计算路径和场景加载,
尚未对照 Iris 逐项确认彩色光传播的视觉一致性。其他维度、材质包、光影全部预设、
其他显卡和长时间运行未在本批次验证。最终 JAR 未在用户的整合包启动器中测试。

## 针对性修复

- 纹理/普通 uniform 通过对象宏别名声明时, 提前生成的声明使用最终名称。
  iterationRP 的 FBTEX_* 声明原先导致 colortex* 未声明错误。
- 多阶段共享图像声明时, 同时传递明确的图像格式, 避免整数图像被错误补成浮点格式。
- 配置文件支持生效分支内的局部 #define/#undef 和对象宏替换,
  iterationRP 天空盒及 RTW 纹理的尺寸不再被丢弃。
- 全屏后处理的 vaPosition/vaUV0 等现代输入接到真实顶点数据,
  不再用零顶点导致 iterationRP 画面全黑。
- 自定义存储纹理允许兼容格式视图; 图形/计算阶段按各自声明选择整数或浮点访问格式,
  采样视图仍保留纹理格式。额外视图随纹理延迟销毁。
- 计算阶段缺少已声明的自定义纹理时, 与图形阶段使用一致的黑色占位,
  避免误判为存储图像缺失而中止整个计算程序。
- 保留未声明格式的 writeonly 图像, 避免 Photon 彩色光照函数调用产生 SPIR-V 类型不匹配。
  该路径依赖移植版已启用的 Vulkan shaderStorageImageWriteWithoutFormat。
- 更新转换缓存标识, 防止继续使用修复前的转换结果。
- 第三版补齐图形阶段 colorimgN 存储图像绑定: 按读取侧选择纹理,
  使用单层存储视图、正确 Vulkan 描述符及无采样器绑定。
  分配规划识别片段/顶点阶段的声明、宏别名和 readonly 图像,
  创建纹理时带上存储用途。沿用已有覆盖图形写入和后续采样的同步屏障。
  iterationRP 的 colorimg8/9 现在实际获得可写纹理, 修复原先正午剪影问题。
- 动态 blocks.png/blocks_s.png/blocks_n.png 图集引用在绘制时解析,
  随资源重载获取当前图集; 缺少材质图时使用标准材质默认值。
- 将 MC_VERSION 从遗留的 260200 修正为 260300。

## iterationRP 未解决事项

第三版已消除本次复现场景的严重偏暗。验证使用原包和默认资源,
未对照 Iris 逐项验证体素光照、反射和时序效果, 未覆盖全部维度、预设和天气。
动态材质图集已接入, 但带法线/高光贴图的第三方材质包尚未实测;
不能据此宣称材质光追完整兼容。

## 可复现验证

源代码内 CompatibilityRegression 检查局部宏条件分支、调用者设置不变、
纹理别名链、跨阶段图像格式、全屏顶点、访问格式元数据及 formatless writeonly 图像。
第三版增加动态图集路径和临时光影包测试, 验证宏别名图像分配、顶点 readonly
图像分配、图形存储绑定分类及 ping-pong 侧选择。

```text
gradle :common:compatibilityRegression :neoforge:build checkText
```

本机记录: port-bsl-1.log (三包首次检查), port-iteration-2.log 至
port-iteration-6.log (迭代修复), port-compat-7.log (Photon 彩色光照/重载,
iterationRP 混合格式, BSL 回归), port-compat-build.log (构建与回归检查)。
第三版运行记录: port-iteration-images-runtime.log; 最终构建: port-compat-3-final.log。
本次再次检查 BSL, 并使用 COLORED_LIGHTS=true 检查 Photon。
日志和光影包不随源码分发。

## 检查对象 SHA-256

```text
0e9e8d045ea273c19832a0f49d503a6e27e76759769ea4f38f9bbfdc1cf987c2  BSL_v10.1.6.zip
b73e180401eca11bce696cf6e1697271ef332e6e7d6427a738efa446080693bc  iterationRP Alpha 0.8.26 hotfix.zip
120897768eaa8cb1f14a3e98f88dff009ae3204ee3b96904a4fe8586edfc1ead  photon_v1.3b.zip
```
