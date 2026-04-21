# @dummy
assert entity @e[type=minecraft:player,distance=..3]
dummy @s jump
tellraw @a "ward chat probe"
assert chat "ward chat probe"
assert chat "ward chat probe" @a
assert not chat "text that was never sent"
dummy ward_quiet spawn
assert not chat "ward chat probe" @e[type=minecraft:player,name=ward_quiet]
dummy ward_quiet leave
