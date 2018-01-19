/*******************************************************************************
 * Copyright (c) 2018 École Polytechnique de Montréal
 *
 * All rights reserved. This program and the accompanying materials are
 * made available under the terms of the Eclipse Public License v1.0 which
 * accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/

package org.eclipse.tracecompass.incubator.internal.virtual.machine.analysis.core.model.handlers;

import java.util.Set;

import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.tracecompass.analysis.os.linux.core.model.HostThread;
import org.eclipse.tracecompass.analysis.os.linux.core.trace.IKernelAnalysisEventLayout;
import org.eclipse.tracecompass.incubator.analysis.core.model.IHostModel;
import org.eclipse.tracecompass.incubator.analysis.core.model.ModelManager;
import org.eclipse.tracecompass.statesystem.core.ITmfStateSystemBuilder;
import org.eclipse.tracecompass.tmf.core.event.ITmfEvent;
import org.eclipse.tracecompass.tmf.core.event.aspect.TmfCpuAspect;
import org.eclipse.tracecompass.tmf.core.trace.TmfTraceUtils;

/**
 * The interface that event handler for virtual machine model should implement
 *
 * @author Geneviève Bastien
 */
public interface IVirtualMachineEventHandler {

    /**
     * The name of the guest VMs attribute in the state system
     */
    String GUEST_VMS = "Guests"; //$NON-NLS-1$
    /**
     * The name of the CPUs attribute in the state system
     */
    String CPUS = "CPUs"; //$NON-NLS-1$
    /**
     * The name of the process attribute in the state system
     */
    String PROCESS = "Process Id"; //$NON-NLS-1$

    Set<String> getRequiredEvents(IKernelAnalysisEventLayout layout);

    void handleEvent(ITmfStateSystemBuilder ss, ITmfEvent event, IKernelAnalysisEventLayout eventLayout);

    static @Nullable HostThread getCurrentHostThread(ITmfEvent event, long ts) {
        /* Get the CPU the event is running on */
        Integer cpu = TmfTraceUtils.resolveIntEventAspectOfClassForEvent(event.getTrace(), TmfCpuAspect.class, event);
        if (cpu == null) {
            /* We couldn't find any CPU information, ignore this event */
            return null;
        }
        /* Get the LTTng kernel analysis for the host */
        String hostId = event.getTrace().getHostId();
        IHostModel model = ModelManager.getModelFor(hostId);
        int tid = model.getThreadOnCpu(cpu, ts, true);

        if (tid == IHostModel.UNKNOWN_TID) {
            return null;
        }
        return new HostThread(hostId, tid);
    }

}
