# Android 消息入口登记

2026-10-29：App owner protocol recovery，source adfebfcc。

分类依据业务未决事实，统一复用现有 ReaderRecoveryNotice；展示和隐藏不拥有业务状态。本表只登记本次新增或变更入口，未登记模块不代表已整合。

All rows use ReaderProtocolRecoveryNotice -> existing ReaderRecoveryNotice. Their physical host is ReaderProtocolRecoveryHost in the active non-reading ReaderApp message column (bookshelf/directory/settings), or the active ReaderReadingScreen top message column. ReaderApp's mutually exclusive reading branch prevents a second simultaneous shelf host. The business owner is AppCoreOwner/CoreProtocolRecoveryOwner with the original bridge/service/journal; the component only renders and forwards an intention.

| Business entry / fact | Exact presentation and original identity | Action admission / fact boundary | Retirement, clearing and stale-callback boundary |
| --- | --- | --- | --- |
| CONSUMED_ACK_FAILED, including original/control/Host delivery | Immutable presentation UUID; runtimeSession + originalId + originalRequestId + requestToken + deliveryId; exact retained Delivery object | “核对并继续”: current presentation and original lease rechecked under gate; same consumed delivery ACK only; then only start-captured IDs now resolved may be accepted. No consumer or Core business resend | Original terminal ACK alone releases its large business sink; control/Host ACK cannot. New/foreign faults remain visible. Failed ACK retains original frame. Removed/changed presentation cannot ACK again |
| RESOLVED_FAULTS_AWAIT_ACCEPTANCE / HOST_NON_ADMISSION_RECORDED without the active continue intent | Presentation UUID plus original protocol reference and immutable exact resolved fault-ID set | Explicit acceptance rechecks every offered original fault is still resolved and obeys Actor Host-outcome gate; no fresh Core query qualifies it | Clear only the IDs actually accepted by actor.resumeAdmission; the small original route remains for late frames. Original business pending journal is not cleared by protocol acceptance |
| HOST_NON_ADMISSION_UNRECORDED | Presentation UUID + exact plan/session/request/token/method/params hash and immediate native status/diagnostic | Record only the separate original Host non-admission fact, then retire that actor plan. No fabricated wire terminal or business success | Record failure retains plan/fact/action qualification. Successful record transitions to its actual resolved-fault presentation; original business intent remains owned by its service |
| HOST_CONTROL_ERROR_RETAINED | Presentation UUID + exact control Delivery/raw wire, original token/id and separate control requestId | Record the original control error independently, then ACK that control frame. Never run original business sink or claim Host success/original completion | Clear only the consumed/ACKed control frame. Original terminal/uncertain Host outcome remains retained. If its record consumer fails, no generic consumer retry is offered |
| CONSUMER_QUALIFICATION_REQUIRED / ORIGINAL_UNCERTAIN / OWNER_QUARANTINED | Presentation UUID + exact original reference when known, otherwise actor runtime protocol identity; never a fabricated book/entity | No action when qualification is missing. True WritePending and dispatch-unknown facts retain their exact original sink; unknown/unparsed/foreign/late-Host quarantine is explicit | Clear/update only from original owner snapshot and exact protocol facts. Hiding/route exit does not cancel or complete anything. Consumer-failed delivery is never automatically consumed again |

Every visible callback captures the current VM UUID + generation + route and presentationId. VM checks that host identity and current presentation membership; App owner then resolves the original small route and rechecks it after the gate. A new VM may display an old transaction through the shared feed but cannot substitute its current book/session for the original route. Unmounting either host retires only that rendering/callback; no business cancellation, retry or navigation is performed. No new Toast/Alert/category frame is introduced.

## 书架管理

宿主仅为当前管理页；业务写入及原回执由 App 共享 ReaderBookshelfManagement 持有。ReaderShelfManagement 只持有 UI 状态，主 VM gate 后再核捕获身份。

| 入口与事实 | 公共类别/物理组件 | 身份与动作条件 | 失效与清理 |
| --- | --- | --- | --- |
| 原管理操作未确认、可明确继续原操作 | Recovery / ReaderRecoveryNotice | 原 operationId + recoveryRevision；当前管理页可见且 gate 后同身份，continue 另需准确 canContinueOriginal | 退出或重新读取退休旧 revision；隐藏不取消、重派或清原意图；新 list 不作为写 ACK |
| 原业务已确认后的短反馈 | Transient / ReaderTransientFeedback | feedbackIdentity；消息只来自实际 service 返回的原结果 | 仅相同 feedbackIdentity 到时清展示；后续读取失败不能改变已确认写结果 |
| 分组/书架读取或局部失败 | PageState / ReaderPageState | 当前 read.revision 或未加载时 recoveryRevision；刷新重新核相同展示身份 | 刷新先退休旧行与选择；失败不开放旧行；旧 callback 不能清新选择 |
| 删除自选分组、移出所选书籍 | Confirmation / ReaderNativeConfirmation | 不可变 confirmation UUID、UI owner、read.revision、准确分组或有序 sourceId/bookId 列表；确认后与 VM gate 后再核 | dismiss 只退展示；已准入业务不因隐藏取消；移出明示阅读位置/正文修改/缓存清除、原文件保留，不能称 Host 文件清理完成 |

## 正文搜索关闭

| 入口与事实 | 公共类别/物理组件 | 身份与动作条件 | 失效与清理 |
| --- | --- | --- | --- |
| 原搜索 cursor 关闭尚未确认 | Recovery / 既有 ReaderRecoveryNotice；当前 ReaderApp 非阅读消息列或 ReaderReadingScreen 阅读消息列互斥宿主 | 回调捕获完整原 session、展示 route 和 reading identity；VM 在入口核仍为同一宿主与 pending 原 session，Library 的 App owner gate 只观察或消费同一原关闭请求 | 换页面/reading/session 后旧回调失效；关闭面板和 pending=null 均不表示 ACK；ACK 失败或 Core 错误保原 Call，不能自动重派；成功确认仅同 readingOwner/VM 代次/完整 session/inflight 可投影，原业务清理由 service 持有 |

searchCloseInFlight 与 lastSearchCloseConfirmation 是原操作事实的内存投影，不新增用户消息或样式出口。后者只包含原 parsed terminal 的重序列 JSON，不声明传输字节相同。协议未决优先由上面的 App owner protocol recovery 入口处理；展示隐藏不完成、取消或重试原请求。


## A3 retained-original format and physical WebView host

| Business fact / entry | Public category / physical host | Exact identity and validity | Cleanup / action boundary |
| --- | --- | --- | --- |
| Original book format/authority read fails before chapter publication | Transient / existing ReaderTransientFeedback in the original ReaderApp or reading message column | Original VM request generation and selected book; only the active perform owner may publish | A new request retires the old feedback. Missing authority does not become TXT fallback, new import or automatic re-import |
| Physical ebook preparation, resource, canonical restore or draw fails | PageState / existing ReaderPageState under ReaderEbookReadingBody or ReaderEbookBody | Captured readingOwner + full chapter/session/body/processing + single-use physical view/render UUID; ready/failure also retain original layout epoch/token | Dispose/route/session replacement closes only the exact view. No generic retry, refresh or hidden business cancellation; hiding the error does not confirm an operation |
| Same original renderer operation has a typed protocol pending cause | Recovery / existing ReaderProtocolRecoveryHost, mutually exclusive reading or non-reading message column | Existing CoreProtocolReference and presentationId remain App-owned; UI forwards only a cause from its still-current view/reading scope | Existing original protocol action eligibility applies. No import-pending alias, new Core request, replay or alternate ACK inferred from renderer/PageState/Ready |

Native Ready/selection/tap/visible-anchor callbacks are scoped renderer facts,
not user message components. Selection observation owns no bookmark/highlight
write or clipboard receipt. Blocked external/cross-chapter link navigation is
unadmitted, not a success message or a new generic Alert/Toast flow.
