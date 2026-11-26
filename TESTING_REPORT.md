# Android App Testing Report

Tests are recorded only from observed results. A test remains `NOT TESTED` until all of its listed steps are completed.

| Test ID | Feature | Test Steps | Expected Result | Actual Result | Status | Bug Description | Fix Applied | Evidence |
|---|---|---|---|---|---|---|---|---|
| Test 01 | Project Build & App Launch | 1. Run `.\gradlew.bat assembleDebug --console=plain`. 2. Install and launch the app on an Android emulator or device. | Debug build succeeds and the app opens without crashing. | Initial build succeeded and user reports the app opened. After the item-type change, the unit-test and Debug build tasks also succeeded; updated app launch is pending. | PASS | None observed. | None | Initial Gradle output: `BUILD SUCCESSFUL in 38s`; user feedback: `opened`; post-change Gradle output: `BUILD SUCCESSFUL in 57s` for `testDebugUnitTest assembleDebug`. |
| Test 02 | Student Registration & Login | 1. Open Profile from bottom navigation. 2. Sign out if already logged in; otherwise open the login form. 3. Open Sign up, register a new student account, log out, then log in with that account. | Registration succeeds and the new account can log in successfully. | User reports the existing profile and login navigation worked, Sign up opened, test registration succeeded, Logout returned to login, and the new test account logged in successfully. | PASS | None observed. | None | User feedback: `profile`, `login`, `opened`, `success`, `login`, and `success` for the new account login. |
| Test 03 | Admin Login & Permissions | 1. Sign in with an authorized admin test account. 2. Verify admin-only access and behavior. | Admin account reaches its authorized admin screens; unauthorized users cannot access protected admin functions. | Not tested: user reports no admin test account is available. | NOT TESTED | Not assessed; permissions remain unverified. | None | User feedback: no admin test account available. |
| Test 04 | Lost Item CRUD | Sign in with the student test account; create a Lost item, verify it appears, edit it, then delete it. | The Lost item can be created, viewed, updated, and deleted; changes are reflected in the app. | User reports student login succeeded, the publishing form opened with Lost selected, saving the test item succeeded, and the item is visible in My Posts. Edit and delete are pending. | NOT TESTED | None observed so far; remaining CRUD operations are unverified. | User-requested item-type selection behavior implemented; manual UI verification pending. | User feedback: `success` for login, `lost_form` for the form, `success` for saving, and `visible` in My Posts; post-change unit tests and build succeeded. |

## Summary

- PASS: 2
- FAIL: 0
- NOT TESTED: 2
- NOT IMPLEMENTED: 0
- Remaining issues: Admin login/permissions could not be verified because no admin test account is available; Test 04 and later cases remain to be tested.

## Requested UI Adjustment

- Posts form now starts with neither Lost nor Found selected.
- Saving without explicitly choosing a type is blocked with a prompt.
- Verification: focused unit tests and Debug build passed; updated-app UI verification is pending.
