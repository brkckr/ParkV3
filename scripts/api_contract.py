"""The shape of the İSPARK responses that ParkV3 relies on (docs/API_CONTRACT.md).

check() compares one probe run with the measured contract below. Violations fail the probe
and, on scheduled runs, open an issue; notes are only reported. Standard library only, so it
runs on a bare GitHub runner and under `python3 -m unittest`.
"""
import collections
import math
import random
import re
from dataclasses import dataclass, field
from datetime import datetime

# Measured on 2026-10-04/05. When the source changes, update this together with
# docs/API_CONTRACT.md and, if needed, the app's parser.
LIST_FIELDS = {
    "parkID": {"int"},
    "parkName": {"str"},
    "lat": {"str"},
    "lng": {"str"},
    "capacity": {"int"},
    "emptyCapacity": {"int"},
    "workHours": {"str"},
    "parkType": {"str"},
    "district": {"str"},
    "freeTime": {"int"},
}
DETAIL_FIELDS = {
    **LIST_FIELDS,
    "locationName": {"str"},
    "address": {"str"},
    "monthlyFee": {"float"},
    "tariff": {"str"},
    "updateDate": {"str", "null"},
    "areaPolygon": {"str"},
}
# Sent by the source but never read by the app, so their presence, type or values are not
# checked (isOpen: docs/adr/0014).
IGNORED_FIELDS = {"isOpen"}

MIN_LIST_RECORDS = 123  # half of the 246 measured records, like the app's shrink guard
TOLERANCE = 0.05  # share of records that may differ before it counts as a change
ISTANBUL_LAT = (40.5, 41.9)
ISTANBUL_LNG = (27.5, 30.0)
POLYGON_NEAR_M = 2_000
DETAIL_SAMPLE_SIZE = 20

OBSERVED_UPDATE_DATE = "%d.%m.%Y %H:%M:%S"
# Everything ParkJsonParser.parseSourceTimestamp accepts.
APP_DATE_PATTERNS = (OBSERVED_UPDATE_DATE, "%d.%m.%Y %H:%M", "%Y-%m-%d %H:%M:%S", "%Y-%m-%d %H:%M")
MS_DATE = re.compile(r"^/Date\((-?\d+)([+-]\d{4})?\)/$")
WKT_POLYGON = re.compile(r"^\s*(MULTI)?POLYGON\s*\(", re.IGNORECASE)
WKT_PAIR = re.compile(r"(-?\d+(?:\.\d+)?)\s+(-?\d+(?:\.\d+)?)")

# Android 17 enforces certificate transparency for apps targeting API 37 (docs/adr/0015):
# the server certificate must carry signed certificate timestamps (SCTs) from CT logs.
# Embedded SCTs (RFC 6962 section 3.3) are checked; two is the usual minimum of CT policies.
SCT_LIST_OID = bytes.fromhex("060a2b06010401d679020402")  # 1.3.6.1.4.1.11129.2.4.2
MIN_EMBEDDED_SCTS = 2


@dataclass
class ContractResult:
    violations: list = field(default_factory=list)
    notes: list = field(default_factory=list)
    polygon_orders: collections.Counter = field(default_factory=collections.Counter)

    def flag(self, share, message):
        if share > TOLERANCE:
            self.violations.append(message)
        elif share > 0:
            self.notes.append(message)


def json_type(value):
    if value is None:
        return "null"
    if isinstance(value, bool):
        return "bool"
    if isinstance(value, int):
        return "int"
    if isinstance(value, float):
        return "float"
    if isinstance(value, str):
        return "str"
    return type(value).__name__


def as_number(value):
    if isinstance(value, bool) or value is None:
        return None
    if isinstance(value, (int, float)):
        return float(value)
    if isinstance(value, str) and value.strip():
        try:
            return float(value.strip().replace(",", "."))
        except ValueError:
            return None
    return None


def park_id(value):
    """The id the app would use: a positive whole number, given as a number or numeric text."""
    number = as_number(value)
    if number is None or not math.isfinite(number) or not number.is_integer() or number <= 0:
        return None
    return int(number)


def distance_m(lat1, lng1, lat2, lng2):
    r = 6_371_000.0
    p1, p2 = math.radians(lat1), math.radians(lat2)
    dp, dl = p2 - p1, math.radians(lng2 - lng1)
    a = math.sin(dp / 2) ** 2 + math.cos(p1) * math.cos(p2) * math.sin(dl / 2) ** 2
    return 2 * r * math.asin(min(1.0, math.sqrt(a)))


def in_istanbul(record):
    lat, lng = as_number(record.get("lat")), as_number(record.get("lng"))
    return (
        lat is not None and lng is not None
        and ISTANBUL_LAT[0] <= lat <= ISTANBUL_LAT[1]
        and ISTANBUL_LNG[0] <= lng <= ISTANBUL_LNG[1]
    )


def polygon_order(wkt, lat, lng):
    """Which axis order puts the polygon on the record's own point.

    Returns "lng-lat" (WKT standard, X = longitude), "lat-lng", "far" (near the point in
    neither order), "no-point", "empty" or "unparseable".
    """
    if wkt is None or (isinstance(wkt, str) and not wkt.strip()):
        return "empty"
    if not isinstance(wkt, str) or not WKT_POLYGON.match(wkt):
        return "unparseable"
    pairs = [(float(x), float(y)) for x, y in WKT_PAIR.findall(wkt)]
    if len(pairs) < 3:
        return "unparseable"
    if lat is None or lng is None:
        return "no-point"
    mean_x = sum(x for x, _ in pairs) / len(pairs)
    mean_y = sum(y for _, y in pairs) / len(pairs)
    if distance_m(lat, lng, mean_y, mean_x) <= POLYGON_NEAR_M:
        return "lng-lat"
    if distance_m(lat, lng, mean_x, mean_y) <= POLYGON_NEAR_M:
        return "lat-lng"
    return "far"


def app_can_parse_date(text):
    text = text.strip()
    if MS_DATE.match(text):
        return True
    try:
        datetime.fromisoformat(text)
        return True
    except ValueError:
        pass
    for pattern in APP_DATE_PATTERNS:
        try:
            datetime.strptime(text, pattern)
            return True
        except ValueError:
            pass
    return False


def matches(text, pattern):
    try:
        datetime.strptime(text.strip(), pattern)
        return True
    except ValueError:
        return False


def describe(data):
    if isinstance(data, list):
        return f"array of {len(data)}"
    return json_type(data)


def check_fields(label, records, expected, result):
    total = len(records)
    present = collections.Counter()
    types = collections.defaultdict(collections.Counter)
    for record in records:
        for name, value in record.items():
            present[name] += 1
            types[name][json_type(value)] += 1
    for name, allowed in expected.items():
        missing = total - present[name]
        result.flag(missing / total, f"{label}: `{name}` missing in {missing}/{total} records")
        differing = sum(count for kind, count in types[name].items() if kind not in allowed)
        result.flag(
            differing / total,
            f"{label}: `{name}` types {dict(types[name])}, expected {sorted(allowed)} ({differing}/{total} differ)",
        )
    for name in sorted(set(present) - set(expected) - IGNORED_FIELDS):
        result.flag(present[name] / total, f"{label}: new field `{name}` {dict(types[name])} in {present[name]}/{total} records")


def check_list(status, data, result):
    if status != 200:
        result.violations.append(f"Park: HTTP {status}")
        return []
    if not isinstance(data, list):
        result.violations.append(f"Park: top level is {describe(data)}, expected an array")
        return []
    records = [r for r in data if isinstance(r, dict)]
    if len(records) < MIN_LIST_RECORDS:
        result.violations.append(f"Park: {len(records)} records, expected at least {MIN_LIST_RECORDS}")
    if not records:
        return []
    total = len(records)
    check_fields("Park", records, LIST_FIELDS, result)
    no_id = sum(park_id(r.get("parkID")) is None for r in records)
    result.flag(no_id / total, f"Park: {no_id}/{total} records without a usable parkID")
    outside = sum(not in_istanbul(r) for r in records)
    result.flag(outside / total, f"Park: {outside}/{total} coordinates missing or outside the Istanbul box")
    return records


def check_detail(requested_id, status, data, result):
    label = f"ParkDetay?id={requested_id}"
    if status != 200:
        result.violations.append(f"{label}: HTTP {status}")
        return None
    if not (isinstance(data, list) and len(data) == 1 and isinstance(data[0], dict)):
        result.violations.append(f"{label}: expected a one-element array, got {describe(data)}")
        return None
    record = data[0]
    if park_id(record.get("parkID")) != requested_id:
        result.violations.append(
            f"{label}: answered parkID {record.get('parkID')!r}; the app treats that as not found"
        )
    return record


def check_detail_values(records, result):
    total = len(records)
    unparseable = [r.get("updateDate") for r in records
                   if isinstance(r.get("updateDate"), str) and r["updateDate"].strip()
                   and not app_can_parse_date(r["updateDate"])]
    result.flag(len(unparseable) / total, f"ParkDetay: updateDate the app cannot read: {unparseable[:3]}")
    other_format = [r.get("updateDate") for r in records
                    if isinstance(r.get("updateDate"), str) and r["updateDate"].strip()
                    and app_can_parse_date(r["updateDate"]) and not matches(r["updateDate"], OBSERVED_UPDATE_DATE)]
    if other_format:
        result.notes.append(f"ParkDetay: updateDate in a new (still readable) format: {other_format[:3]}")
    odd_tariff = [r.get("tariff") for r in records
                  if isinstance(r.get("tariff"), str)
                  and any(":" not in part for part in r["tariff"].split(";") if part.strip())]
    if odd_tariff:
        result.notes.append(f"ParkDetay: tariff lines without 'label : value': {odd_tariff[:2]}")

    for r in records:
        order = polygon_order(r.get("areaPolygon"), as_number(r.get("lat")), as_number(r.get("lng")))
        result.polygon_orders[order] += 1
    orders = result.polygon_orders
    if orders["lat-lng"]:
        result.violations.append(f"ParkDetay: {orders['lat-lng']} areaPolygon(s) in latitude-longitude order")
    result.flag(orders["unparseable"] / total, f"ParkDetay: {orders['unparseable']}/{total} areaPolygon values are not WKT polygons")


def check_unknown_id(requested_id, status, data, result):
    records = data if isinstance(data, list) else [data] if isinstance(data, dict) else []
    records = [r for r in records if isinstance(r, dict)]
    if any(park_id(r.get("parkID")) == requested_id for r in records):
        result.violations.append(f"ParkDetay?id={requested_id} (unknown id): answered a record with that id")
    elif not (status == 200 and len(records) == 1 and as_number(records[0].get("parkID")) == 0):
        result.notes.append(
            f"ParkDetay?id={requested_id} (unknown id): HTTP {status}, {describe(data)} instead of the parkID 0 "
            "placeholder. The app shows 'not found' for an empty answer and an error message for HTTP errors"
        )


def _der_length(buf, i):
    """(length, offset of the content) for the DER length that starts at buf[i]."""
    first = buf[i]
    if first < 0x80:
        return first, i + 1
    size = first & 0x7F
    return int.from_bytes(buf[i + 1:i + 1 + size], "big"), i + 1 + size


def embedded_scts(cert_der):
    """How many SCTs a DER certificate embeds; 0 when it has none or they cannot be read."""
    at = cert_der.find(SCT_LIST_OID)
    if at < 0:
        return 0
    try:
        i = at + len(SCT_LIST_OID)
        if cert_der[i] == 0x01:  # optional "critical" BOOLEAN
            length, i = _der_length(cert_der, i + 1)
            i += length
        if cert_der[i] != 0x04:  # extnValue OCTET STRING
            return 0
        length, i = _der_length(cert_der, i + 1)
        value = cert_der[i:i + length]
        if not value or value[0] != 0x04:  # the SCT list, itself an OCTET STRING
            return 0
        length, i = _der_length(value, 1)
        tls = value[i:i + length]  # TLS-encoded SignedCertificateTimestampList
        end = min(len(tls), 2 + int.from_bytes(tls[:2], "big"))
        count, pos = 0, 2
        while pos + 2 <= end:
            size = int.from_bytes(tls[pos:pos + 2], "big")
            if size == 0 or pos + 2 + size > end:
                break
            count += 1
            pos += 2 + size
        return count
    except IndexError:
        return 0


def check_certificate(host, cert_der, issuer, result):
    if cert_der is None:
        result.notes.append(f"{host}: the TLS certificate could not be read, certificate transparency not checked")
        return
    count = embedded_scts(cert_der)
    if count < MIN_EMBEDDED_SCTS:
        result.violations.append(
            f"{host}: TLS certificate (issuer: {issuer}) embeds {count} SCT(s), expected at least "
            f"{MIN_EMBEDDED_SCTS}. Android 17 enforces certificate transparency for the app (targetSdk 37). "
            "SCTs sent in a TLS extension or OCSP response are not checked here, and a TLS-intercepting "
            "proxy in front of the probe also shows up as 0"
        )


def check(list_response, detail_responses, unknown_response, certificate=None):
    """list_response = (status, data); detail_responses = {id: (status, data)};
    unknown_response = (id, status, data); certificate = (host, DER bytes or None, issuer), or
    None when the source is not reached over HTTPS. `data` is the decoded JSON, or None."""
    result = ContractResult()
    if certificate is not None:
        check_certificate(*certificate, result)
    check_list(*list_response, result)
    details = [record for pid, (status, data) in detail_responses.items()
               if (record := check_detail(pid, status, data, result)) is not None]
    if details:
        check_fields("ParkDetay", details, DETAIL_FIELDS, result)
        check_detail_values(details, result)
    check_unknown_id(*unknown_response, result)
    return result


def pick_detail_ids(records, size=DETAIL_SAMPLE_SIZE, seed=0):
    """A sample spread over park types. The probe seeds it with the ISO week, so the sample
    rotates from week to week but a run can be reproduced."""
    by_type = collections.defaultdict(set)
    for record in records:
        pid = park_id(record.get("parkID"))
        if pid is not None:
            by_type[str(record.get("parkType"))].add(pid)
    rng = random.Random(seed)
    pools = [rng.sample(sorted(ids), len(ids)) for _, ids in sorted(by_type.items())]
    picked = []
    while len(picked) < size and any(pools):
        for pool in pools:
            if pool and len(picked) < size:
                picked.append(pool.pop())
    return picked


def report_lines(result):
    lines = ["## Contract check", ""]
    if result.violations:
        lines += [f"**{len(result.violations)} violation(s):**", ""] + [f"- {v}" for v in result.violations] + [""]
    else:
        lines += ["No violations.", ""]
    if result.notes:
        lines += ["Notes:", ""] + [f"- {n}" for n in result.notes] + [""]
    if result.polygon_orders:
        counts = ", ".join(f"{k}={v}" for k, v in sorted(result.polygon_orders.items()))
        lines += [f"areaPolygon axis order in the detail sample: {counts}", ""]
    return lines


def issue_body(result, run_url):
    lines = [
        "Haftalık `api-probe` çalışması İSPARK yanıtlarında sözleşmeden sapma buldu.",
        "",
        f"Çalışma: {run_url}",
        "",
        "**Sapmalar:**",
        "",
    ]
    lines += [f"- {v}" for v in result.violations]
    if result.notes:
        lines += ["", "**Notlar:**", ""] + [f"- {n}" for n in result.notes]
    lines += [
        "",
        "Ne yapılmalı: Değişikliğin uygulamayı etkileyip etkilemediğini `docs/API_CONTRACT.md` ile "
        "karşılaştırın. Gerekirse ayrıştırıcıyı (`ParkJsonParser`) güncelleyin. Kalıcı bir değişiklikse "
        "`scripts/api_contract.py` içindeki beklentiyi ve belgeyi güncelleyin. Sapma sürerse sonraki "
        "çalışmalar bu issue'ya yorum ekler.",
    ]
    return "\n".join(lines) + "\n"
