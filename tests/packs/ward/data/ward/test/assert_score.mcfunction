scoreboard objectives add ward.score dummy
scoreboard players set #a ward.score 5
scoreboard players set #b ward.score 3
scoreboard players set #c ward.score 5
assert score #a ward.score > #b ward.score
assert score #b ward.score < #a ward.score
assert score #a ward.score >= #c ward.score
assert score #b ward.score <= #a ward.score
assert score #a ward.score = #c ward.score
assert score #a ward.score matches 5
assert score #a ward.score matches 1..10
assert not score #b ward.score matches 5..
scoreboard objectives remove ward.score
