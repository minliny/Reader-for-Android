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


## 历史 UNKNOWN 与新的阅读位置意图

分类仍为 Recovery，复用 ReaderRecoveryNotice。同一原 UNKNOWN 的事实、是否阻挡本次新 save、以及新操作是否获 ACK 是三个独立事实；UI 不铸造资格、原回执或完成状态。

| 入口与事实 | 公共类别/宿主 | 精确身份与动作 | 失效、清理与消息边界 |
| --- | --- | --- | --- |
| 原进度保存仍未确认，当前保存 barrier 未解除 | Recovery / ReadingScreen 顶部现有消息列；书架、目录、设置等沿 ReaderApp 互斥消息列 | 原 PendingPosition id/book；原核对回调捕获 route + readingOwner，入口及 VM gate 后再核同宿主和原条目；原 reconcile 只处理准确原操作 | 不通过新 read、当前行或另一 ACK 结束原记录；退出宿主仅隐藏展示。旧回调不能操作新阅读/页面 |
| 用户明确要求保留原记录并开始新的位置意图 | Recovery / 同一个 ReaderRecoveryNotice 的可选第二动作，不增 Alert/Toast/消息外框 | 捕获 readingOwner、chapter session token、完整 chapter body/processing、当前同书 pending ID 集；正文须有实际 matching Ready。VM gate 后再核同集合；Library/App owner 内部资格化，UI 不提供 runtime/bool 权限 | 新内存 intent 不是 progress originalId，不是 ACK。失败保留原 barrier；只有 backend 接受真实 Ready/visible-anchor 后，当前 reading 才区分 retained IDs 与保存 barrier。新 current-runtime UNKNOWN 仍阻挡 |
| 旧 UNKNOWN 已获本 reading 的非 barrier 资格，但原结果仍未知 | Recovery / 阅读 controls 展开时沿原顶部消息列，书架仍沿原宿主显示 | 真实 Library-issued intent 对象 + readingOwner/session/fullscope；只使用现有“原记录会继续保留”文案，无原结果确认动作 | 合上 controls、退出或替换 reading 仅退休该显示/callback；pendingPositions 原列表不删、不写旧 Completed。当前章以外导航/书签/搜索 guard 不因该资格开放 |

lastPositionSaveAttempt 是当前捕获 readingOwner/session/位置对 typed 保存结果的投影。SkippedUnchanged 只表示未派发，不更新 ACK、Saved 消息或原 pending；ConfirmedNewOriginal 必须来自其新 UUID/request/current runtime 的真实回执并匹配完整 scope。旧回调不能将其投影给新阅读。新 Ready/restore/reflow 本身不生成新的业务位置；新 visible anchor 必须先经原 Native 完整 gate 与 backend 原子观察资格。本文记录接入和生命周期合同；源码、准确测试、产物与设备证据分别记账。


## A4 书源与在线搜索工作区（源码接入合同）

宿主为 ReaderRoute.Online 的 ReaderOnlineWorkspaceScreen；现有 App protocol recovery 仍位于同一 ReaderApp 消息列，协议原事实优先。ReaderOnlineWorkspace 只拥有展示、文件选择票据与查询代次；业务由同一个 Library/client、App serviceTransport 与原事务服务持有。

| 入口与事实 | 公共类别/物理组件 | 身份与准入 | 失效与清理 |
| --- | --- | --- | --- |
| 书源列表/搜索/详情读取，未加载、已确认空结果、有限失败 | PageState / ReaderPageState；有限加载沿现 Material 指示器 | Workspace UUID + 可见 host + query generation + revision；VM gate 后再核；空搜索态只来自已成功解析的当前页，不把未知/错误当空结果 | 改查询、换源、返回、离页、VM 退场分别退休旧展示；失败 session 禁止 next/detail，只允许用户明确发起新 query；不是自动重试 |
| 完整 JSON 对象/数组文件预览、单项选择/标准解码失败 | PageState / ReaderPageState | picker UUID 绑定完整原 UI ticket；只读预览保完整原 bytes/SHA/count；选择再核原 document UUID + epoch/index + host/query/revision ticket，且未 busy/pending。文件结果本身不导入 | 取消/换文件/离页只清预览与选择；旧 file/result/choice 不得导入。文件只经 ContentResolver 有界读一次，不保 URI 授权、不写裁剪文件；选中完整对象使用 org.json 一次冻结的序列化字段，明确不是全文原字节；规则仍由 Core 单笔处理 |
| 原书源导入、原在线入架未确认 | Recovery / ReaderRecoveryNotice | 原 SourceImport/Add operationId + 当前 UI revision + pending membership；VM gate 与实际 backend 均核准确原操作 | 仅 reconcile 原记录；不以 source.list/bookshelf.list/status 清原未决，不重派 unknown。candidateRetained 或协议未决继续保留；隐藏不取消业务 |
| 原搜索/详情获取未确认 | Recovery / ReaderRecoveryNotice | 原 acquisition UUID、method/source/runtime/request、原 immutable params 的业务投影；UI 仅转交原 id | “读取原状态”只读既有 original facts，不重发 HTTP/Core 请求、不自动宣布 ACK。catalog transportAcknowledged 的后续原收尾缺口仍 OPEN |
| 已获 durable 原导入/入架结果 | Transient / ReaderTransientFeedback | 当前 feedback UUID 与真实 service confirmation；只在实际 durable APPLIED/ADDED/原有入架结果后发 | 仅同 feedback UUID 到时退展示。Add 后的书架读取失败不改变已确认 Add、不会重新入架 |
| 已入架在线文字书及目录/阅读入口（源码候选，执行未证） | 常规书架行 → OnlineDirectory / OnlineReading；读取/未决消息走本节下方已登记公共宿主 | 精确 ShelfBookKey(sourceId,bookId)，真实 Core 目录 v2 issued target、entry.snapshot 完整 source/chapter/body/processing 与独立 progress.get CAS；与本地 LibraryBook/Original/ReadingSession 分离 | 正常退出仅同新 original ACK 后返回书架；unknown 保原记录、不重派，远程书签/搜索/TTS等未接工具不开放；本行不表示真实来源/网络/设备验收 |

Online 离页只关闭准确原 Host 搜索 session（Core 没有 online search cursor/close RPC）；每个 session 同时登记于既有 App client retiring-cleanup gate，VM scope 取消不取消已准入 original turn。关闭展示、退 session、释放大候选数据均不清原 acquisition/事务记录。标准文件入口读取完整单对象或数组，展示实际名称/URL后，用户明确只导入选中一个完整对象；不逐元素批量导入，不增 Host 批次事务或规则解释器。本文登记源码接入合同；准确方法、真实书源、网络、UI、产物和设备验收分别记账。


## A4 在线文字阅读宿主

本入口通过同 Library/client/gate 与实际 Core entry/progress 接线；在线内容不拥有 local format 或 Original。该登记随源码候选，尚无真实书源/网络/设备验收。

| 事实 | 公共组件及物理宿主 | 身份、准入与失效 |
| --- | --- | --- |
| 读取目录/规范正文失败或原保存确认后的书架读取失败 | ReaderPageState；OnlineDirectory / OnlineReading，或已退出后的 Bookshelf | 实际复合 key + UI directory UUID / reading UUID、Core chapter session token、同 VM generation；已知失败只展示，不自动重做 Core/HTTP。已确认退出先结束 reading，再刷新书架；刷新失败不派第二次保存 |
| 原在线进度保存未决 | ReaderRecoveryNotice；当前 online reading/directory 或 Bookshelf 原记录宿主 | 原 operationId + sourceId/bookId + 捕获 host UUID/generation；gate 后重新核原条目。只 reconcile 该原 wire；fresh get/列表/其它 ACK 不确认它，未决时不派新 save，不离开当前 reading 冒保存成功 |
| 原在线获取请求未决 | ReaderRecoveryNotice；原 reading/directory，返回 Bookshelf 后仅展示 | 实际 original id/request/runtime/immutable params；读状态只观察同 original，不清 barrier、不重发 HTTP。无有效原宿主不开放动作；现有 catalog later-ACK projection 缺口仍保守 OPEN |
| 已持久归档的原保存明确拒绝 | ReaderTransientFeedback；当时的 online host 或 Bookshelf | 独立 feedback UUID；只在 backend reconcile 已归档原 definite rejection 后发。仅同 UUID 退展示，不取消/重试/导航 |
| 协议未决 | 既有 ReaderProtocolRecoveryHost，reading 与非 reading 互斥宿主 | 原 protocol reference/presentation；仍先于业务错误分类。页面不冒认 ACK、不释放新外来 fault |

Native anchor 的 Boolean 仅为当前完整 reading/token/key 范围内的显示接纳；busy/同 key 未决/旧 opened 对象被拒，不推进 reflow 基准、不生成保存。现有450ms调度与正常退出均走同原 progress journal/ACK；离页、VM取消及隐藏消息不取消已准入 App turn。远程书签/正文搜索/TTS/格式/原件工具未接入，入口保持缺席。local 进度提示及旧新意图列表明确 sourceId=local，不能借相同 bookId 认领在线原件。
