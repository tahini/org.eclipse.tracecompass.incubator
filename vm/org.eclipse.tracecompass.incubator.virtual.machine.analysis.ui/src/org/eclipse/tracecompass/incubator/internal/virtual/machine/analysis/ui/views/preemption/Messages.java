/*******************************************************************************
 * Copyright (c) 2013 École Polytechnique de Montréal
 *
 * All rights reserved. This program and the accompanying materials are
 * made available under the terms of the Eclipse Public License v1.0 which
 * accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *   Mohamad Gebai - Initial API and implementation
 *******************************************************************************/

package org.eclipse.tracecompass.incubator.internal.virtual.machine.analysis.ui.views.preemption;

import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.osgi.util.NLS;

@SuppressWarnings("javadoc")
public class Messages extends NLS {
    private static final String BUNDLE_NAME = "org.eclipse.linuxtools.internal.lttng2.analysis.vm.ui.view.preemption.messages"; //$NON-NLS-1$

    public static @Nullable String VmView_stateTypeName;
    public static @Nullable String VmView_multipleStates;
    public static @Nullable String VmView_nextResourceActionNameText;
    public static @Nullable String VmView_nextResourceActionToolTipText;
    public static @Nullable String VmView_previousResourceActionNameText;
    public static @Nullable String VmView_previousResourceActionToolTipText;
    public static @Nullable String VmView_attributeCpuName;
    public static @Nullable String VmView_attributeIrqName;
    public static @Nullable String VmView_attributeSoftIrqName;
    public static @Nullable String VmView_attributeHoverTime;
    public static @Nullable String VmView_attributeTidName;
    public static @Nullable String VmView_attributeProcessName;
    public static @Nullable String VmView_attributeSyscallName;

    static {
        // initialize resource bundle
        NLS.initializeMessages(BUNDLE_NAME, Messages.class);
    }

    private Messages() {
    }
}
