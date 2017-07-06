/*******************************************************************************
 * Copyright (c) 2017 École Polytechnique de Montréal
 *
 * All rights reserved. This program and the accompanying materials are
 * made available under the terms of the Eclipse Public License v1.0 which
 * accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/

package org.eclipse.tracecompass.incubator.dependency.analysis.core.tests.stubs;

import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.tracecompass.tmf.core.event.ITmfEvent;
import org.eclipse.tracecompass.tmf.core.event.matching.IEventMatchingKey;
import org.eclipse.tracecompass.tmf.core.event.matching.ITmfMatchEventDefinition;
import org.eclipse.tracecompass.tmf.core.event.matching.TmfEventMatching.Direction;
import org.eclipse.tracecompass.tmf.core.trace.ITmfTrace;

/**
 * Event matching definition for the stub traces
 *
 * @author Geneviève Bastien
 */
public class OpenTracingAPIMatchingStub implements ITmfMatchEventDefinition {

    private static final String INJECT_EVENT = "inject";
    private static final String RECV_EVENT = "recv";
    private static final String FIELD_NAME = "span";

    private static class OTMatchingKey implements IEventMatchingKey {

        private final String fSpanId;

        public OTMatchingKey(String spanId) {
            fSpanId = spanId;
        }

        @Override
        public int hashCode() {
            return fSpanId.hashCode();
        }

        @Override
        public boolean equals(@Nullable Object obj) {
            if (!(obj instanceof OTMatchingKey)) {
                return false;
            }
            return ((OTMatchingKey) obj).fSpanId.equals(fSpanId);
        }

    }

    @Override
    public IEventMatchingKey getEventKey(@Nullable ITmfEvent event) {
        if (event == null) {
            throw new NullPointerException("Event should not be null");
        }
        String span = event.getContent().getFieldValue(String.class, FIELD_NAME);
        if (span == null) {
            throw new IllegalArgumentException("The event does not have the required field");
        }
        return new OTMatchingKey(span);
    }

    @Override
    public boolean canMatchTrace(@Nullable ITmfTrace trace) {
        return true;
    }

    @Override
    public @Nullable Direction getDirection(@Nullable ITmfEvent event) {
        if (event == null) {
            return null;
        }
        String name = event.getName();
        if (name.equals(INJECT_EVENT)) {
            return Direction.CAUSE;
        }
        if (name.equals(RECV_EVENT)) {
            return Direction.EFFECT;
        }
        return null;
    }

}
