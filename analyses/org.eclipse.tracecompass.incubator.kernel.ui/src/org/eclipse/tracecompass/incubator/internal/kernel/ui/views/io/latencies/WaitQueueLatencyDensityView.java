/******************************************************************************
 * Copyright (c) 2015, 2016 Ericsson
 *
 * All rights reserved. This program and the accompanying materials are
 * made available under the terms of the Eclipse Public License v1.0 which
 * accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/

package org.eclipse.tracecompass.incubator.internal.kernel.ui.views.io.latencies;

import java.util.function.Function;

import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.jface.viewers.TableViewer;
import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.tracecompass.analysis.timing.core.segmentstore.ISegmentStoreProvider;
import org.eclipse.tracecompass.analysis.timing.ui.views.segmentstore.density.AbstractSegmentStoreDensityView;
import org.eclipse.tracecompass.analysis.timing.ui.views.segmentstore.density.AbstractSegmentStoreDensityViewer;
import org.eclipse.tracecompass.analysis.timing.ui.views.segmentstore.table.AbstractSegmentStoreTableViewer;
import org.eclipse.tracecompass.analysis.timing.ui.views.segmentstore.table.SegmentStoreTableViewer;
import org.eclipse.tracecompass.internal.analysis.os.linux.core.inputoutput.InputOutputAnalysisModule;
import org.eclipse.tracecompass.segmentstore.core.ISegment;
import org.eclipse.tracecompass.segmentstore.core.segment.interfaces.INamedSegment;
import org.eclipse.tracecompass.tmf.core.trace.ITmfTrace;
import org.eclipse.tracecompass.tmf.core.trace.TmfTraceUtils;

/**
 * System Call Density view
 *
 * @author Matthew Khouzam
 * @author Marc-Andre Laperle
 */
public class WaitQueueLatencyDensityView extends AbstractSegmentStoreDensityView {

    /** The view's ID */
    public static final String ID = "org.eclipse.tracecompass.analysis.os.linux.views.io.density"; //$NON-NLS-1$

    /**
     * Constructs a new density view.
     */
    public WaitQueueLatencyDensityView() {
        super(ID);
    }

    private static @Nullable ISegmentStoreProvider getSegmentStoreProvider(ITmfTrace trace) {
        InputOutputAnalysisModule module = TmfTraceUtils.getAnalysisModuleOfClass(trace, InputOutputAnalysisModule.class, InputOutputAnalysisModule.ID);
        return (module != null) ? module.getWaitingQueueSegmentStore() : null;
    }

    @Override
    protected AbstractSegmentStoreTableViewer createSegmentStoreTableViewer(Composite parent) {
        return new SegmentStoreTableViewer(new TableViewer(parent, SWT.FULL_SELECTION | SWT.VIRTUAL), InputOutputAnalysisModule.ID, false) {

            @Override
            protected @Nullable ISegmentStoreProvider getSegmentStoreProvider(ITmfTrace trace) {
                return WaitQueueLatencyDensityView.getSegmentStoreProvider(trace);
            }

        };
    }

    @Override
    protected AbstractSegmentStoreDensityViewer createSegmentStoreDensityViewer(Composite parent) {
        return new AbstractSegmentStoreDensityViewer(parent) {

            @Override
            protected @Nullable Function<@NonNull ISegment, @NonNull String> getSubSeriesFunction() {
                return segment -> {
                    if (segment instanceof INamedSegment) {
                        return ((INamedSegment) segment).getName();
                    }
                    return "Unknown"; //$NON-NLS-1$
                };
            }

            @Override
            protected @Nullable ISegmentStoreProvider getSegmentStoreProvider(ITmfTrace trace) {
                return WaitQueueLatencyDensityView.getSegmentStoreProvider(trace);
            }
        };
    }

}
