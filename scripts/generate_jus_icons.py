#!/usr/bin/env python3
"""
Build-time per-package generator — wraps MonoProcessor forensic pipeline (Kotlin) in Python
and vectorizes to glyph-only vectors. Keeps MonoProcessor algorithm untouched.
Pipeline: ComponentInfo -> synthetic source Bitmap -> d7/f -> centered square -> ALPHA_8 -> vector path
"""
import hashlib
import pathlib
import xml.etree.ElementTree as ET
from PIL import Image, ImageDraw, ImageFont

# MonoProcessor constants (from MonoProcessor.kt)
ANALYZE_SIZE = 50
VALID_ALPHA = 40
ALPHA_GATE = 110
CLAMP_GRAY = 248
CLAMP_SQ = 61504
GAIN = 210.0
FILL_RATIO_GATE = 0.6
CROP_SCALE_VISUAL = 0.72
CROP_SCALE_FORENSIC = 0.3888889
BUCKETS = 32

def gray_luminance(r,g,b):
    return int(r*0.3 + g*0.59 + b*0.11)

def f_combine(a,b):
    s=a+b
    return 510-s if s>255 else s

def i_pick(alpha, mapped, cur_min):
    return cur_min if alpha<=ALPHA_GATE or mapped>=cur_min else mapped

def centered_square_rect(alpha_array, n, valid_alpha=VALID_ALPHA):
    # Find bounds where alpha > valid
    left=n
    for c in range(n):
        for r in range(n):
            if alpha_array[r][c] > valid_alpha:
                left=c
                break
        if left!=n:
            break
    right=-1
    for c in range(n-1,-1,-1):
        for r in range(n):
            if alpha_array[r][c] > valid_alpha:
                right=c
                break
        if right!=-1:
            break
    top=n
    for r in range(n):
        for c in range(n):
            if alpha_array[r][c] > valid_alpha:
                top=r
                break
        if top!=n:
            break
    bottom=-1
    for r in range(n-1,-1,-1):
        for c in range(n):
            if alpha_array[r][c] > valid_alpha:
                bottom=r
                break
        if bottom!=-1:
            break
    if left>right or top>bottom:
        return None
    pad = min(left, top, n-1-right, n-1-bottom)
    return (pad, pad, n-pad, n-pad)

def mono_process(source_img, icon_size=192, scale=CROP_SCALE_VISUAL, binary=True):
    # source_img is PIL Image 192x192 RGBA
    n = icon_size
    # Ensure size
    if source_img.size != (n,n):
        source_img = source_img.resize((n,n), Image.BILINEAR)
    # Analyze 50x50
    tmp = source_img.resize((ANALYZE_SIZE, ANALYZE_SIZE), Image.BILINEAR)
    pix = list(tmp.getdata())
    hist=[0]*BUCKETS
    inside=0
    filled=0
    r=n//2 if n==ANALYZE_SIZE else ANALYZE_SIZE//2
    r2=r*r
    # For analyze we need 50x50 center
    # Use ANALYZE_SIZE for radius
    rad = ANALYZE_SIZE//2
    for i,p in enumerate(pix):
        alpha = p[3] if len(p)==4 else 255
        gray = gray_luminance(p[0],p[1],p[2])
        if alpha>ALPHA_GATE:
            hist[gray//8]+=1
        col=i%ANALYZE_SIZE
        row=i//ANALYZE_SIZE
        dx=col-rad
        dy=row-rad
        if dx*dx+dy*dy <= rad*rad:
            inside+=1
            if alpha>0:
                filled+=1
    best_idx = max(range(BUCKETS), key=lambda i: hist[i])
    dominant = (best_idx+1)*8-1
    # full size arrays
    pixels = list(source_img.getdata())
    # Ensure RGBA
    gray_arr=[0]*(n*n)
    alpha_arr=[[0]*n for _ in range(n)]
    for i,p in enumerate(pixels):
        rcol,gcol,bcol,acol = (p[0],p[1],p[2],p[3]) if len(p)==4 else (p[0],p[1],p[2],255)
        gray_arr[i]=gray_luminance(rcol,gcol,bcol)
        alpha_arr[i//n][i%n]=acol
    # eStage
    threshold = 255 - dominant
    # suitable false for build-time (we don't have fillRatio gate)
    min_gray=255
    for i in range(n*n):
        row=i//n; col=i%n
        alpha=alpha_arr[row][col]
        if alpha==0:
            continue
        mapped=f_combine(gray_arr[i], threshold)
        # iPick
        if not (alpha<=ALPHA_GATE or mapped>=min_gray):
            min_gray=mapped
        gray_arr[i]=mapped
    # dStage
    f10 = GAIN/(CLAMP_SQ - min_gray*min_gray) if CLAMP_SQ - min_gray*min_gray !=0 else 0
    max_a=0
    out_pixels=[0]*(n*n)
    for idx in range(n*n):
        row=idx//n; col=idx%n
        a=alpha_arr[row][col]
        g=gray_arr[idx]
        if a>ALPHA_GATE and min_gray<CLAMP_GRAY:
            a = int((g*g - CLAMP_SQ)*f10 + 255)
            a = max(0,min(255,a))
        if a>max_a:
            max_a=a
        alpha_arr[row][col]=a
        out_pixels[idx]=(a<<24)
    if max_a <= VALID_ALPHA:
        return None
    if binary:
        for i in range(n*n):
            a = (out_pixels[i]>>24)&0xFF
            b = 255 if a>127 else 0
            out_pixels[i]=b<<24
            alpha_arr[i//n][i%n]=b
        max_a = 255 if any((p>>24)&0xFF==255 for p in out_pixels) else 0
    # centered square
    rect = centered_square_rect(alpha_arr, n)
    if rect is None:
        return None
    pad_l, pad_t, pad_r, pad_b = rect
    # Create cropped source bitmap with pixels at same location
    # We'll create an image with out_pixels
    mono_full = Image.new("RGBA", (n,n), (0,0,0,0))
    for y in range(n):
        for x in range(n):
            a = alpha_arr[y][x]
            mono_full.putpixel((x,y), (0,0,0,a))
    # Now create final scaled mono
    w = int(n*scale)
    h=w
    dst_x = (n - w)//2
    dst_y = (n - w)//2
    # Use the rect as src
    src = mono_full.crop((pad_l, pad_t, pad_r, pad_b))
    # Resize src to w x h and paste centered
    src_scaled = src.resize((w,h), Image.BILINEAR)
    out = Image.new("RGBA", (n,n), (0,0,0,0))
    out.paste(src_scaled, (dst_x, dst_y), src_scaled)
    return out

def synthetic_source_for_package(pkg, size=192):
    # Create a synthetic source icon unique per package: hash-based color + letter
    h = hashlib.md5(pkg.encode()).hexdigest()
    # Use first 6 hex for color
    r = int(h[0:2],16)
    g = int(h[2:4],16)
    b = int(h[4:6],16)
    # Ensure not too dark/light
    img = Image.new("RGBA", (size,size), (0,0,0,0))
    draw = ImageDraw.Draw(img)
    # Draw a rounded rect background with package color
    # Adaptive foreground simulation: draw a shape
    # Use circle or rect based on hash
    shape = int(h[6],16) % 3
    cx, cy = size//2, size//2
    rad = size*0.3
    if shape==0:
        draw.ellipse([cx-rad, cy-rad, cx+rad, cy+rad], fill=(r,g,b,255))
    elif shape==1:
        draw.rectangle([cx-rad, cy-rad, cx+rad, cy+rad], fill=(r,g,b,255))
    else:
        # triangle
        draw.polygon([(cx, cy-rad), (cx-rad, cy+rad), (cx+rad, cy+rad)], fill=(r,g,b,255))
    # Draw letter (first char of package)
    letter = pkg.split(".")[-1][0].upper() if pkg else "A"
    try:
        # Try to load a font
        font = ImageFont.load_default()
        # Use larger size if available
        # Draw text centered
        bbox = draw.textbbox((0,0), letter, font=font)
        tw = bbox[2]-bbox[0]
        th = bbox[3]-bbox[1]
        draw.text((cx-tw//2, cy-th//2), letter, fill=(255,255,255,255), font=font)
    except:
        pass
    return img

def vectorize_mono(mono_img, viewport=48):
    # mono_img is 192x192 RGBA with alpha = mono
    # We want to vectorize to pathData scaled to viewport
    n = mono_img.size[0]
    scale = viewport / n
    # Convert to binary mask
    mask = []
    for y in range(n):
        row=[]
        for x in range(n):
            a = mono_img.getpixel((x,y))[3]
            row.append(1 if a>127 else 0)
        mask.append(row)
    # Find runs per row and create path
    paths=[]
    for y in range(n):
        x=0
        while x < n:
            if mask[y][x]==1:
                x0=x
                while x<n and mask[y][x]==1:
                    x+=1
                x1=x
                # Convert to viewport coords
                vx0 = x0*scale
                vy0 = y*scale
                vw = (x1 - x0)*scale
                vh = 1*scale
                # Add rect as path: M x y h w v h h -w Z
                # Use format with 2 decimal
                paths.append(f"M{vx0:.2f},{vy0:.2f}h{vw:.2f}v{vh:.2f}h{-vw:.2f}Z")
            else:
                x+=1
    if not paths:
        # Fallback: empty
        return ""
    # Merge? Keep as is
    return " ".join(paths)

def generate_for_packages(packages, out_dir="app/src/main/res/drawable", scale=CROP_SCALE_VISUAL):
    out_path = pathlib.Path(out_dir)
    out_path.mkdir(parents=True, exist_ok=True)
    generated=[]
    for pkg in packages:
        # drawable name: jus_<package_last> OR jus_<hash>
        # Use sanitized: jus_<package> with . -> _
        name = "jus_" + pkg.replace(".", "_").replace("-", "_")
        # Limit length
        if len(name) > 40:
            name = name[:40]
        # Also ensure starts with letter and valid
        name = name.lower()
        # Generate synthetic source and process
        src = synthetic_source_for_package(pkg)
        mono = mono_process(src, scale=scale, binary=True)
        if mono is None:
            print(f"Skip {pkg}:MonoProcessor returned None")
            continue
        path_data = vectorize_mono(mono)
        if not path_data:
            print(f"Skip {pkg}: empty path")
            continue
        # Write vector drawable glyph-only (no circle)
        xml = f'''<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="48dp" android:height="48dp" android:viewportWidth="48" android:viewportHeight="48">
    <path android:fillColor="#FFFFFFFF" android:pathData="{path_data}" />
</vector>
'''
        (out_path / f"{name}.xml").write_text(xml)
        generated.append((pkg, name))
        print(f"Generated {name} for {pkg} path_len {len(path_data)}")
    return generated

if __name__ == "__main__":
    # Curated list: take common packages + lawnicons top
    # For demo, use our existing curated + 20 common
    common = [
        "com.google.android.gm",
        "com.google.android.youtube",
        "com.android.chrome",
        "com.whatsapp",
        "com.instagram.android",
        "com.spotify.music",
        "com.google.android.apps.photos",
        "com.google.android.apps.maps",
        "com.facebook.katana",
        "com.twitter.android",
        "com.reddit.frontpage",
        "com.netflix.mediaclient",
        "com.google.android.apps.youtube.music",
        "com.amazon.mShop.android.shopping",
        "com.microsoft.office.outlook",
        "org.telegram.messenger",
        "com.snapchat.android",
        "com.google.android.apps.docs",
        "com.google.android.keep",
        "com.google.android.apps.messaging",
    ]
    gen = generate_for_packages(common)
    # Also need to update appfilter and drawable.xml?
    # For now just generate files
    print(f"Done {len(gen)}")

