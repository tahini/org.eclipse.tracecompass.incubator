/*******************************************************************************
 * Copyright (c) 2017 École Polytechnique de Montréal
 *
 * All rights reserved. This program and the accompanying materials are
 * made available under the terms of the Eclipse Public License v1.0 which
 * accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/

package org.eclipse.tracecompass.incubator.internal.virtual.machine.analysis.core.model.analysis;

import org.eclipse.tracecompass.analysis.os.linux.core.model.HostThread;
import org.eclipse.tracecompass.incubator.internal.virtual.machine.analysis.core.model.VirtualCPU;
import org.eclipse.tracecompass.incubator.internal.virtual.machine.analysis.core.model.VirtualMachine;
import org.eclipse.tracecompass.statesystem.core.ITmfStateSystemBuilder;
import org.eclipse.tracecompass.tmf.core.event.ITmfEvent;

/**
 * The virtual environment model implementation based on a state system
 *
 * Package-private so it is not directly accessible to the other analyses of
 * this plugin
 *
 * @author Geneviève Bastien
 */
public class VirtualEnvironmentBuilder extends VirtualEnvironment {

    private final ITmfStateSystemBuilder fStateSystem;

    /**
     * Constructor
     *
     * @param stateSystem
     *            The state system containing the model
     */
    public VirtualEnvironmentBuilder(ITmfStateSystemBuilder stateSystem) {
        super(stateSystem);
        fStateSystem = stateSystem;
    }

    @Override
    public synchronized VirtualMachine getCurrentMachine(ITmfEvent event) {
        VirtualMachine machine = innerGetCurrentMachine(event);
        if (machine == null) {
            synchronized (fStateSystem) {
                String hostId = event.getTrace().getHostId();
                machine = createMachine(fStateSystem, event.getTimestamp().toNanos(), hostId, event.getTrace().getName());
                fKnownMachines.put(hostId, machine);
            }

        }
        return machine;
    }

    private static VirtualMachine createMachine(ITmfStateSystemBuilder ss, long ts, String hostId, String traceName) {
        int quark = ss.getQuarkAbsoluteAndAdd(hostId);
        ss.modifyAttribute(ts, traceName, quark);
        return VirtualMachine.newUnknownMachine(hostId, traceName);
    }

    /**
     * Associate a guest machine with a thread on the machine that hosts it
     *
     * @param vm
     *            The Guest virtual machine
     * @param ht
     *            The thread on the host machine
     */
    public void setGuestMachine(VirtualMachine vm, HostThread ht) {
        fTidToVm.put(ht, vm);
    }

    /**
     * Associate a virtual CPU to a thread on the machine that hosts it
     *
     * @param virtualCPU
     *            The virtual CPU
     * @param ht
     *            The thread on the host machine
     */
    public void setGuestCpu(VirtualCPU virtualCPU, HostThread ht) {
        fTidToVcpu.put(ht, virtualCPU);
    }

}
