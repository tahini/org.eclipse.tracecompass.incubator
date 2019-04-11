# load additional UI commands
loadModule('/TraceCompass/Analysis');
loadModule('/TraceCompass/View');

from py4j.java_gateway import JavaClass

# Create an analysis for this script
analysis = getAnalysis("activetid_python.js")

if analysis is None:
    print("Trace is null")
    exit()

ss = analysis.getStateSystem(False)

def strToVarargs(str):
    object_class = java.lang.String
    object_array = gateway.new_array(object_class, 1)
    object_array[0] = str
    return object_array

def runAnalysis():
    iter = analysis.getEventIterator()
   
    event = None
    while iter.hasNext():
       
        event = iter.next();
       
        if event.getName() == "sched_switch":
            cpu = getFieldValue(event, "CPU")
            tid = getFieldValue(event, "next_tid")
            if (not(cpu is None) and not(tid is None)):
                quark = ss.getQuarkAbsoluteAndAdd(strToVarargs(str(cpu)))
                ss.modifyAttribute(event.getTimestamp().toNanos(), str(tid), quark)
       
    if not(event is None):
        ss.closeHistory(event.getTimestamp().toNanos())

if not(ss.waitUntilBuilt(0)):
    runAnalysis()

provider = createTimeGraphProvider(analysis, {'path' : '*'});
if not(provider is None):
    openTimeGraphView(provider)

print("Done")