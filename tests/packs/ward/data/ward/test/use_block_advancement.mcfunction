# @dummy
# Dummy block interactions must fire the any_block_use criteria trigger
setblock ~ ~ ~2 minecraft:stone
setblock ~ ~1 ~2 minecraft:lever[face=floor]
dummy @s use block ~ ~1 ~2
assert entity @s[advancements={ward:use_lever=true}]
