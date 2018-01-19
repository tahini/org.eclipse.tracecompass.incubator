/*******************************************************************************
 * Copyright (c) 2014, 2016 École Polytechnique de Montréal
 *
 * All rights reserved. This program and the accompanying materials are
 * made available under the terms of the Eclipse Public License v1.0 which
 * accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *   Mohamad Gebai - Initial API and implementation
 *******************************************************************************/

package org.eclipse.tracecompass.incubator.internal.virtual.machine.analysis.core.model.analysis;

import static org.eclipse.tracecompass.common.core.NonNullUtils.checkNotNull;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.tracecompass.analysis.os.linux.core.trace.DefaultEventLayout;
import org.eclipse.tracecompass.analysis.os.linux.core.trace.IKernelAnalysisEventLayout;
import org.eclipse.tracecompass.analysis.os.linux.core.trace.IKernelTrace;
import org.eclipse.tracecompass.incubator.internal.virtual.machine.analysis.core.model.handlers.IVirtualMachineEventHandler;
import org.eclipse.tracecompass.incubator.internal.virtual.machine.analysis.core.model.handlers.QemuKvmEventHandler;
import org.eclipse.tracecompass.statesystem.core.ITmfStateSystemBuilder;
import org.eclipse.tracecompass.tmf.core.event.ITmfEvent;
import org.eclipse.tracecompass.tmf.core.statesystem.AbstractTmfStateProvider;
import org.eclipse.tracecompass.tmf.core.trace.ITmfTrace;
import org.eclipse.tracecompass.tmf.core.trace.experiment.TmfExperiment;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Multimap;

/**
 * This is the state provider that builds the model for the Virtual Machine
 * environment
 *
 * Attribute tree:
 *
 * <pre>
 * |- <Machine host ID> -> Friendly name (trace name, can be host and/or guest)
 * |  |- Guests VMs
 * |  |  |- <Guest Host ID> -> Friendly name (trace name)
 * |  |  |  |- Process ID -> Process ID
 * |  |  |  |- Hypervisor -> hypervisor
 * |  |  |  |- CPUs
 * |  |  |  |  |- <VCPU id> -> TID on host
 * |  |- Containers
 * |  |  |- <Container ID>
 * </pre>
 *
 * @author Geneviève Bastien
 */
public class VirtualMachineModelStateProvider extends AbstractTmfStateProvider {

    /**
     * Version number of this state provider. Please bump this if you modify the
     * contents of the generated state history in some way.
     */
    private static final int VERSION = 1;

    private final Map<ITmfTrace, IKernelAnalysisEventLayout> fLayouts;

    private final Multimap<String, IVirtualMachineEventHandler> fEventNames = HashMultimap.create();

    private final Collection<IVirtualMachineEventHandler> fHandlers;

    // ------------------------------------------------------------------------
    // Constructor
    // ------------------------------------------------------------------------

    /**
     * Constructor
     *
     * @param experiment
     *            The virtual machine experiment
     */
    public VirtualMachineModelStateProvider(TmfExperiment experiment) {
        super(experiment, "Virtual Machine State Provider"); //$NON-NLS-1$

        fLayouts = new HashMap<>();
        fHandlers = ImmutableSet.of(new QemuKvmEventHandler());
    }

    // ------------------------------------------------------------------------
    // Event names management
    // ------------------------------------------------------------------------

    private void buildEventNames(ITmfTrace trace) {
        IKernelAnalysisEventLayout layout;
        if (trace instanceof IKernelTrace) {
            layout = ((IKernelTrace) trace).getKernelEventLayout();
        } else {
            /* Fall-back to the base LttngEventLayout */
            layout = DefaultEventLayout.getInstance();
        }
        fLayouts.put(trace, layout);
        fHandlers.forEach(handler -> {
            handler.getRequiredEvents(layout).forEach(event -> {
                fEventNames.put(event, handler);
            });
        });
    }

    // ------------------------------------------------------------------------
    // IStateChangeInput
    // ------------------------------------------------------------------------

    @Override
    public TmfExperiment getTrace() {
        ITmfTrace trace = super.getTrace();
        if (trace instanceof TmfExperiment) {
            return (TmfExperiment) trace;
        }
        throw new IllegalStateException("VirtualMachineStateProvider: The associated trace should be an experiment"); //$NON-NLS-1$
    }

    @Override
    public int getVersion() {
        return VERSION;
    }

    @Override
    public VirtualMachineModelStateProvider getNewInstance() {
        TmfExperiment trace = getTrace();
        return new VirtualMachineModelStateProvider(trace);
    }

    @Override
    protected void eventHandle(@Nullable ITmfEvent event) {
        if (event == null) {
            return;
        }

        /* Is the event managed by this analysis */
        final String eventName = event.getName();
        IKernelAnalysisEventLayout eventLayout = fLayouts.get(event.getTrace());
        if (eventLayout == null) {
            buildEventNames(event.getTrace());
            eventLayout = fLayouts.get(event.getTrace());
            if (eventLayout == null) {
                return;
            }
        }

        ITmfStateSystemBuilder ss = checkNotNull(getStateSystemBuilder());
        Collection<IVirtualMachineEventHandler> handlers = fEventNames.get(eventName);
        if (handlers.isEmpty()) {
            return;
        }
        for (IVirtualMachineEventHandler handler : handlers) {
            handler.handleEvent(ss, event, eventLayout);
        }
    }

}
