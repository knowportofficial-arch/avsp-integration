# M6 APK Version Comparison V1 → V6

**Scope:** Forensic comparison only. **No source patch applied in this document.**  
**Baseline:** Version 2 (`1.0.1-m7`) — known-good VIDEO recording.  
**App:** `com.avsp.pro` · module path `CURRENT_M1_M3/` · branch `cursor/avsp-m7-into-pro-integration-ddb1`

---

## 1. V1–V6 identity map

| Ver | versionName | versionCode | Git commit | Commit time (UTC) | APK artifact | sha256 (first 16…) | VIDEO recording |
|-----|-------------|-------------|------------|-------------------|--------------|--------------------|-----------------|
| **V1** | `1.0.0-m7` | 1 | `7ee91c3` Integrate M7 into Pro | 2026-08-26 01:57 | **APK NOT FOUND** in `/opt/cursor/artifacts` or agent store | — | Source: sample = all VIDEO; expected to record |
| **V2** | `1.0.1-m7` | 2 | `d6c83b2` Fix ERROR_SOURCE_INACTIVE (4) | 2026-08-26 03:27 | `avsp-pro-m7-guided-video-fix-debug.apk` (03:25, 71,023,725 B) | `fb39ec1065f3b1c3…` | **YES — known-good** |
| **V3** | `1.0.2-m7` | 3 | `109db43` Unplayable MP4 / FileProvider audit | 2026-08-26 04:56 | `avsp-pro-m7-guided-audit-video-fix-debug.apk` (04:56, 71,139,730 B) | `66bf4c8307ed23b2…` | YES (still `sample()`, all VIDEO) |
| **V4** | `1.0.3-m7` | 4 | `c3094d1` Semantic naming restore | 2026-08-26 05:37 | `avsp-pro-m7-guided-naming-restore-debug.apk` (05:37, 71,024,400 B) | `c515c8caef97d61c…` | YES (still `sample()`, all VIDEO) |
| **V5** | `1.0.4-m7` | 5 | `18bf049` Guided Capture completion / plan adapter | 2026-08-26 06:05 | `avsp-pro-m7-guided-completion-1.0.4-debug.apk` (06:05, 71,057,168 B) | `94865d80139ca841…` | **REGRESSION starts here** when opened with a projectId |
| **V6** | `1.0.5-m7` | 6 | `1ffbfb7` KEEP duration gate | 2026-08-26 07:10 | `avsp-pro-m7-keep-duration-gate-1.0.5-debug.apk` (07:09, 71,057,168 B) | `aeafd415cdfe0619…` | Same V5 plan→PHOTO behavior + KEEP gate |

### V1 APK status

`1.0.0-m7` exists only as a **Git commit** (`7ee91c3`). No matching APK was retained under `/opt/cursor/artifacts` or `/cursor/stores/self/artifacts`. Behavior below for V1 is reconstructed from that commit’s source.

### Current HEAD vs V6 APK

| Check | Result |
|-------|--------|
| HEAD `versionName` | `1.0.5-m7` / code `6` |
| HEAD commit | `1ffbfb7` |
| V6 APK package | `aapt`: `versionName='1.0.5-m7' versionCode='6'` |
| Match | **Yes** — V6 APK is built from current GitHub branch source at `1ffbfb7` |

---

## 2. Known-good V2 behavior (baseline)

V2 Activity always loaded:

```kotlin
val template = GuidedCaptureTemplate.sample()
```

`GuidedCaptureTemplate.sample()` defines Intro / Wide / Medium / Close with:

- `mediaType` **default = `GuidedClipMediaType.VIDEO`** (not overridden per clip)
- `timerSeconds` default = **0** (SelfTimer countdown skipped unless set)
- `targetDurationSeconds` = 5 / 10 / 8 / 5

Pipeline on capture:

```
beginCapture()
  → (timerSeconds==0) startCaptureForCurrentClip()
  → when (VIDEO) startVideoCapture()
  → phase=RECORDING + red LinearProgressIndicator
  → CameraController.startRecordingToFile()
  → auto-stop at targetDurationSeconds
  → Finalize → REVIEW
```

That matches the reported good UX: recording for the shot duration, red progress bar, auto-stop, real MP4, Review/Next.

**Note on “5-second countdown” in V2:** In source, sample clips have `timerSeconds = 0`, so the Compose `COUNTDOWN` phase is **not** entered for the stock sample. The “5s” on Intro is `targetDurationSeconds` (recording length). Device testers may have described the recording timer (`0s / 5s`) as a countdown. If a non-zero self-timer was set in a local/debug template, COUNTDOWN would appear *before* RECORDING — still followed by real VIDEO.

---

## 3. Comparison table (actual differences)

Legend: `=` same as V2 · `Δ` changed · `+` new · `—` N/A

| Area | V1 | V2 (good) | V3 | V4 | V5 | V6 |
|------|----|-----------|----|----|----|----|
| **GuidedCaptureActivity template source** | `sample()` | `sample()` | `sample()` | `sample()` | **`LocalShotPlanner` + `GuidedCapturePlanAdapter` when `projectId` set; sample only if blank** | same as V5 |
| **GuidedCaptureScreen** | template arg | = | = | naming overlay | session/`loadSession`; READY chip | + INSUFFICIENT_DURATION / KEEP gate UI |
| **GuidedCaptureViewModel** | VIDEO/PHOTO branch | + bind policy for ERROR 4 | + video validator finalize | = V3 path | + live ML Kit; quality on finalize | + duration/KEEP gate |
| **GuidedCaptureTemplate.sample()** | 4× VIDEO (default) | = | = | = (+ naming helper) | = still 4× VIDEO default | = |
| **GuidedCapturePlanAdapter** | absent | absent | absent | absent | **+ maps `PlannedMediaType` → PHOTO/VIDEO** | = V5 |
| **GuidedCaptureState** | basic phases | = | = | = | + liveGuidance / quality | + keepEligible / INSUFFICIENT_DURATION |
| **CameraController** | baseline | **Δ** recording-active bind guard | = V2 | = | = | = |
| **PHOTO capture** | `startPhotoCapture` | = | = | = | used heavily via planner | = |
| **VIDEO capture** | `startVideoCapture` | fixed ERROR 4 | + playable gate | = | still present; **often not selected** for early plan shots | = + duration gate |
| **Countdown (`timerSeconds`)** | only if >0 | = | = | = | = (still unused by sample/plan adapter) | = |
| **Recording progress bar** | on RECORDING | = | = | = | = (only if mediaType=VIDEO) | = |
| **Auto-stop duration** | `targetDurationSeconds` | = | = | = | VIDEO hardcode **8s**; PHOTO display **5s** in adapter | = V5 + KEEP requires full duration |
| **Media type handling** | default VIDEO | = | = | = | **driven by `LocalShotPlanner` (mostly PHOTO)** | = V5 |
| **AI / plan integration** | none (sample) | none | none | none | **LocalShotPlanner only** (not Gemini) | = |
| **Review/Next** | yes | yes | yes | yes | + KEEP/REVIEW/RETAKE labels | KEEP blocked until duration met |
| **Storage/output** | clip dir + JSON | = | FileProvider + playable skip | semantic displayName | + missionId/missionShotId | = |

### Files that actually diverge for this bug

| File | First regression commit |
|------|-------------------------|
| `GuidedCaptureActivity.kt` | **V5 `18bf049`** — stopped using `sample()` when projectId present |
| `GuidedCapturePlanAdapter.kt` | **V5 `18bf049`** — new; maps planner PHOTO/VIDEO |
| `LocalShotPlanner.kt` | pre-existing; **wired into Guided only at V5** |
| `GuidedCaptureViewModel.kt` | VIDEO/PHOTO `when` unchanged in spirit; V5/V6 add intelligence/KEEP — **not the media-type switch bug** |
| `CameraController.startRecordingToFile` | unchanged since V2 bind guard — **not broken** |
| `ImageCapture` / `takePhotoToFile` | correctly invoked when `mediaType==PHOTO` |

---

## 4. Capture pipeline trace (every version)

### V1–V4 (sample path)

```
GuidedCaptureTemplate.sample()
  → GuidedClipSpec.mediaType = VIDEO (default)
  → beginCapture()
  → COUNTDOWN only if timerSeconds>0 (sample: 0 → skip)
  → startCaptureForCurrentClip()
  → when (VIDEO) → startVideoCapture()
  → CameraController.startRecordingToFile()
  → CameraX VideoCapture → MP4
  → REVIEW
```

### V5–V6 with projectId (current device path)

```
project.name + description
  → LocalShotPlanner.createPlan(request)
  → MasterShotPlan.shots[].mediaType = PlannedMediaType.PHOTO|VIDEO
  → GuidedCapturePlanAdapter.toClipSpec()
       mediaType = PHOTO if PlannedMediaType.PHOTO else VIDEO
       targetDurationSeconds = 8 if VIDEO else 5   // hardcoded, not from planner duration field
  → GuidedCaptureScreen / ViewModel.loadSession()
  → beginCapture()
  → startCaptureForCurrentClip()
  → when (clip.mediaType)
       PHOTO → startPhotoCapture() → ImageCapture → JPEG → REVIEW   // ← observed “takes a photo”
       VIDEO → startVideoCapture() → VideoCapture → MP4 → REVIEW
```

### Where V6 diverges from V2

**Not** in CameraX VIDEO API.  
**At template construction:** V2 forced an all-VIDEO sample sequence; V5+ builds clips from `LocalShotPlanner`, whose first shot for nearly every intent is **PHOTO**.

Example (`LocalShotPlanner` counts across all intents): **20 PHOTO / 5 VIDEO**. Typical first shots:

| Intent | Shot 1 | mediaType |
|--------|--------|-----------|
| GENERAL | Establishing view | PHOTO |
| FOOD | Cooking setup | PHOTO |
| TEMPLE | Entrance | PHOTO |
| PRODUCT | Product overview | PHOTO |
| EVENT | Event establishing | PHOTO |
| PERSON | Take My Photo | PHOTO |

So after optional countdown / tap, Guided correctly executes **PHOTO**, not VIDEO.

### Misleading UI compounding the bug

`GuidedCapturePlanAdapter` sets `targetDurationSeconds = 5` for PHOTO and formats:

`Cooking setup · WIDE · 5s · Wide (Establishing…)`

That **looks like a 5-second video instruction** even though `mediaType=PHOTO`. Countdown is not “eaten as recording”; the shot was never VIDEO.

---

## 5. Regression answers (explicit)

1. **Why did V2 record video correctly?**  
   It always used `GuidedCaptureTemplate.sample()`, and every clip defaulted to `GuidedClipMediaType.VIDEO`, then `startVideoCapture()` + CameraX `VideoCapture`.

2. **What changed between V2 and V6?**  
   V3–V4: video playability / naming — still sample VIDEO.  
   **V5:** Activity switched to `LocalShotPlanner` + `GuidedCapturePlanAdapter`.  
   **V6:** KEEP duration gate only (does not flip PHOTO→VIDEO).

3. **First version where VIDEO regression appears?**  
   **V5 (`1.0.4-m7`, commit `18bf049`)** when Guided Capture is opened **with a projectId**.

4. **Was `mediaType` changed?**  
   Yes at the **session construction** layer (plan adapter), not inside `startVideoCapture`.

5. **Was `mediaType` lost between AI planning and Guided Capture?**  
   **No.** `PlannedMediaType` is mapped faithfully. Most planner shots are intentionally PHOTO.

6. **Was a default value introduced?**  
   Sample still defaults VIDEO. Plan adapter **explicitly** sets PHOTO/VIDEO from planner. Separate issue: durations hardcoded to 8/5.

7. **Was PHOTO used as a fallback?**  
   Not as a silent fallback inside ViewModel. PHOTO comes from planner data. (Blank projectId still falls back to sample = VIDEO.)

8. **Was countdown completion logic changed?**  
   No material change: still `timerSeconds > 0` → COUNTDOWN → `startCaptureForCurrentClip()`. Plan adapter does **not** set `timerSeconds`.

9. **Was `beginCapture()` changed?**  
   Structure unchanged (timer then start).

10. **Was `startCaptureForCurrentClip()` changed?**  
    Still `when (clip.mediaType)` VIDEO/PHOTO — correct.

11. **Was `startVideoCapture()` changed?**  
    V5/V6 added guidance/quality/KEEP bookkeeping; core recording start remains. Not the reason PHOTO fires.

12. **Was `CameraController.startRecordingToFile()` changed after V2?**  
    **No** (diff empty V2→V6). V1→V2 only added bind-while-recording guard.

13. **Was ImageCapture accidentally invoked after countdown?**  
    **No — intentionally** when `mediaType==PHOTO`.

14. **Is current APK from current GitHub source?**  
    **Yes** — V6 APK `1.0.5-m7` / code 6 matches HEAD `1ffbfb7`.

15. **Is Activity still using `GuidedCaptureTemplate.sample()`?**  
    **Only when `projectId` is blank.** With a project (normal Pro flow): **LocalShotPlanner**, not sample.

16. **Is AI Shot Planner connected to `GuidedClipSpec.mediaType`?**  
    **`LocalShotPlanner` yes** via `GuidedCapturePlanAdapter`. **`GeminiShotPlanner` is not** used by `GuidedCaptureActivity`. Live Field Capture / cinematographer mission is a **separate** path.

17. **Integration gap (separate from CameraX breakage)?**  
    **Yes.** Gaps:
    - Guided uses **local deterministic planner**, not Gemini / live AI mission.
    - Planner has **no duration field**; adapter invents 8s VIDEO / 5s PHOTO.
    - PHOTO shots still **display “Ns”** in naming, looking like video.
    - Opening with projectId replaces the V2 all-VIDEO sample sequence.

---

## 6. Root cause (one paragraph)

**Root cause:** Starting in **V5**, Guided Capture stopped using the all-VIDEO `sample()` template whenever a Pro `projectId` is present, and instead adapted `LocalShotPlanner` output. That planner marks most shots (including nearly every first shot) as `PlannedMediaType.PHOTO`. The ViewModel/CameraX VIDEO path from V2 is intact; Guided correctly takes a JPEG because `GuidedClipSpec.mediaType` is PHOTO. The UI still shows a duration like `5s` for those PHOTO clips, which makes the session look like a “5-second video” that “only takes a photo.”

**Not root cause:** Countdown being treated as recording; CameraX VideoCapture deleted; KEEP gate in V6; accidental ImageCapture after VIDEO branch.

---

## 7. Minimal fix (proposed — not applied)

Smallest safe restore of V2 VIDEO behavior **without** discarding plan integration:

**A. Preserve planner media type, stop lying about PHOTO duration**

In `GuidedCapturePlanAdapter.toClipSpec(PlannedShot)`:

1. Keep `mediaType = PHOTO|VIDEO` from `shot.mediaType` (already correct).
2. For PHOTO: do **not** present `targetDurationSeconds` as a shoot-for-N-seconds video cue in `captureInstruction` (e.g. use a photo label or omit seconds), **or** set display duration only for VIDEO.
3. For VIDEO: pass through a real duration when available; until planner gains duration, keep an explicit VIDEO duration constant but ensure VIDEO shots actually run `startVideoCapture`.

**B. Product-sequence / Intro VIDEO expectation**

If product UX requires V2’s Intro→Wide→Medium→Close **all VIDEO** when no richer AI plan exists, either:

- Prefer `fromSampleFallback()` until a plan contains at least one VIDEO with durations, **or**
- Extend `LocalShotPlanner` / Gemini plan so intended VIDEO shots are `PlannedMediaType.VIDEO` (e.g. Intro establishing as VIDEO when AI says VIDEO).

**C. Do not**

- Force all shots PHOTO or all VIDEO.
- Rewrite CameraX.
- Treat countdown completion as media-type selection.

---

## 8. Correct AI → Guided mapping (target contract)

```
AI / PlannedShot.mediaType == PHOTO
  → GuidedClipMediaType.PHOTO
  → optional timerSeconds countdown
  → startPhotoCapture() → one JPEG → REVIEW
  → naming must NOT imply “record Ns of video”

AI / PlannedShot.mediaType == VIDEO
  → GuidedClipMediaType.VIDEO
  → optional timerSeconds countdown (pre-roll only)
  → startVideoCapture() → RECORDING + red bar
  → record targetDurationSeconds of real video
  → auto-stop → MP4 → REVIEW

Mixed plans: PHOTO → VIDEO → PHOTO → VIDEO must each follow the branch above.
```

Preserve through the adapter (once planner exposes them): duration, countdown/`timerSeconds`, orientation, aspect ratio, resolution, frame rate. Today only media type + framing/subject/guidance are mapped; duration/aspect/orientation are largely **defaults**.

---

## 9. Regression-prevention tests (to add after patch)

1. PHOTO shot → exactly one `.jpg` (inspect magic / decoder), never enters `RECORDING`.
2. VIDEO 5s → playable MP4 with measured duration ≈5s (±tolerance).
3. VIDEO 10s → MP4 ≈10s.
4. Sequence PHOTO→VIDEO→PHOTO→VIDEO executes media types in order.
5. Countdown (`timerSeconds`) does not change `mediaType`.
6. `PlannedMediaType` equals `GuidedClipSpec.mediaType` after adapter (property test over planner intents).
7. Red `LinearProgressIndicator` only when `phase==RECORDING` and mediaType VIDEO.
8. PHOTO never sets `GuidedCapturePhase.RECORDING`.
9. VIDEO always reaches `RECORDING` before REVIEW (happy path).
10. Auto-stop at `targetDurationSeconds` for VIDEO.
11. Output inspection: JPEG SOI / MP4 `ftyp` — do not trust metadata alone.
12. With `projectId`, first shot media type matches planner (not silently sample VIDEO).
13. PHOTO captureInstruction must not claim a video recording duration if product forbids it.

---

## 10. Summary

| Item | Finding |
|------|---------|
| Known-good | **V2** `1.0.1-m7` / `d6c83b2` / `avsp-pro-m7-guided-video-fix-debug.apk` |
| Regression first appears | **V5** `1.0.4-m7` / `18bf049` |
| Responsible functions | `GuidedCaptureActivity.loadGuidedSession`, `GuidedCapturePlanAdapter.toClipSpec`, `LocalShotPlanner.createPlan` |
| CameraX VIDEO path | Intact since V2 |
| Root cause | Plan-driven **PHOTO** clips (esp. shot 1) replace V2 all-VIDEO sample; UI still shows `5s` |
| V1 APK | **Missing** from artifacts |
| V6 APK vs Git | **Matched** |
| Patch status | **Not applied** — awaiting approval after this report |

---

*Generated from Git history, `aapt dump badging` on retained APKs, and source inspection of `CURRENT_M1_M3/.../guided/*` and `LocalShotPlanner.kt`. No application source was modified for this forensic pass.*
