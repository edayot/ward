# @timeout 100
# Await run re-executes the parsed tail every tick until satisfied
scoreboard objectives add ward.run_await dummy
scoreboard players set #tick ward.run_await 0
schedule function ward:helper/bump_run 5t
await run execute if score #tick ward.run_await matches 1..
await result 1.. run scoreboard players get #tick ward.run_await
await not run execute if score #tick ward.run_await matches 2..
await function ward:helper/pass
await not function ward:helper/fail
scoreboard objectives remove ward.run_await
