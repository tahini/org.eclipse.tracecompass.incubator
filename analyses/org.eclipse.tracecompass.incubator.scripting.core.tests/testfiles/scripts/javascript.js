/*******************************************************************************
 * Copyright (c) 2019 Geneviève Bastien
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/

// load additional UI commands
loadModule('/TraceCompass/Analysis');
loadModule('/TraceCompass/View');

// open message box
var analysis = getAnalysis("activetid.js");

if (analysis == null) {
    print("Trace is null");
    exit();
}

var ss = analysis.getStateSystem(false);

function runAnalysis() {
    var iter = analysis.getEventIterator();
   
    var event = null;
    while (iter.hasNext()) {
       
        event = iter.next();
       
        if (event.getName() == "sched_switch") {
            cpu = getFieldValue(event, "CPU");
            tid = getFieldValue(event, "next_tid");
            if ((cpu != null) && (tid != null)) {
                quark = ss.getQuarkAbsoluteAndAdd(cpu);
                ss.modifyAttribute(event.getTimestamp().toNanos(), "" + tid, quark);
            }
        }
       
    }
    if (event != null) {
        ss.closeHistory(event.getTimestamp().toNanos());
    }
}

if (!ss.waitUntilBuilt(0)) {
    runAnalysis();
}

provider = createTimeGraphProvider(analysis, {'path' : '*'});
if (provider != null) {
    openTimeGraphView(provider);
}

print("Done");


