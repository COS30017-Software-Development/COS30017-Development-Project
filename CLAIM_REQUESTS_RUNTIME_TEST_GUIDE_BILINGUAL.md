# Claim Requests Runtime Test Guide / Claim Requests 运行测试指南

This guide covers the current Claim Requests implementation. It is for manual testing on an Android Studio Emulator or Android phone, plus the Room instrumentation test. Follow it with a dedicated Firebase test project and disposable accounts only.

本指南对应当前 Claim Requests 实现，说明如何在 Android Studio 模拟器或 Android 手机上进行手动测试，以及如何运行 Room Instrumentation Test。请仅使用专用 Firebase 测试项目和临时测试账号。

## Important project behavior / 项目行为须知

- The home feed and **Add Item** screen use Firestore. The regular app UI does not create or list Room-only items. Existing Room Claim behavior is therefore tested through `LostAndFoundRepositoryTest`; a Room-only item may not be reachable through the normal UI.
- 首页和 **Add Item** 页面使用 Firestore。当前常规 UI 不创建或展示仅存于 Room 的物品。因此 Room Claim 流程通过 `LostAndFoundRepositoryTest` 测试；仅存于 Room 的物品可能无法从常规 UI 打开。
- **My Claims** subscribes to Firestore Claims for the signed-in Firebase UID. Historical Room Claims are not migrated to or displayed by this screen.
- **My Claims** 订阅当前 Firebase UID 对应的 Firestore Claims。历史 Room Claims 不会自动迁移，也不会显示在此页面。
- Firestore admins are authorized by the Firebase Custom Claim `role=ADMIN`. The admin dashboard also depends on the local Room user role. Use an account provisioned for both; changing a local profile role alone does not grant Firestore permissions.
- Firestore 管理员权限要求 Firebase Custom Claim 为 `role=ADMIN`；管理员入口还依赖本地 Room 用户角色。请使用两处均已配置的测试账号；只修改本地角色不会获得 Firestore 权限。
- The app does not connect to the Firestore Emulator in its normal runtime path. `firebase.json` declares an Auth Emulator port, but not a Firestore Emulator, and the app code does not configure emulator hosts. Normal app testing therefore uses the Firebase project in the app's existing Google Services configuration. Use a test project, never production data.
- App 的常规运行路径没有连接 Firestore Emulator。`firebase.json` 声明了 Auth Emulator 端口，但没有 Firestore Emulator；App 代码也没有配置 Emulator 主机。因此常规 App 测试会使用现有 Google Services 配置对应的 Firebase 项目。请使用测试项目，勿操作正式数据。

## 1. Prepare Android Studio and a device / 准备 Android Studio 和设备

### Emulator / 模拟器

1. Open the repository root in Android Studio and wait for **Gradle Sync** to finish. Resolve any SDK/license prompt through Android Studio before running.
   在 Android Studio 中打开仓库根目录，等待 **Gradle Sync** 完成。如提示安装 SDK 或接受许可，请先按 Android Studio 提示处理。
2. Select **Tools → Device Manager → Create Device**. Choose a Pixel device profile, then select an available Android system image with API 24 or newer. Download the image if prompted and finish creation.
   选择 **Tools → Device Manager → Create Device**。选择 Pixel 设备型号，再选 API 24 或更高版本的 Android 系统镜像；如需下载，完成下载后创建设备。
3. Start the virtual device from Device Manager and wait until Android reaches its home screen.
   在 Device Manager 中启动模拟器，并等待 Android 主屏幕完全显示。

### Physical Android phone / Android 手机

1. On the phone, enable **Developer options** (usually by tapping **Build number** seven times in **Settings → About phone**), then enable **USB debugging**.
   在手机中启用**开发者选项**（通常在**设置 → 关于手机**中连续点按**版本号**七次），然后启用 **USB 调试**。
2. Connect the phone by USB, unlock it, and accept the USB debugging authorization prompt. Select the phone from Android Studio's device selector.
   使用 USB 连接并解锁手机，接受 USB 调试授权提示，然后在 Android Studio 的设备选择器中选中手机。

### Launch the Debug app / 启动 Debug App

1. In Android Studio, select the **app** run configuration and the emulator/phone, then click **Run** (green triangle). Confirm that the app opens to the Campus Lost & Found home screen.
   在 Android Studio 中选择 **app** 运行配置和模拟器/手机，点击绿色 **Run** 按钮。确认 App 打开并显示 Campus Lost & Found 首页。
2. If you prefer the Android Studio Terminal, run from the project root:
   如需使用 Android Studio Terminal，请在项目根目录运行：

   ```powershell
   .\gradlew.bat assembleDebug --console=plain
   ```

3. Do not share `google-services.json`, passwords, ID tokens, or screenshots containing personal account data when reporting setup problems.
   报告配置问题时，不要分享 `google-services.json`、密码、ID Token 或包含个人账号信息的截图。

## 2. Student login and basic browsing / 学生登录与基本浏览

1. Use a disposable student test account. If creating one in the app, switch to **Sign Up**, enter the requested name, student number, email, and password, and submit. Registration uses Firebase Authentication and creates/links a local Room profile.
   使用临时学生测试账号。如需在 App 中注册，请切换至 **Sign Up**，填写姓名、学号、邮箱和密码并提交。注册会使用 Firebase Authentication，并创建/关联本地 Room 用户资料。
2. Sign in, open the **Items** page, browse or search for a **Found** item, and open its details. Confirm title, location, category, and status are displayed.
   登录后打开 **Items** 页面，浏览或搜索 **Found** 物品并进入详情页。确认标题、地点、类别和状态均显示正确。
3. A Lost item, a closed item, or an item posted by the current student must not offer a usable Claim action.
   Lost 物品、已关闭物品或当前学生自己发布的物品不应允许提交 Claim。
4. To test description boundaries, use separate Found items or a fresh student/item pair: 9 trimmed characters should be rejected; 10 and 500 should be accepted; 501 should be rejected. The form trims surrounding whitespace before validation.
   测试描述长度边界时，请使用不同的 Found 物品或全新的学生/物品组合：去除首尾空格后 9 个字符应被拒绝；10 和 500 个字符应通过；501 个字符应被拒绝。表单会先去除首尾空格再验证。

## 3. Room Claim flow and resubmission / Room Claim 流程与重新提交

### Run the existing Room instrumentation test / 运行现有 Room Instrumentation Test

1. In Android Studio's **Project** pane, open:
   在 Android Studio 的 **Project** 窗格中打开：

   `app\src\androidTest\java\com\example\cos30017developmentproject\data\local\LostAndFoundRepositoryTest.kt`

2. Start an emulator or connect a phone. Click the green gutter icon beside the test method `rejectedRoomClaimCanBeResubmittedWithoutCreatingDuplicate` and choose **Run**. To run the class, use the green icon beside `LostAndFoundRepositoryTest`.
   启动模拟器或连接手机。点击测试方法 `rejectedRoomClaimCanBeResubmittedWithoutCreatingDuplicate` 左侧的绿色运行图标并选择 **Run**。如需运行整个测试类，点击 `LostAndFoundRepositoryTest` 类声明旁的绿色图标。
3. Expected result: the Room Claim is initially submitted, changed to Rejected, then resubmitted using the same record; its status returns to Pending, its description is updated, the item becomes Claimed, and the pending count is one.
   预期结果：Room Claim 首次提交后被改为 Rejected，再次提交时复用同一条记录；状态恢复为 Pending、描述更新、物品变为 Claimed，待审数为 1。
4. The test uses an in-memory Room database. It verifies repository/DAO behavior, not the full Activity UI. There is currently no normal UI path to create a Room-only item.
   此测试使用内存 Room 数据库，验证 Repository/DAO 行为，不会启动完整 Activity UI。当前没有通过常规 UI 创建仅存于 Room 的物品的入口。
5. To execute instrumentation tests from the Android Studio Terminal instead, keep a device running and run:
   如需从 Android Studio Terminal 执行 Instrumentation Tests，请保持设备运行并执行：

   ```powershell
   .\gradlew.bat connectedDebugAndroidTest --console=plain
   ```

   `assembleDebugAndroidTest` only compiles/packages the test APK; it does not execute the tests.
   `assembleDebugAndroidTest` 只编译/打包测试 APK，不会实际执行测试。

## 4. Firestore Claim submission and My Claims / Firestore Claim 提交与 My Claims

1. Sign in as a **different** test student from the account that owns the Found item. If needed, create a Found item using **Add Item** while signed in as the owner; Add Item writes to Firestore.
   使用与 Found 物品发布者**不同**的测试学生账号登录。如需新物品，请先以发布者账号通过 **Add Item** 创建 Found 物品；Add Item 会写入 Firestore。
2. Open that Found item's details, submit a valid 10–500 character description, and confirm the success message. The details status should immediately change to **Claimed**, and the Claim button should disappear.
   打开该 Found 物品详情，提交 10–500 个字符的有效描述并确认成功提示。详情页状态应立即变为 **Claimed**，Claim 按钮应消失。
3. Open **My Claims** from the profile/drawer navigation. Confirm the new card shows the item's title, description, **Pending** status, and submission date. The list is live-updated from Firestore and scoped to the signed-in Firebase UID.
   从 Profile/抽屉导航打开 **My Claims**。确认新卡片显示物品标题、描述、**Pending** 状态及提交日期。列表由 Firestore 实时更新，并按当前 Firebase UID 过滤。
4. Sign out and sign in as another student. That student's My Claims must not display the first student's Claim.
   退出并切换到另一个学生账号。另一个学生的 My Claims 不应显示第一位学生的 Claim。

## 5. Multiple Claims and Admin review / 多个 Claims 与管理员审核

### Prepare two Pending Claims / 准备两个 Pending Claims

1. Use a fresh Found item with `status = OPEN` and `pendingClaimCount = 0`. Its owner must be different from both claimant accounts.
   使用一个新的 Found 物品，初始 `status = OPEN`、`pendingClaimCount = 0`；物品发布者不能与两位申请学生相同。
2. Sign in as Student A and submit a Claim, then sign in as Student B and submit a Claim for the same item. Both Claims should be Pending. In Firebase Console → **Firestore Database → Data**, verify the item's `status` is `CLAIMED`, `pendingClaimCount` is `2`, and there are two documents in `claims`.
   先以学生 A 登录并提交 Claim，再以学生 B 登录并对同一物品提交 Claim。两个 Claim 都应为 Pending。在 Firebase Console → **Firestore Database → Data** 中确认物品 `status` 为 `CLAIMED`、`pendingClaimCount` 为 `2`，且 `claims` 中有两条记录。

### Reject all Claims / 拒绝全部 Claims

1. Sign in with the authorized admin test account and open **Admin Dashboard → Claim Review**. Reject one of the two Pending Claims.
   使用已授权的管理员测试账号登录，打开 **Admin Dashboard → Claim Review**，拒绝其中一个 Pending Claim。
2. Expected after the first rejection: that Claim is Rejected; the other remains Pending; the item stays `CLAIMED`; `pendingClaimCount` becomes `1`.
   第一次拒绝后的预期：该 Claim 变为 Rejected，另一个仍为 Pending，物品保持 `CLAIMED`，`pendingClaimCount` 变为 `1`。
3. Reject the remaining Claim. Expected: both Claims are Rejected, the count is `0`, and the item returns to `OPEN`.
   再拒绝剩余 Claim。预期：两个 Claim 均为 Rejected，计数变为 `0`，物品恢复为 `OPEN`。
4. Sign in as Student A and submit again for the same item. Expected: the rejected Firestore document is reused/reset to Pending, its `submittedAt` is refreshed, and the item becomes `CLAIMED` with count `1`.
   以学生 A 登录并对同一物品重新提交。预期：复用/重置原 Firestore Claim 文档为 Pending，刷新 `submittedAt`，物品变为 `CLAIMED` 且计数为 `1`。

### Approve one Claim / 批准一个 Claim

1. Use a **different fresh** Found item and repeat the two-student submission. Approve one Claim.
   使用**另一个全新** Found 物品，重复两位学生提交步骤，然后批准其中一个 Claim。
2. Expected: the selected Claim becomes Approved, the item becomes `CLOSED`, and the pending count decreases by one. The other Claim remains Pending until processed.
   预期：选中的 Claim 变为 Approved，物品变为 `CLOSED`，待审数减 1。另一个 Claim 在处理前仍为 Pending。
3. For that same item, the remaining Pending card should offer **Reject** but not **Approve**. Reject it. Expected: its status becomes Rejected, the count reaches `0`, and the item stays `CLOSED` because a Claim was already approved.
   对同一物品，剩余 Pending 卡片应只提供 **Reject**，不再提供 **Approve**。拒绝该 Claim 后，预期其状态变为 Rejected、计数变为 `0`；由于已有 Claim 获批，物品仍保持 `CLOSED`。

### Verify Firestore records / 核对 Firestore 记录

In Firebase Console, inspect only your test documents:

在 Firebase Console 中仅检查测试文档：

- `items/{itemId}`: `status`, `pendingClaimCount`, `itemId`, `postedBy`.
- `items/{itemId}`：检查 `status`、`pendingClaimCount`、`itemId`、`postedBy`。
- `claims/{studentUid}_{itemId}`: `studentId`, `itemId`, `description`, `status`, `submittedAt`.
- `claims/{studentUid}_{itemId}`：检查 `studentId`、`itemId`、`description`、`status`、`submittedAt`。
- Do not delete test items while `pendingClaimCount` is nonzero. Use test data only and clean it up only after all Claims are resolved.
- `pendingClaimCount` 非零时不要删除测试物品。仅使用测试数据，并在所有 Claim 处理完后再清理。

## 6. Results log / 测试记录

Record the observed result rather than marking a test passed based only on the expected behavior.

请记录实际观察结果，不要仅根据预期行为标记通过。

| Test / 测试 | Expected / 预期 | Actual / 实际 | Status / 状态 |
|---|---|---|---|
| Student login and browse / 学生登录与浏览 | Login works; Found details open / 登录成功；可打开 Found 详情 |  | Not run / 未执行 |
| Room Claim resubmission test / Room Claim 重提测试 | Same record is reset to Pending / 同一记录重置为 Pending |  | Not run / 未执行 |
| Firestore Claim and My Claims / Firestore 提交与 My Claims | Own Claim appears with correct status / 显示自己的 Claim 且状态正确 |  | Not run / 未执行 |
| Reject multiple Claims / 拒绝多个 Claims | Count decrements; all rejected returns item to OPEN / 计数递减；全部拒绝后物品为 OPEN |  | Not run / 未执行 |
| Approve then reject remaining / 批准后拒绝其余 Claim | Item stays CLOSED; remaining count reaches zero / 物品保持 CLOSED；剩余计数归零 |  | Not run / 未执行 |
| Room/Firestore separation / Room 与 Firestore 隔离 | No cross-write or automatic migration / 不互相写入或自动迁移 |  | Not run / 未执行 |
| Instrumentation execution / 实际运行 Instrumentation | Room test executes on connected device / Room 测试在已连接设备上执行 |  | Not run / 未执行 |

Use only **Passed / 通过**, **Failed / 失败**, **Not run / 未执行**, or **Blocked / 受阻** as the final status.

最终状态仅使用 **Passed / 通过**、**Failed / 失败**、**Not run / 未执行** 或 **Blocked / 受阻**。

## 7. Troubleshooting / 故障排查

- **Gradle Sync/build fails:** read the first actionable error in Android Studio's Build window. Confirm the Android SDK required by this project is installed and licenses are accepted. Do not replace Firebase configuration unless the error specifically identifies it.
- **Gradle Sync/构建失败：**查看 Android Studio Build 窗口中的首个可操作错误；确认项目所需 Android SDK 已安装且许可已接受。除非错误明确指出 Firebase 配置问题，否则不要替换 Firebase 配置。
- **Login/register fails:** confirm the test Firebase project has Email/Password Authentication enabled and the device has network access. Do not send passwords or tokens in a bug report.
- **登录/注册失败：**确认测试 Firebase 项目启用了 Email/Password Authentication，且设备网络可用。反馈问题时不要发送密码或 Token。
- **Firestore reads/writes fail:** confirm the app is pointed at the intended test project, the latest `firestore.rules` are deployed there, and the signed-in account is the expected one.
- **Firestore 读写失败：**确认 App 使用的是目标测试项目、该项目已部署当前 `firestore.rules`，且登录账号正确。
- **Admin dashboard missing:** the linked local Room user may not have the Admin role. **Claim Review** permission denied: the signed-in Firebase user may be missing the `role=ADMIN` custom claim or may need to sign out and back in to refresh the ID token. Only the project administrator can provision custom claims.
- **看不到管理员入口：**关联的本地 Room 用户可能没有 Admin 角色。**Claim Review** 提示权限拒绝：Firebase 用户可能没有 `role=ADMIN` Custom Claim，或需要退出后重新登录以刷新 ID Token。Custom Claims 只能由项目管理员配置。
- **No emulator/device in Android Studio:** start an AVD in Device Manager or reconnect/unlock the phone and accept its USB debugging prompt. If no device is available, the instrumentation test is **Blocked / 受阻**, not passed.
- **Android Studio 找不到模拟器/手机：**在 Device Manager 启动 AVD，或重新连接并解锁手机、接受 USB 调试授权。没有设备时，Instrumentation Test 应记为 **Blocked / 受阻**，不能记为通过。
