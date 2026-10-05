"""Offline tests for the contract check: python3 -m unittest discover -s scripts"""
import copy
import unittest

import api_contract
import api_probe
from api_contract import check, pick_detail_ids, polygon_order

TYPES = ["AÇIK OTOPARK", "KAPALI OTOPARK", "YOL ÜSTÜ"]


def list_record(pid):
    lat, lng = 41.0 + pid * 0.0001, 29.0 + pid * 0.0001
    return {
        "parkID": pid, "parkName": f"Otopark {pid}", "lat": f"{lat:.4f}", "lng": f"{lng:.4f}",
        "capacity": 100, "emptyCapacity": 10, "workHours": "24 Saat", "parkType": TYPES[pid % 3],
        "freeTime": 15, "district": "FATİH", "isOpen": pid % 2,
    }


def square(x, y, d=0.0005):
    points = [(x - d, y - d), (x + d, y - d), (x + d, y + d), (x - d, y + d), (x - d, y - d)]
    return "POLYGON ((" + ", ".join(f"{a} {b}" for a, b in points) + "))"


def detail_record(pid):
    r = {k: v for k, v in list_record(pid).items() if k != "isOpen"}
    r.update({
        "locationName": f"{pid} Otopark", "address": "Adres", "monthlyFee": 0.0,
        "tariff": "0-1 Saat : 110,00;Tam Gün : 370,00", "updateDate": "05.10.2026 04:55:23",
        "areaPolygon": square(float(r["lng"]), float(r["lat"])),
    })
    return r


def der_length(n):
    return bytes([n]) if n < 0x80 else bytes([0x80 | 2]) + n.to_bytes(2, "big")


def certificate(scts, critical=False):
    """A stand-in DER blob with the embedded SCT list extension laid out as in RFC 6962 3.3."""
    entries = b""
    for i in range(scts):
        sct = bytes([0]) + bytes([i]) * 32 + bytes(8) + bytes(2) + bytes([4, 3]) + (71).to_bytes(2, "big") + bytes(71)
        entries += len(sct).to_bytes(2, "big") + sct
    tls = len(entries).to_bytes(2, "big") + entries
    value = b"\x04" + der_length(len(tls)) + tls
    extension = api_contract.SCT_LIST_OID + (b"\x01\x01\xff" if critical else b"") + b"\x04" + der_length(len(value)) + value
    return b"\x30\x82\x04\x00" + bytes(40) + b"\x30" + der_length(len(extension)) + extension + bytes(16)


class Probe:
    """One probe run's responses, built from records shaped like the measured ones."""

    def __init__(self):
        self.list = [list_record(pid) for pid in range(1, 131)]
        self.details = {pid: (200, [detail_record(pid)]) for pid in range(1, 21)}
        self.unknown = (999999999, 200, [{"parkID": 0, "parkName": "", "capacity": 1, "emptyCapacity": 1}])
        self.list_status = 200
        self.certificate = ("api.ibb.gov.tr", certificate(scts=3), "CN=Test CA")

    def run(self):
        return check((self.list_status, self.list), self.details, self.unknown, self.certificate)

    def detail(self, pid):
        return self.details[pid][1][0]


class ContractTest(unittest.TestCase):

    def test_responses_shaped_like_the_measurement_pass(self):
        result = Probe().run()
        self.assertEqual(result.violations, [])
        self.assertEqual(result.notes, [])
        self.assertEqual(result.polygon_orders["lng-lat"], 20)

    def test_a_field_missing_from_many_records_is_a_violation_but_one_record_is_a_note(self):
        probe = Probe()
        del probe.list[0]["workHours"]
        self.assertEqual(probe.run().violations, [])
        self.assertIn("`workHours` missing in 1/130", " ".join(probe.run().notes))

        for r in probe.list[:20]:
            r.pop("workHours", None)
        self.assertIn("`workHours` missing in 20/130", " ".join(probe.run().violations))

    def test_type_change_and_new_field_are_violations(self):
        probe = Probe()
        for r in probe.list:
            r["lat"] = float(r["lat"])
            r["fee"] = 10.0
        violations = " ".join(probe.run().violations)
        self.assertIn("`lat` types", violations)
        self.assertIn("new field `fee`", violations)

    def test_list_shape_count_and_status(self):
        probe = Probe()
        probe.list = probe.list[:50]
        self.assertIn("50 records, expected at least", " ".join(probe.run().violations))

        probe = Probe()
        probe.list = None  # an HTML page decodes to nothing
        self.assertIn("top level is null", " ".join(probe.run().violations))

        probe = Probe()
        probe.list_status = 404
        self.assertIn("Park: HTTP 404", probe.run().violations)

    def test_coordinates_outside_istanbul_are_a_violation(self):
        probe = Probe()
        for r in probe.list[:20]:
            r["lat"], r["lng"] = r["lng"], r["lat"]
        self.assertIn("outside the Istanbul box", " ".join(probe.run().violations))

    def test_is_open_is_ignored_whatever_it_holds(self):
        # The app does not read isOpen (docs/adr/0014): changing, dropping or adding it is no drift.
        for change in (lambda r: r.update(isOpen=True), lambda r: r.update(isOpen="Açık"), lambda r: r.pop("isOpen")):
            probe = Probe()
            for r in probe.list:
                change(r)
            probe.detail(1)["isOpen"] = 1
            result = probe.run()
            self.assertEqual(result.violations, [])
            self.assertEqual(result.notes, [])

    def test_embedded_scts_are_counted(self):
        for count in (0, 1, 2, 3):
            self.assertEqual(api_contract.embedded_scts(certificate(count)), count)
        self.assertEqual(api_contract.embedded_scts(certificate(2, critical=True)), 2)
        self.assertEqual(api_contract.embedded_scts(b"\x30\x03\x02\x01\x01"), 0)  # no extension
        # A cut-off extension is unreadable, not a crash.
        self.assertEqual(api_contract.embedded_scts(api_contract.SCT_LIST_OID), 0)
        self.assertLess(api_contract.embedded_scts(certificate(3)[:-200]), 3)

    def test_certificate_without_enough_scts_is_a_violation(self):
        probe = Probe()
        probe.certificate = ("api.ibb.gov.tr", certificate(scts=1), "CN=Test CA")
        self.assertIn("api.ibb.gov.tr: TLS certificate (issuer: CN=Test CA) embeds 1 SCT(s)", " ".join(probe.run().violations))

        probe.certificate = ("api.ibb.gov.tr", b"\x30\x03\x02\x01\x01", "CN=Test CA")
        self.assertIn("embeds 0 SCT(s)", " ".join(probe.run().violations))

    def test_unreadable_or_unchecked_certificate_is_no_violation(self):
        probe = Probe()
        probe.certificate = ("api.ibb.gov.tr", None, None)  # TLS handshake failed
        result = probe.run()
        self.assertEqual(result.violations, [])
        self.assertIn("certificate transparency not checked", " ".join(result.notes))

        probe.certificate = None  # plain HTTP base URL for a local run
        self.assertEqual(probe.run().notes, [])

    def test_detail_answering_another_id_is_a_violation(self):
        probe = Probe()
        probe.detail(3)["parkID"] = 0
        self.assertIn("ParkDetay?id=3: answered parkID 0", " ".join(probe.run().violations))

    def test_update_date_must_stay_readable_by_the_app(self):
        probe = Probe()
        probe.detail(1)["updateDate"] = None
        probe.detail(2)["updateDate"] = "2026-10-05T04:55:23+03:00"
        result = probe.run()
        self.assertEqual(result.violations, [])
        self.assertIn("new (still readable) format", " ".join(result.notes))

        for pid in (3, 4):
            probe.detail(pid)["updateDate"] = "5 Ekim 2026"
        self.assertIn("updateDate the app cannot read", " ".join(probe.run().violations))

    def test_polygon_in_latitude_longitude_order_is_a_violation(self):
        probe = Probe()
        r = probe.detail(5)
        r["areaPolygon"] = square(float(r["lat"]), float(r["lng"]))
        result = probe.run()
        self.assertIn("ParkDetay: 1 areaPolygon(s) in latitude-longitude order", result.violations)
        self.assertEqual(result.polygon_orders["lat-lng"], 1)

    def test_unknown_id_echo_is_a_violation_and_other_changes_are_notes(self):
        probe = Probe()
        probe.unknown = (999999999, 200, [{"parkID": 999999999}])
        self.assertIn("answered a record with that id", " ".join(probe.run().violations))

        probe.unknown = (999999999, 404, None)
        result = probe.run()
        self.assertEqual(result.violations, [])
        self.assertIn("instead of the parkID 0 placeholder", " ".join(result.notes))

    def test_issue_body_lists_violations_and_the_run(self):
        probe = Probe()
        probe.list_status = 404
        body = api_contract.issue_body(probe.run(), "https://example.invalid/run/1")
        self.assertIn("Park: HTTP 404", body)
        self.assertIn("https://example.invalid/run/1", body)


class PolygonOrderTest(unittest.TestCase):

    def test_orders(self):
        lat, lng = 41.0251, 29.0915
        self.assertEqual(polygon_order(square(lng, lat), lat, lng), "lng-lat")
        self.assertEqual(polygon_order(square(lat, lng), lat, lng), "lat-lng")
        self.assertEqual(polygon_order(square(28.5, 40.9), lat, lng), "far")
        self.assertEqual(polygon_order(square(lng, lat), None, None), "no-point")
        self.assertEqual(polygon_order("", lat, lng), "empty")
        self.assertEqual(polygon_order(None, lat, lng), "empty")
        self.assertEqual(polygon_order("POINT (29 41)", lat, lng), "unparseable")
        self.assertEqual(polygon_order("POLYGON ((29 41))", lat, lng), "unparseable")


class SampleTest(unittest.TestCase):

    def test_sample_covers_every_park_type_without_duplicates(self):
        records = [list_record(pid) for pid in range(1, 131)]
        ids = pick_detail_ids(records, size=20, seed=202641)
        self.assertEqual(len(ids), 20)
        self.assertEqual(len(set(ids)), 20)
        self.assertEqual({TYPES[pid % 3] for pid in ids}, set(TYPES))

    def test_sample_is_reproducible_per_seed_and_rotates(self):
        records = [list_record(pid) for pid in range(1, 131)]
        self.assertEqual(pick_detail_ids(records, seed=1), pick_detail_ids(records, seed=1))
        self.assertNotEqual(pick_detail_ids(records, seed=1), pick_detail_ids(records, seed=2))

    def test_records_without_a_usable_id_are_skipped(self):
        records = [list_record(1), {**list_record(2), "parkID": "x"}, {**list_record(3), "parkID": 0}]
        self.assertEqual(pick_detail_ids(records, size=5), [1])


class OutageTest(unittest.TestCase):

    def test_network_errors_and_server_errors_are_outages_not_contract_changes(self):
        self.assertTrue(api_probe.unreachable(None))
        self.assertTrue(api_probe.unreachable(503))
        self.assertFalse(api_probe.unreachable(404))
        self.assertFalse(api_probe.unreachable(200))


if __name__ == "__main__":
    unittest.main()
