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
| 单书源启停/删除原事务与原 HTTP 会话清理（候选、未执行） | Recovery / ReaderRecoveryNotice；成功短反馈复用 ReaderTransientFeedback | 同 App SourceRules owner、同 rules head；原 method/sourceId/observedSourceVersion + exact operationId/sequence/params/原 ACK；Workspace issued row对象、host/query/revision 及 VM gate 后再核 | Core receipt 与 Host cleanup 独立。unknown/candidate/fault 不发后续 mutation；已 queryQualified 原 ACK 后，显式继续只重做精确源清理，不重派 Core、不以列表当 ACK；隐藏/离页不取消原操作。新管理 method 仍为 schema1 有限判别变体，旧 import 原件不改解释 |
| 删除单书源确认（候选） | Confirmation / ReaderNativeConfirmation | 不可变 confirmation UUID + captured source object/sourceVersion + Workspace ticket；确认时及 VM gate 后核当前 issued row；后端 fresh sourceVersion 同 lane 再核 | dismiss/Back/离页仅退休该展示；重复/迟到确认不生新事务。确认文案只声明 Core source.delete 实际清理范围及精确 HTTP source 会话，不声明移出书架/清位置/删除本地 Original |
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


### A4 已展示正文的邻章窗口（源码候选）

| 事实 | 公共宿主 | 资格与退休 |
| --- | --- | --- |
| 正在准备前后各两章缓存 | 既有 LinearProgressIndicator，OnlineReading controls 内局部区域 | 实际 Native Ready 后，同 reading UUID/opened/session/full source-book-body-processing、App client lease、Core catalog revision 的一个窗口；默认自动调度，每章独立 App turn，控件仅反映本窗口 |
| 已知邻章准备失败 | ReaderPageState，OnlineReading controls；明确“重新准备邻章”动作 | 同 reading/window owner；失败不退出正文、不清原操作，不自动重试；动作入口与 gate 后再核原 reading/unknown/barrier |
| 窗口 acquisition/protocol 未决 | 本节既有 ReaderRecoveryNotice / ReaderProtocolRecoveryHost | 保实际原 request/runtime/params/terminal；晚结果仍归 App 原 owner。退休只停止后续调度，不能称 Native.cancel、join 或原 ACK |

“继续阅读（缓存）”只请求 Core 现存 entry，缺正文不隐式联网/升级。源已停用、删除或版本不同不抹除缓存正文与原位置；Core ready/fullscope 仍是正文准入，新 begin/read 与缺章网络获取另要求当前启用且同 sourceVersion。±1/±2 只来自 Core indexed navigation，显式切邻章须先同当前章实际新 Completed 原 ACK；窗口、缓存存在、当前位置查询都不是保存 ACK。本候选不开放近末尾目录刷新、整书下载、换源或后台退休 client 的新写入。

## A6 漫画阅读位置与图片窗口（源码候选）

| 事实 | 公共组件及宿主 | 身份、动作与失效 |
| --- | --- | --- |
| 首次章节/图片加载失败、保存位置的章节/manifest/page 无法匹配 | ReaderPageState；ReaderMangaReadingScreen | 当前 owner generation + 完整 chapter/manifest；重新打开须先无 dirty/在途原保存。失配不回落页首并覆盖原位置；没有 dirty/pending 时可以退出 |
| 后续图片窗口失败，原图片仍有效 | ReaderRecoveryNotice；漫画图片区域上方 | 原失败窗口 generation/revision/chapter/manifest/sourceRule/decode/start/limit；明确重试仅请求同失败窗口，新窗口原子接管后退休旧提示，不清已有位置 |
| 初次窗口超出受管图片字节预算且尚无可见图片 | ReaderPageState；漫画图片区域，明确“单页读取”动作 | 仅同 generation/chapter/准确 budget failedWindow 可用，读取原恢复目标的一页；该动作不创建翻页意图，不以限位后的显示位置覆盖原保存位置；失败继续保留原目标，不自动缩窗重试 |
| 漫画原进度保存未知或尚未确认 | ReaderRecoveryNotice；当前漫画宿主，协议事实另走既有 ReaderProtocolRecoveryHost | 实际 mangaProgress operationId + sourceId/bookId + owner generation；只查询该原件。隐藏提示、get 当前位置或新布局均不能确认原写入；未确认不自动重派、不完成退出 |
| 原进度被明确拒绝且已持久归档 | ReaderRecoveryNotice；漫画宿主，明确“不保存并返回”动作 | 仅 Service 签发的 ReaderMangaProgressRejected 原件与 owner 当前尝试相符时开放；点击先冻结测量，在 Mutex 内重新核同 generation/原件与 pending 为空，才舍弃本地未保存位置并退出；不删书，不改已确认 token/位置，不发 get/update，不自动重试。普通异常或 pending 为空不能提供该资格 |
| 实际保存执行中 | 页面现有进度状态与退出禁用 | 原 measured anchor + layout ticket + CAS token；只原 ACK 更新 lastConfirmedProgress。后台与退出经同串行 owner，450ms 调度仅取消未派发 timer，不取消已准入原操作 |

布局票据由 owner 单调签发；图片真实坐标和 draw/idle 观察只归当前 ticket/resource。首次恢复、尺寸变化、缩放和窗口替换建立显示基线，不制造保存；重排前真实 dirty 位置保留。旧 callback 不修改新页面；退出先冻结新测量，原保存确认或确实没有新写入后退休。此表登记接入与生命周期，不表示设备或用户验收。

短图的“前一段／继续阅读”为固定宿主的正常导航控件，源于当前准确布局实际可见范围与有界窗口；自动补窗只保持当前显示范围，不创建进度。明确跳段意图绑定 owner generation/window/目标页和恢复票据，唯有该目标真实绘制后，实际可见位置才可保存。窗口预算拒绝保留旧图片和真实 dirty；受管 encoded bytes 的限额不代表 Host 解码、Bitmap/GPU 或整进程预算已验收。

### A6 漫画目录与相邻章节（源码候选）

| 事实 | 公共组件及宿主 | 身份、动作与失效 |
| --- | --- | --- |
| 目录读取/定位失败，原目录资格需重新取得 | ReaderRecoveryNotice；漫画底部目录面板 | 当前 VM 目录代次与实际 issued directory；“重新读取目录”只交给 VM 的显式读取入口。error 时旧节点及“更多目录”均不可派发；新目录到达后旧对象失效。关闭面板仅隐藏展示，不取消已准入读取或保存 |
| 当前目录没有可展示条目 | ReaderPageState；同目录面板 | 仅当前 issued nodes 为空且不在读取、没有错误时展示；分组/disabled 行不能作为选章目标，更多分页只使用当前 nextOffset |
| 已确认没有前一章或后一章 | ReaderTransientFeedback；现有图片视口顶部覆盖消息列 | VM 签发的 notice id 与业务文案；到时只退休相同 id，不重读目录、不推算 index、不形成切章事务 |
| 保存、准备及真实呈现新章节 | 现有标题行进度与“取消”控件 | 同 chapterTransition id/originGeneration/issued node/targetIndex/landing；禁止新的选章、缩放、原生滚动与退出，取消只转交当前事务 owner。SAVING/PREPARING 不提交图位置，PRESENTING 保留真实 layout/draw/restore 路径 |
| 章节切换或候选图片真实呈现失败，原章节已保留/回退 | ReaderRecoveryNotice；图片视口顶部覆盖消息列；无自动重试 | chapterError 由 owner 发布；图片 bounds/bitmap 失败只回传原候选的 exact layoutTicket，超时由 owner 持有。消息不自行确认、保存或回退；旧票不能操作新章节 |

目录复用现有阅读 controls 的底部 Surface 面板规则，作为图片宿主的覆盖兄弟节点；目录开关不卸载图片、清理资源或改变阅读视口高度。上一章、目录、下一章及取消沿用现有标题行；已有 48.dp 图片段落 footer 保持。所有导航按钮与系统返回在原生 idle 且实际移动已被当前布局接纳后才准入，目录选章转交精确 issued node 对象，前后章由 VM 解析真实目录顺序。临时隐藏面板不取消业务；只有明确选章产生切章意图。此登记仅为源码接入与生命周期合同，构建、真实设备交互及用户验收另记。


A6 图片解码准入：Host 与显示缓存入口在完整像素解码前采用共享的逐图工作集预检（编码输入 16 MiB 与局部估算 32 MiB 分开；普通/区域输出预留软件像素格式回退上界）。超限由既有 PageState/失败窗口或章节恢复宿主展示，不自动降采样、切页或重试；章节候选仍按原 ticket 回退。该预检不代表多 Bitmap、GPU、GC 或全进程预算已闭合，也不声明长图分块显示已支持。

极窄长图的完整显示高度若无法由当前 Compose 约束表示，页面状态显示“图片尺寸超出当前显示范围。”，不进入 Image/aspectRatio 和像素解码；候选章节通过当前 layout ticket 走既有章节回退。按实际内容宽度和缩放重新计算，不裁剪尺寸、不跳过图片或更新阅读位置。此入口的长图分块替代仍开放。
