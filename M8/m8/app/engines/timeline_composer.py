"""
P11 — Timeline Composer.
Builds canonical timeline from Creative EDL. Ensures no accidental duration expansion.
CTA remains exactly 5.0s.
"""
from __future__ import annotations

from pathlib import Path
from typing import Optional

from app.schemas.edl import CreativeEDL
from app.schemas.timeline import (
    Timeline, NarrationEvent, VisualEvent, OverlayEvent,
    TransitionEvent, SfxEvent, MusicEvent, CaptionEvent, CtaEvent,
)
from app.m8.errors import M8Error, ErrorCode


class TimelineComposer:
    def __init__(self, placeholder_color: str = "black"):
        self.placeholder_color = placeholder_color

    def compose(self, edl: CreativeEDL, audio_path: Optional[str] = None) -> Timeline:
        if not edl.scenes:
            raise M8Error(ErrorCode.TIMELINE_ERROR, "EDL has no scenes", stage="timeline_compose")

        fmt = edl.format
        width, height = (1080, 1920) if fmt in ("9:16", "shorts") else (1920, 1080)

        narration = []
        visual = []
        overlays = []
        transitions = []
        sfx = []
        captions = []
        cta = None
        t_end = 0.0

        for sc in edl.scenes:
            start = float(sc.start)
            dur = float(sc.duration)
            end = start + dur
            t_end = max(t_end, end)

            # Narration
            if sc.narration_segment:
                narration.append(NarrationEvent(
                    start=start,
                    end=end,
                    text=sc.narration_segment,
                    audio_path=audio_path,
                ))

            # Visual
            if sc.selected_media and sc.selected_media.path and Path(sc.selected_media.path).exists():
                visual.append(VisualEvent(
                    start=start,
                    end=end,
                    path=sc.selected_media.path,
                    media_type=sc.selected_media.type,
                    source=sc.selected_media.source,
                    asset_id=sc.selected_media.asset_id,
                ))
            else:
                # Placeholder so M4 still gets a timeline (color black)
                visual.append(VisualEvent(
                    start=start,
                    end=end,
                    path="",  # signal placeholder
                    media_type="color",
                    source="generated",
                    asset_id="placeholder",
                ))

            # Punch — overlay, does NOT extend duration
            if sc.punch and sc.punch.punch_asset:
                p_start = min(sc.punch.start_time, end - 0.1)
                p_end = min(p_start + sc.punch.duration, end)
                if p_end > p_start:
                    overlays.append(OverlayEvent(
                        start=p_start,
                        end=p_end,
                        kind="punch",
                        path=sc.punch.punch_asset,
                        reason=sc.punch.reason,
                    ))

            # Emoji
            if sc.emoji:
                e_end = min(sc.emoji.start + sc.emoji.duration, end)
                if e_end > sc.emoji.start:
                    overlays.append(OverlayEvent(
                        start=sc.emoji.start,
                        end=e_end,
                        kind="emoji",
                        text=sc.emoji.emoji,
                        position=sc.emoji.position,
                        scale=sc.emoji.scale,
                        reason=sc.emoji.reason,
                    ))

            # Transition
            if sc.transition:
                transitions.append(TransitionEvent(
                    at=start,
                    type=sc.transition.type,
                    duration=min(sc.transition.duration, 0.8),
                    from_scene=sc.transition.from_scene,
                    to_scene=sc.transition.to_scene,
                    reason=sc.transition.reason,
                ))

            # SFX
            if sc.sfx and sc.sfx.asset:
                s_end = min(sc.sfx.start + sc.sfx.duration, end)
                if s_end > sc.sfx.start:
                    sfx.append(SfxEvent(
                        start=sc.sfx.start,
                        end=s_end,
                        path=sc.sfx.asset,
                        volume=sc.sfx.volume,
                        reason=sc.sfx.reason,
                    ))

            # Captions from narration
            if sc.narration_segment:
                captions.append(CaptionEvent(
                    start=start,
                    end=end,
                    text=sc.narration_segment[:120],
                    style=sc.caption_style.style if sc.caption_style else "default",
                    emphasis=bool(sc.caption_style and sc.caption_style.emphasis),
                    position="bottom",
                ))

            # CTA
            if sc.scene_id == edl.cta_scene_id or sc.scene_id == "scene_9":
                cta_dur = float(edl.cta_duration)
                cta = CtaEvent(
                    start=start,
                    end=start + cta_dur,
                    text=sc.narration_segment or "Thanks for watching",
                    duration=cta_dur,
                )
                # Force visual end to match CTA
                if visual:
                    visual[-1].end = start + cta_dur
                t_end = max(t_end, start + cta_dur)

        music = None
        if edl.bgm and edl.bgm.music_asset:
            duck_regions = [{"start": n.start, "end": n.end} for n in narration]
            music = MusicEvent(
                path=edl.bgm.music_asset,
                start=0.0,
                duration=t_end,
                base_volume=edl.bgm.base_volume,
                duck_volume=edl.bgm.duck_volume,
                duck_regions=duck_regions,
            )

        # Fix placeholder paths for M4: use color dict convention via empty path handling upstream
        for v in visual:
            if v.media_type == "color" or not v.path:
                v.path = ""  # M4 adapter will convert to color black

        tl = Timeline(
            project_id=edl.project_id,
            format=fmt,
            width=width,
            height=height,
            fps=30.0,
            duration=round(t_end, 3),
            narration=narration,
            visual=visual,
            overlays=overlays,
            transitions=transitions,
            sfx=sfx,
            music=music,
            captions=captions,
            cta=cta,
        )
        return tl
