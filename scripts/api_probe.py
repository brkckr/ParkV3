#!/usr/bin/env python3
"""Probe the live İSPARK endpoints, summarise them and check the contract (api_contract.py).

Writes raw responses and a Markdown report to --out, prints the report, and appends it to
$GITHUB_STEP_SUMMARY when available. Exit codes: 0 = contract holds, 1 = the source could
not be reached (network error or HTTP 5xx; nothing is concluded), 2 = contract violated
(issue.md is written for the workflow to file). Standard library only.
"""
import argparse
import collections
import datetime
import json
import os
import socket
import ssl
import sys
import time
import urllib.error
import urllib.parse
import urllib.request

import api_contract
from api_contract import as_number, json_type as jtype

BASE = os.environ.get("ISPARK_PROBE_BASE", "https://api.ibb.gov.tr/ispark/")  # override for local runs
LAT_RANGE = api_contract.ISTANBUL_LAT
LNG_RANGE = api_contract.ISTANBUL_LNG
UNKNOWN_ID = 999999999
EXIT_OUTAGE = 1
EXIT_VIOLATION = 2


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


def peer_certificate(base):
    """(host, DER certificate or None, issuer, error) for an HTTPS base URL; None otherwise."""
    url = urllib.parse.urlsplit(base)
    if url.scheme != "https":
        return None
    try:
        with socket.create_connection((url.hostname, url.port or 443), timeout=30) as sock:
            with ssl.create_default_context().wrap_socket(sock, server_hostname=url.hostname) as tls:
                issuer = ", ".join(f"{k}={v}" for rdn in tls.getpeercert().get("issuer", ()) for k, v in rdn)
                return url.hostname, tls.getpeercert(binary_form=True), issuer, None
    except Exception as e:  # network or TLS failure: report, do not crash
        return url.hostname, None, None, repr(e)


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
    out.append("**isOpen distribution** (not read by the app, docs/adr/0014): " + ", ".join(f"`{k}`×{v}" for k, v in is_open.most_common()))

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


def decode(body):
    """Decoded JSON, or None when the body is empty or not JSON."""
    try:
        return json.loads(body.decode("utf-8-sig")) if body else None
    except (ValueError, UnicodeDecodeError):
        return None


def unreachable(status):
    return status is None or status >= 500


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--out", default="probe-output")
    args = ap.parse_args()
    os.makedirs(args.out, exist_ok=True)
    report = [f"# İSPARK API probe ({time.strftime('%Y-%m-%d %H:%M:%S %Z')})", ""]

    status, headers, body, elapsed, err = fetch(BASE + "Park")
    with open(os.path.join(args.out, "park.json"), "wb") as f:
        f.write(body)
    report.append(f"## GET Park\n\nstatus={status} content-type={headers.get('Content-Type')} "
                  f"bytes={len(body)} elapsed={elapsed:.2f}s error={err}\n")
    list_status = status
    data = decode(body)
    records = []
    if data is None and body:
        report.append(f"JSON decode failed; body head: {body[:300]!r}\n")
    else:
        report.append(f"top-level type: `{jtype(data)}`\n")
    if isinstance(data, list):
        records = [r for r in data if isinstance(r, dict)]
        report.append(f"records: {len(data)} (objects: {len(records)})\n")
        if records:
            report += field_table(records) + [""]
            report += [f"- {line}" for line in analyse_list(records)] + [""]
            report.append("First two records:\n\n```json\n" +
                          json.dumps(data[:2], ensure_ascii=False, indent=2) + "\n```\n")
    list_response = (status, data)

    # A sample spread over park types, rotating weekly (wider coverage of areaPolygon etc.).
    year, week, _ = datetime.date.today().isocalendar()
    detail_responses = {}
    detail_records = []
    report.append("## GET ParkDetay (sample)\n")
    for pid in api_contract.pick_detail_ids(records, seed=year * 100 + week):
        status, headers, body, elapsed, err = fetch(f"{BASE}ParkDetay?id={pid}")
        with open(os.path.join(args.out, f"detail-{pid}.json"), "wb") as f:
            f.write(body)
        d = decode(body)
        detail_responses[pid] = (status, d)
        report.append(f"- id={pid}: status={status} bytes={len(body)} elapsed={elapsed:.2f}s "
                      f"type={jtype(d)} error={err}")
        if isinstance(d, list):
            detail_records += [o for o in d if isinstance(o, dict)]
        time.sleep(0.3)  # be gentle with the public API
    report.append("")
    if detail_records:
        report += field_table(detail_records) + [""]
        shown = dict(detail_records[0])
        poly = shown.get("areaPolygon")
        if isinstance(poly, str) and len(poly) > 160:
            shown["areaPolygon"] = poly[:160] + f"… ({len(poly)} chars)"
        report.append("First detail record:\n\n```json\n" +
                      json.dumps(shown, ensure_ascii=False, indent=2) + "\n```\n")

    status, _, body, _, err = fetch(f"{BASE}ParkDetay?id={UNKNOWN_ID}")
    report.append(f"## GET ParkDetay?id={UNKNOWN_ID} (unknown id)\n\nstatus={status} error={err} body head: "
                  f"`{body[:200].decode('utf-8', 'replace')}`\n")
    unknown_response = (UNKNOWN_ID, status, decode(body))

    certificate = None
    tls = peer_certificate(BASE)
    if tls is not None:
        host, der, issuer, err = tls
        certificate = (host, der, issuer)
        scts = api_contract.embedded_scts(der) if der else None
        report.append(f"## TLS certificate ({host})\n\nissuer: {issuer}, embedded SCTs={scts}, "
                      f"bytes={len(der) if der else 0}, error={err}\n")

    if unreachable(list_status):
        report.append("## Contract check\n\nSkipped: the list endpoint could not be reached "
                      "(network error or HTTP 5xx). Nothing is concluded from this run.\n")
        exit_code = EXIT_OUTAGE
    else:
        # Detail requests that hit an outage say nothing about the contract either.
        reachable = {pid: r for pid, r in detail_responses.items() if not unreachable(r[0])}
        result = api_contract.check(list_response, reachable, unknown_response, certificate)
        report += api_contract.report_lines(result)
        exit_code = EXIT_VIOLATION if result.violations else 0
        if result.violations:
            run_url = "{}/{}/actions/runs/{}".format(
                os.environ.get("GITHUB_SERVER_URL", "https://github.com"),
                os.environ.get("GITHUB_REPOSITORY", "brkckr/ParkV3"),
                os.environ.get("GITHUB_RUN_ID", "local"),
            )
            with open(os.path.join(args.out, "issue.md"), "w", encoding="utf-8") as f:
                f.write(api_contract.issue_body(result, run_url))

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
