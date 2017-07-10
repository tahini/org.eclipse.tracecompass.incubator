/*******************************************************************************
 * Copyright (c) 2017 École Polytechnique de Montréal
 *
 * All rights reserved. This program and the accompanying materials are
 * made available under the terms of the Eclipse Public License v1.0 which
 * accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/

package org.eclipse.tracecompass.incubator.internal.virtual.machine.analysis.core.model.vmware;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.tracecompass.analysis.os.linux.core.model.HostThread;
import org.eclipse.tracecompass.analysis.os.linux.core.trace.IKernelAnalysisEventLayout;
import org.eclipse.tracecompass.common.core.NonNullUtils;
import org.eclipse.tracecompass.incubator.internal.virtual.machine.analysis.core.model.IVirtualMachineModel;
import org.eclipse.tracecompass.incubator.internal.virtual.machine.analysis.core.model.VirtualCPU;
import org.eclipse.tracecompass.incubator.internal.virtual.machine.analysis.core.model.VirtualMachine;
import org.eclipse.tracecompass.tmf.core.event.ITmfEvent;
import org.eclipse.tracecompass.tmf.core.event.aspect.TmfCpuAspect;
import org.eclipse.tracecompass.tmf.core.trace.ITmfTrace;
import org.eclipse.tracecompass.tmf.core.trace.TmfTraceUtils;
import org.eclipse.tracecompass.tmf.core.trace.experiment.TmfExperiment;

import com.google.common.collect.HashBasedTable;
import com.google.common.collect.Table;

/**
 * Virtual machine model for the vmware hypervisor
 *
 * NOTE: VmWare being closed-source, this model is a best guess from traces of
 * vmware gest execution.
 *
 * @author Geneviève Bastien
 */
public class VMWareModel implements IVirtualMachineModel {

    private static final String GUEST_PREFIX = "Guest";

    private static final String VCPU_THREAD_NAME = "vmx-vcpu-";

    /* Associate a host's thread to a virtual CPU */
    private final Map<HostThread, VirtualCPU> fTidToVcpu = new HashMap<>();
    /* Associate a host's thread to a virtual machine */
//    private final Map<HostThread, VirtualMachine> fTidToVm = new HashMap<>();
    /* Maps a virtual machine name to a virtual machine */
    private final Map<String, VirtualMachine> fKnownMachines = new HashMap<>();
    /* Associate a VM and a VCPU to a PCPU */
    private final Table<VirtualMachine, VirtualCPU, Long> fVirtualToPhysicalCpu = NonNullUtils.checkNotNull(HashBasedTable.<VirtualMachine, VirtualCPU, Long> create());

//    private final TmfExperiment fExperiment;

    /**
     * Constructor
     *
     * @param exp
     *            The experiment this model applies to
     */
    public VMWareModel(TmfExperiment exp) {
//        fExperiment = exp;
        /* If there is only one trace we consider it as a host */
        if (exp.getTraces().size() == 1) {
            ITmfTrace trace = exp.getTraces().get(0);
            addKnownMachine(VirtualMachine.newHostMachine(trace.getHostId(), String.valueOf(trace.getName())));
        }
    }

    @Override
    public @Nullable VirtualMachine getCurrentMachine(ITmfEvent event) {
        final String hostId = event.getTrace().getHostId();
        VirtualMachine machine = fKnownMachines.get(hostId);

        /*
         * Even if the machine is known we need to continue because it might not
         * currently have all its roles
         */
        /* Try to get the virtual machine from the event */
        String traceName = event.getTrace().getName();
        if (traceName.startsWith(GUEST_PREFIX)) {
            Long uid = Long.decode(traceName.substring(GUEST_PREFIX.length()));
            if (machine != null) {
                machine.setGuest(uid);
                return machine;
            }
            machine = VirtualMachine.newGuestMachine(uid, hostId, String.valueOf(traceName));
        } else {
            if (machine != null) {
                machine.setHost();
                return machine;
            }
            machine = VirtualMachine.newHostMachine(hostId, String.valueOf(traceName));
        }

        /*
         * Associate the machine to the hostID here, for cached access later
         */
        fKnownMachines.put(hostId, machine);
        return machine;
    }

    @Override
    public Set<String> getRequiredEvents(IKernelAnalysisEventLayout layout) {
        return Collections.emptySet();
    }

    @Override
    public @Nullable VirtualCPU getVCpuEnteringHypervisorMode(ITmfEvent event, HostThread ht, IKernelAnalysisEventLayout layout) {
        return null;
    }

    @Override
    public @Nullable VirtualCPU getVCpuExitingHypervisorMode(ITmfEvent event, HostThread ht, IKernelAnalysisEventLayout layout) {
        return null;
    }

    @Override
    public @Nullable VirtualCPU getVirtualCpu(HostThread ht) {
        return fTidToVcpu.get(ht);
    }

    @Override
    public void handleEvent(ITmfEvent event, IKernelAnalysisEventLayout layout) {
        /* Is the event handled by this model */
        final String eventName = event.getName();
        Integer cpu = TmfTraceUtils.resolveIntEventAspectOfClassForEvent(event.getTrace(), TmfCpuAspect.class, event);
        if (cpu == null) {
            return;
        }
        String hostId = event.getTrace().getHostId();
        VirtualMachine host = fKnownMachines.get(hostId);
        if (host == null) {
            return;
        }
        if (!(layout.eventSchedSwitch().equals(eventName) && host.isHost())) {
            return;
        }
        // If this is the host and we have a sched switch, see if it is a virtual CPU
        // and associate it with a physical CPU
        Long tid = event.getContent().getFieldValue(Long.class, layout.fieldNextTid());
        if (tid == null) {
            return;
        }
        HostThread ht = new HostThread(hostId, tid.intValue());
        VirtualCPU virtualCPU = fTidToVcpu.get(ht);
        if (virtualCPU == null) {
            String fieldValue = event.getContent().getFieldValue(String.class, layout.fieldNextComm());
            if (fieldValue != null && fieldValue.startsWith(VCPU_THREAD_NAME)) {
                Integer vcpuId = Integer.valueOf(fieldValue.substring(VCPU_THREAD_NAME.length()));
                VirtualMachine vm = getOneVM();
                if (vm == null) {
                    return;
                }
                virtualCPU = VirtualCPU.getVirtualCPU(vm, Long.valueOf(vcpuId));
            }
        }
        if (virtualCPU == null) {
            return;
        }
        VirtualMachine vm = virtualCPU.getVm();
        fVirtualToPhysicalCpu.put(vm, virtualCPU, cpu.longValue());
    }

    private @Nullable VirtualMachine getOneVM() {
        // TODO: Support more than one VM
        for (Entry<String, VirtualMachine> entry : fKnownMachines.entrySet()) {
            if (entry.getValue().isGuest()) {
                return entry.getValue();
            }
        }
        return null;
    }

    /**
     * Return the physical cpu where a vcpu is currently running.
     *
     * @param virtualMachine
     *            The virtual machine that possesses the vcpu.
     * @param vcpu
     *            The vcpu.
     * @return The physical cpu.
     */
    public @Nullable Long getPhysicalCpuFromVcpu(VirtualMachine virtualMachine, VirtualCPU vcpu) {
        Long pcpu = fVirtualToPhysicalCpu.get(virtualMachine, vcpu);
        if (pcpu == null) {
            return null;
        }
        VirtualMachine parent = virtualMachine.getParent();
        if (parent != null && parent.isGuest()) {
            pcpu = fVirtualToPhysicalCpu.get(parent, VirtualCPU.getVirtualCPU(parent, pcpu));
        }
        return pcpu;
    }

    /**
     * @param v The virtual machine to add
     */
    public void addKnownMachine(VirtualMachine v) {
        fKnownMachines.put(v.getHostId(), v);
    }
}
