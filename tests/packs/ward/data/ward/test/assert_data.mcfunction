data merge storage ward:assert_data {marker: 1b}
assert data storage ward:assert_data marker
assert not data storage ward:assert_data missing
data remove storage ward:assert_data marker

setblock ~ ~ ~ minecraft:chest
item replace block ~ ~ ~ container.0 with minecraft:diamond
assert data block ~ ~ ~ Items[0]
assert not data block ~ ~ ~ LootTable
setblock ~ ~ ~ minecraft:air

summon minecraft:armor_stand ~ ~ ~ {Tags:["ward_data"]}
assert data entity @e[type=minecraft:armor_stand,tag=ward_data,limit=1] Tags
kill @e[tag=ward_data]
