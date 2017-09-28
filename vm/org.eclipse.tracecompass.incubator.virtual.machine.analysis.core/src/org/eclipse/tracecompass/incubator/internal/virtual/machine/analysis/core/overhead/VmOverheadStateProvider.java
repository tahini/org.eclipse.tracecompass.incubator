/*******************************************************************************
 * Copyright (c) 2017 École Polytechnique de Montréal
 *
 * All rights reserved. This program and the accompanying materials are
 * made available under the terms of the Eclipse Public License v1.0 which
 * accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/

package org.eclipse.tracecompass.incubator.internal.virtual.machine.analysis.core.overhead;

import static org.eclipse.tracecompass.common.core.NonNullUtils.checkNotNull;

import java.util.HashMap;
import java.util.Map;

import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.tracecompass.analysis.os.linux.core.event.aspect.LinuxTidAspect;
import org.eclipse.tracecompass.analysis.os.linux.core.model.HostThread;
import org.eclipse.tracecompass.analysis.os.linux.core.trace.DefaultEventLayout;
import org.eclipse.tracecompass.analysis.os.linux.core.trace.IKernelAnalysisEventLayout;
import org.eclipse.tracecompass.analysis.os.linux.core.trace.IKernelTrace;
import org.eclipse.tracecompass.incubator.analysis.core.model.IHostModel;
import org.eclipse.tracecompass.incubator.analysis.core.model.ModelManager;
import org.eclipse.tracecompass.incubator.callstack.core.instrumented.statesystem.InstrumentedCallStackAnalysis;
import org.eclipse.tracecompass.incubator.internal.virtual.machine.analysis.core.model.IVirtualMachineModel;
import org.eclipse.tracecompass.incubator.internal.virtual.machine.analysis.core.model.VirtualCPU;
import org.eclipse.tracecompass.incubator.internal.virtual.machine.analysis.core.model.VirtualMachine;
import org.eclipse.tracecompass.incubator.internal.virtual.machine.analysis.core.model.qemukvm.QemuKvmVmModel;
import org.eclipse.tracecompass.incubator.internal.virtual.machine.analysis.core.module.VirtualMachineStateProvider;
import org.eclipse.tracecompass.statesystem.core.ITmfStateSystemBuilder;
import org.eclipse.tracecompass.tmf.core.event.ITmfEvent;
import org.eclipse.tracecompass.tmf.core.event.ITmfEventField;
import org.eclipse.tracecompass.tmf.core.statesystem.AbstractTmfStateProvider;
import org.eclipse.tracecompass.tmf.core.statesystem.ITmfStateProvider;
import org.eclipse.tracecompass.tmf.core.trace.ITmfTrace;
import org.eclipse.tracecompass.tmf.core.trace.TmfTraceUtils;
import org.eclipse.tracecompass.tmf.core.trace.experiment.TmfExperiment;

import com.google.common.collect.HashBasedTable;
import com.google.common.collect.Table;

/**
 * @author Geneviève Bastien
 */
public class VmOverheadStateProvider extends AbstractTmfStateProvider {

    /**
     * The threads attribute in the state system
     */
    public static final String TRACES = "Traces"; //$NON-NLS-1$

    /**
     * The threads attribute in the state system
     */
    public static final String THREADS = "Threads"; //$NON-NLS-1$

    /**
     * Version number of this state provider. Please bump this if you modify the
     * contents of the generated state history in some way.
     */
    private static final int VERSION = 1;

    private static final int SCHED_SWITCH_INDEX = 0;
    private static final int KVM_ENTRY_INDEX = 1;
    private static final int KVM_EXIT_INDEX = 2;



    /* TODO: An analysis should support many hypervisor models */
    private IVirtualMachineModel fModel;
    private final Table<ITmfTrace, String, @Nullable Integer> fEventNames;
    private final Map<ITmfTrace, IKernelAnalysisEventLayout> fLayouts;

    // ------------------------------------------------------------------------
    // Constructor
    // ------------------------------------------------------------------------

    /**
     * Constructor
     *
     * @param experiment
     *            The virtual machine experiment
     */
    public VmOverheadStateProvider(TmfExperiment experiment) {
        super(experiment, "Vm Overhead State Provider"); //$NON-NLS-1$

        fModel = QemuKvmVmModel.get(experiment);
        Table<ITmfTrace, String, @Nullable Integer> table = HashBasedTable.create();
        fEventNames = table;
        fLayouts = new HashMap<>();
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
        fEventNames.put(trace, layout.eventSchedSwitch(), SCHED_SWITCH_INDEX);
        for (String kvmEntry : layout.eventsKVMEntry()) {
            fEventNames.put(trace, kvmEntry, KVM_ENTRY_INDEX);
        }
        for (String kvmExit : layout.eventsKVMExit()) {
            fEventNames.put(trace, kvmExit, KVM_EXIT_INDEX);
        }
    }

    @Override
    public int getVersion() {
        return VERSION;
    }

    @Override
    public TmfExperiment getTrace() {
        ITmfTrace trace = super.getTrace();
        if (trace instanceof TmfExperiment) {
            return (TmfExperiment) trace;
        }
        throw new IllegalStateException("VirtualMachineStateProvider: The associated trace should be an experiment"); //$NON-NLS-1$
    }

    @Override
    public ITmfStateProvider getNewInstance() {
        TmfExperiment trace = getTrace();
        return new VirtualMachineStateProvider(trace);
    }

    @Override
    protected void eventHandle(ITmfEvent event) {

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

        if (!eventName.equals(eventLayout.eventSchedSwitch()) &&
                !fModel.getRequiredEvents(eventLayout).contains(eventName)) {
            return;
        }

        ITmfStateSystemBuilder ss = checkNotNull(getStateSystemBuilder());

        /* Have the hypervisor models handle the event first */
        fModel.handleEvent(event, eventLayout);
        // The model should have been populated by the dependent analysis, we can just use it
        VirtualMachine host = fModel.getCurrentMachine(event);
        if (host == null) {
            return;
        }
        Integer idx = fEventNames.get(event.getTrace(), eventName);
        int intval = (idx == null ? -1 : idx.intValue());
        switch (intval) {
        case SCHED_SWITCH_INDEX: // "sched_switch":
            if (host.isHost()) {
                handleHostSchedSwitch(ss, event, eventLayout);
            }
            if (host.isGuest()) {
                handleGuestSchedSwitch(ss, event, eventLayout);
            }
            break;
        case KVM_ENTRY_INDEX:
            handleKvmEntry(ss, event);
            break;
        case KVM_EXIT_INDEX:
            handleKvmExit(ss, event);
            break;
        default:
            // Nothing to do
        }

    }

    private void handleKvmEntry(ITmfStateSystemBuilder ss, ITmfEvent event) {
        Integer currentTid = TmfTraceUtils.resolveIntEventAspectOfClassForEvent(event.getTrace(), LinuxTidAspect.class, event);
        if (currentTid == null) {
            return;
        }
        HostThread ht = new HostThread(event.getTrace().getHostId(), currentTid);
        VirtualCPU vcpu = fModel.getVirtualCpu(ht);
        if (vcpu != null) {
            final long ts = event.getTimestamp().getValue();
            VirtualMachine vm = vcpu.getVm();
            IHostModel model = ModelManager.getModelFor(vm.getHostId());
            int guestTid = model.getThreadOnCpu(vcpu.getCpuId().intValue(), ts);
            if (guestTid != IHostModel.UNKNOWN_TID) {
                int quark = ss.getQuarkAbsoluteAndAdd(TRACES, vm.getTraceName(), THREADS, String.valueOf(guestTid), InstrumentedCallStackAnalysis.CALL_STACK);
                int tidQuark = ss.getQuarkRelativeAndAdd(quark, "1");
                ss.modifyAttribute(ts, "Running", tidQuark);
                int preemptQuark = ss.getQuarkRelativeAndAdd(quark, "2");
                ss.removeAttribute(ts, preemptQuark);
                int statusQuark = ss.getQuarkRelativeAndAdd(quark, "3");
                ss.removeAttribute(ts, statusQuark);
            }
        }
    }

    private void handleKvmExit(ITmfStateSystemBuilder ss, ITmfEvent event) {
        Integer currentTid = TmfTraceUtils.resolveIntEventAspectOfClassForEvent(event.getTrace(), LinuxTidAspect.class, event);
        if (currentTid == null) {
            return;
        }
        HostThread ht = new HostThread(event.getTrace().getHostId(), currentTid);
        VirtualCPU vcpu = fModel.getVirtualCpu(ht);
        if (vcpu != null) {
            final long ts = event.getTimestamp().getValue();
            Long exitReason = event.getContent().getFieldValue(Long.class, "exit_reason");
            VirtualMachine vm = vcpu.getVm();
            IHostModel model = ModelManager.getModelFor(vm.getHostId());
            int guestTid = model.getThreadOnCpu(vcpu.getCpuId().intValue(), ts);
            if (guestTid != IHostModel.UNKNOWN_TID) {
                int quark = ss.getQuarkAbsoluteAndAdd(TRACES, vm.getTraceName(), THREADS, String.valueOf(guestTid), InstrumentedCallStackAnalysis.CALL_STACK);
                int tidQuark = ss.getQuarkRelativeAndAdd(quark, "1");
                ss.modifyAttribute(ts, "Running", tidQuark);
                int preemptQuark = ss.getQuarkRelativeAndAdd(quark, "2");
                ss.modifyAttribute(ts, "VMM", preemptQuark);
                int statusQuark = ss.getQuarkRelativeAndAdd(quark, "3");
                ss.modifyAttribute(ts, String.valueOf(exitReason), statusQuark);
            }
        }
    }

    /**
     * For guest sched_switch, update the status of the previous and next TIDs
     */
    private static void handleGuestSchedSwitch(ITmfStateSystemBuilder ss, ITmfEvent event, IKernelAnalysisEventLayout eventLayout) {
        final ITmfEventField content = event.getContent();
        final long ts = event.getTimestamp().getValue();
        int prevTid = ((Long) content.getField(eventLayout.fieldPrevTid()).getValue()).intValue();
        int nextTid = ((Long) content.getField(eventLayout.fieldNextTid()).getValue()).intValue();

        int quark = ss.getQuarkAbsoluteAndAdd(TRACES, event.getTrace().getName(), THREADS, String.valueOf(prevTid), InstrumentedCallStackAnalysis.CALL_STACK);
        int tidQuark = ss.getQuarkRelativeAndAdd(quark, "1");
        ss.removeAttribute(ts, tidQuark);
        int preemptQuark = ss.getQuarkRelativeAndAdd(quark, "2");
        ss.removeAttribute(ts, preemptQuark);
        int statusQuark = ss.getQuarkRelativeAndAdd(quark, "3");
        ss.removeAttribute(ts, statusQuark);

        quark = ss.getQuarkAbsoluteAndAdd(TRACES, event.getTrace().getName(), THREADS, String.valueOf(nextTid), InstrumentedCallStackAnalysis.CALL_STACK);
        tidQuark = ss.getQuarkRelativeAndAdd(quark, "1");
        ss.modifyAttribute(ts, "Running", tidQuark);
        preemptQuark = ss.getQuarkRelativeAndAdd(quark, "2");
        ss.removeAttribute(ts, preemptQuark);
        statusQuark = ss.getQuarkRelativeAndAdd(quark, "3");
        ss.removeAttribute(ts, statusQuark);
    }

    /**
     * If the previous or next TID is one of a vcpu, update the preemption status of
     * the thread running on that CPU in the guest
     */
    private void handleHostSchedSwitch(ITmfStateSystemBuilder ss, ITmfEvent event, IKernelAnalysisEventLayout eventLayout) {

        final ITmfEventField content = event.getContent();
        final String hostId = event.getTrace().getHostId();
        final long ts = event.getTimestamp().getValue();
        int prevTid = ((Long) content.getField(eventLayout.fieldPrevTid()).getValue()).intValue();
        int nextTid = ((Long) content.getField(eventLayout.fieldNextTid()).getValue()).intValue();
        Long prevState = content.getFieldValue(Long.class, eventLayout.fieldPrevState());

        /* Verify if the previous thread corresponds to a virtual CPU */
        /*
         * If previous thread is virtual CPU, update status of the
         * virtual CPU to preempted
         */
        HostThread ht = new HostThread(hostId, prevTid);
        VirtualCPU vcpu = fModel.getVirtualCpu(ht);
        if (vcpu != null) {
            VirtualMachine vm = vcpu.getVm();
            IHostModel model = ModelManager.getModelFor(vm.getHostId());
            int guestTid = model.getThreadOnCpu(vcpu.getCpuId().intValue(), ts);
            if (guestTid != IHostModel.UNKNOWN_TID) {
                int quark = ss.getQuarkAbsoluteAndAdd(TRACES, vm.getTraceName(), THREADS, String.valueOf(guestTid), InstrumentedCallStackAnalysis.CALL_STACK);
                int tidQuark = ss.getQuarkRelativeAndAdd(quark, "1");
                ss.modifyAttribute(ts, "Running", tidQuark);
                int preemptQuark = ss.getQuarkRelativeAndAdd(quark, "2");
                ss.modifyAttribute(ts, "VCPU Preempted", preemptQuark);
                int statusQuark = ss.getQuarkRelativeAndAdd(quark, "3");
                if (prevState != null) {
                    ss.modifyAttribute(ts, String.valueOf(prevState), statusQuark);
                }
            }
        }

        ht = new HostThread(hostId, nextTid);
        vcpu = fModel.getVirtualCpu(ht);
        if (vcpu != null) {
            VirtualMachine vm = vcpu.getVm();
            IHostModel model = ModelManager.getModelFor(vm.getHostId());
            int guestTid = model.getThreadOnCpu(vcpu.getCpuId().intValue(), ts);
            if (guestTid != IHostModel.UNKNOWN_TID) {
                int quark = ss.getQuarkAbsoluteAndAdd(TRACES, vm.getTraceName(), THREADS, String.valueOf(guestTid), InstrumentedCallStackAnalysis.CALL_STACK);
                int tidQuark = ss.getQuarkRelativeAndAdd(quark, "1");
                ss.modifyAttribute(ts, "Running", tidQuark);
                int preemptQuark = ss.getQuarkRelativeAndAdd(quark, "2");
                ss.removeAttribute(ts, preemptQuark);
                int statusQuark = ss.getQuarkRelativeAndAdd(quark, "3");
                ss.removeAttribute(ts, statusQuark);
            }
        }
    }

}
