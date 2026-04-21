# Assert and await evaluate against the source modified by /execute
summon minecraft:armor_stand ~2 ~1 ~2 {Tags:["ward_modifier"],NoGravity:1b}
execute positioned ~2 ~1 ~2 run assert entity @e[tag=ward_modifier,distance=..0.5]
execute positioned ~2 ~1 ~2 run await entity @e[tag=ward_modifier,distance=..0.5]
execute positioned ~4 ~1 ~4 run assert not entity @e[tag=ward_modifier,distance=..0.5]
kill @e[tag=ward_modifier]
