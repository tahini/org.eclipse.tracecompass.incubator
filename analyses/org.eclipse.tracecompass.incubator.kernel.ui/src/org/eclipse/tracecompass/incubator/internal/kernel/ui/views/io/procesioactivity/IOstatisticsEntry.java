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

import java.math.RoundingMode;
import java.text.DecimalFormat;

import org.eclipse.tracecompass.tmf.ui.viewers.tree.TmfTreeViewerEntry;

/**

 *
 * @author Houssem Daoud
 * @since 2.0
 */
public class IOstatisticsEntry extends TmfTreeViewerEntry {
    private final String threadTid;
    private final String threadName;
    private final Double readValue;
    private final Double writtenValue;

    /**
     * Constructor
     * @param threadTid
     *            threadTid
     * @param threadName
     *            threadName
     * @param readValue
     *            readValue
     * @param writtenValue
     *            writtenValue
     */
    public IOstatisticsEntry(String threadTid, String threadName, double readValue, double writtenValue) {
        super(threadName);
        this.threadTid = threadTid;
        this.threadName = threadName;
        this.readValue = readValue;
        this.writtenValue = writtenValue;
    }

    /**
     * @return threadTid
     */
    public String getThreadTid() {
        return threadTid;
    }

    /**
     * Get the Disk name
     *
     * @return the disk name
     */
    public String getThreadName() {
        return threadName;
    }

    /**
     * Get the MB read
     *
     * @return MB read
     */
    public Double getReadValue() {
        DecimalFormat df = new DecimalFormat("#.###"); //$NON-NLS-1$
        df.setRoundingMode(RoundingMode.FLOOR);
        return new Double(df.format(readValue));
    }
    /**
     * Get the MB written
     *
     * @return MB written
     */
    public Double getWrittenValue() {
        DecimalFormat df = new DecimalFormat("#.###"); //$NON-NLS-1$
        df.setRoundingMode(RoundingMode.FLOOR);
        return new Double(df.format(writtenValue));
    }


}
