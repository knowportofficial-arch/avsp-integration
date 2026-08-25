# AVSP FULL INTEGRATION — CURSOR INSTRUCTIONS

This workspace contains completed/reference AVSP modules.

DO NOT blindly merge, rewrite, or recreate modules.
DO NOT force heavy Windows/Python processing into Android.
DO NOT code before completing the integration audit.

FIRST TASK: AUDIT ONLY

Inspect:
CURRENT_M1_M3, M4, M5, M6, M7, M8, M9, M10.

For every module identify:
1. language/platform
2. entry points
3. inputs
4. outputs
5. data contracts
6. filesystem/artifact contracts
7. dependencies
8. tests/build status
9. inter-module dependencies
10. duplicate/overlapping implementations
11. integration gaps

Create:
- AVSP_MODULE_INTEGRATION_MATRIX.md
- AVSP_CONTRACT_MAP.md
- AVSP_PLATFORM_BOUNDARY.md
- AVSP_DUPLICATE_COMPONENTS.md
- AVSP_INTEGRATION_AUDIT.md

DO NOT MODIFY MODULE SOURCE DURING THE AUDIT.

TARGET LOGICAL FLOW:
M1 → M2 → M3 → M5 when needed → M6/M7 media → M4 → M8 → M9 → M10/QC.

This is a logical workflow; modules may run on different machines.

AFTER AUDIT:
STOP and report findings. Do not implement adapters/bridge until exact requirements are identified.

FINAL GOAL:
A real end-to-end production run from user topic to final MP4/publishing-ready artifact, followed by a fixed 5-minute performance benchmark.
