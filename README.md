# Syncthing GUI

一个以 Android 为首个平台的 Kotlin Multiplatform Syncthing 控制界面。目前仅包含：

- 使用前台服务保持核心运行；
- 启动和停止外置 Syncthing 核心；
- 显示核心版本、运行时长、RSS、Go 内存与 goroutine 数量。
- 通过独立底栏页面查看 Syncthing 与启动器日志。

## 工程结构

- `shared`：Kotlin Multiplatform Library，保存共享状态模型、MVVM ViewModel、控制契约和 Compose 界面。
- `androidApp`：Android 原生入口、核心导入、前台服务、进程控制和状态采集。

后续平台可以添加自己的原生应用入口，并向 `shared` 注入对应平台的 `CoreController` 实现，无需将 Android 生命周期代码带到其他平台。

界面采用 MVVM 依赖方向：`Compose View → CoreViewModel → CoreController → Android 核心实现`。打开系统文件选择器等一次性平台行为由 ViewModel 发出 `CoreUiEffect`，再由 Android 原生入口执行。

日志页面通过共享 `CoreLogViewModel` 和 `CoreLogReader` 契约读取平台日志。Android 实现最多读取最后 256 KiB、500 行，并在页面可见时每两秒刷新一次。

## Android 配置

- Application ID：`moe.https.syncthing`
- `compileSdk`：36
- `targetSdk`：28
- `minSdk`：26
- 支持的核心架构：`arm64-v8a`

## 外置核心

应用不包含 Syncthing 可执行文件，也不会自动下载或检查更新。用户需要在核心页面通过系统文件选择器导入自行获取的 Android ARM64 Syncthing ELF 文件。

导入过程会检查：

1. ELF 文件标识；
2. 64 位小端格式；
3. AArch64 机器类型；
4. 执行 `--version` 能否正常返回版本信息。

新核心校验成功后才会替换现有核心。Syncthing 配置和数据库保存在独立目录，更新核心不会删除它们。

> Android 10 起，面向 API 29 及以上的普通应用不能从应用可写目录执行文件。本项目按需求使用 `targetSdk 28`。较新的 Android 或厂商系统仍可能对这种兼容行为施加额外限制。

## 核心启动参数

核心以 `serve` 模式启动，REST/GUI 仅监听 `127.0.0.1:8384`。应用为本机 REST 控制生成独立 API Key，并传入以下约束：

- 禁止打开浏览器；
- 禁止自动选择其他 GUI 端口；
- 禁止核心自行重启；
- 禁止核心自行升级；
- 日志限制为 1 MiB，并只保留一个旧文件。

## F-Droid

工程不依赖 Google Play Services、Firebase、分析或广告组件，也不包含预编译核心。正式提交 F-Droid 前还需要由项目所有者确定开源许可证，并补充 Fastlane/F-Droid 元数据。
