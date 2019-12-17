loadModule("/TraceCompassTest/Test")

function compute(value) {
    if (value % 2 == 0) {
        return value / 2;
    } 
    return 3 * value + 1;
}

doLoopWithCallback(compute)