# Unresolvable arguments must fail the assert with the command error, never
# silently skip it (the engine swallows exceptions thrown by command bodies)
assert score #t ward.missing matches 1..
