# Route Map

| route | 页面 | 入口来源 | 返回路径 | 主要状态 | 关键交互 |
|---|---|---|---|---|---|
| /bookshelf | 书架 | 启动 / 主导航 | 退出 App | 封面、列表、空状态 | 切换视图、进入阅读 |
| /bookshelf/groups | 分组管理 | 书架更多 | /bookshelf | 默认、编辑 | 新建、重命名 |
| /bookshelf/import | 本地书导入 | 书架空状态 / 菜单 | /bookshelf | 权限、成功、失败 | 选择文件 |
| /search | 搜索首页 | 主导航 | /bookshelf | 首页、历史 | 输入关键词 |
| /search/results | 搜索结果 | 搜索首页 | /search | loading、empty、error、results | 加入书架、详情 |
| /book/detail | 书籍详情 | 搜索结果 / 书架 | 上一页 | 详情、目录预览、已加入 | 开始阅读、换源 |
| /source/switch | 换源结果 | 书籍详情 | /book/detail | 可用、错误 | 切换书源 |
| /sources | 书源管理 | 主导航 / 设置 | /settings | 列表、disabled、error | 启用、详情 |
| /sources/add | 添加书源 | 书源管理 | /sources | 表单、导入 | 保存 |
| /discover | 发现首页 | 主导航 | /bookshelf | 推荐、RSS入口 | 打开推荐 |
| /rss | RSS 列表 | 发现 | /discover | 列表、empty、error | 打开详情 |
| /settings | 全局设置 | 主导航 | /bookshelf | 入口列表 | 进入子设置 |
| /settings/webdav | WebDAV 配置 | 同步设置 | /settings | 未登录、已连接、auth error | 登录、测试 |
| /settings/backup | 备份设置 | 同步设置 | /settings | 开关、冲突 | 备份、恢复 |
| /settings/sync | 阅读进度同步 | 同步设置 | /settings | 正常、冲突、错误 | 同步、解决冲突 |
| /reader | 阅读页入口 | 书架 / 详情 | 来源页 | 基础、8 个控制层状态 | 翻页、打开控制层 |
