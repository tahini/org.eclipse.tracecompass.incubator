/*******************************************************************************
 * Copyright (c) 2017 École Polytechnique de Montréal
 *
 * All rights reserved. This program and the accompanying materials are
 * made available under the terms of the Eclipse Public License v1.0 which
 * accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/

package org.eclipse.tracecompass.incubator.internal.virtual.machine.analysis.core.model.analysis;

import java.util.Collection;
import java.util.Collections;

import org.eclipse.tracecompass.incubator.internal.virtual.machine.analysis.core.model.VirtualMachine;

/**
 * @author Geneviève Bastien
 */
public class VirtualMachineModel {

    private final VirtualMachineModelAnalysis fAnalysis;

    public VirtualMachineModel(VirtualMachineModelAnalysis analysis) {
        fAnalysis = analysis;
    }

    public Collection<VirtualMachine> getMachines() {
        return Collections.emptySet();
    }

    public VirtualMachine getCurrentMachine(long time, String hostId) {

    }

}
