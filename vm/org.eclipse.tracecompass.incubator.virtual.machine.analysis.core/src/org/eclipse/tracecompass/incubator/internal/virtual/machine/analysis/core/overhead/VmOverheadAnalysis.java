/*******************************************************************************
 * Copyright (c) 2017 École Polytechnique de Montréal
 *
 * All rights reserved. This program and the accompanying materials are
 * made available under the terms of the Eclipse Public License v1.0 which
 * accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/

package org.eclipse.tracecompass.incubator.internal.virtual.machine.analysis.core.overhead;

import java.util.Collections;

import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.tracecompass.incubator.internal.virtual.machine.analysis.core.module.VirtualMachineCpuAnalysis;
import org.eclipse.tracecompass.tmf.core.analysis.IAnalysisModule;
import org.eclipse.tracecompass.tmf.core.statesystem.ITmfStateProvider;
import org.eclipse.tracecompass.tmf.core.statesystem.TmfStateSystemAnalysisModule;
import org.eclipse.tracecompass.tmf.core.trace.ITmfTrace;
import org.eclipse.tracecompass.tmf.core.trace.TmfTraceUtils;
import org.eclipse.tracecompass.tmf.core.trace.experiment.TmfExperiment;

/**
 * @author Geneviève Bastien
 *
 */
public class VmOverheadAnalysis extends TmfStateSystemAnalysisModule {

    /** The ID of this analysis module */
    public static final String ID = "org.eclipse.tracecompass.incubator.virtual.machine.analysis.core.overhead.analysis"; //$NON-NLS-1$

    private @Nullable VirtualMachineCpuAnalysis getDependentAnalysis() {
        ITmfTrace trace = getTrace();
        if (trace == null) {
            return null;
        }
        return TmfTraceUtils.getAnalysisModuleOfClass(trace, VirtualMachineCpuAnalysis.class,VirtualMachineCpuAnalysis.ID);
    }

    @Override
    protected @NonNull Iterable<@NonNull IAnalysisModule> getDependentAnalyses() {
        VirtualMachineCpuAnalysis dependentAnalysis = getDependentAnalysis();
        if (dependentAnalysis == null) {
            return Collections.emptySet();
        }
        return Collections.singleton(dependentAnalysis);
    }

    @Override
    protected @NonNull StateSystemBackendType getBackendType() {
        return StateSystemBackendType.FULL;
    }

    @Override
    protected ITmfStateProvider createStateProvider() {
        ITmfTrace trace = getTrace();
        if (!(trace instanceof TmfExperiment)) {
            throw new IllegalStateException();
        }
        return new VmOverheadStateProvider((TmfExperiment) trace);
    }

}
