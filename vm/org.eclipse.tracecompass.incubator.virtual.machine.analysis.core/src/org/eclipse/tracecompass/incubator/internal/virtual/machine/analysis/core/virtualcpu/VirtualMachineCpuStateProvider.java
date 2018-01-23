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

package org.eclipse.tracecompass.incubator.internal.virtual.machine.analysis.core.virtualcpu;

import static org.eclipse.tracecompass.common.core.NonNullUtils.checkNotNull;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.tracecompass.analysis.os.linux.core.trace.DefaultEventLayout;
import org.eclipse.tracecompass.analysis.os.linux.core.trace.IKernelAnalysisEventLayout;
import org.eclipse.tracecompass.analysis.os.linux.core.trace.IKernelTrace;
import org.eclipse.tracecompass.incubator.internal.virtual.machine.analysis.core.IVirtualMachineEventHandler;
import org.eclipse.tracecompass.incubator.internal.virtual.machine.analysis.core.data.VcpuStateValues;
import org.eclipse.tracecompass.incubator.internal.virtual.machine.analysis.core.model.IVirtualEnvironmentModel;
import org.eclipse.tracecompass.incubator.internal.virtual.machine.analysis.core.model.VirtualCPU;
import org.eclipse.tracecompass.incubator.internal.virtual.machine.analysis.core.model.VirtualMachine;
import org.eclipse.tracecompass.incubator.internal.virtual.machine.analysis.core.virtualcpu.VirtualMachineCpuAnalysis.VmWrongSegment;
import org.eclipse.tracecompass.incubator.internal.virtual.machine.analysis.core.virtualcpu.handlers.QemuKvmEventHandler;
import org.eclipse.tracecompass.incubator.internal.virtual.machine.analysis.core.virtualcpu.handlers.SchedSwitchEventHandler;
import org.eclipse.tracecompass.segmentstore.core.ISegment;
import org.eclipse.tracecompass.segmentstore.core.ISegmentStore;
import org.eclipse.tracecompass.statesystem.core.ITmfStateSystemBuilder;
import org.eclipse.tracecompass.statesystem.core.statevalue.TmfStateValue;
import org.eclipse.tracecompass.tmf.core.event.ITmfEvent;
import org.eclipse.tracecompass.tmf.core.event.aspect.TmfCpuAspect;
import org.eclipse.tracecompass.tmf.core.statesystem.AbstractTmfStateProvider;
import org.eclipse.tracecompass.tmf.core.trace.ITmfTrace;
import org.eclipse.tracecompass.tmf.core.trace.TmfTraceUtils;
import org.eclipse.tracecompass.tmf.core.trace.experiment.TmfExperiment;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Multimap;

/**
 * This is the state provider which translates the virtual machine experiment
 * events to a state system.
 *
 * Attribute tree:
 *
 * <pre>
 * |- Virtual Machines
 * |  |- <Guest Host ID> -> Friendly name (trace name)
 * |  |  |- <VCPU number>
 * |  |  |  |- Status -> <Status value>
 * </pre>
 *
 * The status value of the VCPUs are either {@link VcpuStateValues#VCPU_IDLE},
 * {@link VcpuStateValues#VCPU_UNKNOWN} or {@link VcpuStateValues#VCPU_RUNNING}.
 * Those three values are ORed with flags {@link VcpuStateValues#VCPU_VMM}
 * and/or {@link VcpuStateValues#VCPU_PREEMPT} to indicate respectively whether
 * they are in hypervisor mode or preempted on the host.
 *
 * @author Mohamad Gebai
 */
public class VirtualMachineCpuStateProvider extends AbstractTmfStateProvider {

    /**
     * Version number of this state provider. Please bump this if you modify the
     * contents of the generated state history in some way.
     */
    private static final int VERSION = 1;

    private final IVirtualEnvironmentModel fModel;
    private final Multimap<String, IVirtualMachineEventHandler> fEventNames = HashMultimap.create();
    private final Collection<IVirtualMachineEventHandler> fHandlers;
    private final Map<ITmfTrace, IKernelAnalysisEventLayout> fLayouts;
    private final ISegmentStore<ISegment> fSegmentStore;
    private final Map<VirtualCPU, Long> fLastRunningTimes = new HashMap<>();
    private final Multimap<VirtualCPU, ITmfEvent> fWrongEvents = HashMultimap.create();

    // ------------------------------------------------------------------------
    // Constructor
    // ------------------------------------------------------------------------

    /**
     * Constructor
     *
     * @param experiment
     *            The virtual machine experiment
     * @param segmentStore
     *            The segment store to store events that should not happen where
     *            they happen
     * @param model
     *            The virtual environment model
     */
    public VirtualMachineCpuStateProvider(TmfExperiment experiment, ISegmentStore<ISegment> segmentStore, IVirtualEnvironmentModel model) {
        super(experiment, "Virtual Machine State Provider"); //$NON-NLS-1$

        fModel = model;
        fLayouts = new HashMap<>();
        fSegmentStore = segmentStore;
        fHandlers = ImmutableSet.of(new SchedSwitchEventHandler(this), new QemuKvmEventHandler());
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
    public VirtualMachineCpuStateProvider getNewInstance() {
        TmfExperiment trace = getTrace();
        return new VirtualMachineCpuStateProvider(trace, fSegmentStore, fModel);
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

        // Check that the event occurred within an acceptable range
        checkEventTime(event, ss);

        Collection<IVirtualMachineEventHandler> handlers = fEventNames.get(eventName);
        if (handlers.isEmpty()) {
            return;
        }
        IVirtualEnvironmentModel virtEnv = fModel;
        for (IVirtualMachineEventHandler handler : handlers) {
            handler.handleEvent(ss, event, virtEnv, eventLayout);
        }

    }

    private void checkEventTime(ITmfEvent event, ITmfStateSystemBuilder ss) {
        /* Do we know this trace's role yet? */
        VirtualMachine host = fModel.getCurrentMachine(event);

        /* Make sure guest traces are added to the state system */
        if (host.isGuest()) {
            /*
             * If event from a guest OS, make sure the guest exists in the state system
             */
            int vmQuark = ss.optQuarkAbsolute(VmAttributes.VIRTUAL_MACHINES, host.getHostId());
            if (vmQuark < 0) {
                /*
                 * We should enter this catch only once per machine, so it is not so costly to
                 * do compared with adding the trace's name for each guest event
                 */
                vmQuark = ss.getQuarkAbsoluteAndAdd(VmAttributes.VIRTUAL_MACHINES, host.getHostId());
                TmfStateValue machineName = TmfStateValue.newValueString(event.getTrace().getName());
                ss.modifyAttribute(event.getTimestamp().toNanos(), machineName, vmQuark);
            }

            // Put out a warning if the event happened when the virtual CPU is supposed to
            // be out
            Integer cpu = TmfTraceUtils.resolveIntEventAspectOfClassForEvent(event.getTrace(), TmfCpuAspect.class, event);
            if (cpu != null) {
                VirtualCPU vcpu = VirtualCPU.getVirtualCPU(host, cpu.longValue());
                int curStatusQuark = ss.getQuarkRelativeAndAdd(vmQuark, vcpu.getCpuId().toString(), VmAttributes.STATUS);
                if (curStatusQuark >= 0) {
                    int val = ss.queryOngoingState(curStatusQuark).unboxInt();
                    if (val > 0 && (val & (VcpuStateValues.VCPU_PREEMPT | VcpuStateValues.VCPU_VMM)) > 0) {
                        // This event should not have happened
                        fWrongEvents.put(vcpu, event);
                    }
                }
            }
        }
    }

    private static boolean isStatusVm(int status) {
        return (status & (VcpuStateValues.VCPU_PREEMPT | VcpuStateValues.VCPU_VMM)) > 0;
    }

    /**
     * The state of a virtual CPU has changed
     *
     * @param vcpu
     *            The virtual CPU affected by a state change
     * @param prevStatus
     *            The previous state
     * @param curStatus
     *            The new state
     * @param ts
     *            The timestamp at which it happened
     */
    public void vcpuStatusChanged(VirtualCPU vcpu, int prevStatus, int curStatus, long ts) {
        if (isStatusVm(prevStatus) && !isStatusVm(curStatus)) {
            // The VM is running again
            Collection<ITmfEvent> events = fWrongEvents.removeAll(vcpu);
            Long lastRun = fLastRunningTimes.get(vcpu);
            if (lastRun == null) {
                // This should not happen
                return;
            }
            for (ITmfEvent event : events) {
                long nanos = event.getTimestamp().toNanos();
                String eventName = vcpu.getVm().getTraceName() + ':' + event.getName();
                if (nanos - lastRun < ts - nanos) {
                    fSegmentStore.add(new VmWrongSegment(lastRun, nanos, eventName));
                } else {
                    fSegmentStore.add(new VmWrongSegment(ts, nanos, eventName));
                }
            }
        }
        if (!isStatusVm(prevStatus) && isStatusVm(curStatus)) {
            // The VM is not running anymore
            fLastRunningTimes.put(vcpu, ts);
        }

    }

    @Override
    public void done() {
        super.done();
    }

}
