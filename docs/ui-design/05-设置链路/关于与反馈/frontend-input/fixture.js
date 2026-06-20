window.ABOUT_FEEDBACK_FIXTURE = {
  pageClass: "af-page-frame",
  stateClass: "af-state-workbench",
  topBar: {
    title: "关于与反馈",
    backLabel: "返回"
  },
  metrics: [
    { icon: "book", label: "当前版本", value: "Reader 1.0.0" },
    { icon: "check", label: "更新状态", value: "已是最新版本" }
  ],
  sections: [
    {
      title: "应用信息",
      rows: [
        { type: "link", icon: "refresh", title: "检查更新", meta: "检查应用是否有新版本", value: "已是最新版本" },
        { type: "link", icon: "file", title: "更新日志", meta: "查看版本更新记录" },
        { type: "link", icon: "link", title: "开源许可", meta: "查看开源软件许可信息" }
      ]
    },
    {
      title: "帮助与反馈",
      rows: [
        { type: "link", icon: "info", title: "使用帮助", meta: "查看常见问题与使用指南" },
        { type: "link", icon: "message", title: "问题反馈", meta: "提交问题或建议给开发团队" },
        { type: "link", icon: "log", title: "导出诊断日志", meta: "导出应用运行日志以便排查问题" }
      ]
    },
    {
      title: "法律与隐私",
      rows: [
        { type: "link", icon: "shield", title: "隐私协议", meta: "了解我们如何收集与使用数据" },
        { type: "link", icon: "file", title: "用户协议", meta: "阅读完整的用户协议" }
      ]
    }
  ],
  toast: {
    success: "已是最新版本",
    error: "加载失败，请重试",
    offline: "网络不可用，请稍后重试"
  },
  confirm: {
    title: "导出诊断日志？",
    copy: "导出的日志只包含运行信息，不包含书籍正文。",
    cancelLabel: "取消",
    confirmLabel: "确认导出"
  },
  states: [
    { key: "default", toast: "success", title: "关于反馈默认态", desc: "版本、更新、反馈和协议链接可见。" },
    { key: "loading", title: "检查更新中", desc: "检查更新时保留页面结构。" },
    { key: "error", title: "检查更新失败态", desc: "失败时保留版本信息并提示重试。" },
    { key: "confirm", title: "导出日志确认态", desc: "导出诊断日志前说明数据范围。" },
    { key: "default", toast: "offline", title: "离线反馈态", desc: "网络不可用时阻断检查更新和反馈提交。" }
  ],
  stateSummary: "用于核对版本、检查更新、反馈入口、协议链接和离线状态。"
};
