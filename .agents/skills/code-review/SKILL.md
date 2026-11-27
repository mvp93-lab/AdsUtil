---
name: code-review
description: "Review the changes since a fixed point (commit, branch, tag, or merge-base) along two axes: Standards (does the code follow this repo's coding standards, Fowler smells, and Android/Compose/AdMob safety rules?) and Spec (does the code match what the originating spec/request asked for?). Runs both reviews in parallel sub-agents and reports them side by side. Use when the user wants to review a branch, a PR, work-in-progress changes, or asks to \"review since X\"."
---

<!-- Adapted from mattpocock/skills (MIT License) for Antigravity and Android/Jetpack Compose/AdMob repositories. -->

Two-axis review of the diff between `HEAD` and a fixed point the user supplies:

- **Standards**: does the code conform to this repo's documented coding standards, clean-code heuristics, and Android/Compose/AdMob safety practices?
- **Spec**: does the code faithfully implement the originating request / issue / spec?

Both axes run as **parallel sub-agents** (`invoke_subagent`, e.g. `TypeName = "research"`) so they don't pollute each other's context, then this skill aggregates their findings. Start every sub-agent with a fresh context window.

## Process

### 1. Pin the fixed point

Whatever the user said is the fixed point (a commit SHA, branch name, tag, `master`, `main`, `HEAD~5`, etc.). If they didn't specify one, ask for it (or suggest the default branch such as `master` / `main` if obvious).

Capture the diff command once: `git diff <fixed-point>...HEAD` (three-dot, so the comparison is against the merge-base). Also note the list of commits via `git log <fixed-point>..HEAD --oneline`.

Before going further, confirm the fixed point resolves (`git rev-parse <fixed-point>`) and the diff is non-empty. A bad ref or empty diff should fail here, not inside two parallel sub-agents.

### 2. Identify the spec source

Look for the originating spec, in this order:

1. Requirements or task description provided directly in the user's prompt.
2. Issue or PR references in the commit messages (`#123`, `Closes #45`, etc.) — if `gh` is available and authenticated, fetch the issue/PR body.
3. A spec file under `docs/`, `specs/`, or `.scratch/` matching the branch name or feature, or a path the user passed as an argument.
4. If nothing is found, ask the user where the spec is. If they say there isn't one, skip the **Spec** sub-agent and report "no spec available".

### 3. Identify the standards sources

Search the repo for every file that documents how code should be written (e.g., `CODING_STANDARDS.md`, `CONTRIBUTING.md`, `GEMINI.md`, `AGENTS.md`, `README.md`).

On top of whatever the repo documents, the Standards axis always carries the **smell baseline** and **Android / Compose / AdMob baseline** below. Two rules bind them:

- **The repo overrides.** A documented repo standard always wins; where it endorses something the baseline would flag, suppress the smell.
- **Always a judgement call.** Each smell is a labelled heuristic ("possible Feature Envy"), never a hard violation. Skip anything tooling/compiler already enforces.

#### Fowler Code Smells Baseline

Each smell reads *what it is* → *how to fix*; match it against the diff:

- **Mysterious Name**: a function, variable, or type whose name doesn't reveal what it does or holds. → rename it; if no honest name comes, the design's murky.
- **Duplicated Code**: the same logic shape appears in more than one hunk or file in the change. → extract the shared shape, call it from both.
- **Feature Envy**: a method that reaches into another object's data more than its own. → move the method onto the data it envies.
- **Data Clumps**: the same few fields or params keep travelling together (a type wanting to be born). → bundle them into one type, pass that.
- **Primitive Obsession**: a primitive or string standing in for a domain concept that deserves its own type. → give the concept its own small type.
- **Repeated Switches**: the same `switch`/`if`-cascade on the same type recurs across the change. → replace with polymorphism, or one map both sites share.
- **Shotgun Surgery**: one logical change forces scattered edits across many files in the diff. → gather what changes together into one module.
- **Divergent Change**: one file or module is edited for several unrelated reasons. → split so each module changes for one reason.
- **Speculative Generality**: abstraction, parameters, or hooks added for needs the spec doesn't have. → delete it; inline back until a real need shows.
- **Message Chains**: long `a.b().c().d()` navigation the caller shouldn't depend on. → hide the walk behind one method on the first object.
- **Middle Man**: a class or function that mostly just delegates onward. → cut it, call the real target direct.
- **Refused Bequest**: a subclass or implementer that ignores or overrides most of what it inherits. → drop the inheritance, use composition.

#### Android / Jetpack Compose / AdMob Baseline

- **Lifecycle & Context Leaks**: holding an `Activity` or `View` reference inside a singleton or long-lived coroutine scope (`AdManager`, `AppOpenManager`); failing to check `!activity.isFinishing && !activity.isDestroyed` before showing ads; failing to detach/destroy views when an `Activity` finishes.
- **Main-Thread Safety**: creating, mutating, hiding, or destroying `AdView`, `NativeAdView`, `PictureInPictureAd`, or `ProcessLifecycleOwner` observers off the Android main looper.
- **Compose Stability & Recomposition**: creating views or launching side effects directly during composition instead of `remember` / `LaunchedEffect` / `DisposableEffect`; missing keys in `remember(...)` when inputs like `adUnitId` or `adSize` change; missing `onRelease` cleanup on `AndroidView` where needed.
- **AdMob / UMP Policy & Debug Safety**: loading ads before UMP `canRequestAds()` / SDK initialization; leaking production Ad Unit IDs in debug builds or showing debug toasts in release builds; missing AdChoices / "Ad" attribution on Native Ad layouts.
- **Concurrency & Caching**: unguarded concurrent loads overwriting or deleting valid cached ads on failure; double-showing single-use fullscreen ads; unbounded memory retention without cleanup on `setAdsEnabled(false)`.

### 4. Spawn both sub-agents in parallel

Issue both `invoke_subagent` calls together in the same turn, then stop calling tools to wait for their results and aggregate them.

**Standards sub-agent prompt** should include:

- The full diff command and commit list.
- The list of standards-source files you found in step 3, **plus both baselines from step 3** pasted in full (the sub-agent has no other access to them).
- The brief: "Report, per file/hunk where relevant, (a) every place the diff violates a documented standard: cite the standard (file + the rule); and (b) any baseline smell or Android/Compose/AdMob risk you spot: name it and quote the hunk. Distinguish hard violations from judgement calls: documented-standard breaches and clear runtime/leak bugs can be hard, while smells are judgement calls, and a documented repo standard overrides the baseline. Skip anything the compiler/tooling enforces. Under 450 words."

**Spec sub-agent prompt** should include:

- The diff command and commit list.
- The path or pasted contents of the spec.
- The brief: "Report: (a) requirements the spec asked for that are missing or partial; (b) behaviour in the diff that wasn't asked for (scope creep); (c) requirements that look implemented but where the implementation looks wrong. Quote the spec line for each finding. Under 400 words."

If the spec is missing, skip the Spec sub-agent and note this in the final report.

### 5. Aggregate

Present the two reports under `## Standards` and `## Spec` headings, verbatim or lightly cleaned. Do **not** merge or rerank findings, because the two axes are deliberately separate (see _Why two axes_).

End with a one-line summary: total findings per axis, and the worst issue _within each axis_ (if any). Don't pick a single winner across axes: that's the reranking the separation exists to prevent.

## Why two axes

A change can pass one axis and fail the other:

- Code that follows every standard but implements the wrong thing → **Standards pass, Spec fail.**
- Code that does exactly what the issue asked but breaks the project's conventions → **Spec pass, Standards fail.**

Reporting them separately stops one axis from masking the other.
