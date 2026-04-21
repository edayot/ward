scoreboard objectives add ward.delay dummy
scoreboard players set #t ward.delay 1
await delay 10
scoreboard players add #t ward.delay 1
assert score #t ward.delay matches 2
scoreboard objectives remove ward.delay
