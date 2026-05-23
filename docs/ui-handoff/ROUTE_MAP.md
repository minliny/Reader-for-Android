# Route Map

正式 App 一级主导航固定为 4 项：书架 / 发现 / 书源 / 我的。搜索、书籍详情和阅读页均为二级或功能 route，不作为底部主按钮。

| route | 页面 | 所属主模块 | 入口来源 | 返回路径 | 主要状态 | 关键交互 |
|---|---|---|---|---|---|---|
| `/bookshelf` | 书架 | 书架 | 启动 / 主导航 | 退出 App | 封面、列表、loading、empty、error、offline | 顶部搜索、切换视图、分组、进入详情/阅读 |
| `/bookshelf/groups` | 分组管理 | 书架 | 书架更多 | `/bookshelf` | 默认、编辑 | 新建、重命名 |
| `/bookshelf/import` | 本地书导入 | 书架 | 书架空状态 / 更多 | `/bookshelf` | 权限、成功、失败 | 选择文件 |
| `/search` | 搜索首页 | 书架二级 / 全局功能 | 书架顶部 / 发现顶部 / 页面内入口 | 来源页 | 首页、历史、loading、empty、error | 输入关键词 |
| `/search/results` | 搜索结果 | 书架二级 / 全局功能 | 搜索首页 | `/search` | loading、empty、error、results | 加入书架、详情 |
| `/book/detail/{bookId}` | 书籍详情 | 二级详情 | 搜索结果 / 书架 / 换源 | 上一页 | 详情、目录预览、已加入 | 开始阅读、继续阅读、换源 |
| `/reader/{bookId}` | 阅读页 | 二级阅读 | 书架 / 书籍详情 | 来源页 | 基础控制层、9 个 overlay 状态 | 翻页、目录、朗读、界面、阅读设置 |
| `/discover` | 发现首页 | 发现 | 主导航 | `/bookshelf` | 推荐、RSS、empty、error、offline | 刷新、打开 RSS、订阅管理 |
| `/rss_list` | RSS 列表 | 发现 | 发现首页 | `/discover` | 列表、loading、empty、error | 打开文章 |
| `/rss_detail/{rssId}` | RSS 详情 | 发现 | RSS 列表 | `/rss_list` | 详情、阅读状态、error | 打开链接、标记已读 |
| `/rss_subscription` | RSS 订阅管理 | 发现 | 发现首页 / RSS 列表 | `/discover` | 订阅、disabled、error | 添加、启用、禁用、删除 |
| `/sources` | 书源管理 | 书源 | 主导航 | `/bookshelf` | 列表、disabled、error、test 状态 | 启用、详情、导入 |
| `/source_detail/{sourceId}` | 书源详情 | 书源 | 书源管理 | `/sources` | 规则预览、状态 | 编辑、测试 |
| `/source_edit/{sourceId}` | 书源编辑 | 书源 | 书源详情 / 添加 | `/sources` | 表单、URL 校验 | 保存、取消 |
| `/source_import` | 书源导入 | 书源 | 书源管理 | `/sources` | JSON、URL、blank 提示、error | 导入 JSON、从 URL 导入 |
| `/mine` | 我的 | 我的 | 主导航 | `/bookshelf` | 身份状态、同步摘要、入口分组 | 打开设置、同步、备份、关于 |
| `/global_settings` | 全局阅读设置 | 我的 | 我的 | `/mine` | 阅读设置、外观 | 字号、行距、夜间模式 |
| `/webdav_config` | WebDAV 配置 | 我的 | 我的 / 同步与备份 | `/mine` | 未配置、已配置、auth error、offline | 配置、测试、断开 |
| `/backup_settings` | 备份设置 | 我的 | 我的 / 同步与备份 | `/mine` | enabled、disabled、failure | 开关、立即备份 |
| `/progress_sync` | 阅读进度同步 | 我的 | 我的 / 同步与备份 | `/mine` | running、success、failure、conflict、offline | 同步、解决冲突 |
| `/remote_webdav_books` | 远程 WebDAV 书籍 | 我的 | 我的 / WebDAV | `/mine` | 列表、empty、error | 打开远程书籍 |
| `/about` | 关于版本 | 我的 | 我的 | `/mine` | 版本、权限、隐私、日志 | 检查更新、导出日志 |
| `/state/error/{message}` | 全局错误状态 | 我的 / 全局状态 | route fallback | 上一页 | error | 重试 |
| `/state/offline` | 全局离线状态 | 我的 / 全局状态 | route fallback | 上一页 | offline | 重试 |
