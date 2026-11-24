# Android Campus Lost & Found App - Core Feature Test Report

## 1. Project Overview

This report covers the native Android Campus Lost & Found application. The app uses Kotlin, XML layouts, Material Components, Room, coroutines/Flows, and SharedPreferences for authentication state.

The testing scope includes authentication, student item posts, search and filtering, claim submission and review, admin controls, item status, ownership permissions, Room persistence, and navigation/UI behavior.

## 2. Testing Environment

| Item | Value |
|---|---|
| Operating System | Windows |
| Project | COS30017-Development-Project |
| Android Gradle Plugin build | Debug configuration |
| Compile/Target SDK | 37 |
| Minimum SDK | 24 |
| Emulator | Medium_Phone (AVD), Android 17 / API 37 |
| Emulator identifier | emulator-5554 |
| Test date | 2026-11-24 |

## 3. Test Scope

The existing JVM unit tests, Android instrumentation tests, and newly added Room/Repository integration tests were executed.

The following areas were exercised:

1. Login / Register
2. Student Posts CRUD
3. Search and Filtering
4. Claim Submission
5. Admin Dashboard
6. Admin Claim Approval / Rejection
7. Item Status Management
8. Student / Admin Permission Control
9. Room Database Persistence
10. Navigation and UI Interaction

## 4. Test Cases

| Test ID | Feature | Test Scenario | Expected Result | Actual Result | Status |
|---|---|---|---|---|---|
| UT-001 | Login / Register | Password validation accepts valid passwords and rejects short, overlong, missing-character, and whitespace cases | Valid passwords pass; invalid passwords fail | All 8 validation assertions passed | PASS |
| UT-002 | Login / Register | Name, student ID, and email format validation | Invalid formats are rejected and valid formats accepted | All registration format assertions passed | PASS |
| UT-003 | Login / Register | Password hashing, verification, wrong-password rejection, and salt uniqueness | Correct password verifies; wrong password fails; salts and hashes differ | All password hashing assertions passed | PASS |
| UT-004 | Student Posts CRUD | Valid item input and blank/oversized/invalid-image-name validation | Valid input passes; invalid fields return the matching validation error | All item validation assertions passed | PASS |
| UT-005 | Search and Filtering | Case-insensitive whitespace-tolerant search, title/description matching, and no-result handling | Matching is correct; no-result state is safe | All pagination/search assertions passed | PASS |
| UT-006 | Search and Filtering | Page bounds, exact multiples, empty lists, and positive page-size rules | Navigation remains in bounds and invalid page sizes are rejected | All pagination boundary assertions passed | PASS |
| UT-007 | Claim Submission | Minimum 10-character claim description and maximum 500-character claim description | 10 characters are valid; below 10 or above 500 is rejected | All claim-description boundary assertions passed | PASS |
| UT-008 | Claim Submission | Duplicate and repeated claim submissions | Duplicate submission is prevented | Repository integration test passed | PASS |
| UT-009 | Admin Permission | Student and admin role distinction and default student role | New users default to STUDENT; ADMIN is distinct | All role assertions passed | PASS |
| UT-010 | Admin Claim Approval / Rejection | Only pending claims can be processed; approved claims close found items | PENDING is accepted; APPROVED/REJECTED are rejected; approval closes the item | All claim-review policy assertions passed | PASS |
| UI-001 | Login / Register | Protected Profile/Post entries open login; Back returns Home | AuthActivity opens and Home remains available after Back | Passed on emulator | PASS |
| UI-002 | Login / Register | Registration fields survive recreation; login mode hides registration fields | Registration fields appear only in registration mode and recreation preserves mode | Passed on emulator | PASS |
| UI-003 | Login / Register | Password requirements update individually and visibility stays synchronized | Requirements and password visibility update correctly | Passed on emulator | PASS |
| UI-004 | Login / Register | Sign-up errors clear on edit and invalid values are reported | Field errors clear and appear correctly | Passed on emulator | PASS |
| UI-005 | Search and Filtering | Navigation shows all Room items and open Items page | Home sample data and 8-item items page render correctly | Passed on emulator | PASS |
| UI-006 | Search and Filtering | Search matches descriptions and handles no results | Search returns 2 library matches and empty state for no matches | Passed on emulator | PASS |
| UI-007 | Navigation and UI Interaction | Layout/page state survives recreation and Back returns Home | List/Grid state and page state survive recreation; Back returns Home | Passed on emulator | PASS |
| UI-008 | Navigation and UI Interaction | Bottom navigation labels stay above system navigation bar | Labels remain within the window-inset-safe area | Passed on emulator | PASS |
| UI-009 | Navigation and UI Interaction | Drawer starts hidden, opens/closes, and Back behavior remains on Items page | Drawer hidden initially; open/close and Back behavior are correct | Passed on emulator | PASS |
| RM-001 | Room Database Persistence | Insert, read, update, wrong-owner update, and delete Lost Items | CRUD succeeds for owner; wrong owner cannot update | Repository integration test passed | PASS |
| RM-002 | Room Database Persistence | Insert, read, update, wrong-owner delete, and delete Found Items | CRUD succeeds for owner; wrong owner cannot delete | Repository integration test passed | PASS |
| RM-003 | Claim Submission | Owner, short-description, success, duplicate, and closed-item claim cases | Owner and short claims are rejected; success is recorded once; closed item is rejected | Repository integration test passed | PASS |
| RM-004 | Admin Claim Approval / Rejection | Approve one pending claim | Approved claim closes item and is marked APPROVED | Repository integration test passed | PASS |
| RM-005 | Admin Claim Approval / Rejection | Other pending claims after approval | Competing pending claims become REJECTED | Repository integration test passed | PASS |
| RM-006 | Student / Admin Permission | Student cannot approve/reject or delete as admin | Student receives ACCESS_DENIED/false | Repository integration test passed | PASS |
| RM-007 | Admin Claim Rejection | Admin rejects a pending claim | Claim becomes REJECTED once and repeated processing is prevented | Repository integration test passed | PASS |
| RM-008 | Room Database Persistence | Close and reopen a physical Room database, then update and delete rows | Data survives close/reopen; update and delete remain correct | 1 on-disk database test passed on Medium_Phone API 37 | PASS |
| E2E-001 | Login / Register | Create a unique account, log in, reject wrong password, and verify/logout session state through the real UI | Registration/login works; wrong credentials do not create a session; logout clears session | 1 real-UI authentication test passed | PASS |
| E2E-002 | Student Posts CRUD | Create, edit, delete Lost/Found posts through the real UI | Owner CRUD works and unauthorised users cannot edit/delete | Repository ownership tests pass; no complete Create/Edit/Delete Activity automation was added | BLOCKED |
| E2E-003 | Admin Dashboard | Open dashboard and filter All/Lost/Found through the real UI | Admin sees both item types and filters correctly | Admin dashboard implementation is permission-checked; no dedicated UI automation was added | BLOCKED |
| E2E-004 | Admin Claim Approval / Rejection | Approve/reject claims through the real Admin UI | Admin actions update claim/item status once and safely | Repository review tests pass; no dedicated Admin Activity automation was added | BLOCKED |
| E2E-005 | Item Status Management | Display Open/Claimed/Closed status and prevent claims on Closed items | Status is displayed; Closed items reject claims | Repository/status policy tests pass; closed-item details UI has no dedicated instrumentation test | BLOCKED |

## 5. Test Results

### Executed Test Counts

| Suite | Executed Methods | Passed | Failed |
|---|---:|---:|---:|
| JVM unit tests | 24 | 24 | 0 |
| Android instrumentation tests | 16 | 16 | 0 |
| **Total** | **40** | **40** | **0** |

Additional build checks:

| Check | Result |
|---|---|
| Debug APK assembly | PASS |
| Android lint | PASS |
| Emulator instrumentation suite | PASS |

## 6. Failed Tests and Error Details

No executed test failed. No test error details were reported.

## 7. Bugs Found and Fixes

No executed test assertion failed.

Two genuine consistency fixes were made:

1. Found-item statuses were normalized from `ACTIVE` to `OPEN`, with `CLAIMED` and `CLOSED` states and a migration from database version 6 to 7.
2. Claim submission now changes an item to `CLAIMED`, and rejection reopens it when no pending claims remain.

A test-initialization defect was also fixed: the on-disk Room test now has a void JUnit method, with `runBlocking` inside the method.

## 8. Retest Results

The newly added Room/Repository instrumentation tests were included in a fresh `connectedDebugAndroidTest` run.

The final `testDebugUnitTest --rerun-tasks` run also passed with all 24 JVM test methods executed.

## 9. Remaining Issues

| Area | Remaining Issue |
|---|---|
| E2E UI coverage | Complete create/login/logout UI automation is not present. |
| Student post UI | End-to-end Create/Edit/Delete Activity automation is not present, although repository ownership behavior is tested. |
| Admin UI | Admin Dashboard and Claim Review Activity automation is not present, although repository permission and review behavior is tested. |
| Persistent Room reopen | Persistence should receive a physical process/reopen test rather than only an in-memory database test. |

## 10. Overall Testing Summary

The executable baseline passed completely:

- **40/40 executed tests passed**
- **0 failed**
- Debug APK assembly passed
- Android lint passed
- Emulator instrumentation suite passed

Dedicated UI automation remains for several end-to-end user/admin journeys and persistent on-disk Room reopen testing.

## Actual Test Commands

```powershell
.\gradlew.bat testDebugUnitTest --console=plain
.\gradlew.bat assembleDebug lintDebug --console=plain
& 'C:\Users\User\AppData\Local\Android\Sdk\platform-tools\adb.exe' devices
.\gradlew.bat connectedDebugAndroidTest --console=plain
.\gradlew.bat testDebugUnitTest --rerun-tasks --console=plain
```
