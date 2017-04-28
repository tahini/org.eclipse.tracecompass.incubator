/*******************************************************************************
 * Copyright (c) 2017 École Polytechnique de Montréal
 *
 * All rights reserved. This program and the accompanying materials are
 * made available under the terms of the Eclipse Public License v1.0 which
 * accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/

package org.eclipse.tracecompass.incubator.internal.lttng.callstack.context.core.analysis;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.tracecompass.incubator.callstack.core.callstack.IEventCallStackProvider;
import org.eclipse.tracecompass.tmf.core.analysis.TmfAbstractAnalysisModule;
import org.eclipse.tracecompass.tmf.core.event.ITmfEvent;
import org.eclipse.tracecompass.tmf.core.event.ITmfEventField;
import org.eclipse.tracecompass.tmf.core.event.aspect.TmfCpuAspect;
import org.eclipse.tracecompass.tmf.core.exceptions.TmfAnalysisException;
import org.eclipse.tracecompass.tmf.core.trace.TmfTraceUtils;

/**
 * @author Geneviève Bastien
 */
public class ContextCallStackAnalysis extends TmfAbstractAnalysisModule implements IEventCallStackProvider {

    /**
     * ID of this analysis
     */
    public static final String ID = "org.eclipse.tracecompass.incubator.callstack.core.context"; //$NON-NLS-1$

    private static final String KERNEL_CALLSTACK_FIELD = "context._callstack_kernel"; //$NON-NLS-1$
    private static final String USER_CALLSTACK_FIELD = "context._callstack_user"; //$NON-NLS-1$
    private static final String KERNEL_STACK_NAME = "kernel"; //$NON-NLS-1$
    private static final String USER_STACK_NAME = "user"; //$NON-NLS-1$

    @Override
    protected boolean executeAnalysis(@NonNull IProgressMonitor monitor) throws TmfAnalysisException {
        /* This analysis is per event, nothing to do at execution */
        return true;
    }

    @Override
    protected void canceling() {

    }

    /**
     * Get CPU
     *
     * @param event
     *            The event containing the cpu
     *
     * @return the CPU number (null for not set)
     */
    public static @Nullable Integer getCpu(ITmfEvent event) {
        Integer cpuObj = TmfTraceUtils.resolveIntEventAspectOfClassForEvent(event.getTrace(), TmfCpuAspect.class, event);
        if (cpuObj == null) {
            /* We couldn't find any CPU information, ignore this event */
            return null;
        }
        return cpuObj;
    }

    @Override
    public Map<String, Collection<Object>> getCallStack(ITmfEvent event) {

        Map<String, Collection<Object>> map = new HashMap<>();
        ITmfEventField content = event.getContent();
        ITmfEventField field = content.getField(KERNEL_CALLSTACK_FIELD);
        if (field != null) {
            map.put(KERNEL_STACK_NAME, getCallstack(field));
        }
        field = content.getField(USER_CALLSTACK_FIELD);
        if (field != null) {
            map.put(USER_STACK_NAME, getCallstack(field));
        }
        return map;
    }

    private static Collection<Object> getCallstack(ITmfEventField field) {
        Object value = field.getValue();
        if (!(value instanceof long[])) {
            return Collections.emptyList();
        }
        long[] callstack = (long[]) value;
        List<Object> longList = new ArrayList<>();
        for (long callsite : callstack) {
            longList.add(callsite);
        }
        return longList;

    }



}
