base = 10;
limit = 300000
value = base;
while (base < limit) {
    if (value == 1) {
        base = base + 1
        value = base;
    }
    if (value % 2 == 0) {
        value = value / 2;
    } else {
        value = 3 * value + 1;
    }
}