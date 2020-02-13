/**********************************************************************
 * Copyright (c) 2014 Ericsson, École Polytechnique de Montréal
 *
 * All rights reserved. This program and the accompanying materials are
 * made available under the terms of the Eclipse Public License v1.0 which
 * accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *   Bernd Hufmann - Initial API and implementation
 *   Geneviève Bastien - Create and use base class for XY plots
 **********************************************************************/

package org.eclipse.tracecompass.incubator.internal.kernel.ui.views.io.procesioactivity;

import static org.eclipse.tracecompass.common.core.NonNullUtils.checkNotNull;

import java.util.Arrays;
import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.tracecompass.analysis.os.linux.core.inputoutput.Attributes;
import org.eclipse.tracecompass.analysis.os.linux.core.inputoutput.InputOutputAnalysisModule;
import org.eclipse.tracecompass.statesystem.core.ITmfStateSystem;
import org.eclipse.tracecompass.statesystem.core.exceptions.AttributeNotFoundException;
import org.eclipse.tracecompass.statesystem.core.exceptions.StateSystemDisposedException;
import org.eclipse.tracecompass.statesystem.core.exceptions.TimeRangeException;
import org.eclipse.tracecompass.tmf.core.statesystem.TmfStateSystemAnalysisModule;
import org.eclipse.tracecompass.tmf.core.trace.ITmfTrace;
import org.eclipse.tracecompass.tmf.core.trace.TmfTraceUtils;
import org.eclipse.tracecompass.tmf.ui.viewers.xycharts.barcharts.TmfBarChartViewer;
import org.swtchart.ISeries;

/**
 *
 * @author Houssem Daoud
 * @since 2.0
 */
public class ProcessesioViewer extends TmfBarChartViewer {

    private TmfStateSystemAnalysisModule fModule = null;
    private static final int KB_TO_BYTE = 1024;
    private long fSelectedThread = 6413;
    private final String ReadSerieName = "Read"; //$NON-NLS-1$
    private final String WriteSerieName = "Write"; //$NON-NLS-1$

    /**
     * Constructor
     *
     * @param parent
     *            parent view
     */
    public ProcessesioViewer(Composite parent) {
        super(parent, null, null, null, 10);
        addSeries(ReadSerieName, Display.getDefault().getSystemColor(SWT.COLOR_RED).getRGB());
        addSeries(WriteSerieName, Display.getDefault().getSystemColor(SWT.COLOR_BLUE).getRGB());
        System.out.println(fSelectedThread);
    }

    /**
     * @param tid
     *            tid
     */
    public void setSelectedThread(long tid) {
        fSelectedThread = tid;
        updateContent();
    }

    @Override
    protected void readData(final ISeries series, final long start, final long end, final int nb) {
        if (getTrace() != null) {
            final double fYValuesWritten[] = new double[nb];
            final double fYValuesRead[] = new double[nb];
            Thread thread = new Thread("Histogram viewer update") { //$NON-NLS-1$
                @Override
                public void run() {
                    Arrays.fill(fYValuesWritten, 0.0);
                    Arrays.fill(fYValuesRead, 0.0);
                    Integer fWrittenQuarks;
                    Integer FReadQuarks;
                    ITmfTrace trace = getTrace();
                    trace = checkNotNull(trace);
                    fModule = TmfTraceUtils.getAnalysisModuleOfClass(trace, TmfStateSystemAnalysisModule.class, InputOutputAnalysisModule.ID);
                    fModule.waitForInitialization();
                    ITmfStateSystem ss = fModule.getStateSystem();
                    if (ss == null) {
                        return;
                    }

                    double xvalues[] = getXAxis(start, end, nb);
                    String stringSelectedThread = Long.toString(fSelectedThread);
                    System.out.println(fSelectedThread);
                    int quark;
                    try {
                        quark = ss.getQuarkAbsolute(Attributes.THREADS, stringSelectedThread);
                        fWrittenQuarks = ss.getQuarkRelative(quark, Attributes.BYTES_WRITTEN);
                        FReadQuarks = ss.getQuarkRelative(quark, Attributes.BYTES_READ);
                    } catch (AttributeNotFoundException e1) {
                        e1.printStackTrace();
                        return;
                    }
                    double yvalueWritten = 0.0;
                    double yvalueRead = 0.0;
                    /*
                     * make sure that time is in the trace range after double to
                     * long conversion
                     */
                    long traceStart = getStartTime();
                    long traceEnd = getEndTime();
                    long offset = getTimeOffset();
                    long prevTime = 0;
                    for (int i = 0; i < xvalues.length; i++) {
                        double x = xvalues[i];
                        long time = (long) x + offset;
                        time = Math.max(traceStart, time);
                        time = Math.min(traceEnd, time);

                        try {
                            yvalueWritten = ss.querySingleState(time, fWrittenQuarks).getStateValue().unboxLong() - ss.querySingleState(prevTime, fWrittenQuarks).getStateValue().unboxLong();
                            yvalueRead = ss.querySingleState(time, FReadQuarks).getStateValue().unboxLong() - ss.querySingleState(prevTime, FReadQuarks).getStateValue().unboxLong();
                        } catch (AttributeNotFoundException | StateSystemDisposedException | TimeRangeException e) {
                            yvalueWritten = 0;
                            e.printStackTrace();
                        }
                        fYValuesWritten[i] = yvalueWritten / KB_TO_BYTE;
                        fYValuesRead[i] = yvalueRead / KB_TO_BYTE;
                        prevTime = time;
                    }
                    if (series.getId().equals(ReadSerieName)){
                        drawChart(series, xvalues, fYValuesRead);
                    } else if (series.getId().equals(WriteSerieName)) {
                        drawChart(series, xvalues, fYValuesWritten);
                    }
                }
            };
            thread.start();
        }
    }

}
