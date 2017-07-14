/*******************************************************************************
 * Copyright (c) 2017 École Polytechnique de Montréal
 *
 * All rights reserved. This program and the accompanying materials are
 * made available under the terms of the Eclipse Public License v1.0 which
 * accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/

package org.eclipse.tracecompass.incubator.internal.virtual.machine.analysis.core.graph;

import org.eclipse.tracecompass.analysis.graph.core.base.TmfGraph;
import org.eclipse.tracecompass.analysis.graph.core.building.ITraceEventHandler;
import org.eclipse.tracecompass.tmf.core.event.ITmfEvent;

/**
 * A graph handler to handle kvm events and add proper links to the graph
 *
 * @author Geneviève Bastien
 */
public class KvmGraphHandler implements ITraceEventHandler {

    private final VirtualMachineExecGraphProvider fProvider;

    /**
     * Constructor
     *
     * @param provider
     *            The graph provider
     */
    public KvmGraphHandler(VirtualMachineExecGraphProvider provider) {
        fProvider = provider;
    }

    @Override
    public void handleEvent(ITmfEvent event) {
        TmfGraph assignedGraph = fProvider.getAssignedGraph();
    }

    @Override
    public boolean isCancelled() {
        return false;
    }

    @Override
    public void cancel() {
        // Nothing to do
    }

}
