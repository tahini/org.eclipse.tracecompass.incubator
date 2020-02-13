/*******************************************************************************
 * Copyright (c) 2013, 2014 Ericsson
 *
 * All rights reserved. This program and the accompanying materials are
 * made available under the terms of the Eclipse Public License v1.0 which
 * accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *   Alexandre Montplaisir - Initial API and implementation
 *   Bernd Hufmann - Updated to new TMF chart framework
 *******************************************************************************/
package org.eclipse.tracecompass.incubator.internal.kernel.ui.views.io.latencyAverage;

import static org.eclipse.tracecompass.common.core.NonNullUtils.checkNotNull;

import java.util.ArrayList;
import java.util.Arrays;

import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.tracecompass.analysis.os.linux.core.inputoutput.InputOutputAnalysisModule;
import org.eclipse.tracecompass.tmf.core.trace.ITmfTrace;
import org.eclipse.tracecompass.tmf.core.trace.TmfTraceUtils;
import org.eclipse.tracecompass.tmf.ui.viewers.xycharts.barcharts.TmfBarChartViewer;
import org.swtchart.Chart;
import org.swtchart.IAxis;
import org.swtchart.ISeries;
import org.swtchart.LineStyle;

/**
 * Histogram Viewer implementation based on TmfBarChartViewer.
 *
 * @author Alexandre Montplaisir
 * @author Bernd Hufmann
 * @since 2.0
 */
public class LatencyAverageViewer extends TmfBarChartViewer {


    private ArrayList<Long> table = new ArrayList<>();
    /**
     * Creates a Histogram Viewer instance.
     * @param parent
     *            The parent composite to draw in.
     */
    public LatencyAverageViewer(Composite parent) {
        super(parent, null, null, null, TmfBarChartViewer.MINIMUM_BAR_WIDTH);

        Chart swtChart = getSwtChart();

        IAxis xAxis = swtChart.getAxisSet().getXAxis(0);
        IAxis yAxis = swtChart.getAxisSet().getYAxis(0);

        /* Hide the grid */
        xAxis.getGrid().setStyle(LineStyle.NONE);
        yAxis.getGrid().setStyle(LineStyle.NONE);

        /* Hide the legend */
        swtChart.getLegend().setVisible(false);

        addSeries("Number of events", Display.getDefault().getSystemColor(SWT.COLOR_DARK_CYAN).getRGB()); //$NON-NLS-1$
    }

    @Override
    protected void readData(final ISeries series, final long start, final long end, final int nb) {
        if (getTrace() != null) {
            final double y[] = new double[nb];

            Thread thread = new Thread("Histogram viewer update") { //$NON-NLS-1$
                @Override
                public void run() {
                    double x[] = getXAxis(start, end, nb);
                    Arrays.fill(y, 0.0);

                    /* Add the values for each trace */
                        ITmfTrace trace = getTrace();
                        trace = checkNotNull(trace);
                        /* Retrieve the statistics object */
                        final InputOutputAnalysisModule ioMod = TmfTraceUtils.getAnalysisModuleOfClass(trace, InputOutputAnalysisModule.class ,InputOutputAnalysisModule.ID);
                        if (ioMod == null) {
                            return;
                        }
                        ioMod.waitForInitialization();

                        for (int i = 1; i < x.length ; i++) {
                            long starttime = (long)x[i-1] + getTimeOffset();
                            long endtime = (long)x[i] + getTimeOffset();
                            table = ioMod.getLatencyTables(starttime,endtime);
                            if (table.isEmpty()){
                                y[i] = 0;
                                continue;
                            }
                            Long sum = new Long(0);
                            for (Long lat : table){
                                sum += lat;
                            }
                            y[i] = sum / table.size();
                        }
                    drawChart(series, x, y);
                }
            };
            thread.start();
        }
        return;
    }
}
