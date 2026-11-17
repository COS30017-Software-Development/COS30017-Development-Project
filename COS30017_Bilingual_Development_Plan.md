# COS30017 Campus Lost & Found App — Bilingual Development Plan
# COS30017 校园失物招领 App — 双语开发计划

> Source / 依据：COS30017 Project v2.pdf (primary rubric), COS30017 Project.pdf, COS30017 Plan.docx.  
> Strategy / 策略：Complete and test all core requirements before implementing advanced extensions. / 先完成并测试全部基础要求，再开发进阶功能。

## Table of Contents / 简易目录

1. Scope and grading notes / 范围与评分注意事项
2. Phase 1: Basic requirements / 第一阶段：基础要求
   1. Project setup and proposal / 项目初始化与提案
   2. Data models and local persistence / 数据模型与本地持久化
   3. Login and role separation / 登录与角色区分
   4. Student UI and navigation / 学生界面与导航
   5. Item CRUD / 物品帖文增删改查
   6. Claims and admin management / 认领与管理员管理
   7. Core testing and milestone / 基础测试与里程碑
3. Phase 2: Additional requirements / 第二阶段：进阶要求
   1. Firebase backend and authentication / Firebase 后端与身份验证
   2. Image upload / 图片上传
   3. Advanced search / 高级搜索
   4. Push notifications / 推送通知
   5. Integration tests / 整合测试
4. Phase 3: Report and demonstration / 第三阶段：报告与演示
5. Suggested schedule / 建议时间表
6. Final acceptance checklist / 最终验收清单

---

## 1. Scope and grading notes / 范围与评分注意事项

**中文：** 项目名称为 Campus Lost & Found App，面向学生和管理员。学生可以浏览、发布、修改和删除自己的失物或拾获物品帖文，提交认领申请并查看状态；管理员可以管理所有帖文、审批申请及更新物品状态。必须让两种角色具有真正不同的操作权限。正式全面开发前，应取得 lecturer 对题目的批准。

**English:** Campus Lost & Found App serves students and administrators. Students browse, create, edit and delete their own lost/found posts, submit claims, and track statuses. Administrators manage all posts, review claims, and update item statuses. The two roles must have meaningfully different permissions. Obtain lecturer approval before full implementation.

**Rubric clarification / 评分说明：** The v2 rubric's summary lists Core = 27 and Advanced = 13 (total 40), and Tasks 1–6 sum to 27 (3+4+6+4+5+5), but the Section A heading says 25. Confirm the discrepancy with the lecturer. / v2 总表和分项合计均为基础 27 分、进阶 13 分，但 Section A 标题写 25 分；需向老师确认。

**Scope decision / 范围决定：** Use Kotlin + XML layouts + Room (SQLite) for the core. Firebase Authentication, Cloud Firestore, Firebase Storage and FCM belong to the advanced phase. Simple name search may be basic; combined filters/sorting are advanced. This is an implementation recommendation, not an explicit mandate from the rubric. / 基础版建议使用 Kotlin、XML 和 Room；Firebase 系列留到进阶。基础版可做名称搜索，组合筛选与排序留到进阶。这是实施建议，不是评分文件指定的唯一方案。

## 2. Phase 1: Basic requirements / 第一阶段：基础要求

### Step 1 — Project setup and proposal / 步骤一：项目初始化与提案

**中文任务：**
- [ ] 确认 lecturer 已批准项目题目。
- [ ] 在 Android Studio 建立 Kotlin + XML Android 项目，并确认模拟器可以运行。
- [ ] 整理项目目标、目标用户、Student/Admin 权限、功能清单。
- [ ] 画导航流程图：Login → Student Home → Item Details / Add-Edit / My Posts / My Claims；Login → Admin Dashboard → Manage Posts / Manage Claims。
- [ ] 简述移动端设计：可读字体、适当间距、滚动表单、软键盘处理和不同屏幕尺寸。

**English tasks:**
- [ ] Confirm lecturer approval; create and run the Kotlin/XML project in Android Studio.
- [ ] Define the purpose, target users, features, and separate Student/Admin permissions.
- [ ] Draw the navigation flow for student and admin screens.
- [ ] Document mobile usability considerations: readable text, spacing, scrollable forms, keyboard handling and screen sizes.

**Deliverable / 产出：** Runnable empty project, proposal outline, navigation diagram. / 可运行的空项目、提案大纲、导航图。

### Step 2 — Data models and local persistence / 步骤二：数据模型与本地持久化

**中文任务：**
- [ ] 定义 `User(id, name, email, role)`、`Item(id, name, description, category, location, date, type, status, ownerId, imageUri?)`、`Claim(id, itemId, studentId, description, status, requestDate)`。
- [ ] 使用 Room 建立 Entity、DAO、Database、Repository；先准备 Student 和 Admin 演示账户与样本数据。
- [ ] 定义状态：Item = Open / Claimed / Closed；Claim = Pending / Approved / Rejected。
- [ ] 先确定关联和业务规则：只有本人能修改自己的帖文；管理员可以管理全部；只有 Found 类型且可认领的物品能提交 Claim；已处理申请不能重复审批。
- [ ] 测试关闭并重新打开 App 后数据仍存在。

**English tasks:**
- [ ] Create User, Item and Claim models with identifiers and relationships.
- [ ] Implement Room entities, DAOs, database and repository, with seeded demo accounts and sample data.
- [ ] Define item and claim statuses and enforce ownership/role rules in business logic, not only by hiding buttons.
- [ ] Verify persistence after app restart.

**Deliverable / 产出：** Working local database and reusable repository. / 可用的本地数据库与 Repository。

### Step 3 — Login and role separation / 步骤三：登录与角色区分

**中文任务：**
- [ ] 建立 Login 页面、基础注册表单及输入验证；基础版可采用本地演示身份机制，不要把它描述为生产级安全认证。
- [ ] 登录后按角色进入 Student Home 或 Admin Dashboard。
- [ ] 建立 Session 管理及 Logout；阻止学生执行管理员操作。
- [ ] 测试空输入、错误凭证、角色权限与返回导航。

**English tasks:**
- [ ] Build login and basic registration with input validation. A local demo login is acceptable as a planned core approach, but must not be presented as production-grade security.
- [ ] Route students and admins to different destinations; implement session and logout.
- [ ] Enforce role restrictions and test invalid credentials and unauthorized actions.

**Deliverable / 产出：** Functional role-based entry flow. / 可运行的角色登录流程。

### Step 4 — Student UI and navigation / 步骤四：学生界面与导航

**中文任务：**
- [ ] 完成 Login/Register、Student Home、Item Details、Add/Edit Item、My Posts、My Claims。
- [ ] 使用 RecyclerView 显示物品列表，适当使用 EditText、Spinner/Dropdown、DatePicker、Buttons。
- [ ] 点击物品进入详情，通过 Intent extras 或合适的机制传递 `itemId`；页面从 Repository 读取完整记录。
- [ ] 对加载、空列表、成功、失败和表单错误提供反馈。
- [ ] 检查小屏幕、旋转（如支持）和软键盘遮挡。

**English tasks:**
- [ ] Build the six student-facing screens and connect navigation.
- [ ] Use suitable Android UI controls and a RecyclerView list.
- [ ] Pass an item ID between screens (e.g., Intent extras), then load details from the repository.
- [ ] Handle empty/error/success states and test screen sizes and keyboard behavior.

**Deliverable / 产出：** Navigable student interface. / 完整可导航的学生界面。

### Step 5 — Item CRUD / 步骤五：物品帖文 CRUD

**中文任务：**
- [ ] Create：学生发布 Lost / Found 帖文，验证必填字段与日期。
- [ ] Read：首页和详情页从数据库显示帖文；My Posts 只显示当前学生自己的帖文。
- [ ] Update：只有帖文所有者可编辑自己的帖文；保存后列表与详情同步更新。
- [ ] Delete：删除前确认；定义相关 Claim 的处理策略，避免孤立数据。
- [ ] 添加基础名称搜索（可选但符合原 Plan），不要在此阶段投入复杂筛选逻辑。

**English tasks:**
- [ ] Implement Create, Read, Update and Delete for item posts, with validation and confirmation.
- [ ] Filter My Posts by the current user; enforce owner-only editing/deletion.
- [ ] Keep displayed data consistent and handle claims related to deleted items.
- [ ] Optionally add basic name search; defer complex filtering.

**Deliverable / 产出：** Persistent, validated, role-aware CRUD. / 有持久化与权限控制的 CRUD。

### Step 6 — Claims and admin management / 步骤六：认领与管理员管理

**中文任务：**
- [ ] 学生从 Found Item Details 提交 Claim Description，初始状态 Pending。
- [ ] My Claims 显示自己的申请及状态。
- [ ] 建立 Admin Dashboard、Manage Posts、Manage Claim Requests。
- [ ] 管理员查看全部帖文、删除不合适或重复帖文、批准或拒绝申请，并更新物品状态。
- [ ] 明确业务规则：拒绝申请不应自动将物品标记 Claimed；批准申请后如何更新 Item 状态、其他 Pending 申请如何处理，必须统一且可测试。
- [ ] 将物品实际归还后的完成操作与 Approved 状态区分（如将 Item 标记 Closed）。

**English tasks:**
- [ ] Allow students to submit claims for eligible found items and view Pending/Approved/Rejected status.
- [ ] Build admin dashboard, post management and claim review screens.
- [ ] Allow admins to approve/reject claims and update item statuses.
- [ ] Define consistent status transitions and what happens to competing claims and completed returns.

**Deliverable / 产出：** End-to-end Student → Claim → Admin Review → Student Status workflow. / 学生申请到管理员审批的完整流程。

### Step 7 — Core testing and milestone / 步骤七：基础测试与里程碑

**中文任务：**
- [ ] 逐一测试登录、导航、CRUD、权限、认领、审批、数据重启保留。
- [ ] 测试空名称、无效日期、重复提交、错误登录、无权限修改、已关闭物品认领等边界情况。
- [ ] 建立测试记录：Test ID、Steps/Input、Expected、Actual、Pass/Fail、Fix。
- [ ] 修复错误，整理代码命名、Repository/Activity 职责、重复逻辑和注释。
- [ ] 截取每个主要页面及成功/失败案例的截图，保存可演示样本数据。

**English tasks:**
- [ ] Test all main flows, invalid inputs, boundaries, authorization and persistence.
- [ ] Record test cases, expected and actual results, failures and fixes.
- [ ] Refactor code and collect screenshots and demo data.

**Core completion gate / 基础版完成标准：** With no Firebase dependency, both roles can log in, student item CRUD works, claims can be submitted and reviewed, status updates persist, and tests are documented. Do not begin advanced features before this gate is met. / 不依赖 Firebase，也能完成双角色、CRUD、认领审批、状态持久化及测试记录，才进入进阶阶段。

## 3. Phase 2: Additional requirements / 第二阶段：进阶要求

### Step 8 — Firebase backend and authentication (up to 4 marks) / Firebase 后端与身份验证（最高 4 分）

**中文任务：**
- [ ] 建立 Firebase 项目并整合 Firebase Authentication 与 Cloud Firestore。
- [ ] 将 Repository 抽象为可替换的数据来源，逐步将 User/Item/Claim 数据迁移到云端。
- [ ] 在 Firestore Security Rules 强制学生只能修改自己的帖文、管理员才能审批及管理全部帖文；管理员角色不能仅由客户端自行声明。
- [ ] 测试不同账号/设备之间的数据同步、网络失败及重试/错误提示。

**English tasks:**
- [ ] Integrate Firebase Authentication and Cloud Firestore; migrate records through a repository abstraction.
- [ ] Enforce ownership and admin privileges using Firestore security rules and trusted role assignment.
- [ ] Demonstrate real cloud CRUD, cross-account synchronization and network-error handling.

**Evidence / 证据：** Cloud records changing after app operations; permission and offline/error tests. / App 操作影响云端记录的证据，以及权限和网络异常测试。

### Step 9 — Image upload (additional feature) / 图片上传（附加功能）

**中文任务：**
- [ ] 使用 Android Photo Picker 选择照片，先显示预览。
- [ ] 上传至 Firebase Storage，在 Item 记录中保存图片路径或 URL。
- [ ] 处理取消、上传失败、加载失败及无图片占位图；限制文件类型/大小。
- [ ] 配置 Storage Rules，避免任意用户覆盖其他人的照片。

**English tasks:**
- [ ] Add photo selection/preview and Firebase Storage upload.
- [ ] Save image references on items; handle upload errors and missing images.
- [ ] Validate file type/size and enforce storage access rules.

### Step 10 — Advanced search and filtering (additional feature) / 高级搜索与筛选（附加功能）

**中文任务：**
- [ ] 支持按名称、Category、Lost/Found 类型组合筛选。
- [ ] 可选：按日期排序、仅显示 Open 项目、清除筛选条件。
- [ ] 测试无结果、大小写、空查询和筛选组合；确保不是只做外观控件。

**English tasks:**
- [ ] Combine text search with category and Lost/Found filters; optionally add date sorting and Open-only filtering.
- [ ] Test empty results, case handling and combined filters.

### Step 11 — Push notifications (up to 3 marks) / 推送通知（最高 3 分）

**中文任务：**
- [ ] 整合 Firebase Cloud Messaging (FCM)，处理 Android 通知权限和 device token。
- [ ] 当管理员批准或拒绝 Claim 时，由可信后端触发给申请学生的通知；不要把 FCM server credentials 放在 Android 客户端。
- [ ] 测试前台、后台、通知点击跳转和未授权通知权限的情况。

**English tasks:**
- [ ] Integrate FCM and handle notification permission and device tokens.
- [ ] Trigger claim approval/rejection messages from a trusted backend, not with server credentials embedded in the app.
- [ ] Test foreground/background delivery and notification navigation.

### Step 12 — Advanced integration testing / 进阶整合测试

**中文任务：**
- [ ] 完整重测基础功能，确保云端迁移没有破坏 CRUD 与角色权限。
- [ ] 测试两个账号、网络中断、图片失败、重复审批及通知状态。
- [ ] 记录 Advanced Feature 的实际操作截图、日志与测试结果。
- [ ] 如时间不足，优先保住稳定基础版，再完成一个端到端可演示的进阶功能；不要堆积未完成的功能。

**English tasks:**
- [ ] Run regression tests on core features after Firebase integration.
- [ ] Test multiple accounts, network failures, upload failures, duplicate approvals and notifications.
- [ ] Capture evidence; prioritize a fully working extension over several unfinished ones.

## 4. Phase 3: Report and demonstration / 第三阶段：报告与演示

**中文任务：**
- [ ] 报告说明 App Purpose、Target Users、Main Features、Student/Admin Roles。
- [ ] 插入导航流程图和每个主要 UI 页面截图，每张配 1–2 句说明。
- [ ] 解释关键实现选择：Kotlin/XML、数据模型、Room、角色权限、Intent/导航、Firebase（如有）。
- [ ] 附测试表格：Expected vs Actual、Pass/Fail、遇到的问题和修复。
- [ ] 说明进阶功能及演示证据，并列出外部资料、libraries、教程与 AI assistance。
- [ ] 压缩可构建的 Android Studio 项目，上传 Google Drive 或 OneDrive，将链接放在报告封面。
- [ ] 准备学生与管理员演示账号、样本帖文、Pending Claim；现场演示发布 → 浏览 → 认领 → 审批 → 查看状态。
- [ ] 练习解释关键代码，并准备回答小幅修改问题。

**English tasks:**
- [ ] Report the purpose, users, features, roles, navigation diagram and UI screenshots (1–2 explanatory sentences per screen).
- [ ] Explain implementation decisions, testing evidence, issues/fixes and advanced features.
- [ ] Acknowledge external resources, libraries, tutorials and AI assistance.
- [ ] Zip the buildable project, upload it to Drive/OneDrive, and place the link on the report cover.
- [ ] Prepare demo accounts/data and rehearse the full student-to-admin workflow and code explanations.

## 5. Suggested schedule / 建议时间表

> Relative schedule only; no submission date was provided. / 以下是相对时间安排，尚未提供实际截止日期。

| Week / 周 | Focus / 重点 | Completion target / 完成标准 |
|---|---|---|
| 1 | Setup, approval, diagrams, models / 初始化、批准、流程图、模型 | App runs; design approved / 项目可运行，设计获批准 |
| 2 | Room, login, roles, student UI / 数据库、登录、角色、学生 UI | Persistent data and navigation / 数据持久化及导航完成 |
| 3 | Item CRUD, claims, admin / CRUD、认领、管理 | Full core workflow / 核心流程完成 |
| 4 | Core tests, fixes, screenshots / 基础测试、修复、截图 | **Core milestone passed / 基础版验收通过** |
| 5 | Firebase Auth + Firestore / 云端身份与数据库 | Cloud CRUD and rules / 云端 CRUD 与权限可用 |
| 6 | Images, advanced search / 图片、高级搜索 | Integrated extensions / 进阶功能可演示 |
| 7 | FCM, regression testing / 通知、回归测试 | Stable app and evidence / 稳定应用及证据 |
| 8 | Report, zip, demo rehearsal / 报告、打包、演练 | Submission ready / 可以提交 |

## 6. Final acceptance checklist / 最终验收清单

### Core / 基础
- [ ] Lecturer approval and clear proposal / 老师批准及明确提案
- [ ] Navigation diagram / 导航图
- [ ] Two roles with real permission differences / 双角色真实权限差异
- [ ] Complete, usable mobile UI / 完整移动端 UI
- [ ] Activity/screen navigation and data passing / 页面导航及数据传递
- [ ] Input validation and meaningful feedback / 输入验证与反馈
- [ ] Persistent Item CRUD / 物品 CRUD 与持久化
- [ ] Claim submission and admin approval/rejection / 认领申请与审批
- [ ] Test cases, results and bug fixes / 测试案例、结果及修复
- [ ] Readable code and confident explanation / 可维护代码及演示能力

### Advanced / 进阶
- [ ] Firebase cloud CRUD, authentication, access rules and network handling / 云端 CRUD、认证、权限与网络处理
- [ ] Image upload with storage permissions / 图片上传与权限
- [ ] Integrated advanced search/filtering / 整合高级搜索筛选
- [ ] Event-driven push notifications / 事件触发推送通知
- [ ] Advanced feature test evidence / 进阶测试证据

### Submission / 提交
- [ ] Report with navigation diagram, UI screenshots, implementation, testing and references / 完整报告
- [ ] Buildable zipped source project and report-cover Drive/OneDrive link / 可构建项目压缩包与封面链接
- [ ] Sample data and individual demonstration prepared / 样本数据与个人演示准备完成

---

**Priority principle / 优先原则：** A complete, tested core application is more valuable than a feature-rich but unstable prototype. Advanced marks depend on working integration and your ability to explain it. / 完整且经过测试的基础版优先于功能很多但不稳定的原型；进阶评分看实际整合、正确性及你能否解释实现。
