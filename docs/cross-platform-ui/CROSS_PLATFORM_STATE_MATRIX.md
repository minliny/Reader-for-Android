# Cross Platform State Matrix

| 模块 | 状态 | 说明 |
|---|---|---|
| 书架 | loading / empty / error / offline / cover / list | 默认首页，搜索入口在顶部 |
| 发现 | RSS loading / empty / error / subscription disabled / offline | 内容发现与订阅流，不接真实网络时使用 fixture |
| 书源 | list / disabled / test loading / test success / test failure / import error | 管理书源，不承载搜索结果主导航 |
| 我的 | WebDAV not configured / configured / auth error / backup enabled / sync running / conflict / permission | 设置、同步、备份、关于和系统入口 |
| 搜索 | home / loading / results / empty / error | 二级功能，不是 App 主底栏 |
| 详情 | loading / detail / TOC preview / error | 从书架或搜索进入 |
| 阅读页 | base / search / auto scroll / replace / night / directory / tts / appearance / settings | 独立阅读控制层，不使用 App 主底栏 |
| 全局状态 | loading / empty / error / offline / permission required | 可由各模块复用 |
