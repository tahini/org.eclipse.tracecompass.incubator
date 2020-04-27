/*******************************************************************************
 * Copyright (c) 2017 Ericsson
 *
 * All rights reserved. This program and the accompanying materials are
 * made available under the terms of the Eclipse Public License 2.0 which
 * accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/

package org.eclipse.tracecompass.incubator.internal.kernel.core.inputoutput;

import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.tracecompass.analysis.timing.core.segmentstore.ISegmentStoreProvider;
import org.eclipse.tracecompass.analysis.timing.core.segmentstore.statistics.AbstractSegmentStatisticsAnalysis;
import org.eclipse.tracecompass.segmentstore.core.ISegment;
import org.eclipse.tracecompass.segmentstore.core.segment.interfaces.INamedSegment;
import org.eclipse.tracecompass.tmf.core.trace.ITmfTrace;

/**
 * Call graph statistics analysis used to get statistics on each function type.
 *
 * @author Matthew Khouzam
 */
public class IOQueueStatisticsAnalysis extends AbstractSegmentStatisticsAnalysis {

    /** The analysis module ID */
    private final ISegmentStoreProvider fSegmentStoreProvider;

    public IOQueueStatisticsAnalysis(ISegmentStoreProvider ss) {
        fSegmentStoreProvider = ss;
    }

    @Override
    protected @Nullable ISegmentStoreProvider getSegmentProviderAnalysis(@NonNull ITmfTrace trace) {
        return fSegmentStoreProvider;
    }

    @Override
    protected @Nullable String getSegmentType(@NonNull ISegment segment) {
        if (segment instanceof INamedSegment) {
            INamedSegment named = (INamedSegment) segment;
            return String.valueOf(named.getName());
        }
        return null;
    }


}
