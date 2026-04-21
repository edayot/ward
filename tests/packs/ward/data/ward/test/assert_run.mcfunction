# Run conditions test the reported outcome of a fully parsed command
assert run function ward:helper/pass
assert not run function ward:helper/fail

# The result form matches the returned value against a range
assert result 42 run function ward:helper/pass
assert not result 1.. run function ward:helper/fail

scoreboard objectives add ward.run dummy
scoreboard players set #run ward.run 7
assert run scoreboard players get #run ward.run
assert result 7 run scoreboard players get #run ward.run
assert not result 8.. run scoreboard players get #run ward.run
scoreboard objectives remove ward.run

# The function condition mirrors execute if function: nonzero return
# satisfies, returning fail or never returning does not
assert function ward:helper/pass
assert not function ward:helper/fail
assert not function ward:helper/quiet
