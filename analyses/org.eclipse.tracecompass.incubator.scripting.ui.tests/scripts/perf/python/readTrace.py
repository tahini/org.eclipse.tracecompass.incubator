loadModule("/TraceCompass/Trace")

trace = openMinimalTrace("Tracing", argv[0])

eventIterator = getEventIterator(trace)
schedSwitchCnt = 0;
while eventIterator.hasNext():
    event = eventIterator.next()
    if event.getName() == "sched_switch":
        schedSwitchCnt = schedSwitchCnt + 1

print(schedSwitchCnt)