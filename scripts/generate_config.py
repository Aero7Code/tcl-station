#!/usr/bin/env python3
"""Generate public defaults or a private local weather override for Android."""
from pathlib import Path
import json
import math

ROOT = Path(__file__).resolve().parent.parent
settings = {
    "name": "Denver",
    "latitude": "39.7392",
    "longitude": "-104.9903",
    "timezone": "America/Denver",
}
private = ROOT / "station.local.properties"
if private.exists():
    for line in private.read_text(encoding="utf-8").splitlines():
        line = line.strip()
        if not line or line.startswith("#"):
            continue
        key, sep, value = line.partition("=")
        if not sep or key.strip() not in settings:
            raise ValueError("Invalid station.local.properties entry")
        settings[key.strip()] = value.strip()
for key, lower, upper in (("latitude", -90, 90), ("longitude", -180, 180)):
    value = float(settings[key])
    if not math.isfinite(value) or not lower <= value <= upper:
        raise ValueError(f"Invalid {key}")
    settings[key] = str(value)
if not settings["name"] or len(settings["name"]) > 80 or any(ord(c) < 32 for c in settings["name"]):
    raise ValueError("Invalid name")
if settings["timezone"] != "America/Denver":
    raise ValueError("Only America/Denver is supported by this prototype")
out = ROOT / "build" / "generated" / "StationConfig.java"
out.parent.mkdir(parents=True, exist_ok=True)
out.write_text(
    "package com.aero.tclstation;\n"
    "final class StationConfig {\n"
    + "\n".join(f"    static final String {k.upper()} = {json.dumps(v, ensure_ascii=True)};" for k, v in settings.items())
    + "\n    private StationConfig() {}\n}\n",
    encoding="utf-8",
)
print("Weather configuration generated (local overrides are not printed)")
