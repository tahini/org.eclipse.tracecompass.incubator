/*******************************************************************************
 * Copyright (c) 2019 École Polytechnique de Montréal
 *
 * All rights reserved. This program and the accompanying materials are
 * made available under the terms of the Eclipse Public License v1.0 which
 * accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/

package org.eclipse.tracecompass.incubator.internal.scripting.core.trace;

import java.io.File;
import java.io.IOException;
import java.util.Iterator;
import java.util.NoSuchElementException;

import org.eclipse.ease.modules.WrapToScript;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.tracecompass.common.core.collect.BufferedBlockingQueue;
import org.eclipse.tracecompass.statesystem.core.ITmfStateSystemBuilder;
import org.eclipse.tracecompass.statesystem.core.StateSystemFactory;
import org.eclipse.tracecompass.statesystem.core.backend.IStateHistoryBackend;
import org.eclipse.tracecompass.statesystem.core.backend.StateHistoryBackendFactory;
import org.eclipse.tracecompass.tmf.core.event.ITmfEvent;
import org.eclipse.tracecompass.tmf.core.event.ITmfEventField;
import org.eclipse.tracecompass.tmf.core.event.TmfEvent;
import org.eclipse.tracecompass.tmf.core.request.ITmfEventRequest;
import org.eclipse.tracecompass.tmf.core.request.TmfEventRequest;
import org.eclipse.tracecompass.tmf.core.timestamp.TmfTimeRange;
import org.eclipse.tracecompass.tmf.core.trace.ITmfContext;
import org.eclipse.tracecompass.tmf.core.trace.ITmfTrace;
import org.eclipse.tracecompass.tmf.core.trace.TmfTraceManager;
import org.eclipse.tracecompass.tmf.core.trace.TmfTraceUtils;

public class TraceModule {

    private static final int DEFAULT_EVENTS_QUEUE_SIZE = 127;
    private static final int DEFAULT_EVENTS_CHUNK_SIZE = 127;

    /**
     * Fake event indicating the build is over, and the provider should close
     */
    private static class EndEvent extends TmfEvent {
        public EndEvent() {
            super(null, ITmfContext.UNKNOWN_RANK, null, null, null);
        }
    }

    private static final EndEvent END_EVENT = new EndEvent();


    /** Module identifier. */
    public static final String MODULE_ID = "/TraceCompass/Trace"; //$NON-NLS-1$

    /**
     * Adapt object to target type. Try to get an adapter for an object.
     *
     * @return adapted object or <code>null</code>
     */
    @WrapToScript
    public ITmfTrace currentTrace() {
        return TmfTraceManager.getInstance().getActiveTrace();
    }

    @WrapToScript
    public Iterator<ITmfEvent> getEventIterator(ITmfTrace trace) {
        if (trace == null) {
            return null;
        }
        BufferedBlockingQueue<ITmfEvent> eventsQueue = new BufferedBlockingQueue<>(DEFAULT_EVENTS_QUEUE_SIZE, DEFAULT_EVENTS_CHUNK_SIZE);
        trace.sendRequest(new ScriptEventRequest(eventsQueue));
        return new EventIterator(eventsQueue);
    }

    @WrapToScript
    public Object getFieldValue(ITmfEvent event, String fieldName) {

        final ITmfEventField field = event.getContent().getField(fieldName);

        /* If the field does not exist, see if it's a special case */
        if (field == null) {
            // This will allow to use any column as input
            return TmfTraceUtils.resolveAspectOfNameForEvent(event.getTrace(), fieldName, event);
        }
        return field.getValue();

    }

    @WrapToScript
    public @Nullable ITmfStateSystemBuilder getStateSystem(ITmfTrace trace, String fileId) {

        if (trace == null) {
            return null;
        }

        String directory = TmfTraceManager.getSupplementaryFileDir(trace);
        File htFile = new File(directory + fileId);

        /* If the target file already exists, do not rebuild it uselessly */
        // TODO for now we assume it's complete. Might be a good idea to check
        // at least if its range matches the trace's range.

        if (htFile.exists()) {
            try {
                IStateHistoryBackend backend = StateHistoryBackendFactory.createHistoryTreeBackendExistingFile(
                        fileId, htFile, 1);
                return StateSystemFactory.newStateSystem(backend, false);
            } catch (IOException e) {
                /*
                 * There was an error opening the existing file. Perhaps it was corrupted,
                 * perhaps it's an old version? We'll just fall-through and try to build a new
                 * one from scratch instead.
                 */
            }
        }

        /* Size of the blocking queue to use when building a state history */
        final int QUEUE_SIZE = 10000;

        try {
            IStateHistoryBackend backend = StateHistoryBackendFactory.createHistoryTreeBackendNewFile(
                    fileId, htFile, 1, trace.getStartTime().toNanos(), QUEUE_SIZE);
            return StateSystemFactory.newStateSystem(backend);
        } catch (IOException e) {

        }
        return null;

    }


    private static class EventIterator implements Iterator<ITmfEvent> {

        private final BufferedBlockingQueue<ITmfEvent> fEventsQueue;
        private @Nullable ITmfEvent fNext;

        public EventIterator(BufferedBlockingQueue<ITmfEvent> eventsQueue) {
            fEventsQueue = eventsQueue;
        }

        @Override
        public synchronized boolean hasNext() {
            ITmfEvent next = fNext;
            if (next == null) {
                next = fEventsQueue.take();
                fNext = next;
            }
            return next != END_EVENT;
        }

        @Override
        public synchronized ITmfEvent next() {
            if (hasNext()) {
                ITmfEvent next = fNext;
                fNext = null;
                return next;
            }
            throw new NoSuchElementException("No more elements in the queue"); //$NON-NLS-1$
        }

    }

    private static class ScriptEventRequest extends TmfEventRequest {

        private BufferedBlockingQueue<ITmfEvent> fEventsQueue;

        public ScriptEventRequest(BufferedBlockingQueue<ITmfEvent> eventsQueue) {
            super(ITmfEvent.class, TmfTimeRange.ETERNITY, 0, ITmfEventRequest.ALL_DATA, ExecutionType.BACKGROUND, 100);
            fEventsQueue = eventsQueue;
        }

        @Override
        public void handleData(@NonNull ITmfEvent event) {
            super.handleData(event);
            fEventsQueue.put(event);
        }

        @Override
        public synchronized void done() {
            super.done();
            fEventsQueue.put(END_EVENT);
            fEventsQueue.flushInputBuffer();
        }

        @Override
        public synchronized void cancel() {
            super.cancel();
            fEventsQueue.put(END_EVENT);
            fEventsQueue.flushInputBuffer();
        }

    }
}
