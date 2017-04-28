/*******************************************************************************
 * Copyright (c) 2017 École Polytechnique de Montréal
 *
 * All rights reserved. This program and the accompanying materials are
 * made available under the terms of the Eclipse Public License v1.0 which
 * accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/

package org.eclipse.tracecompass.incubator.dependency.analysis.core.matching;

import static org.eclipse.tracecompass.common.core.NonNullUtils.checkNotNull;

import java.io.IOException;
import java.io.ObjectInputStream;

import org.eclipse.tracecompass.analysis.timing.core.segmentstore.AbstractSegmentStoreAnalysisEventBasedModule;
import org.eclipse.tracecompass.segmentstore.core.ISegment;
import org.eclipse.tracecompass.segmentstore.core.ISegmentStore;
import org.eclipse.tracecompass.tmf.core.event.ITmfEvent;

public class SpanDependencyAnalysis extends AbstractSegmentStoreAnalysisEventBasedModule {

    @Override
    public AbstractSegmentStoreAnalysisRequest createAnalysisRequest(ISegmentStore<ISegment> syscalls) {
        return new SyscallLatencyAnalysisRequest(syscalls);
    }

    @Override
    protected Object[] readObject(ObjectInputStream ois) throws ClassNotFoundException, IOException {
        return checkNotNull((Object[]) ois.readObject());
    }

    private class SyscallLatencyAnalysisRequest extends AbstractSegmentStoreAnalysisRequest {

        public SyscallLatencyAnalysisRequest(ISegmentStore<ISegment> syscalls) {
            super(syscalls);
        }

        @Override
        public void handleData(final ITmfEvent event) {

        }

        @Override
        public void handleCompleted() {

        }

        @Override
        public void handleCancel() {
            super.handleCancel();
        }
    }

}
