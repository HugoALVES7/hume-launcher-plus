#!/usr/bin/env python3
# Fichier ajouté dans le fork Hume Launcher+ : génère les avatars de profils.
"""Builds the profile avatars (assets/avatars) from tools/avatars.json.

Each picture is downloaded once (tools/.avatar_cache), cropped square around the face with Apple's
Vision framework (tools/face_crop.swift, macOS) and written as a 360 px JPEG, plus
assets/avatars/index.json for the app. The pictures belong to their owners: they are generated
locally for a personal build and not committed (see .gitignore).
    python3 tools/fetch_avatars.py            # only what's missing
    python3 tools/fetch_avatars.py --force    # redo every crop
An item may carry a 4th field "cx,cy,side" to set the crop by hand (fractions, origin top-left).
"""
import json
import os
import subprocess
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(HERE)
OUT = os.path.join(ROOT, "assets", "avatars")
CACHE = os.path.join(HERE, ".avatar_cache")
SIZE = 360
force = "--force" in sys.argv

cats = json.load(open(os.path.join(HERE, "avatars.json")))
os.makedirs(CACHE, exist_ok=True)
index = []
for cat in cats:
    os.makedirs(os.path.join(OUT, cat["id"]), exist_ok=True)
    items = []
    for it in cat["items"]:
        key, name, url = it[0], it[1], it[2]
        raw = os.path.join(CACHE, cat["id"] + "_" + key)
        if not os.path.exists(raw):
            # A picture that can't be fetched (or put in tools/.avatar_cache by hand) is skipped.
            got = subprocess.run(["curl", "-sfL", "--max-time", "60", "-A", "HumeAvatarBuilder/1.0 (personal build)",
                                  "-o", raw, url]).returncode == 0
            if not got:
                print(cat["id"], key, "not downloaded, skipped")
                if os.path.exists(raw):
                    os.remove(raw)
                continue
        dst = os.path.join(OUT, cat["id"], key + ".jpg")
        if force or not os.path.exists(dst):
            args = ["swift", os.path.join(HERE, "face_crop.swift"), raw, dst, str(SIZE)] + cat["tint"]
            if len(it) > 3:
                args.append(it[3])
            res = subprocess.run(args, capture_output=True, text=True)
            print(cat["id"], key, (res.stdout or res.stderr).strip())
        items.append({"id": cat["id"] + "/" + key, "name": name})
    if items:
        index.append({"id": cat["id"], "name": cat["name"], "items": items})
with open(os.path.join(OUT, "index.json"), "w") as f:
    json.dump(index, f, ensure_ascii=False, indent=1)
print("avatars:", sum(len(c["items"]) for c in index))
