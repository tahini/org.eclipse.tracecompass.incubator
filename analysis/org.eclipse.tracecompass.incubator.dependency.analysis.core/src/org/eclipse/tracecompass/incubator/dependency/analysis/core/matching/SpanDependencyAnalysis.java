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
import java.util.Collection;
import java.util.Collections;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.tracecompass.analysis.os.linux.core.event.aspect.LinuxTidAspect;
import org.eclipse.tracecompass.analysis.os.linux.core.model.HostThread;
import org.eclipse.tracecompass.analysis.timing.core.segmentstore.AbstractSegmentStoreAnalysisModule;
import org.eclipse.tracecompass.segmentstore.core.ISegment;
import org.eclipse.tracecompass.segmentstore.core.ISegmentStore;
import org.eclipse.tracecompass.tmf.core.event.ITmfEvent;
import org.eclipse.tracecompass.tmf.core.event.matching.IMatchProcessingUnit;
import org.eclipse.tracecompass.tmf.core.event.matching.TmfEventDependency;
import org.eclipse.tracecompass.tmf.core.event.matching.TmfEventMatching;
import org.eclipse.tracecompass.tmf.core.exceptions.TmfAnalysisException;
import org.eclipse.tracecompass.tmf.core.trace.ITmfTrace;
import org.eclipse.tracecompass.tmf.core.trace.TmfTraceUtils;

/**
 * A dependency analysis that matches events together and create links between them
 *
 * @author Geneviève Bastien
 */
public class SpanDependencyAnalysis extends AbstractSegmentStoreAnalysisModule {

    private class DependencyMatchProcessingUnit implements IMatchProcessingUnit {

        private final ISegmentStore<ISegment> fSegStore;

        public DependencyMatchProcessingUnit(ISegmentStore<ISegment> segmentStore) {
            fSegStore = segmentStore;
        }

        @Override
        public void matchingEnded() {
        }

        @Override
        public int countMatches() {
            return fSegStore.size();
        }

        @Override
        public void addMatch(@Nullable TmfEventDependency match) {
            if (match == null) {
                return;
            }
            ITmfEvent sourceEvent = match.getSourceEvent();
            ITmfEvent destinationEvent = match.getDestinationEvent();
            Object sourceTid = TmfTraceUtils.resolveEventAspectOfClassForEvent(sourceEvent.getTrace(), LinuxTidAspect.class, sourceEvent);
            Object destTid = TmfTraceUtils.resolveEventAspectOfClassForEvent(destinationEvent.getTrace(), LinuxTidAspect.class, destinationEvent);
            // If we cannot associate it with a thread, ignore this match
            if (sourceTid == null || destTid == null) {
                return;
            }
            SpanDependency spanDependency = new SpanDependency(new HostThread(sourceEvent.getTrace().getHostId(), (int) sourceTid), new HostThread(destinationEvent.getTrace().getHostId(), (int) destTid), sourceEvent.getTimestamp().toNanos(), destinationEvent.getTimestamp().toNanos());
            fSegStore.add(spanDependency);
        }

        @Override
        public void init(Collection<ITmfTrace> fTraces) {

        }

    }

    /**
     * ID of this analysis
     */
    public static final String ID = "org.eclipse.tracecompass.incubator.dependency.analysis.core.dependency"; //$NON-NLS-1$

    @Deprecated
    @Override
    protected Object[] readObject(ObjectInputStream ois) throws ClassNotFoundException, IOException {
        return checkNotNull((Object[]) ois.readObject());
    }

    @Override
    protected boolean buildAnalysisSegments(ISegmentStore<ISegment> segmentStore, @NonNull IProgressMonitor monitor) throws TmfAnalysisException {
        ITmfTrace trace = getTrace();
        if (trace == null) {
            throw new NullPointerException("The trace should not be null at this location"); //$NON-NLS-1$
        }
        TmfEventMatching eventMatching = new TmfEventMatching(Collections.singleton(trace), new DependencyMatchProcessingUnit(segmentStore));
        eventMatching.initMatching();
        return eventMatching.matchEvents();
    }

    @Override
    protected void canceling() {

    }

}
