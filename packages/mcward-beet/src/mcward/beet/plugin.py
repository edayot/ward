from typing import ClassVar

from beet import Context, NamespaceFile, NamespaceFileScope, TextFileBase


class TestFunction(TextFileBase, NamespaceFile):
    """Represents a Ward test function file."""

    scope: ClassVar[NamespaceFileScope] = ("test",)
    extension: ClassVar[str] = ".mcfunction"


def beet_default(ctx: Context) -> None:
    """Include test functions from the test folder."""
    # Idempotent: the beet test command requires this plugin automatically,
    # and the project may already require it itself
    if TestFunction not in ctx.data.extend_namespace:
        ctx.data.extend_namespace.append(TestFunction)
