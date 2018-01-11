/*******************************************************************************
 * Copyright (c) 2014, 2015 École Polytechnique de Montréal
 *
 * All rights reserved. This program and the accompanying materials are
 * made available under the terms of the Eclipse Public License v1.0 which
 * accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *   Mohamad Gebai - Initial API and implementation
 *   Geneviève Bastien - Initial API and implementation
 *******************************************************************************/

package org.eclipse.tracecompass.incubator.internal.virtual.machine.analysis.core.model.analysis;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.tracecompass.analysis.os.linux.core.tid.TidAnalysisModule;
import org.eclipse.tracecompass.tmf.core.analysis.IAnalysisModule;
import org.eclipse.tracecompass.tmf.core.statesystem.ITmfStateProvider;
import org.eclipse.tracecompass.tmf.core.statesystem.TmfStateSystemAnalysisModule;
import org.eclipse.tracecompass.tmf.core.trace.ITmfTrace;
import org.eclipse.tracecompass.tmf.core.trace.TmfTraceUtils;
import org.eclipse.tracecompass.tmf.core.trace.experiment.TmfExperiment;

import com.google.common.collect.ImmutableSet;

/**
 * Module for the virtual machine CPU analysis. It tracks the status of the
 * virtual CPUs for each guest of the experiment.
 *
 * @author Mohamad Gebai
 * @author Geneviève Bastien
 */
public class VirtualMachineModelAnalysis extends TmfStateSystemAnalysisModule {

    /** The ID of this analysis module */
    public static final String ID = "org.eclipse.tracecompass.incubator.virtual.machine.analysis.core.model.analysis"; //$NON-NLS-1$

    // TODO: Update with event layout when requirements are back */
    static final Set<String> REQUIRED_EVENTS = ImmutableSet.of(
            // LttngStrings.SCHED_SWITCH
            );

    /**
     * Constructor
     */
    public VirtualMachineModelAnalysis() {
        super();
    }

    @Override
    protected ITmfStateProvider createStateProvider() {
        ITmfTrace trace = getTrace();
        if (!(trace instanceof TmfExperiment)) {
            throw new IllegalStateException();
        }
        return new VirtualMachineModelStateProvider((TmfExperiment) trace);
    }

    @Override
    protected @NonNull StateSystemBackendType getBackendType() {
        return StateSystemBackendType.FULL;
    }

    @Override
    public String getHelpText() {
        return Messages.getMessage(Messages.VirtualMachineModelAnalysis_Help);
    }

    @Override
    protected Iterable<IAnalysisModule> getDependentAnalyses() {
        Set<IAnalysisModule> modules = new HashSet<>();
        ITmfTrace trace = getTrace();
        if (trace == null) {
            return Collections.emptySet();
        }
        TmfTraceUtils.getAnalysisModulesOfClass(trace, TidAnalysisModule.class).forEach(modules::add);
        return modules;
    }

}
