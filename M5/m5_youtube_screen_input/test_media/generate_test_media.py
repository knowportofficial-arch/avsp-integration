"""
M5.1 Deterministic Test Media Generator
Generates small test images with real text for REAL OCR testing
Keeps ZIP small by generating on-demand
"""
import os
import cv2
import numpy as np
from pathlib import Path
from PIL import Image, ImageDraw, ImageFont
import sys

def get_font(size=32):
    # Try to find a font that supports Bengali/Hindi if available, fallback to default
    possible_paths = [
        "/usr/share/fonts/truetype/noto/NotoSansBengali-Regular.ttf",
        "/usr/share/fonts/truetype/noto/NotoSansDevanagari-Regular.ttf",
        "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf",
        "/usr/share/fonts/truetype/liberation/LiberationSans-Regular.ttf",
    ]
    for fp in possible_paths:
        if os.path.exists(fp):
            try:
                return ImageFont.truetype(fp, size)
            except:
                pass
    # Fallback - default (may not support Bengali/Hindi glyphs, but still deterministic)
    try:
        return ImageFont.load_default()
    except:
        return None

def create_text_image(text, filename, size=(800,200), bg=(255,255,255), fg=(0,0,0)):
    font = get_font(32)
    img = Image.new('RGB', size, bg)
    draw = ImageDraw.Draw(img)
    # Center text
    try:
        # For PIL >= 8.0
        bbox = draw.textbbox((0,0), text, font=font)
        w = bbox[2]-bbox[0]
        h = bbox[3]-bbox[1]
    except:
        w, h = draw.textsize(text, font=font) if font else (len(text)*10, 20)
    x = (size[0]-w)//2
    y = (size[1]-h)//2
    draw.text((x,y), text, fill=fg, font=font)
    img.save(filename)
    print(f"Created {filename} with text: {text}")
    return filename

def create_video_from_images(image_paths, video_path, fps=1):
    if not image_paths:
        return
    first = cv2.imread(str(image_paths[0]))
    h, w = first.shape[:2]
    fourcc = cv2.VideoWriter_fourcc(*'mp4v')
    out = cv2.VideoWriter(str(video_path), fourcc, fps, (w,h))
    for p in image_paths:
        img = cv2.imread(str(p))
        # Write each image for 1 sec (fps frames)
        for _ in range(fps):
            out.write(img)
    out.release()
    print(f"Created video {video_path}")

def main():
    out_dir = Path(__file__).parent
    out_dir.mkdir(exist_ok=True)
    # Clean old generated
    for f in out_dir.glob("real_*.png"):
        f.unlink()
    for f in out_dir.glob("real_*.mp4"):
        f.unlink()

    # English
    create_text_image("ENGLISH TEST 123", out_dir / "real_english.png")
    # Numbers
    create_text_image("Numbers 42 3.14 100 0.93", out_dir / "real_numbers.png")
    # Bengali - may render as boxes if font missing, but still deterministic file
    create_text_image("বাংলা ভাষা পরীক্ষা", out_dir / "real_bengali.png")
    # Hindi
    create_text_image("हिंदी भाषा परीक्षण", out_dir / "real_hindi.png")
    # Multilingual
    create_text_image("AI Video 42 বাংলা हिंदी", out_dir / "real_multilingual.png")

    # Video from images
    imgs = [out_dir / "real_english.png", out_dir / "real_numbers.png", out_dir / "real_bengali.png", out_dir / "real_hindi.png"]
    # Only include existing
    imgs = [p for p in imgs if p.exists()]
    if imgs:
        create_video_from_images(imgs, out_dir / "real_multilingual.mp4", fps=1)

    print("Test media generation complete.")

if __name__ == "__main__":
    main()
