"""Tests for version curation in the environment selection helpers."""

from mcward import Version
from mcward.cli.environments import curate_versions


class TestCurateVersions:
    """Test the curated version listing policy."""

    def test_keeps_newest_snapshot_only(self) -> None:
        """Only the newest snapshot per (year, major) line survives."""
        versions = [
            Version.parse("26.2-snapshot-6"),
            Version.parse("26.2-snapshot-5"),
            Version.parse("26.1.2"),
            Version.parse("26.1.1"),
        ]
        names = [v.name for v in curate_versions(versions)]
        assert names == ["26.2-snapshot-6", "26.1.2", "26.1.1"]
