"""Reporters present a test run: each module exposes the same ``run()``.

- ``live`` — interactive Rich display, updating as results stream in.
- ``github`` — plain logs plus GitHub Actions annotations for CI.
"""

from . import github, live

__all__ = ["github", "live"]
