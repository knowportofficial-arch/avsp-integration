# M7 V3 Device Recommendation Display Fix

Observed on real device after M7 V2:
- 98% -> REVIEW
- 74% -> REVIEW
- 69% -> REVIEW
- 51% -> REVIEW
- 39% -> REVIEW
- 15% -> REVIEW
- 0% -> REVIEW

Root cause in the display layer: MediaLibraryScreen derived its recommendation only from
`qualityScoreNormalized`. Legacy/existing rows can have a populated 0-100 `qualityScore`
while the newly-added normalized field remains 0.0. The UI therefore treated those rows as
zero-score/review.

Fix:
1. Prefer `qualityScoreNormalized` when > 0.
2. Otherwise derive from legacy `qualityScore` using RecommendationPolicy.
3. If no score exists, preserve only RETAKE; never convert stored KEEP/default into KEEP.
4. Unanalyzed rows remain REVIEW.

Expected:
>=70% KEEP
40-69% REVIEW
<40% RETAKE
Unanalyzed/no score REVIEW
