/**********************************************************************
 * Copyright (c) 2019 École Polytechnique de Montréal
 *
 * All rights reserved. This program and the accompanying materials are
 * made available under the terms of the Eclipse Public License v1.0 which
 * accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 **********************************************************************/

package org.eclipse.tracecompass.incubator.internal.callstack.core.flamegraph;

/**
 * Utility class to deal with data providers additinal functionalities, like
 * actions to execute on certain entries and states.
 *
 * TODO: Move to mainline Trace Compass
 *
 * @author Geneviève Bastien
 */
public final class DataProviderUtils {

    /**
     * Time requested key
     */
    public static final String ACTION_PREFIX = "#"; //$NON-NLS-1$

    /**
     * Selected items key
     */
    public static final String ACTION_GOTO_TIME = "TIME:"; //$NON-NLS-1$

    private DataProviderUtils() {
        // Private constructor
    }

    /**
     * Create an action string that will create a goto time signal
     *
     * @param time
     *            The time to go to
     * @return The action string
     */
    public static String createGoToTimeAction(long time) {
        return ACTION_GOTO_TIME + time;
    }

    /**
     * Create an action string that will create a goto time range signal
     *
     * @param time1
     *            The beginning of the time range to go to
     * @param time2
     *            The end of the time range to go to
     * @return The action string
     */
    public static String createGoToTimeAction(long time1, long time2) {
        return ACTION_GOTO_TIME + time1 + ',' + time2;
    }

}
