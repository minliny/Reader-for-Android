# Reader Android 历史前端参考

本工作树保留旧 `com.reader.android` Compose 源码、预览及设计结构检查，供迁移追溯与明确选择的历史重放使用。当前目录没有完整 Gradle 构建入口，不能把这里的源码检查当作当前 Android 原生候选的 APK 或业务验收。

当前原生开发候选由 Reader 工作区的唯一 `DEVELOPMENT_BACKLOG.md` 绑定到 `codex/android-harmony-parity` 分支及实际工作树。候选的 Gradle `sourceSets` 明确消费 `io/reader/android` 的生产、JVM 和 instrumentation 源集；保留的 `com/reader/...` 源码和测试不属于该活动集合。

本工作树已迁移删除的 `docs/ui-design` 不自动恢复。旧 `FrontendInput*`、`*StructureTest` 仍可能引用该历史目录或固定源码写法；它们保护的是原设计输入与原实现布局，缺少那些准确输入时不具备重放前提。需要重放时使用原有隔离历史版本和来源，不回退到另一个工作树的邻近文件，也不将旧测试重新加入当前源集。

当前产品确实需要保留的约束应在实际 `io/...` 生产 owner 上验证其行为和独有反例。旧页面身份列表、源码 token 和历史截图不作为新的业务事实或测试资格来源。源码、准确 JVM 方法、Native/APK、安装、设备行为与用户验收分别记录。
