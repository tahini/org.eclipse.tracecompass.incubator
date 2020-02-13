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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.jface.viewers.Viewer;
import org.eclipse.jface.viewers.ViewerComparator;
import org.eclipse.tracecompass.analysis.os.linux.core.kernel.KernelAnalysisModule;
import org.eclipse.tracecompass.analysis.os.linux.core.inputoutput.InputOutputAnalysisModule;
import org.eclipse.tracecompass.statesystem.core.ITmfStateSystem;
import org.eclipse.tracecompass.statesystem.core.StateSystemUtils;
import org.eclipse.tracecompass.statesystem.core.exceptions.AttributeNotFoundException;
import org.eclipse.tracecompass.statesystem.core.exceptions.StateSystemDisposedException;
import org.eclipse.tracecompass.statesystem.core.exceptions.StateValueTypeException;
import org.eclipse.tracecompass.statesystem.core.interval.ITmfStateInterval;
import org.eclipse.tracecompass.statesystem.core.statevalue.ITmfStateValue;
import org.eclipse.tracecompass.tmf.core.statesystem.TmfStateSystemAnalysisModule;
import org.eclipse.tracecompass.tmf.core.trace.ITmfTrace;
import org.eclipse.tracecompass.tmf.core.trace.TmfTraceUtils;
import org.eclipse.tracecompass.tmf.ui.viewers.tree.AbstractTmfTreeViewer;
import org.eclipse.tracecompass.tmf.ui.viewers.tree.ITmfTreeColumnDataProvider;
import org.eclipse.tracecompass.tmf.ui.viewers.tree.ITmfTreeViewerEntry;
import org.eclipse.tracecompass.tmf.ui.viewers.tree.TmfTreeColumnData;
import org.eclipse.tracecompass.tmf.ui.viewers.tree.TmfTreeViewerEntry;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.tracecompass.analysis.os.linux.core.inputoutput.Attributes;

/**
 * @author Houssem Daoud
 * @since 2.0
 *
 */
public class IOstatistics extends AbstractTmfTreeViewer {

    // Timeout between to wait for in the updateElements method
    private static final long BUILD_UPDATE_TIMEOUT = 500;
    private static final int MB_TO_BYTE = 1024 * 1024;
    private TmfStateSystemAnalysisModule fModule = null;

    private static final String[] COLUMN_NAMES = new String[] {
            Messages.IOstatistics_ProcessPid,
            Messages.IOstatistics_ProcessName,
            Messages.IOstatistics_Read,
            Messages.IOstatistics_Write
    };

    /* A map that saves the mapping of a thread ID to its executable name */
    private final Map<String, String> fThreadNameMap = new HashMap<>();

    /** Provides label for the CPU usage tree viewer cells */
    protected static class StatLabelProvider extends TreeLabelProvider {

        @Override
        public String getColumnText(Object element, int columnIndex) {
            IOstatisticsEntry obj = (IOstatisticsEntry) element;
            if (columnIndex == 0) {
                return obj.getThreadTid();
            } else if (columnIndex == 1) {
                return obj.getThreadName();
            } else if (columnIndex == 2) {
                return Double.toString(obj.getReadValue());
            } else if (columnIndex == 3) {
                return Double.toString(obj.getWrittenValue());
            }
            return element.toString();
        }

    }

    /**
     * Constructor
     *
     * @param parent
     *            The parent composite that holds this viewer
     */
    public IOstatistics(Composite parent) {
        super(parent, false);
        setLabelProvider(new StatLabelProvider());
    }

    @Override
    protected ITmfTreeColumnDataProvider getColumnDataProvider() {
        return new ITmfTreeColumnDataProvider() {

            @Override
            public List<TmfTreeColumnData> getColumnData() {
                /* All columns are sortable */
                List<TmfTreeColumnData> columns = new ArrayList<>();

                TmfTreeColumnData column = new TmfTreeColumnData(COLUMN_NAMES[0]);
                column.setComparator(new ViewerComparator() {
                    @Override
                    public int compare(Viewer viewer, Object e1, Object e2) {
                        IOstatisticsEntry n1 = (IOstatisticsEntry) e1;
                        IOstatisticsEntry n2 = (IOstatisticsEntry) e2;

                        return n1.getThreadTid().compareTo(n2.getThreadTid());

                    }
                });
                column.setWidth(300);
                columns.add(column);
                column = new TmfTreeColumnData(COLUMN_NAMES[1]);
                column.setComparator(new ViewerComparator() {
                    @Override
                    public int compare(Viewer viewer, Object e1, Object e2) {
                        IOstatisticsEntry n1 = (IOstatisticsEntry) e1;
                        IOstatisticsEntry n2 = (IOstatisticsEntry) e2;

                        return n1.getThreadName().compareTo(n2.getThreadName());

                    }
                });
                column.setWidth(300);
                columns.add(column);

                column = new TmfTreeColumnData(COLUMN_NAMES[2]);
                column.setComparator(new ViewerComparator() {
                    @Override
                    public int compare(Viewer viewer, Object e1, Object e2) {
                        IOstatisticsEntry n1 = (IOstatisticsEntry) e1;
                        IOstatisticsEntry n2 = (IOstatisticsEntry) e2;

                        return n1.getReadValue().compareTo(n2.getReadValue());

                    }
                });
                column.setWidth(300);
                columns.add(column);

                column = new TmfTreeColumnData(COLUMN_NAMES[3]);
                column.setComparator(new ViewerComparator() {
                    @Override
                    public int compare(Viewer viewer, Object e1, Object e2) {
                        IOstatisticsEntry n1 = (IOstatisticsEntry) e1;
                        IOstatisticsEntry n2 = (IOstatisticsEntry) e2;

                        return n1.getWrittenValue().compareTo(n2.getWrittenValue());

                    }
                });
                column.setWidth(300);
                columns.add(column);
                return columns;
            }

        };
    }

    // ------------------------------------------------------------------------
    // Operations
    // ------------------------------------------------------------------------

    @Override
    public void initializeDataSource() {
        ITmfTrace trace = getTrace();
        if (trace != null) {
        fModule = TmfTraceUtils.getAnalysisModuleOfClass(trace, TmfStateSystemAnalysisModule.class, InputOutputAnalysisModule.ID);
        if (fModule == null) {
            return;
        }
        fModule.schedule();
        fModule.waitForInitialization();
        fThreadNameMap.clear();
        }
    }

    @Override
    protected ITmfTreeViewerEntry updateElements(long start, long end, boolean isSelection) {

            if (isSelection || (start == end)) {
                return null;
            }
            if (getTrace() == null || fModule == null) {
                return null;
            }
            fModule.waitForInitialization();
            ITmfStateSystem ss = fModule.getStateSystem();
            if (ss == null) {
                return null;
            }

            boolean complete = false;
            long currentEnd = start;

            while (!complete && currentEnd < end) {
                complete = ss.waitUntilBuilt(BUILD_UPDATE_TIMEOUT);
                currentEnd = ss.getCurrentEndTime();
            }

            /* Initialize the data */
            //Map<String, Long> cpuUsageMap = fModule.getCpuUsageInRange(Math.max(start, getStartTime()), Math.min(end, getEndTime()));

            TmfTreeViewerEntry root = new TmfTreeViewerEntry(""); //$NON-NLS-1$
            List<ITmfTreeViewerEntry> entryList = root.getChildren();

            long starttime = Math.max(getStartTime(),start);
            long endtime = Math.min(getEndTime(),end);
        try {
            int threadsQuark = ss.getQuarkAbsolute(Attributes.THREADS);
            List<Integer> threadQuarks = ss.getSubAttributes(threadsQuark, false);
            for (int quark : threadQuarks) {
            String threadTid = ss.getAttributeName(quark);
            int writtenQuark = ss.getQuarkRelative(quark, Attributes.BYTES_WRITTEN);
            double writtenValue = getdifference(ss, writtenQuark, starttime, endtime) / (float)MB_TO_BYTE;
            int readQuark = ss.getQuarkRelative(quark, Attributes.BYTES_READ);
            double readValue = getdifference(ss, readQuark, starttime, endtime) / MB_TO_BYTE;
            IOstatisticsEntry obj = new IOstatisticsEntry(threadTid,getThreadName(threadTid), readValue, writtenValue);
            entryList.add(obj);
            }
        } catch (AttributeNotFoundException | StateValueTypeException e) {

        }

        return root;

    }

    private static long getdifference(ITmfStateSystem ss,Integer quark,long starttime,long endtime) {
        long value;
        try {
        long startvalue = ss.querySingleState(starttime, quark).getStateValue().unboxLong();
        long endvalue = ss.querySingleState(endtime, quark).getStateValue().unboxLong();
        value = (endvalue - startvalue);
        }  catch (AttributeNotFoundException | StateValueTypeException | StateSystemDisposedException e) {
        value = 0;
        }
        return value;
    }

    /*
     * Get the process name from its TID by using the LTTng kernel analysis
     * module
     */
    private String getThreadName(String tid) {
        String execName = fThreadNameMap.get(tid);
        if (execName != null) {
            return execName;
        }
        ITmfTrace trace = getTrace();
        if (trace == null) {
            return tid;
        }
        ITmfStateSystem kernelSs = TmfStateSystemAnalysisModule.getStateSystem(trace, KernelAnalysisModule.ID);
        if (kernelSs == null) {
            return tid;
        }

        try {
            int cpusNode = kernelSs.getQuarkAbsolute(Attributes.THREADS);

            /* Get the quarks for each cpu */
            List<Integer> cpuNodes = kernelSs.getSubAttributes(cpusNode, false);

            for (Integer tidQuark : cpuNodes) {
                if (kernelSs.getAttributeName(tidQuark).equals(tid)) {
                    int execNameQuark;
                    List<ITmfStateInterval> execNameIntervals;
                    try {
                        execNameQuark = kernelSs.getQuarkRelative(tidQuark, Attributes.EXEC_NAME);
                        execNameIntervals = StateSystemUtils.queryHistoryRange(kernelSs, execNameQuark, getStartTime(), getEndTime());
                    } catch (AttributeNotFoundException e) {
                        /* No information on this thread (yet?), skip it for now */
                        continue;
                    } catch (StateSystemDisposedException e) {
                        /* State system is closing down, no point continuing */
                        break;
                    }

                    for (ITmfStateInterval execNameInterval : execNameIntervals) {
                        if (!execNameInterval.getStateValue().isNull() &&
                                execNameInterval.getStateValue().getType() == ITmfStateValue.Type.STRING) {
                            execName = execNameInterval.getStateValue().unboxStr();
                            fThreadNameMap.put(tid, execName);

                        }
                    }
                    return execName;
                }
            }

        } catch (AttributeNotFoundException e) {
            /* can't find the process name, just return the tid instead */
        }
        return tid;
    }

}
