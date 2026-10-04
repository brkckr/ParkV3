#!/usr/bin/env python3
"""Probe the live İSPARK endpoints and summarise their contract.

Writes raw responses and a Markdown report to --out, prints the report, and appends it to
$GITHUB_STEP_SUMMARY when available. Exits non-zero only if the list endpoint is unusable.
Standard library only.
"""
import argparse
import collections
import json
import os
import sys
import time
import urllib.error
import urllib.request

BASE = "https://api.ibb.gov.tr/ispark/"
LAT_RANGE = (40.5, 41.9)
LNG_RANGE = (27.5, 30.0)


def fetch(url):
    started = time.monotonic()
    req = urllib.request.Request(url, headers={"Accept": "application/json", "User-Agent": "ParkV3-api-probe"})
    try:
        with urllib.request.urlopen(req, timeout=30) as resp:
            body = resp.read()
            return resp.status, dict(resp.headers), body, time.monotonic() - started, None
    except urllib.error.HTTPError as e:
        return e.code, dict(e.headers or {}), e.read() if e.fp else b"", time.monotonic() - started, None
    except Exception as e:  # network failure: report, do not crash
        return None, {}, b"", time.monotonic() - started, repr(e)


def jtype(v):
    if v is None:
        return "null"
    if isinstance(v, bool):
        return "bool"
    if isinstance(v, int):
        return "int"
    if isinstance(v, float):
        return "float"
    if isinstance(v, str):
        return "str"
    return type(v).__name__


def as_number(v):
    if isinstance(v, bool) or v is None:
        return None
    if isinstance(v, (int, float)):
        return float(v)
    if isinstance(v, str):
        try:
            return float(v.strip().replace(",", ".")) if v.strip() else None
        except ValueError:
            return None
    return None


def field_table(records):
    present = collections.Counter()
    types = collections.defaultdict(collections.Counter)
    samples = collections.defaultdict(list)
    for r in records:
        for k, v in r.items():
            present[k] += 1
            types[k][jtype(v)] += 1
            s = v if not isinstance(v, str) else (v[:60] + "…" if len(v) > 60 else v)
            if s not in samples[k] and len(samples[k]) < 3:
                samples[k].append(s)
    lines = ["| field | present | types | samples |", "|---|---|---|---|"]
    for k in sorted(present):
        t = ", ".join(f"{n}:{c}" for n, c in types[k].most_common())
        lines.append(f"| `{k}` | {present[k]}/{len(records)} | {t} | {json.dumps(samples[k], ensure_ascii=False)} |")
    return lines


def analyse_list(records):
    out = []
    is_open = collections.Counter(json.dumps(r.get("isOpen", "<missing>"), ensure_ascii=False) for r in records)
    out.append("**isOpen distribution:** " + ", ".join(f"`{k}`×{v}" for k, v in is_open.most_common()))

    cap_missing = cap_nonpos = empty_missing = empty_neg = empty_gt_cap = 0
    for r in records:
        cap, emp = as_number(r.get("capacity")), as_number(r.get("emptyCapacity"))
        cap_missing += cap is None
        empty_missing += emp is None
        cap_nonpos += cap is not None and cap <= 0
        empty_neg += emp is not None and emp < 0
        empty_gt_cap += cap is not None and emp is not None and emp > cap
    out.append(
        f"**capacity:** missing/null={cap_missing}, <=0={cap_nonpos}. "
        f"**emptyCapacity:** missing/null={empty_missing}, <0={empty_neg}, >capacity={empty_gt_cap}"
    )

    coord_types = collections.Counter((jtype(r.get("lat")), jtype(r.get("lng"))) for r in records)
    inside = outside = zero = missing = swapped_would_fit = 0
    for r in records:
        lat, lng = as_number(r.get("lat")), as_number(r.get("lng"))
        if lat is None or lng is None:
            missing += 1
            continue
        if lat == 0 and lng == 0:
            zero += 1
        if LAT_RANGE[0] <= lat <= LAT_RANGE[1] and LNG_RANGE[0] <= lng <= LNG_RANGE[1]:
            inside += 1
        else:
            outside += 1
            if LAT_RANGE[0] <= lng <= LAT_RANGE[1] and LNG_RANGE[0] <= lat <= LNG_RANGE[1]:
                swapped_would_fit += 1
    out.append(
        f"**coordinates:** types={dict(coord_types)}, inside Istanbul box={inside}, outside={outside} "
        f"(of which swapped would fit={swapped_would_fit}), zero={zero}, missing={missing}"
    )
    ids = [r.get("parkID") for r in records]
    dup = [k for k, v in collections.Counter(map(str, ids)).items() if v > 1]
    out.append(f"**parkID:** distinct={len(set(map(str, ids)))}, duplicates={dup[:10]}")
    for key in ("district", "parkType", "workHours", "freeTime", "fee", "monthlyFee"):
        if any(key in r for r in records):
            c = collections.Counter(json.dumps(r.get(key, "<missing>"), ensure_ascii=False) for r in records)
            out.append(f"**{key}** top values: " + ", ".join(f"`{k}`×{v}" for k, v in c.most_common(8)))
    return out


def pick_detail_ids(records):
    chosen = []

    def add(pred):
        for r in records:
            if pred(r) and r.get("parkID") not in chosen:
                chosen.append(r.get("parkID"))
                return

    add(lambda r: True)
    add(lambda r: str(r.get("isOpen")) in ("0", "False", "false"))
    add(lambda r: r.get("isOpen") is None)
    add(lambda r: (as_number(r.get("emptyCapacity")) or 0) > (as_number(r.get("capacity")) or 1e9))
    return [i for i in chosen if i is not None][:4]


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--out", default="probe-output")
    args = ap.parse_args()
    os.makedirs(args.out, exist_ok=True)
    report = [f"# İSPARK API probe ({time.strftime('%Y-%m-%d %H:%M:%S %Z')})", ""]
    exit_code = 0

    status, headers, body, elapsed, err = fetch(BASE + "Park")
    with open(os.path.join(args.out, "park.json"), "wb") as f:
        f.write(body)
    report.append(f"## GET Park\n\nstatus={status} content-type={headers.get('Content-Type')} "
                  f"bytes={len(body)} elapsed={elapsed:.2f}s error={err}\n")
    records = []
    try:
        data = json.loads(body.decode("utf-8-sig")) if body else None
        report.append(f"top-level type: `{jtype(data)}`\n")
        if isinstance(data, list):
            records = [r for r in data if isinstance(r, dict)]
            report.append(f"records: {len(data)} (objects: {len(records)})\n")
            report += field_table(records) + [""]
            report += [f"- {line}" for line in analyse_list(records)] + [""]
            report.append("First two records:\n\n```json\n" +
                          json.dumps(data[:2], ensure_ascii=False, indent=2) + "\n```\n")
        else:
            exit_code = 1
    except (ValueError, UnicodeDecodeError) as e:
        report.append(f"JSON decode failed: {e!r}; body head: {body[:300]!r}\n")
        exit_code = 1
    if status != 200 or not records:
        exit_code = 1

    for pid in pick_detail_ids(records):
        status, headers, body, elapsed, err = fetch(f"{BASE}ParkDetay?id={pid}")
        with open(os.path.join(args.out, f"detail-{pid}.json"), "wb") as f:
            f.write(body)
        report.append(f"## GET ParkDetay?id={pid}\n\nstatus={status} content-type={headers.get('Content-Type')} "
                      f"bytes={len(body)} elapsed={elapsed:.2f}s error={err}\n")
        try:
            d = json.loads(body.decode("utf-8-sig")) if body else None
        except (ValueError, UnicodeDecodeError) as e:
            report.append(f"JSON decode failed: {e!r}; body head: {body[:300]!r}\n")
            continue
        report.append(f"top-level type: `{jtype(d)}`, length: {len(d) if isinstance(d, list) else '-'}\n")
        objs = d if isinstance(d, list) else [d] if isinstance(d, dict) else []
        objs = [o for o in objs if isinstance(o, dict)]
        if objs:
            report += field_table(objs) + [""]
            shown = dict(objs[0])
            poly = shown.get("areaPolygon")
            if isinstance(poly, str) and len(poly) > 160:
                shown["areaPolygon"] = poly[:160] + f"… ({len(poly)} chars)"
            report.append("```json\n" + json.dumps(shown, ensure_ascii=False, indent=2) + "\n```\n")

    # Unknown id behaviour.
    status, _, body, _, err = fetch(BASE + "ParkDetay?id=999999999")
    report.append(f"## GET ParkDetay?id=999999999 (unknown id)\n\nstatus={status} error={err} body head: "
                  f"`{body[:200].decode('utf-8', 'replace')}`\n")

    text = "\n".join(report)
    with open(os.path.join(args.out, "report.md"), "w", encoding="utf-8") as f:
        f.write(text)
    print(text)
    summary = os.environ.get("GITHUB_STEP_SUMMARY")
    if summary:
        with open(summary, "a", encoding="utf-8") as f:
            f.write(text + "\n")
    return exit_code


if __name__ == "__main__":
    sys.exit(main())
