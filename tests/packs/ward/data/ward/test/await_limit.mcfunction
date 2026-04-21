# A delay spanning the whole timeout fits: the executor runs through the
# timeout tick, which the framework itself only fails strictly after
await delay 100
