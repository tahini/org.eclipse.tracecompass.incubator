/*******************************************************************************
 * Copyright (c) 2017 École Polytechnique de Montréal
 *
 * All rights reserved. This program and the accompanying materials are
 * made available under the terms of the Eclipse Public License v1.0 which
 * accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/

package org.eclipse.tracecompass.incubator.web.analysis.experiment;

import java.util.Collections;
import java.util.Set;

import org.eclipse.tracecompass.tmf.core.event.ITmfEvent;
import org.eclipse.tracecompass.tmf.core.trace.ITmfTrace;
import org.eclipse.tracecompass.tmf.core.trace.experiment.TmfExperiment;

/**
 * Experiment class containing traces from a web application (client and server)
 *
 * @author Geneviève Bastien
 */
public class WebExperiment extends TmfExperiment {

    /**
     * Default constructor. Needed by the extension point.
     */
    public WebExperiment() {
        this("", Collections.EMPTY_SET); //$NON-NLS-1$
    }

    /**
     * Constructor with traces and id
     *
     * @param id
     *            The ID of this experiment
     * @param traces
     *            The set of traces that are part of this experiment
     */
    public WebExperiment(String id, Set<ITmfTrace> traces) {
        super(ITmfEvent.class, id, traces.toArray(new ITmfTrace[traces.size()]), TmfExperiment.DEFAULT_INDEX_PAGE_SIZE, null);
    }

}
