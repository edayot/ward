# @timeout 200
summon minecraft:area_effect_cloud ~ ~1 ~ {Duration: 10, Tags: ["ward_await"]}
assert entity @e[tag=ward_await]
await not entity @e[tag=ward_await]
