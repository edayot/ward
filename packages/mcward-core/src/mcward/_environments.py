"""Environment state management.

States are snapshots, not live handles: obtain them from the manager, and
discard an instance after calling a transition on it — the old object still
exists but no longer reflects reality (a stopped RunningEnvironment fails
with connection errors, not with "already stopped").
"""

import asyncio
import shutil
from collections.abc import Iterator
from dataclasses import dataclass
from pathlib import Path

from . import _assets, _daemon
from ._daemon import RunningProcess
from ._exceptions import DeployError, InstallError
from ._protocol import Event, Status
from ._versions import Version


@dataclass(frozen=True)
class UninstalledEnvironment:
    """Represents an environment that has not yet been installed."""

    directory: Path
    version: Version

    def install(self) -> InstalledEnvironment:
        """Download and prepare the environment for use."""
        asyncio.run(_assets.install(self.directory, self.version))
        return InstalledEnvironment(self.directory, self.version)


@dataclass(frozen=True)
class InstalledEnvironment:
    """Represents an environment that has been installed but not running."""

    directory: Path
    version: Version

    def start(self) -> RunningEnvironment:
        """Start process for this environment."""
        return RunningEnvironment(self.directory, self.version, _daemon.start(self.directory))

    def uninstall(self) -> UninstalledEnvironment:
        """Uninstall the environment by removing its directory."""
        try:
            if self.directory.exists():
                shutil.rmtree(self.directory)
        except OSError as e:
            raise InstallError(f"Could not remove {self.directory}: {e}") from e
        return UninstalledEnvironment(self.directory, self.version)


@dataclass(frozen=True)
class RunningEnvironment:
    """Represents an environment with process running."""

    directory: Path
    version: Version
    process: RunningProcess

    def status(self) -> Status:
        """Get process status."""
        return _daemon.status(self.process.address)

    def stop(self) -> InstalledEnvironment:
        """Stop process and transition back to installed state."""
        _daemon.stop(self.process)
        return InstalledEnvironment(self.directory, self.version)

    def test(
        self,
        datapacks: list[Path],
        selector: str = "*:*",
        timeout: float | None = None,
    ) -> Iterator[Event]:
        """Deploy the given datapacks and stream a test run.

        ``timeout`` bounds the wait between consecutive events; ``None``
        waits indefinitely.
        """
        deployed = self.directory / "world" / "datapacks"
        try:
            if deployed.exists():
                shutil.rmtree(deployed)
            deployed.mkdir(parents=True)
            for datapack, name in zip(datapacks, _unique_names(datapacks), strict=True):
                if datapack.is_file():
                    # Zipped datapacks deploy as-is; the server reads them directly
                    shutil.copyfile(datapack, deployed / name)
                else:
                    shutil.copytree(
                        datapack,
                        deployed / name,
                        ignore=shutil.ignore_patterns(".*", "__pycache__", "node_modules"),
                    )
        except OSError as e:
            raise DeployError(f"Could not copy datapacks to the test server: {e}") from e

        return _daemon.stream_tests(self.process.address, selector, timeout=timeout)


def _unique_names(datapacks: list[Path]) -> list[str]:
    """Deployment names for the packs, suffixing duplicates (pack, pack-2, ...).

    The suffix goes before any extension (pack-2.zip): the server only reads
    zipped datapacks whose file name ends in .zip.
    """
    reserved = {datapack.name for datapack in datapacks}
    names: list[str] = []
    for datapack in datapacks:
        name, count = datapack.name, 1
        while name in names or (name != datapack.name and name in reserved):
            count += 1
            name = f"{datapack.stem}-{count}{datapack.suffix}"
        names.append(name)
    return names
