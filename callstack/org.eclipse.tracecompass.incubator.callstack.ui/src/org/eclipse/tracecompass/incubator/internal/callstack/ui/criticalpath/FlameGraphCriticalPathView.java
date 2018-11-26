/*******************************************************************************
 * Copyright (c) 2016 Ericsson
 *
 * All rights reserved. This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License v1.0 which
 * accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Author:
 *     Sonia Farrah
 *******************************************************************************/
package org.eclipse.tracecompass.incubator.internal.callstack.ui.criticalpath;

import java.util.Collections;

import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.swt.widgets.Display;
import org.eclipse.tracecompass.incubator.internal.callstack.core.criticalpath.CriticalPathAggregated;
import org.eclipse.tracecompass.incubator.internal.callstack.core.criticalpath.CriticalPathToCallGraph;
import org.eclipse.tracecompass.incubator.internal.callstack.core.criticalpath.ICriticalPathListener;
import org.eclipse.tracecompass.incubator.internal.callstack.ui.flamegraph.FlameGraphView;
import org.eclipse.tracecompass.tmf.core.signal.TmfTraceSelectedSignal;
import org.eclipse.tracecompass.tmf.core.trace.ITmfTrace;
import org.eclipse.tracecompass.tmf.core.trace.TmfTraceUtils;

/**
 * View to display the flame graph. This uses the flameGraphNode tree generated
 * by CallGraphAnalysisUI.
 *
 * @author Sonia Farrah
 */
public class FlameGraphCriticalPathView extends FlameGraphView implements ICriticalPathListener {

    /**
     * ID of the view
     */
    public static final String CRITPATH_ID = FlameGraphCriticalPathView.class.getPackage().getName() + ".criticalPathFlamegraph"; //$NON-NLS-1$

    /**
     * Constructor
     */
    public FlameGraphCriticalPathView() {
        super(CRITPATH_ID);
    }



    @Override
    public void traceSelected(TmfTraceSelectedSignal signal) {
        super.traceSelected(signal);
        ITmfTrace trace = signal.getTrace();
        CriticalPathAggregated module = TmfTraceUtils.getAnalysisModuleOfClass(trace, CriticalPathAggregated.class, CriticalPathAggregated.ID);
        if (module != null) {
            module.addListener(this);
        }
    }

    @Override
    public void update(@NonNull CriticalPathToCallGraph module) {
        Display.getDefault().asyncExec(() -> buildFlameGraph(Collections.singleton(module), null, null));
    }

}
