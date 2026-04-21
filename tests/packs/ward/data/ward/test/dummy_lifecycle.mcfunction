# @timeout 200
dummy ward_lifecycle spawn
assert entity @e[type=minecraft:player,name=ward_lifecycle]
kill @e[type=minecraft:player,name=ward_lifecycle]
dummy ward_lifecycle respawn
assert entity @e[type=minecraft:player,name=ward_lifecycle]
dummy ward_lifecycle leave
await not entity @e[type=minecraft:player,name=ward_lifecycle]
