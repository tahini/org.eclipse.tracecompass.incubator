loadModule("/TraceCompassTest/Test")

base = 10
limit = 300000
value = base;
while base < limit:
    if value == 1:
        base = base + 1
        value = base

    value = compute(value)
