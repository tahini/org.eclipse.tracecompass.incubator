/*******************************************************************************
 * Copyright (c) 2017 École Polytechnique de Montréal
 *
 * All rights reserved. This program and the accompanying materials are
 * made available under the terms of the Eclipse Public License v1.0 which
 * accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/

package org.eclipse.tracecompass.incubator.dependency.analysis.core.matching;

import org.eclipse.tracecompass.internal.provisional.datastore.core.interval.IHTIntervalReader;
import org.eclipse.tracecompass.internal.provisional.segmentstore.core.BasicSegment2;

/**
 *
 *
 * @author Geneviève Bastien
 */
@SuppressWarnings("restriction")
public class SpanDependency extends BasicSegment2 {

    /**
     *
     */
    private static final long serialVersionUID = -7586922153351337173L;
    /**
     * The factory to read an object from a buffer
     */
    public static final IHTIntervalReader<SpanDependency> SPAN_DEPENDENCY_READ_FACTORY = buffer -> {
            return new SpanDependency(buffer.getLong(), buffer.getLong());
    };

    /**
     * @param start
     * @param end
     */
    public SpanDependency(long start, long end) {
        super(start, end);
    }


}
