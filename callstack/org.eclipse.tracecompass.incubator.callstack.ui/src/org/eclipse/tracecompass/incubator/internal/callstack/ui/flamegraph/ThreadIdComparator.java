/*******************************************************************************
 * Copyright (c) 2016 Ericsson
 *
 * All rights reserved. This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License v1.0 which
 * accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/
package org.eclipse.tracecompass.incubator.internal.callstack.ui.flamegraph;

import java.util.Comparator;

import org.eclipse.tracecompass.incubator.internal.callstack.core.instrumented.provider.FlameChartEntryModel;
import org.eclipse.tracecompass.incubator.internal.callstack.core.instrumented.provider.FlameChartEntryModel.EntryType;
import org.eclipse.tracecompass.tmf.core.model.tree.ITmfTreeDataModel;
import org.eclipse.tracecompass.tmf.ui.widgets.timegraph.model.ITimeGraphEntry;
import org.eclipse.tracecompass.tmf.ui.widgets.timegraph.model.TimeGraphEntry;

/**
 * Comparator to compare by thread name.
 *
 * @author Bernd Hufmann
 *
 */
class ThreadIdComparator implements Comparator<ITimeGraphEntry> {

    @Override
    public int compare(ITimeGraphEntry o1, ITimeGraphEntry o2) {
        if (o1 instanceof TimeGraphEntry && o2 instanceof TimeGraphEntry) {
            ITmfTreeDataModel entryModel1 = ((TimeGraphEntry) o1).getEntryModel();
            ITmfTreeDataModel entryModel2 = ((TimeGraphEntry) o1).getEntryModel();
            if (entryModel1 instanceof FlameChartEntryModel && entryModel2 instanceof FlameChartEntryModel) {
                FlameChartEntryModel fcEntry1 = (FlameChartEntryModel) entryModel1;
                FlameChartEntryModel fcEntry2 = (FlameChartEntryModel) entryModel2;
                // If any of the entry is a function, compare their depth
                if (fcEntry1.getEntryType().equals(EntryType.FUNCTION) && fcEntry2.getEntryType().equals(EntryType.FUNCTION)) {
                    return Integer.compare(fcEntry1.getDepth(), fcEntry2.getDepth());
                }
            }
        }
        // Fallback to entry name comparator
        return o1.getName().compareTo(o2.getName());
    }
}
