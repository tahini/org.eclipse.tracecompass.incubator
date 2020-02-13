/*******************************************************************************
 * Copyright (c) 2013, 2015 Ericsson
 *
 * All rights reserved. This program and the accompanying materials are
 * made available under the terms of the Eclipse Public License v1.0 which
 * accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *   Bernd Hufmann - Initial API and implementation
 *******************************************************************************/
package org.eclipse.tracecompass.incubator.internal.kernel.ui.views.io.latencyAverage;

import org.eclipse.swt.widgets.Composite;
import org.eclipse.tracecompass.incubator.internal.kernel.ui.views.io.latencyAverage.LatencyAverageViewer;
import org.eclipse.tracecompass.tmf.ui.viewers.xycharts.TmfXYChartViewer;
import org.eclipse.tracecompass.tmf.ui.views.TmfChartView;

/**
 * Histogram View based on TmfChartView.
 *
 * @author Bernd Hufmann
 * @since 2.0
 */
public class LatencyAverageView extends TmfChartView {
    /** The view ID. */
    public static final String ID = "org.eclipse.tracecompass.analysis.os.linux.views.latencyaverage"; //$NON-NLS-1$

    /**
     * Default Constructor
     */
    public LatencyAverageView() {
        super(ID);
    }

    @Override
    protected TmfXYChartViewer createChartViewer(Composite parent) {
        return new LatencyAverageViewer(parent);
    }
}
