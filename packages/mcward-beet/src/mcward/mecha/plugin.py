"""
Inspired by a beet packtest plugin https://github.com/CarbonSmasher/packtest-beet/blob/main/packtest_beet/nesting.py

"""


from dataclasses import dataclass
from typing import ClassVar
import importlib.util
import pathlib

from mecha import AstRoot, CompilationDatabase, Parser, FileTypeCompilationUnitProvider, Mecha
from tokenstream import TokenStream, set_location
from beet import Context, JsonFile

from mcward.beet.plugin import TestFunction

@dataclass(frozen=True, slots=True)
class AstTestRoot(AstRoot):
    """Ast test root node.

    Technically not required but it's good practice to have custom root nodes for custom
    file types. Makes it easier to target with @rule and bolt won't treat it as a plain module.
    """

@dataclass
class TestRootParser:
    """Parser for test root."""

    database: CompilationDatabase
    root_parser: Parser

    def __call__(self, stream: TokenStream):
        if "test_file" not in stream.data:
            test_file = isinstance(self.database.current, TestFunction)
            with stream.provide(test_file=test_file):
                node = self.root_parser(stream)
            if test_file and isinstance(node, AstRoot):
                test_root = AstTestRoot(commands=node.commands)
                node = set_location(test_root, node)
            return node
        return self.root_parser(stream)

def beet_default(ctx: Context) -> None:
    """Include test functions from the test folder."""
    ctx.require("mcward.beet.plugin")


    command_tree_path = pathlib.Path(__file__).parent / "commands.json"
    command_tree = JsonFile(source_path=command_tree_path)

    mc = ctx.inject(Mecha)
    mc.providers.append(FileTypeCompilationUnitProvider([TestFunction]))
    mc.spec.add_commands(command_tree.data)
    mc.spec.parsers["root"] = TestRootParser(mc.database, mc.spec.parsers["root"])
