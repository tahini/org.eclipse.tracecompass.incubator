/*******************************************************************************
 * Copyright (c) 2017 École Polytechnique de Montréal
 *
 * All rights reserved. This program and the accompanying materials are
 * made available under the terms of the Eclipse Public License v1.0 which
 * accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/

package org.eclipse.tracecompass.incubator.dependency.analysis.core.matching;

import java.util.Objects;

import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.tracecompass.analysis.os.linux.core.model.HostThread;
import org.eclipse.tracecompass.datastore.core.interval.IHTIntervalReader;
import org.eclipse.tracecompass.datastore.core.serialization.ISafeByteBufferWriter;
import org.eclipse.tracecompass.segmentstore.core.BasicSegment;

/**
 *
 *
 * @author Geneviève Bastien
 */
public class SpanDependency extends BasicSegment {

    private final HostThread fSource;
    private final HostThread fDestination;

    /**
     *
     */
    private static final long serialVersionUID = -7586922153351337173L;
    /**
     * The factory to read an object from a buffer
     */
    public static final IHTIntervalReader<SpanDependency> SPAN_DEPENDENCY_READ_FACTORY = buffer -> {

            return new SpanDependency(new HostThread(buffer.getString(), buffer.getInt()), new HostThread(buffer.getString(), buffer.getInt()), buffer.getLong(), buffer.getLong());
    };

    /**
     * @param source
     * @param destination
     * @param start
     * @param end
     */
    public SpanDependency(HostThread source, HostThread destination, long start, long end) {
        super(start, end);
        fSource = source;
        fDestination = destination;
    }



    @Override
    public long getStart() {
        // TODO Auto-generated method stub
        return super.getStart();
    }



    @Override
    public long getEnd() {
        // TODO Auto-generated method stub
        return super.getEnd();
    }



    public HostThread getSource() {
        return fSource;
    }

    public HostThread getDestination() {
        return fDestination;
    }

    @Override
    public void writeSegment(@NonNull ISafeByteBufferWriter buffer) {
        buffer.putString(fSource.getHost());
        buffer.putInt(fSource.getTid());
        buffer.putString(fDestination.getHost());
        buffer.putInt(fDestination.getTid());
        buffer.putLong(getStart());
        buffer.putLong(getEnd());
    }

    @Override
    public int hashCode() {
        return Objects.hash(getStart(), getEnd(), fSource, fDestination);
    }

    @Override
    public boolean equals(@Nullable Object obj) {
        if (obj == null) {
            return false;
        }
        if (obj.getClass() != getClass()) {
            return false;
        }
        SpanDependency other = (SpanDependency) obj;
        return (getStart() == other.getStart()
                && getEnd() == other.getEnd()
                && fSource.equals(other.fSource)
                && fDestination.equals(other.fDestination));
    }

}
