summon minecraft:armor_stand ~ ~ ~ {Tags:["ward_entity"]}
assert entity @e[type=minecraft:armor_stand,tag=ward_entity]
assert entity @e[tag=ward_entity] inside
assert not entity @e[type=minecraft:warden,distance=..8]
kill @e[tag=ward_entity]
