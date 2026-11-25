# COS30017 --- Android App Runtime & Regression Testing Guide

# COS30017 --- Android 应用运行与回归测试指南

> **Purpose / 目的:** Guide me step-by-step through running and testing
> the existing Campus Lost & Found Android project on an Android
> Emulator or physical device. The instructions and explanations must be
> bilingual (English + 简体中文). Do not make unnecessary code changes.
>
> 逐步指导我在 Android Emulator（模拟器）或真实 Android
> 手机上运行并测试现有 Campus Lost & Found
> 项目。所有指导与解释必须中英双语。不要进行不必要的代码修改。

------------------------------------------------------------------------

## Prompt to give your coding agent / 提交给 AI Coding Agent 的提示词

You are assisting with the COS30017 Android Development Project (Campus
Lost & Found). Code changes and static checks have already been
completed for the recent Claim Requests updates. Your current task is
**runtime testing and verification---not further feature development**.

你正在协助 COS30017 Android 开发项目（Campus Lost & Found）。近期 Claim
Requests
的代码修复和静态检查已完成。当前任务是**运行时测试和验证，而不是继续开发新功能**。

### 1. Rules of engagement / 工作原则

-   **Do not refactor, redesign, or add features unless a real test
    failure requires a targeted fix.**\
    除非实际测试发现问题且需要直接修复，否则不要重构、重新设计或添加功能。
-   First guide me through one step at a time. Give clear,
    beginner-friendly instructions in both English and Chinese.\
    首先一步一步指导我。每一步都提供清晰、适合初学者的中英双语说明。
-   Do not assume I have an emulator, a connected device, ADB, or
    Firebase configuration ready. Check and guide me to confirm.\
    不要假设我已经准备好模拟器、手机、ADB 或 Firebase
    配置。先检查并指导我确认。
-   For each step, explain **what to do, where to click/type, what I
    should see, and how to report the result**.\
    每一步说明**具体操作、点击/输入位置、预期看到的结果，以及如何反馈结果**。
-   Do not claim a test passed unless it was actually executed and its
    result observed. Distinguish **Passed / Failed / Not run /
    Blocked**.\
    只有在测试实际执行且观察到结果时才能说通过。明确区分**通过 / 失败 /
    未执行 / 受阻**。
-   If an issue is found, identify its scope and propose the smallest
    safe change. Explain the change bilingually, then run the relevant
    tests. Do not make unrelated changes.\
    如果发现问题，先判断范围并提出最小、稳妥的修复。中英双语解释修改，然后运行相关测试。不要修改无关内容。
-   Do not expose credentials, private keys, passwords, or sensitive
    account details. Do not ask me to share them.\
    不要暴露或要求我分享凭据、私钥、密码或敏感账号信息。
-   Keep a concise testing log as we progress.\
    随进度维护简洁的测试记录。

### 2. Initial setup and launch / 环境检查与启动

Guide me through:

1.  Open the project in Android Studio and wait for Gradle sync to
    finish.\
    在 Android Studio 中打开项目并等待 Gradle Sync 完成。
2.  Check whether an Android Emulator is available. If not, guide me to
    create one using Device Manager.\
    检查是否有可用模拟器；如果没有，指导我通过 Device Manager 创建。
3.  Alternatively, guide me to connect a physical Android device safely,
    including developer options and USB debugging, if needed.\
    或者指导我安全地连接真实 Android 手机，包括必要时启用开发者选项和
    USB 调试。
4.  Confirm the correct build variant (normally Debug) and run the app.\
    确认正确的构建变体（通常为 Debug）并运行 App。
5.  If launch fails, diagnose the exact error before changing code.\
    如果启动失败，先诊断具体错误，再决定是否修改代码。

### 3. Runtime test plan / 运行时测试计划

Run these tests in order where feasible. For each test, record the
steps, expected result, observed result, and status.

尽可能按以下顺序执行。每项记录操作步骤、预期结果、实际观察结果和状态。

#### A. Student flow / 学生流程

-   Student login / 学生登录
-   Browse and search items / 浏览与搜索物品
-   Open item details / 打开物品详情
-   Submit a Firestore Claim / 提交 Firestore Claim
-   Confirm the claim appears in My Claims under the current Firebase
    UID / 确认 My Claims 显示当前 UID 的 Claim
-   Verify input validation, including the claim description length
    boundaries (10 and 500 characters) and invalid values / 验证 Claim
    描述长度边界（10 和 500 字符）及无效输入

#### B. Room/local Claim flow / Room 本地 Claim 流程

-   Exercise the existing local Room-based Claim flow / 测试现有 Room
    本地 Claim 流程
-   Reject a claim and resubmit it / 拒绝 Claim 后再次提交
-   Confirm the rejected record is reused/reset as intended, with
    updated status and submission time /
    确认被拒记录按预期复用/重置，状态和提交时间更新
-   Verify that Room data and Firestore-backed claims do not overwrite
    each other / 验证 Room 数据与 Firestore Claims 不会互相覆盖

#### C. Admin review flow / 管理员审核流程

-   Sign in using an authorized admin test account /
    使用授权的管理员测试账号登录
-   Review pending claims / 查看 Pending Claims
-   Approve one claim when appropriate / 在适当情况下批准一个 Claim
-   Verify the approved claim, item state, and pending count / 验证
    Approved 状态、物品状态及待审计数
-   Confirm remaining pending cards no longer offer an invalid approval
    action, where applicable / 确认剩余待审卡片不再提供无效批准操作
-   Reject remaining claims one at a time / 逐条拒绝剩余 Claims
-   Verify count changes and final item state /
    验证计数变化及物品最终状态
-   Check that an item with pending claims cannot be deleted / 检查有
    Pending Claims 的物品是否无法删除

#### D. Authorization and security / 权限与安全

Use test accounts and safe test data only. Do not attempt destructive
actions on real users' data.

只使用测试账号和安全的测试数据。不要对真实用户数据进行破坏性操作。

-   Confirm students can read only their own claims /
    确认学生只能读取自己的 Claims
-   Confirm a student cannot approve or reject claims /
    确认学生无法审批或拒绝 Claims
-   Confirm admin-only actions require the appropriate Firebase custom
    role / 确认管理员操作需要正确的 Firebase custom role
-   If testing Rules against production, use a dedicated test project
    and test accounts; prefer the Emulator where possible /
    如果在正式环境测试 Rules，请使用专用测试项目和测试账号；优先使用
    Emulator

#### E. Instrumentation tests / Instrumentation 测试

-   Check whether an Emulator/device is connected and visible in Android
    Studio / 检查 Android Studio 是否识别模拟器或设备
-   Guide me to execute the Room instrumentation tests (not just compile
    them) / 指导我实际执行 Room instrumentation tests（不仅是编译）
-   Record whether they ran, failed, or were blocked (e.g. no
    ADB/device) / 记录测试已执行、失败或受阻（例如没有 ADB/设备）

#### F. Network and error handling / 网络与错误处理

-   Where safe, test a temporary network interruption /
    在安全情况下测试短暂网络中断
-   Observe loading, error, retry, and state recovery behavior /
    观察加载、错误提示、重试和状态恢复
-   Do not make risky changes or simulate destructive production
    scenarios / 不要进行高风险修改或模拟破坏性正式环境场景

### 4. Expected state logic to verify / 需要验证的状态逻辑

Verify against the implementation and actual rules; do not assume the
intended logic is correct without checking:

根据代码和实际 Rules 验证以下逻辑，不要未经检查就假设逻辑一定正确：

-   A claim approval/rejection and the relevant pending-count update
    must be consistent and atomic as designed.\
    Claim 批准/拒绝与对应待审计数更新必须按设计保持一致并原子执行。
-   Claims are processed individually in the current Firestore admin
    workflow.\
    当前 Firestore 管理员流程逐条处理 Claims。
-   When all pending claims are rejected and no claim is approved, the
    item should return to OPEN as designed.\
    当所有 Pending Claims 被拒绝且没有 Claim 获批时，物品应按设计恢复
    OPEN。
-   If a claim has been approved, the item should remain CLOSED while
    remaining pending claims are handled according to the
    implementation.\
    如果已有 Claim 获批，物品应保持 CLOSED，其他待审 Claim
    按实现逐条处理。
-   Verify Room and Firestore workflows remain separate. Historical Room
    claims are not automatically migrated to Firestore.\
    验证 Room 与 Firestore 流程保持分离。历史 Room Claims 不会自动迁移到
    Firestore。

### 5. Reporting format / 汇报格式

Maintain this table and update it as tests are performed:

  -------------------------------------------------------------------------------
  Test / 测试       Expected / 预期  Actual / 实际  Status / 状态  Notes / 备注
  ----------------- ---------------- -------------- -------------- --------------
  Student submits   Pending created  ---            Not run /      ---
  Claim / 学生提交  / 创建 Pending                  未执行         
  Claim                                                            

  My Claims / 我的  Own claims shown ---            Not run /      ---
  Claims            / 显示自己的                    未执行         
                    Claims                                         

  Admin approval /  Correct state    ---            Not run /      ---
  管理员批准        and count /                     未执行         
                    状态与计数正确                                 

  Admin rejection / Correct state    ---            Not run /      ---
  管理员拒绝        and count /                     未执行         
                    状态与计数正确                                 

  Room resubmission Rejected record  ---            Not run /      ---
  / Room 重提       reset /                         未执行         
                    被拒记录重置                                   

  Authorization /   Unauthorized     ---            Not run /      ---
  权限              action blocked /                未执行         
                    越权被拒绝                                     

  Instrumentation   Executed         ---            Not run /      ---
  tests /           successfully /                  未执行         
  Instrumentation   实际执行通过                                   
  测试                                                             

  Network recovery  Error/retry      ---            Not run /      ---
  / 网络恢复        behaves                         未执行         
                    correctly /                                    
                    错误与重试正常                                 
  -------------------------------------------------------------------------------

Use only these status values: - **Passed / 通过** - **Failed / 失败** -
**Not run / 未执行** - **Blocked / 受阻**

### 6. Final summary / 最终总结

At the end, produce a concise bilingual report containing:

-   Environment and device used / 环境及设备
-   Tests actually executed / 实际执行的测试
-   Passed, failed, not run, and blocked tests /
    通过、失败、未执行及受阻的测试
-   Issues found and minimal fixes made / 发现的问题及最小修复
-   Remaining risks and unverified items / 剩余风险及未验证项目
-   Evidence I should capture for my COS30017 report / COS30017
    报告需要保存的证据
-   Recommended next step / 建议的下一步

Do not start additional feature development unless I explicitly ask for
it. Begin with the first environment check and wait for my response
before proceeding to the next step.

除非我明确要求，否则不要开始新的功能开发。请从第一项环境检查开始，并等待我的反馈再进入下一步。
