# Automix

This module is licensed under the **GNU Affero General Public License, version 3 or later**
([`LICENSE`](LICENSE)), unlike the rest of Jelyvo, which is under the GPL-3.0. Section 13 of each
licence permits combining the two. These files stay under the AGPL as part of that combination.

## Where it comes from

`TrackAnalysis.kt`, `TransitionPolicy.kt`, `TransitionPlanner.kt` and their tests come from
[BitChord](https://github.com/kushagrasinghx/BitChord) with only their package changed:

- Copyright (C) 2026 SFG545, for the original [Orchard](https://github.com/SFG5453/Orchard)
  implementation.
- Copyright (C) 2026 Kushagra Singh, for the BitChord adaptation.

`LevelAnalysis.kt` and `LevelMeter.kt` were written for Jelyvo.

## What Jelyvo uses

The planner can do far more than Jelyvo currently plays. `LevelAnalysis` measures only loudness,
so it reports no tempo and the planner always chooses a plain equal-power crossfade. That crossfade
still benefits from the analysis: it ignores leading silence, leaves before a quiet ending, and
leaves speech, live recordings and albums played in order alone.

Not ported: beat tracking and vocal detection (the Beat This! and open-unmix ONNX models), the
native analyzer, tempo stretching, filter transitions and the loop mix.
