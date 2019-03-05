/*******************************************************************************
 * Copyright (c) 2019 École Polytechnique de Montréal
 *
 * All rights reserved. This program and the accompanying materials are
 * made available under the terms of the Eclipse Public License v1.0 which
 * accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/

package org.eclipse.tracecompass.incubator.internal.scripting.core.trace;

import org.eclipse.ease.modules.WrapToScript;
import org.eclipse.tracecompass.tmf.core.trace.ITmfTrace;
import org.eclipse.tracecompass.tmf.core.trace.TmfTraceManager;

public class TraceModule {


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
    public Object getEventIterator(ITmfTrace trace) {
        if (trace == null) {
            return null;
        }

    }
}
