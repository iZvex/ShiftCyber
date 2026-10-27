# Shift Cyber — Android project

Kotlin + Jetpack Compose, Room, WorkManager. Android SDK 35, JDK 17, Gradle 8.11.1.

## Build APK directly from a phone using GitHub Actions

No PC or Android SDK installation is required on your phone. GitHub builds the APK in the cloud.

1. Download the project ZIP from this chat and extract it on your phone (for example, with ZArchiver).
2. In GitHub, create a **new empty repository** named `ShiftCyber`. Do not add a README, license, or `.gitignore` during repository creation.
3. Open the repository in your phone browser. Use **Add file → Upload files** and upload the *contents* of the extracted project, including the hidden `.github/workflows/android-apk.yml` file. If the mobile page does not show hidden files or upload folders, use GitHub's web editor to create `.github/workflows/android-apk.yml` and paste the workflow from the project archive, then upload the other project files. GitHub's upload page may require selecting files rather than a folder.
4. Commit the files to the `main` branch.
5. Open the repository's **Actions** tab and select **Build Shift Cyber APK**. If prompted, enable workflows. Press **Run workflow** (or wait for the automatic run after the first push).
6. Wait for the run to finish with a green check. Open the run, scroll to **Artifacts**, and download `ShiftCyber-debug-apk`.
7. Extract the downloaded artifact ZIP and open `app-debug.apk`. Allow installation from the browser/file manager if Android asks.

If the workflow fails, open the failed run, expand the first red step, and send a screenshot or copy the error log here. The APK is not confirmed to build until Actions finishes successfully.

## Local build (optional)

Requires JDK 17, Android SDK Platform 35 and Gradle 8.11.1:

```bash
gradle assembleDebug
```

APK output: `app/build/outputs/apk/debug/app-debug.apk`.

## Notes

- This workflow builds a **debug APK** for personal testing, not a Play Store release.
- GitHub Actions stores the APK artifact for 14 days; download it before expiry.
- The project has not been compiled or tested on a physical device in the authoring environment. Runtime issues may still need fixing based on the first build logs.
