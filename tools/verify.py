"""Run the Ward integration suite.

Uses mcward itself end to end, exercising the same code paths as the CLI:
the dev environment is installed like `mcward install dev --force`, started
in daemon mode, and the fixture packs are tested over the bridge like
`mcward test`. The aggregated session is compared against
tests/expected.toml.
"""

import os
import sys
import tomllib
from pathlib import Path

from mcward import (
    EnvironmentManager,
    InstalledEnvironment,
    RunningEnvironment,
    TestSession,
    run_tests,
)

ROOT = Path(__file__).resolve().parent.parent
TESTS = ROOT / "tests"

EVENT_TIMEOUT = 600  # seconds without any test event before giving up
STATUS_COLORS = {"passed": "32", "failed": "31", "skipped": "33"}


def color(text: str, code: str) -> str:
    return f"\x1b[{code}m{text}\x1b[0m"


def step(message: str) -> None:
    print(color(f"> {message}", "1"), flush=True)


def detail(message: str) -> None:
    print(f"  {message}", flush=True)


def main() -> int:
    os.chdir(ROOT)
    environment = prepare_environment()
    log = environment.directory / "logs" / "latest.log"

    step("Running tests over the bridge")
    detail(f"server log: {log}")
    running = environment.start()
    try:
        session = stream_run(running)
    finally:
        running.stop()

    if session.aborted:
        fail_with_log(log, next(iter(session.aborted.values())))
    return check_session(session, TESTS / "expected.toml")


def prepare_environment() -> InstalledEnvironment:
    """Reinstall the dev environment, like mcward install dev --force."""
    env = EnvironmentManager().get("dev")
    step(f"Installing environment {env.version}")
    detail(f"directory: {env.directory}")

    if isinstance(env, RunningEnvironment):
        detail("stopping the running dev daemon")
        env = env.stop()
    if isinstance(env, InstalledEnvironment):
        env = env.uninstall()

    return env.install()


def stream_run(running: RunningEnvironment) -> TestSession:
    """Run the fixture packs, echoing each test result as it completes."""
    reported: set[str] = set()
    session = TestSession([running.version])

    # The default selector also pins that vanilla built-in tests (e.g.
    # minecraft:always_pass) are excluded from daemon runs
    for session in run_tests(
        [TESTS / "packs" / "ward", TESTS / "packs" / "broken", TESTS / "packs" / "overlay"],
        [running],
        selector="*:*",
        timeout=EVENT_TIMEOUT,
    ):
        for batch in session.batches:
            for result in batch.results:
                outcome = result.outcomes.get(running.version)
                if outcome and result.name not in reported:
                    reported.add(result.name)
                    status = outcome.status.value
                    duration = color(f"({outcome.time}ms)", "2")
                    detail(f"{result.name} {color(status, STATUS_COLORS[status])} {duration}")

    return session


def fail_with_log(log: Path, message: str) -> None:
    print(f"\n{color('FAIL:', '1;31')} {message}", file=sys.stderr)
    print(f"\n--- last server output ({log}) ---", file=sys.stderr)
    lines = log.read_text(encoding="utf-8", errors="replace").splitlines()
    for line in lines[-40:]:
        print(f"  {line}", file=sys.stderr)
    raise SystemExit(1)


def check_session(session: TestSession, expected_file: Path) -> int:
    """Compare the aggregated test session against the expected manifest."""
    step(f"Comparing against {expected_file.relative_to(ROOT).as_posix()}")
    expected = tomllib.loads(expected_file.read_text(encoding="utf-8"))
    version = session.versions[0]
    results = {result.name: result for batch in session.batches for result in batch.results}
    problems: list[str] = []

    expected_tests = expected.get("tests", {})
    for name, spec in expected_tests.items():
        if name not in results:
            problems.append(f"missing test: {name}")
            continue
        outcome = results[name].outcomes[version]
        result = outcome.status.value
        if result != spec["result"]:
            problems.append(f"{name}: expected {spec['result']}, got {result} ({outcome.error})")
        elif spec.get("message", "") not in outcome.error:
            problems.append(f"{name}: message {outcome.error!r} missing {spec['message']!r}")
    for name in results.keys() - expected_tests.keys():
        problems.append(f"unexpected test in run: {name}")

    diagnostics = list(session.diagnostics)
    for spec in expected.get("diagnostics", []):
        if not any(spec["type"] in d.kind and spec["id"] in d.id for d in diagnostics):
            problems.append(f"missing diagnostic: {spec['type']} for {spec['id']}")

    if problems:
        failure = f"run does not match {expected_file.name}"
        print(f"\n{color('FAIL:', '1;31')} {failure}", file=sys.stderr)
        for problem in problems:
            print(f"  {color('-', '31')} {problem}", file=sys.stderr)
        return 1

    counts = f"{len(expected_tests)} tests and {len(diagnostics)} diagnostics as expected"
    print(f"\n{color('OK:', '1;32')} {counts}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
