/*******************************************************************************
 * Copyright (c) 2017 École Polytechnique de Montréal
 *
 * All rights reserved. This program and the accompanying materials are
 * made available under the terms of the Eclipse Public License v1.0 which
 * accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/

package org.eclipse.tracecompass.incubator.dependency.analysis.core.tests.stubs;

import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.tracecompass.analysis.os.linux.core.event.aspect.LinuxTidAspect;
import org.eclipse.tracecompass.tmf.core.event.ITmfEvent;
import org.eclipse.tracecompass.tmf.tests.stubs.trace.xml.TmfXmlTraceStubNs;

/**
 * Tmf Xml trace stub with a tid field aspect
 *
 * @author Geneviève Bastien
 */
public class TmfXmlTraceStubWithTid extends TmfXmlTraceStubNs {

    private static final LinuxTidAspect TID_ASPECT = new LinuxTidAspect() {

        @Override
        public @Nullable Integer resolve(@NonNull ITmfEvent event) {
            String tidStr = event.getContent().getFieldValue(String.class, "tid");
            return (tidStr == null) ? null : Integer.valueOf(tidStr);
        }

    };

    /**
     * Constructor
     */
    public TmfXmlTraceStubWithTid() {
        super();
        this.addEventAspect(TID_ASPECT);
    }

}
