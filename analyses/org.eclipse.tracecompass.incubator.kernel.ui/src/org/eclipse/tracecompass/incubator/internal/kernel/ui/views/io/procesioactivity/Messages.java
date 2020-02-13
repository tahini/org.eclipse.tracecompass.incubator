/*******************************************************************************
 * Copyright (c) 2014 École Polytechnique de Montréal
 *
 * All rights reserved. This program and the accompanying materials are
 * made available under the terms of the Eclipse Public License v1.0 which
 * accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *   Geneviève Bastien - Initial API and implementation
 *******************************************************************************/

package org.eclipse.tracecompass.incubator.internal.kernel.ui.views.io.procesioactivity;

import org.eclipse.osgi.util.NLS;

/**
 * Translatable strings for the Disk I/O view
 *
 * @author Houssem Daoud
 * @since 2.0
 */
public class Messages extends NLS {
    private static final String BUNDLE_NAME = "org.eclipse.tracecompass.internal.analysis.os.linux.ui.views.io.procesioactivity.messages"; //$NON-NLS-1$
    /** Title of the Disk I/O view */
    public static String ProcessesioView_Title;

    /** Title of the Disk I/O viewer */
    public static String ProcessesioViewer_Title;
    /** X axis caption */
    public static String ProcessesioViewer_XAxis;
    /** Y axis caption */
    public static String ProcessesioViewer_YAxis;
    /**
     thread tid
     */
    public static String IOstatistics_ProcessPid;
    /** Thread name */
    public static String IOstatistics_ProcessName;
    /** KB read */
    public static String IOstatistics_Read;
    /** KB written */
    public static String IOstatistics_Write;
    static {
        // initialize resource bundle
        NLS.initializeMessages(BUNDLE_NAME, Messages.class);
    }

    private Messages() {
    }
}
