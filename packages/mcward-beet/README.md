# mcward-beet

[Beet](https://github.com/mcbeet/beet) integration for
[Ward](https://github.com/mcbookshelf/ward), a testing framework for Minecraft
datapacks using mcfunction.

Provides the `mcward.beet.plugin` plugin that ships `test/` functions with
your built pack, and the `beet test` command that builds the current project
and runs its tests. Most users want the [`mcward`](https://pypi.org/project/mcward/) package
instead:

```
uv add mcward[beet]
```
