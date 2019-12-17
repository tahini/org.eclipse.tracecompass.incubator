loadModule("/TraceCompass/Trace")

var trace = openMinimalTrace("Tracing", argv[0])

eventIterator = getEventIterator(trace);
schedSwitchCnt = 0;
while (eventIterator.hasNext()) {
    event = eventIterator.next();
    if (event.getName().equals("sched_switch")) {
        schedSwitchCnt = schedSwitchCnt + 1
    }
}
print("Sched switch " + schedSwitchCnt)