# Cesia Input Method — Agent Guide

## Build Commands

```bash
# Debug build (requires JDK 17, NDK 25.2.9519653)
./gradlew assembleDebug

# With explicit version (CI does this; versionCode = patch segment)
./gradlew assembleDebug -PapkVersion=1.1.123

# Debug keystore must exist at /tmp/cesia-debug.keystore (pass: cesiacesia)
# CI restores it from secrets; locally you need to create it.
```

APK output: `app/build/outputs/apk/debug/Cesia-{version}-debug.apk`

## Version Scheme

- Format: `1.1.{commitCount}` (e.g. `1.1.500`)
- Version is auto-computed from `git rev-list --count HEAD` when `-PapkVersion` is not provided
- CI always injects the version via `-PapkVersion`

## Architecture

| Module | Language | Purpose |
|--------|----------|---------|
| `app` | Kotlin (Groovy Gradle) | Main IME service, UI, settings |
| `rime-engine` | Kotlin (Kotlin DSL Gradle) | librime JNI wrapper (拼音/T9) |
| `ai-engine` | Kotlin + C++ (Kotlin DSL Gradle) | MNN LLM bridge, Sherpa ONNX |

- **Target ABI**: arm64-v8a only
- **Min SDK 24, Compile SDK 34**
- **JDK 17, Kotlin 1.9.0, AGP 8.1.0, Gradle 8.5**

## Native Code (.so) Management

- `ai-engine/src/main/jniLibs/arm64-v8a/*.so` — tracked via **Git LFS** (MNN, libc++_shared, etc.)
- MNN libs are **manually compiled** from source; the official MNN 3.5.0 package doesn't support Qwen3.5's `partial_rotary_factor`
- `rime-engine/src/main/jniLibs/arm64-v8a/librime_jni.so` — built via `build-librime.yml` workflow (manual trigger)
- CMake (`ai-engine/src/main/cpp/CMakeLists.txt`) auto-downloads MNN 3.5.0 if `.so` files missing, but use the LFS-tracked versions instead
- `libc++_shared.so` conflict: both `ai-engine` and `rime-engine` provide it. Resolved by `pickFirsts` in `app/build.gradle` and an `afterEvaluate` task that replaces the NDK version with MNN's version

## Submodules

Two submodules exist but are **not initialized by default**:
- `ai-engine/src/main/cpp/whisper.cpp`
- `ai-engine/src/main/cpp/llama.cpp`

The `whisper-jni.cpp` bridge in `app/src/main/cpp/` references whisper.cpp headers.

## CI Workflows

- **`build-apk.yml`**: Runs on push to main and PRs. Builds debug APK, creates GitHub Release on main.
- **`build-librime.yml`**: Manual trigger only. Cross-compiles librime for arm64-v8a/armeabi-v7a/x86_64/x86. Outputs `librime_jni.so`.

## Key Gotchas

- **No test suite exists** — no unit tests, instrumented tests, or lint checks are configured
- `app/build.gradle` is Groovy; `ai-engine` and `rime-engine` use Kotlin DSL (`build.gradle.kts`)
- `mimocode.json` sets permission: read=allow, bash=ask, edit=ask
- `app/libs/sherpa-onnx-static.aar` is a local AAR dependency (speech recognition)
- Dictionary rebuild: `python3 rebuild_dict.py <input.yaml> <output.json> [max_chars]`
- Docker setup in `docker/` provides a build environment for cross-compiling librime
- Rime config assets: `rime-engine/src/main/assets/rime/` (basic) and `app/src/main/assets/rime/` (full schema set)
