from __future__ import annotations

import json
import sys
import unittest
from collections import Counter
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / "scripts"))

import generate_workbook_module_specs as generator  # noqa: E402


class WorkbookCatalogTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls) -> None:
        cls.edges = generator.parse_workbook(generator.DEFAULT_WORKBOOK)
        cls.specs = generator.build_module_specs(cls.edges)

    def test_known_snapshot_coverage(self) -> None:
        self.assertEqual(4887, len(self.edges))
        self.assertEqual(1967, len(self.specs))
        self.assertEqual(
            {
                "java": 952,
                "nodejs": 557,
                "go": 265,
                "python": 123,
                "other": 70,
            },
            dict(Counter(spec.ecosystem for spec in self.specs)),
        )
        assigned = [
            edge.excel_row for spec in self.specs for edge in spec.edges
        ]
        self.assertEqual(
            Counter(edge.excel_row for edge in self.edges),
            Counter(assigned),
        )

    def test_only_explicit_java_aliases_are_merged(self) -> None:
        multi_name = {
            frozenset(edge.software for edge in spec.edges)
            for spec in self.specs
            if len({edge.software for edge in spec.edges}) > 1
        }
        expected = {
            frozenset((bare, coordinate))
            for (bare, _), coordinate in (
                generator.SAFE_JAVA_ALIASES.items()
            )
        }
        self.assertEqual(expected, multi_name)

    def test_same_artifact_suffixes_do_not_merge(self) -> None:
        identity_sets = [
            {edge.software for edge in spec.edges}
            for spec in self.specs
        ]
        for names in (
            {
                "com.linkedin.calcite:calcite-core",
                "org.apache.calcite:calcite-core",
            },
            {
                "org.postgresql:postgresql",
                "org.testcontainers:postgresql",
                "postgresql",
            },
            {"bson", "org.mongodb:bson"},
        ):
            self.assertFalse(
                any(names.issubset(identity_set) for identity_set in identity_sets)
            )

    def test_cross_ecosystem_bson_is_split(self) -> None:
        bson_specs = [
            spec
            for spec in self.specs
            if any(
                edge.software in {"bson", "org.mongodb:bson"}
                for edge in spec.edges
            )
        ]
        self.assertEqual(2, len(bson_specs))
        self.assertEqual(
            {"java", "nodejs"},
            {spec.ecosystem for spec in bson_specs},
        )

    def test_module_ids_are_order_independent_and_unique(self) -> None:
        reversed_specs = generator.build_module_specs(
            list(reversed(self.edges))
        )

        def mapping(specs: list[generator.ModuleSpec]) -> dict:
            return {
                (
                    spec.identity,
                    spec.raw_language,
                    spec.target,
                ): (spec.ecosystem, spec.module_slug, spec.module_name)
                for spec in specs
            }

        self.assertEqual(mapping(self.specs), mapping(reversed_specs))
        self.assertEqual(
            len(self.specs),
            len({spec.module_name for spec in self.specs}),
        )
        self.assertEqual(
            len(self.specs),
            len({spec.module_dir for spec in self.specs}),
        )

    def test_direction_classifier_is_fail_closed(self) -> None:
        counts = Counter(
            generator.edge_disposition(edge)[0] for edge in self.edges
        )
        self.assertEqual(
            {
                "upgrade-candidate": 4697,
                "unknown": 107,
                "conflict": 62,
                "same": 21,
            },
            dict(counts),
        )

        def edge(source: str, target: str) -> generator.Edge:
            return generator.Edge(
                excel_row=2,
                sequence="1",
                software="example",
                language="java",
                source=source,
                target=target,
                services="1",
                bucket="B1_Patch直升",
                difficulty="低",
                note="",
            )

        self.assertEqual(
            ("conflict", "mark"),
            generator.edge_disposition(edge("11.0.21", "10.1.57"))[:2],
        )
        self.assertEqual(
            ("same", "noop"),
            generator.edge_disposition(edge("1.0.0", "1.0.0"))[:2],
        )
        self.assertEqual(
            ("unknown", "mark"),
            generator.edge_disposition(
                edge("1.2.3 ... (共10个版本)", "2.0.0")
            )[:2],
        )

    def test_tomcat_user_directive_can_only_restrict_auto(self) -> None:
        spec = next(
            spec
            for spec in self.specs
            if spec.identity
            == "org.apache.tomcat.embed:tomcat-embed-core"
        )
        manifest = json.loads(
            generator.render_manifest(
                spec,
                generator.KNOWN_WORKBOOK_SHA256,
            )
        )
        directive = manifest["taskDirectives"][0]
        self.assertEqual(["11.0.18", "11.0.21"], directive["sourceVersions"])
        self.assertEqual("conflict", directive["relation"])
        self.assertEqual("mark", directive["action"])
        self.assertEqual([], manifest["policy"]["autoSourceWhitelist"])


if __name__ == "__main__":
    unittest.main()
