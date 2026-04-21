# @timeout 100
scoreboard objectives add ward.await dummy
scoreboard players set #tick ward.await 0
schedule function ward:helper/bump 5t
await score #tick ward.await matches 1..
scoreboard objectives remove ward.await
