# @timeout 600
# Forceloads must survive batch ends: the vanilla runner clears every forced
# chunk when a batch completes. This test and ward:forceload_kept run in
# different batches in no guaranteed order, so each forces a chunk, flags
# itself, and whichever runs second checks the chunks forced before and
# during the earlier batch. The block await polls until the chunk actually
# loaded; the delay then gives wrongly unforced chunks time to unload.
forceload add 15000320 15000320
await block 15000320 -64 15000320 minecraft:bedrock
await delay 40
scoreboard players set #during ward.forceload 1
execute if score #kept ward.forceload matches 1 unless loaded 15000000 0 15000000 run fail "Load-time forceloaded chunk was unloaded by the test runner"
execute if score #kept ward.forceload matches 1 unless loaded 15000640 0 15000640 run fail "Mid-test forceloaded chunk was unloaded by the test runner"
