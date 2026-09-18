# AGENTS.md

本文件适用于仓库根目录及其下所有第一方代码。`third_party/scripta` 和
`third_party/syncthing` 是独立上游子模块；除非任务明确要求，否则不要修改其内容、
更新其提交指针或将上游代码复制到第一方源码中。

## 项目概况

- 本项目是 Syncthing GUI，使用 Kotlin Multiplatform 与 Compose Multiplatform。
- `shared` 承载共享 UI、状态管理、业务逻辑及平台抽象。
- `androidApp` 是主要可用客户端，并负责 Android 系统集成与内置 Syncthing Core。
- `iosApp` 当前仅为 UI Demo，不要假定 Android 业务能力已在 iOS 生效。
- 构建使用 Gradle Wrapper、JDK 21、Android SDK 37.2 与 NDK 28.2.13676358。
- 依赖版本集中在 `gradle/libs.versions.toml`；不要在模块脚本中重复硬编码版本。

## 修改原则

- 开始前检查 `git status`，保护用户已有修改，不覆盖、不回退、不顺手整理无关代码。
- 只修改完成当前任务所必需的文件；发现额外问题时先记录并说明，不擅自扩大范围。
- 优先在 `shared/src/commonMain` 实现可复用逻辑；确需平台 API 时使用清晰的
  `expect`/`actual` 或现有平台抽象。
- 不降低现有功能、兼容性或安全性。修改 SDK、ABI、签名、网络安全配置、存储访问、
  后台服务或 Syncthing Core 集成前，先说明影响并取得确认。
- 不提交密钥、口令、证书、`local.properties` 或其他敏感配置，也不要在日志中输出它们。

## 架构约定

项目尽量遵循 MVVM 架构：

- Compose 页面负责渲染不可变 UI 状态，并将用户操作转换为事件；避免在 Composable
  中直接执行持久化、网络请求、进程控制或复杂业务判断。
- `ViewModel` 持有页面级状态、处理事件并编排业务流程。优先采用单向数据流，明确区分
  持久状态与一次性事件。
- 核心控制、配置、存储和平台能力应留在对应的 `core`、`storage`、`platform` 等层，
  不要为了方便把它们塞进 UI 或 ViewModel。
- `commonMain` 的 ViewModel 和业务代码不得依赖 Android `Context`、`Activity` 或其他
  平台类型；通过接口或平台实现注入所需能力。
- 纯视觉、生命周期短且无需跨配置保留的状态（例如展开、焦点或动画状态）可以保留在
  Composable 内，无需机械地放入 ViewModel。
- 新页面优先复用现有组件、状态模型和导航方式，并保持同类页面的事件命名与状态暴露
  方式一致。

## Kotlin 与 Compose 约定

- 遵循 Kotlin 官方代码风格及仓库现有命名、格式和文件组织方式。
- 优先使用不可变数据、明确的空值处理和结构化并发；不要创建脱离生命周期的协程作用域。
- 副作用放入合适的 Compose effect 或 ViewModel，不在重组路径中执行 I/O。
- 用户可见文本应通过 Compose 资源管理，不要在 UI 代码中新增硬编码字符串。
- 保持公开 API 和序列化模型兼容；需要破坏性变更时先说明迁移方案。
- 注释应解释原因、约束或不明显的行为，不复述代码本身。

## Android 与 Syncthing Core

- Android 专属实现放在 `androidApp` 或 `shared/src/androidMain`，不要泄漏到共享源码。
- 保持现有 Application ID、最低/目标 SDK 与 ABI 集合，除非任务明确要求调整。
- `androidApp/build-syncthing.py` 和 `buildBuiltInSyncthing` 会构建多 ABI 的 Syncthing
  Core，成本较高，并依赖固定的 Go、NDK 和子模块版本。
- 不要默认运行 `assembleRelease`；该任务还涉及 Core 构建、代码压缩、签名与 APK 拆分。
- 不修改 `gradle/libs.versions.toml` 中的 Syncthing 版本或提交，除非同时核对并明确更新
  `third_party/syncthing` 子模块指针。

## 验证

- 修改后先检查精确 diff，确认没有格式化或改动无关文件。
- 根据影响范围选择最小验证任务；不要用完整 Release 构建代替局部验证。
- 未经请求或确认，不要在修改完成后直接编译、安装或运行应用。
- 新增或修改可测试的共享业务逻辑时，在 `shared/src/commonTest` 添加聚焦测试；平台逻辑放入
  对应平台测试源集。
- 如果缺少适用测试、环境依赖不齐或验证未执行，交付时如实说明，不声称已经通过。

## 交付要求

- 总结改了什么、为何这样改，以及架构或平台行为是否发生变化。
- 列出实际执行的检查或测试；未执行构建时明确注明。
- 提醒仍存在的风险、TODO 或需要真机验证的 Android 系统行为。
