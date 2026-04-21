"""Tests for the CLI entry point and default-command routing."""

from pathlib import Path

import pytest
from click.testing import CliRunner

from mcward.cli import cli


@pytest.fixture
def runner() -> CliRunner:
    return CliRunner()


class TestDefaultCommandRouting:
    """Test how the group decides between subcommands and the default test command."""

    def test_help_lists_commands(self, runner: CliRunner) -> None:
        result = runner.invoke(cli, ["--help"])
        assert result.exit_code == 0
        for command in ("clean", "install", "list", "start", "status", "stop", "test"):
            assert command in result.output

    def test_unknown_command_is_an_error(self, runner: CliRunner) -> None:
        """A typo'd subcommand must not silently run zero tests."""
        result = runner.invoke(cli, ["instal"])
        assert result.exit_code != 0
        assert "No such command" in result.output

    def test_selector_routes_to_test(
        self, runner: CliRunner, tmp_path: Path, monkeypatch: pytest.MonkeyPatch
    ) -> None:
        """A namespaced selector runs the test command."""
        monkeypatch.chdir(tmp_path)  # no datapacks here
        result = runner.invoke(cli, ["mypack:*"])
        assert "Datapack not found" in result.output

    def test_bare_invocation_routes_to_test(
        self, runner: CliRunner, tmp_path: Path, monkeypatch: pytest.MonkeyPatch
    ) -> None:
        """No arguments at all defaults to the test command."""
        monkeypatch.chdir(tmp_path)
        result = runner.invoke(cli, [])
        assert "Datapack not found" in result.output

    def test_option_routes_to_test(
        self, runner: CliRunner, tmp_path: Path, monkeypatch: pytest.MonkeyPatch
    ) -> None:
        """Leading test options default to the test command."""
        monkeypatch.chdir(tmp_path)
        result = runner.invoke(cli, ["-p", "nonexistent/*"])
        assert "Datapack not found" in result.output

    def test_subcommand_help_works(self, runner: CliRunner) -> None:
        result = runner.invoke(cli, ["test", "--help"])
        assert result.exit_code == 0
        assert "SELECTOR" in result.output.upper()
