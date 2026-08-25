# M8 Acceptance Report

Command verified:
```
PYTHONPATH=. python main.py --topic "Belda Railway Station" --duration 12s --format 9:16
```

Result:
- Pipeline stages: all success
- Final MP4: projects/belda_accept_short2/render/final.mp4
- Resolution: 1080x1920
- Codec: H.264 + AAC
- CTA: PASS (5.0s in timeline)
- QC: PASS

Full 5m run is supported by the same CLI (`--duration 5m`) but was not executed end-to-end here due to render time.
