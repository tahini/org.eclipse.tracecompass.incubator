/*******************************************************************************
 * Copyright (c) 2017 École Polytechnique de Montréal
 *
 * All rights reserved. This program and the accompanying materials are
 * made available under the terms of the Eclipse Public License v1.0 which
 * accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/

package org.eclipse.tracecompass.incubator.internal.perf.profiling.core.trace;

import java.util.Map;
import java.util.Objects;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.tracecompass.incubator.internal.perf.profiling.core.Activator;
import org.eclipse.tracecompass.tmf.core.trace.TraceValidationStatus;
import org.eclipse.tracecompass.tmf.ctf.core.trace.CtfTmfTrace;
import org.eclipse.tracecompass.tmf.ctf.core.trace.CtfTraceValidationStatus;

/**
 * A perf trace converted to CTF using the "perf data convert --to-ctf" command
 * line
 *
 * @author Geneviève Bastien
 */
public class PerfCtfTrace extends CtfTmfTrace {

    /**
     * CTF metadata should mention the tracer as perf, so confidence is pretty high
     */
    private static final int CONFIDENCE = 100;
    private static final String PERF_DOMAIN = "\"perf\""; //$NON-NLS-1$

    /**
     * Constructor
     */
    public PerfCtfTrace() {
        super();
    }

    @Override
    public @Nullable IStatus validate(final @Nullable IProject project, final @Nullable String path) {
        IStatus status = super.validate(project, path);
        if (status instanceof CtfTraceValidationStatus) {
            Map<String, String> environment = Objects.requireNonNull(((CtfTraceValidationStatus) status).getEnvironment());
            /* Make sure the domain is "kernel" in the trace's env vars */
            String domain = environment.get("tracer_name"); //$NON-NLS-1$
            if (domain == null || !PERF_DOMAIN.equals(domain)) {
                return new Status(IStatus.ERROR, Activator.PLUGIN_ID, "This trace is not a perf trace"); //$NON-NLS-1$
            }
            return new TraceValidationStatus(CONFIDENCE, Activator.PLUGIN_ID);
        }
        return status;
    }
}
