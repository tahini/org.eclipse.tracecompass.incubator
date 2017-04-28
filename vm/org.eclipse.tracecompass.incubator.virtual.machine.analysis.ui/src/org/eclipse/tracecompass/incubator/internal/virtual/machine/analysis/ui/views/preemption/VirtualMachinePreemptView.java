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

import static org.eclipse.tracecompass.common.core.NonNullUtils.checkNotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import org.apache.commons.lang3.StringUtils;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.Viewer;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.tracecompass.common.core.NonNullUtils;
import org.eclipse.tracecompass.incubator.internal.virtual.machine.analysis.core.data.Attributes;
import org.eclipse.tracecompass.incubator.internal.virtual.machine.analysis.core.module.StateValues;
import org.eclipse.tracecompass.incubator.internal.virtual.machine.analysis.core.module.VirtualMachineCpuAnalysis;
import org.eclipse.tracecompass.incubator.internal.virtual.machine.analysis.core.trace.VirtualMachineExperiment;
import org.eclipse.tracecompass.incubator.internal.virtual.machine.analysis.ui.views.vcpuview.VirtualMachineCommon.Type;
import org.eclipse.tracecompass.incubator.internal.virtual.machine.analysis.ui.views.vcpuview.VirtualMachineView;
import org.eclipse.tracecompass.incubator.internal.virtual.machine.analysis.ui.views.vcpuview.VirtualMachineViewEntry;
import org.eclipse.tracecompass.statesystem.core.ITmfStateSystem;
import org.eclipse.tracecompass.statesystem.core.StateSystemUtils;
import org.eclipse.tracecompass.statesystem.core.exceptions.AttributeNotFoundException;
import org.eclipse.tracecompass.statesystem.core.exceptions.StateSystemDisposedException;
import org.eclipse.tracecompass.statesystem.core.exceptions.StateValueTypeException;
import org.eclipse.tracecompass.statesystem.core.exceptions.TimeRangeException;
import org.eclipse.tracecompass.statesystem.core.interval.ITmfStateInterval;
import org.eclipse.tracecompass.tmf.core.statesystem.TmfStateSystemAnalysisModule;
import org.eclipse.tracecompass.tmf.core.trace.ITmfTrace;
import org.eclipse.tracecompass.tmf.core.trace.TmfTraceUtils;
import org.eclipse.tracecompass.tmf.ui.views.timegraph.AbstractTimeGraphView;
import org.eclipse.tracecompass.tmf.ui.widgets.timegraph.ITimeGraphContentProvider;
import org.eclipse.tracecompass.tmf.ui.widgets.timegraph.model.ILinkEvent;
import org.eclipse.tracecompass.tmf.ui.widgets.timegraph.model.ITimeEvent;
import org.eclipse.tracecompass.tmf.ui.widgets.timegraph.model.ITimeGraphEntry;
import org.eclipse.tracecompass.tmf.ui.widgets.timegraph.model.TimeEvent;
import org.eclipse.tracecompass.tmf.ui.widgets.timegraph.model.TimeGraphEntry;
import org.eclipse.tracecompass.tmf.ui.widgets.timegraph.model.TimeLinkEvent;
import org.eclipse.ui.ISelectionListener;
import org.eclipse.ui.IWorkbenchPart;

import com.google.common.collect.Iterables;

/**
 * Main implementation for the Virtual Machine view
 *
 * @author Mohamad Gebai
 */
public class VirtualMachinePreemptView extends AbstractTimeGraphView {

    /** View ID. */
    public static final String ID = "org.eclipse.linuxtools.lttng2.analysis.vm.ui.vmpreemptview"; //$NON-NLS-1$

    private static final String[] COLUMN_NAMES = new String[] {
        Messages.VmView_stateTypeName,
        "Duration" //$NON-NLS-1$
    };

//    private static final long EXECUTION_THRESHOLD = 1000; // Threshold in nanoseconds
    private static final long EXECUTION_THRESHOLD = 10000; // Threshold in nanoseconds
    private static final String[] FILTER_COLUMN_NAMES = new String[] {
            Messages.VmView_stateTypeName,
    };

    private static long wrtStartTime;
    private static long wrtEndTime;

    private final VirtualMachinePreemptContentProvider fContentProvider = new VirtualMachinePreemptContentProvider();

    private HashMap<Integer, Integer> tidToVCpuQuark = new HashMap<>();
    private HashMap<Integer, String> tidToVmName = new HashMap<>();
    private HashMap<String, VmPreemptEntry> vmNameToEntry = new HashMap<>();
    private HashMap<Integer, Integer> tidToVmQuark = new HashMap<>();

//    private Object fCurrentValue;
//
//    private class VmPreemptEntryTidComparator implements Comparator<ITimeGraphEntry> {
//
//        @Override
//        public int compare(ITimeGraphEntry o1, ITimeGraphEntry o2) {
//
//            int result = 0;
//
//            if ((o1 instanceof VmPreemptEntry) && (o2 instanceof VmPreemptEntry)) {
//                VmPreemptEntry entry1 = (VmPreemptEntry) o1;
//                VmPreemptEntry entry2 = (VmPreemptEntry) o2;
//                if(entry1.getType() == Type.THREAD && entry2.getType() == Type.THREAD) {
//                    result = Integer.parseInt(entry1.getId()) - Integer.parseInt(entry2.getId());
//                }
//            }
//            return result;
//        }
//    }

    private class VirtualMachinePreemptContentProvider implements ITimeGraphContentProvider {

        private class BuildThread extends Thread {
            private final ITmfTrace fBuildTrace;
            private final IProgressMonitor fMonitor;

            public BuildThread(final ITmfTrace trace) {
                super("Critical path view build"); //$NON-NLS-1$
                fBuildTrace = trace;
                fMonitor = new NullProgressMonitor();
            }

            @Override
            public void run() {
                try {
                    VirtualMachineCpuAnalysis module = TmfTraceUtils.getAnalysisModuleOfClass(fBuildTrace, VirtualMachineCpuAnalysis.class, VirtualMachineCpuAnalysis.ID);
                    if (module == null) {
                        return;
                    }
                    module.schedule();
                    if (module.waitForCompletion(fMonitor)) {
                        // Module is completed, set the start and end time of
                        // this view
//                        setStartEndTime(module);
                        refresh();
                    }

                } finally {
                    fSyncLock.lock();
                    fBuildThread = null;
                    fSyncLock.unlock();
                }
            }

            public void cancel() {
                fMonitor.setCanceled(true);
            }
        }

        private final Lock fSyncLock = new ReentrantLock();
        private final Map<Object, Map<Object, VmPreemptEntry>> workerMaps = new HashMap<>();
        private final Map<Object, List<TimeGraphEntry>> workerEntries = new HashMap<>();
        private @Nullable Object fCurrentObject;
        private @Nullable BuildThread fBuildThread = null;

        @Override
        public ITimeGraphEntry[] getElements(@Nullable Object inputElement) {
            ITimeGraphEntry[] ret = new ITimeGraphEntry[0];
            if (inputElement instanceof List) {
                List<?> list = (List<?>) inputElement;
                if (!list.isEmpty()) {
                    Object first = list.get(0);
                    if (first instanceof VmPreemptEntry) {
                        IGraphWorker worker = ((VmPreemptEntry) first).getWorker();
                        ret = getWorkerEntries(worker);
                    }
                }
            }
            return ret;
        }

        private ITimeGraphEntry[] getWorkerEntries(IGraphWorker worker) {
            fCurrentObject = worker;
            List<TimeGraphEntry> entries = workerEntries.get(worker);
            ITmfTrace trace = getTrace();
            if (entries == null) {
                buildEntryList(worker);
                entries = workerEntries.get(worker);
            }

            return (entries == null) ?
                new ITimeGraphEntry[0] :
                entries.toArray(new @NonNull ITimeGraphEntry[entries.size()]);
        }

        private void buildEntryList() {
            setStartTime(Long.MAX_VALUE);
            setEndTime(Long.MIN_VALUE);

            ArrayList<VmPreemptEntry> entryList = new ArrayList<>();
            ITmfTrace trace = getTrace();

            if (fCurrentEntry == null || fCurrentEntry.getType() != Type.THREAD) {
                return;
            }
            if (!(trace instanceof VirtualMachineExperiment)) {
                return;
            }
            VirtualMachineExperiment vmExperiment = (VirtualMachineExperiment) trace;
            ITmfStateSystem ssq = TmfStateSystemAnalysisModule.getStateSystem(trace, VirtualMachineCpuAnalysis.ID);
            if (ssq == null) {
                return;
            }

            long startTime = ssq.getStartTime();
            long endTime = ssq.getCurrentEndTime() + 1;
            setStartTime(Math.min(getStartTime(), startTime));
            setEndTime(Math.max(getEndTime(), endTime));

//            System.out.println("Start build event list");
            String vmName = fCurrentEntry.getVmName();
            try {
                int vmsNode = ssq.getQuarkAbsolute(Attributes.VIRTUAL_MACHINES);
                int vmQuark = ssq.getQuarkRelative(vmsNode, vmName);
                int currentEntryCurrentCpuQuark = ssq.getQuarkRelative(vmQuark, Attributes.THREADS, fCurrentEntry.getId(), Attributes.CURRENT_CPU);
                HashSet<Integer> visitedThreads = new HashSet<>();
                HashSet<Integer> hostVisitedThreads = new HashSet<>();

                List<ITmfStateInterval> currentEntryCurrentCpuIntervals = StateSystemUtils.queryHistoryRange(ssq, currentEntryCurrentCpuQuark, startTime, endTime);

                if(currentEntryCurrentCpuIntervals == null) {
                    return;
                }
                int i = 0;
                while (currentEntryCurrentCpuIntervals.get(i).getStateValue().isNull()) {
                    i++;
                }
                wrtStartTime = currentEntryCurrentCpuIntervals.get(i).getStartTime();
                i = currentEntryCurrentCpuIntervals.size() - 1;
                while (currentEntryCurrentCpuIntervals.get(i).getStateValue().isNull() && i > 0) {
                    i--;
                }
                wrtEndTime = currentEntryCurrentCpuIntervals.get(i).getEndTime();

                // ENTRY: currentEntry, WRT task
                int currentEntryQuark = ssq.getQuarkRelative(vmQuark, Attributes.THREADS, fCurrentEntry.getId());
                int currentEntryNameQuark = ssq.getQuarkRelative(currentEntryQuark, Attributes.EXEC_NAME);
                String currentEntryName = ssq.querySingleState(wrtEndTime, currentEntryNameQuark).getStateValue().unboxStr(); // TODO This is a hack: I took the value at the end but
                VmPreemptEntry currentThreadEntry = new VmPreemptEntry(currentEntryQuark, vmExperiment, currentEntryName + ": " + fCurrentEntry.getId(),
                        vmName, wrtStartTime, wrtEndTime, Type.ENTRY_THREAD, fCurrentEntry.getId());
                entryList.add(currentThreadEntry);
                visitedThreads.add(Integer.parseInt(fCurrentEntry.getId()));

                // Fill in the vCPUs
                List<Integer> vmsQuarks = ssq.getQuarks(Attributes.VIRTUAL_MACHINES, "*");
                for(int vm: vmsQuarks) {
                    String vmValue = ssq.getAttributeName(vm);
                    List<Integer> cpusQuarks = ssq.getQuarks(Attributes.VIRTUAL_MACHINES, vmValue, Attributes.CPUS, "*"); //$NON-NLS-1$

                    for(int vCpuQuark: cpusQuarks) {
                        int cpuTidQuark = ssq.getQuarkRelative(vCpuQuark, Attributes.TID);
                        int tid = ssq.querySingleState(wrtEndTime, cpuTidQuark).getStateValue().unboxInt();
                        tidToVCpuQuark.put(tid, vCpuQuark);
                        tidToVmName.put(tid, vmValue);
                        tidToVmQuark.put(tid, vm);
                    }

                    if(fCurrentEntry.getVmName().equals(vmValue)) {
                        vmNameToEntry.put(vmValue, new VmPreemptEntry(vm, vmExperiment, vmValue, getStartTime(), getEndTime(),
                            Type.VM, vmValue));
                    } else {
                        vmNameToEntry.put(vmValue, new VmPreemptEntry(vm, vmExperiment, vmValue, getStartTime(), getEndTime(),
                            Type.OTHER_VM, vmValue));
                    }
                    entryList.add(vmNameToEntry.get(vmValue));
                }
                /////////////////////

                // ENTRY: the VM - parent of the threads
//                VmPreemptEntry groupEntry = new VmPreemptEntry(vmQuark, vmExperiment, vmName, getStartTime(), getEndTime(),
//                        Type.VM, vmName);
//                entryList.add(groupEntry);

                VmPreemptEntry hostEntry = new VmPreemptEntry(vmQuark, vmExperiment, vmName, getStartTime(), getEndTime(),
                        Type.HOST, "Host"); //$NON-NLS-1$
                entryList.add(hostEntry);

                for(ITmfStateInterval currentEntryCurrentCpuInterval: currentEntryCurrentCpuIntervals) { // for each "current vCPU" value
                    if(currentEntryCurrentCpuInterval.getStateValue().isNull()) {
                        continue;
                    }
                    long startCurrentEntryCurrentCpu = Math.max(wrtStartTime, currentEntryCurrentCpuInterval.getStartTime());
                    long endCurrentEntryCurrentCpu = Math.min(wrtEndTime, currentEntryCurrentCpuInterval.getEndTime());
                    int currentCpu = currentEntryCurrentCpuInterval.getStateValue().unboxInt();
                    int currentThreadQuark = ssq.getQuarkRelative(vmQuark, Attributes.CPUS, Integer.toString(currentCpu), Attributes.CURRENT_THREAD);

                    List<ITmfStateInterval> currentThreadIntervals = StateSystemUtils.queryHistoryRange(ssq, currentThreadQuark, startCurrentEntryCurrentCpu, endCurrentEntryCurrentCpu);
                    for(ITmfStateInterval currentThreadInterval: currentThreadIntervals) { // for each "current thread" value of the current vCPU
                        if(currentThreadInterval.getStateValue().isNull()) {
                            continue;
                        }
                        int currentTid = currentThreadInterval.getStateValue().unboxInt();
                        if(visitedThreads.contains(currentTid)) {
                            continue;
                        }
                        visitedThreads.add(currentTid);

                        long startCurrentThead = Math.max(startCurrentEntryCurrentCpu, currentThreadInterval.getStartTime());
                        long endCurrentThead = Math.min(endCurrentEntryCurrentCpu, currentThreadInterval.getEndTime());
                        int threadsNode = ssq.getQuarkRelative(vmQuark, Attributes.THREADS);
                        int currentThreadQuarkFromThreadsNode = ssq.getQuarkRelative(threadsNode, Integer.toString(currentTid));
                        int currentThreadNameQuark = ssq.getQuarkRelative(currentThreadQuarkFromThreadsNode, Attributes.EXEC_NAME);
                        String currentThreadName = ssq.querySingleState(currentThreadInterval.getStartTime(), currentThreadNameQuark).getStateValue().unboxStr();
                        VmPreemptEntry threadEntry = new VmPreemptEntry(currentThreadQuarkFromThreadsNode, vmExperiment, currentThreadName + ": " + currentTid,
                                vmName, startCurrentThead, endCurrentThead, Type.THREAD, Integer.toString(currentTid));
//                        groupEntry.addChild(threadEntry);
                        vmNameToEntry.get(vmName).addChild(threadEntry);
                    }

                    // VCpu preemption on host
                    int currentVCpuTidQuark = ssq.getQuarkRelative(vmQuark, Attributes.CPUS, Integer.toString(currentCpu), Attributes.TID);
                    int currentVCpuTid = ssq.querySingleState(endCurrentEntryCurrentCpu, currentVCpuTidQuark).getStateValue().unboxInt();
                    hostVisitedThreads.add(currentVCpuTid); // we do not want to show the current vCpu in the host's threads
                    if(ssq.querySingleState(endCurrentEntryCurrentCpu, currentVCpuTidQuark).getStateValue().isNull()) {
                        continue;
                    }
                    int vCpuCurrentCpuQuark = ssq.getQuarkAbsolute(Attributes.THREADS);
                    vCpuCurrentCpuQuark = ssq.getQuarkRelative(vCpuCurrentCpuQuark, Integer.toString(currentVCpuTid), Attributes.CURRENT_CPU);

                    List<ITmfStateInterval> vCpuCurrentCpuIntervals = StateSystemUtils.queryHistoryRange(ssq, vCpuCurrentCpuQuark, startCurrentEntryCurrentCpu, endCurrentEntryCurrentCpu);
                    for(ITmfStateInterval vCpuCurrentCpuInterval: vCpuCurrentCpuIntervals) { // for each "current CPU" value
                        if(vCpuCurrentCpuInterval.getStateValue().isNull()) {
                            continue;
                        }
                        long startVCpuCurrentCpu = Math.max(startCurrentEntryCurrentCpu, vCpuCurrentCpuInterval.getStartTime());
                        long endVCpuCurrentCpu = Math.min(endCurrentEntryCurrentCpu, vCpuCurrentCpuInterval.getEndTime());
                        int vCpuCurrentCpu = vCpuCurrentCpuInterval.getStateValue().unboxInt();
                        currentThreadQuark = ssq.getQuarkAbsolute(Attributes.CPUS);
                        currentThreadQuark = ssq.getQuarkRelative(currentThreadQuark, Integer.toString(vCpuCurrentCpu), Attributes.CURRENT_THREAD);
                        currentThreadIntervals = StateSystemUtils.queryHistoryRange(ssq, currentThreadQuark, startVCpuCurrentCpu, endVCpuCurrentCpu);
                        for(ITmfStateInterval currentThreadInterval: currentThreadIntervals) { // for each "current thread" value of the current CPU
                            if(currentThreadInterval.getStateValue().isNull()) {
                                continue;
                            }
                            int currentTid = currentThreadInterval.getStateValue().unboxInt();
                            if(hostVisitedThreads.contains(currentTid)/* && !tidToVCpuQuark.containsKey(currentTid)*/) { // we dont want to skip vcpus because we might need to add other threads
                                continue;
                            }
                            hostVisitedThreads.add(currentTid);

                            if(tidToVCpuQuark.containsKey(currentTid)) {
                                int otherVCpuCurrentCpuQuark = ssq.getQuarkRelative(tidToVCpuQuark.get(currentTid), Attributes.CURRENT_CPU);

                                List<ITmfStateInterval> otherVCpuCurrentCpuIntervals = StateSystemUtils.queryHistoryRange(ssq, otherVCpuCurrentCpuQuark, // I think start and end time are supposed to be the whole trace,
                                                                                                                                       // because of the optimization
                                        currentThreadInterval.getStartTime(), currentThreadInterval.getEndTime());
                                for(ITmfStateInterval otherVCpuCurrentCpuInterval: otherVCpuCurrentCpuIntervals) {
                                    if(otherVCpuCurrentCpuInterval.getStateValue().isNull()) {
                                        continue;
                                    }
                                    int otherVCpuCurrentCpu = otherVCpuCurrentCpuInterval.getStateValue().unboxInt();
                                    if(otherVCpuCurrentCpu != vCpuCurrentCpu) {
                                        continue;
                                    }

                                    int otherVCpuCurrentThreadQuark = ssq.getQuarkRelative(tidToVCpuQuark.get(currentTid), Attributes.CURRENT_THREAD);
                                    List<ITmfStateInterval> otherVCpuCurrentThreadIntervals = StateSystemUtils.queryHistoryRange(ssq, otherVCpuCurrentThreadQuark,
                                            otherVCpuCurrentCpuInterval.getStartTime(), otherVCpuCurrentCpuInterval.getEndTime());
                                    for(ITmfStateInterval otherVCpuCurrentThreadInterval: otherVCpuCurrentThreadIntervals) {
                                        if(otherVCpuCurrentThreadInterval.getStateValue().isNull()) {
                                            continue;
                                        }
                                        int otherVCpuCurrentThread = otherVCpuCurrentThreadInterval.getStateValue().unboxInt();
                                        int otherThreadQuark = ssq.getQuarkRelative(tidToVmQuark.get(currentTid), Attributes.THREADS, Integer.toString(otherVCpuCurrentThread));
                                        int otherThreadNameQuark = ssq.getQuarkRelative(otherThreadQuark, Attributes.EXEC_NAME);
                                        String otherThreadName = ssq.querySingleState(otherVCpuCurrentThreadInterval.getStartTime(), otherThreadNameQuark).getStateValue().unboxStr();
                                        String vm = tidToVmName.get(currentTid);
                                        //                                    System.out.println("Thread " + otherThreadName + ": " + otherVCpuCurrentThread + " from VM " + vm); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                                        if(!hostVisitedThreads.contains(otherVCpuCurrentThread)) {
                                            vmNameToEntry.get(vm).addChild(new VmPreemptEntry(otherThreadQuark, vmExperiment, otherThreadName + ": " + otherVCpuCurrentThread, //$NON-NLS-1$
                                                vm, startTime, endTime, Type.THREAD, Integer.toString(otherVCpuCurrentThread)));
                                            hostVisitedThreads.add(otherVCpuCurrentThread);
                                        }
                                    }
                                }
                            } else {

                                int currentThreadQuarkFromThreadsNode = ssq.getQuarkRelative(ssq.getQuarkAbsolute(Attributes.THREADS), Integer.toString(currentTid));
                                int currentThreadNameQuark = ssq.getQuarkRelative(currentThreadQuarkFromThreadsNode, Attributes.EXEC_NAME);
                                String currentThreadName = ssq.querySingleState(currentThreadInterval.getStartTime(), currentThreadNameQuark).getStateValue().unboxStr();
                                VmPreemptEntry threadEntry = new VmPreemptEntry(currentThreadQuarkFromThreadsNode, vmExperiment, currentThreadName + ": " + currentTid,
                                        vmName, startTime, endTime, Type.HOST_THREAD, Integer.toString(currentTid));
                                hostEntry.addChild(threadEntry);
                            }
                        }
                    }
                }
            } catch (AttributeNotFoundException e) {
                e.printStackTrace();
            } catch (TimeRangeException e) {
                e.printStackTrace();
            } catch (StateSystemDisposedException e) {
                e.printStackTrace();
            } catch (StateValueTypeException e) {
                e.printStackTrace();
            }

            putObjectEntryList(fCurrentEntry, new ArrayList<TimeGraphEntry>(entryList));

            for (VmPreemptEntry traceEntry : entryList) {
                if (monitor.isCanceled()) {
                    return;
                }
                startTime = ssq.getStartTime();
                endTime = ssq.getCurrentEndTime() + 1;
                long resolution = (endTime - startTime) / getDisplayWidth();
                List<ITimeEvent> eventList = getEventList(traceEntry, wrtStartTime, wrtEndTime, resolution, monitor);
                traceEntry.setEventList(eventList);

                /////////////////////////////////////////////////////////
//                long debut = System.currentTimeMillis();
//                long fin;
//                if(traceEntry.getType().equals(Type.HOST)) {
//                    fillChildrenEventList(traceEntry, wrtStartTime, wrtEndTime, resolution, monitor);
//                } else {
    //
//                    for (TimeGraphEntry entry : traceEntry.getChildren()) { // TODO join these two loops
//                        eventList = getEventList(entry, wrtStartTime, wrtEndTime, resolution, monitor);
//                        entry.setEventList(eventList);
//                    }
    //
//                }

                fillChildrenEventList(traceEntry, wrtStartTime, wrtEndTime, resolution, monitor);
//                fin = System.currentTimeMillis();
//                System.out.println("Temps pour " + traceEntry.getType() + " avec " + traceEntry.getChildren().size() + " enfants: " + (fin - debut));
                ///////////////////////////////////////////////////////

                List<ITimeGraphEntry> children = new ArrayList<>();
                children.addAll(traceEntry.getChildren());
                for (ITimeGraphEntry entry : traceEntry.getChildren()) {
                    VmPreemptEntry e = (VmPreemptEntry) entry;
                    if(e.getExecutionTime() < EXECUTION_THRESHOLD) {
                        children.remove(entry);
                    }
                }
                Collections.sort(children, new VmPreemptEntryDurationComparator());
                traceEntry.getChildren().clear();
                for (ITimeGraphEntry entry : children) {
                    traceEntry.addChild(entry);
                }
            }

            if (trace.equals(getTrace())) {
                refresh();
            }
            redraw();
            refresh();
        }

        private @Nullable TmfGraph getGraph(final ITmfTrace trace) {
            CriticalPathModule module = Iterables.<@Nullable CriticalPathModule> getFirst(
                    TmfTraceUtils.getAnalysisModulesOfClass(trace, CriticalPathModule.class),
                    null);
            if (module == null) {
                throw new IllegalStateException("View requires an analysis module"); //$NON-NLS-1$
            }

            final TmfGraph graph = module.getCriticalPath();
            return graph;
        }

        public @Nullable List<ILinkEvent> getLinkList(long startTime, long endTime) {
            Object current = fCurrentObject;
            if (current == null) {
                return null;
            }
            final ITmfTrace trace = getTrace();
            if (trace == null) {
                return null;
            }
            /*
             * Critical path typically has relatively few links, so we calculate
             * and save them all, but just return those in range
             */
            List<ILinkEvent> links = fLinks.get(trace, current);
            if (links != null) {
                return getLinksInRange(links, startTime, endTime);
            }

            CriticalPathModule module = Iterables.<@Nullable CriticalPathModule> getFirst(
                    TmfTraceUtils.getAnalysisModulesOfClass(trace, CriticalPathModule.class), null);
            if (module == null) {
                throw new IllegalStateException("View requires an analysis module"); //$NON-NLS-1$
            }

            final TmfGraph graph = module.getCriticalPath();
            if (graph == null) {
                return null;
            }
            final Map<Object, CriticalPathEntry> entryMap = workerMaps.get(current);
            if (entryMap == null) {
                return null;
            }

            TmfVertex vertex = graph.getHead();

            final List<ILinkEvent> graphLinks = new ArrayList<>();

            /* find vertical links */
            graph.scanLineTraverse(vertex, new VerticalLinksVisitor(graph, graphLinks, entryMap));
            fLinks.put(trace, current, graphLinks);

            return getLinksInRange(graphLinks, startTime, endTime);
        }

        private List<ILinkEvent> getLinksInRange(List<ILinkEvent> allLinks, long startTime, long endTime) {
            List<ILinkEvent> linksInRange = new ArrayList<>();
            for (ILinkEvent link : allLinks) {
                if (((link.getTime() >= startTime) && (link.getTime() <= endTime)) ||
                        ((link.getTime() + link.getDuration() >= startTime) && (link.getTime() + link.getDuration() <= endTime))) {
                    linksInRange.add(link);
                }
            }
            return linksInRange;
        }

        @Override
        public void dispose() {
            fSyncLock.lock();
            try {
                BuildThread buildThread = fBuildThread;
                if (buildThread != null) {
                    buildThread.cancel();
                }
            } finally {
                fSyncLock.unlock();
            }
        }

        @Override
        public void inputChanged(@Nullable Viewer viewer, @Nullable Object oldInput, @Nullable Object newInput) {
            // The input has changed, the critical path will be re-computed,
            // wait for the analysis to be finished, then call the refresh
            // method of the view
            if (!(newInput instanceof List)) {
                return;
            }
            List<?> list = (List<?>) newInput;
            if (list.isEmpty()) {
                return;
            }
            final ITmfTrace trace = getTrace();
            if (trace == null) {
                return;
            }

            fSyncLock.lock();
            try {
                BuildThread buildThread = fBuildThread;
                if (buildThread != null) {
                    buildThread.cancel();
                }
                buildThread = new BuildThread(trace);
                buildThread.start();
                fBuildThread = buildThread;
            } finally {
                fSyncLock.unlock();
            }
        }

        @Override
        public ITimeGraphEntry @Nullable [] getChildren(@Nullable Object parentElement) {
            if (parentElement instanceof CriticalPathEntry) {
                List<? extends ITimeGraphEntry> children = ((CriticalPathEntry) parentElement).getChildren();
                return children.toArray(new TimeGraphEntry[children.size()]);
            }
            return null;
        }

        @Override
        public @Nullable ITimeGraphEntry getParent(@Nullable Object element) {
            if (element instanceof CriticalPathEntry) {
                return ((CriticalPathEntry) element).getParent();
            }
            return null;
        }

        @Override
        public boolean hasChildren(@Nullable Object element) {
            if (element instanceof CriticalPathEntry) {
                return ((CriticalPathEntry) element).hasChildren();
            }
            return false;
        }

    }

    private class VmPreemptEntryDurationComparator implements Comparator<ITimeGraphEntry> {

        @Override
        public int compare(ITimeGraphEntry o1, ITimeGraphEntry o2) {

            int result = 0;

            if ((o1 instanceof VmPreemptEntry) && (o2 instanceof VmPreemptEntry)) {
                VmPreemptEntry entry1 = (VmPreemptEntry) o1;
                VmPreemptEntry entry2 = (VmPreemptEntry) o2;
                if (entry1.getType() == Type.THREAD && entry2.getType() == Type.THREAD) {
                    result = entry1.getExecutionTime() < entry2.getExecutionTime() ? 1 : (entry1.getExecutionTime() > entry2.getExecutionTime() ? -1 : 0);
                } else if (entry1.getType() == Type.THREAD && entry2.getType() == Type.THREAD) {
                    result = entry1.getExecutionTime() < entry2.getExecutionTime() ? 1 : (entry1.getExecutionTime() > entry2.getExecutionTime() ? -1 : 0);
                }
            }
            return result;
        }
    }

    // ------------------------------------------------------------------------
    // Constructors
    // ------------------------------------------------------------------------

    /**
     * Default constructor
     */
    public VirtualMachinePreemptView() {
        super(ID, new VMPreemptPresentationProvider());
        setFilterColumns(FILTER_COLUMN_NAMES);
        setTreeColumns(COLUMN_NAMES);
        setTreeLabelProvider(new VmViewTreeLabelProvider());
        setTimeGraphContentProvider(fContentProvider);
        setEntryComparator(new VmPreemptEntryDurationComparator());
    }

    @Override
    protected String getNextText() {
        return NonNullUtils.checkNotNull(Messages.VmView_nextResourceActionNameText);
    }

    @Override
    protected String getNextTooltip() {
        return NonNullUtils.checkNotNull(Messages.VmView_nextResourceActionToolTipText);
    }

    @Override
    protected String getPrevText() {
        return NonNullUtils.checkNotNull(Messages.VmView_previousResourceActionNameText);
    }

    @Override
    protected String getPrevTooltip() {
        return NonNullUtils.checkNotNull(Messages.VmView_previousResourceActionToolTipText);
    }

    /**
     * @author gbastien
     *
     */
    protected static class VmViewTreeLabelProvider extends TreeLabelProvider {

        @Override
        public String getColumnText(@Nullable Object element, int columnIndex) {
            if (element == null) {
                return StringUtils.EMPTY;
            }
            VmPreemptEntry entry = (VmPreemptEntry) element;
            String result = ""; //$NON-NLS-1$

            switch(columnIndex) {
            case 0:
                result = entry.getName();
                break;
            case 1:
                result = String.format("%f", entry.getExecutionTime() / 1000000000.0); //$NON-NLS-1$
                break;
            default:
                break;
            }

            return result;
        }
    }

    protected List<ITimeEvent> fillChildrenEventList(TimeGraphEntry entry,  long startTime, long endTime, long resolution,
            IProgressMonitor monitor) {

        VmPreemptEntry vmPreemptEntry = (VmPreemptEntry) entry;
        ITmfStateSystem ssq = TmfStateSystemAnalysisModule.getStateSystem(vmPreemptEntry.getTrace(), VirtualMachineStateSystemModule.ID);
        if (ssq == null) {
            return Collections.EMPTY_LIST;
        }
        final long realStart = Math.max(startTime, ssq.getStartTime());
        final long realEnd = Math.min(endTime, ssq.getCurrentEndTime() + 1);
        if (realEnd <= realStart) {
            return null;
        }
        List<ITimeEvent> eventList = null;

        try {
            if (vmPreemptEntry.getType().equals(Type.VM)) {

                int vmQuark = vmPreemptEntry.getQuark();
                int currentEntryCurrentCpuQuark = ssq.getQuarkRelative(fCurrentEntry.getQuark(), Attributes.CURRENT_CPU);
                int currentEntryStatusQuark = ssq.getQuarkRelative(fCurrentEntry.getQuark(), Attributes.STATUS);

                eventList = new ArrayList<>();

                List<ITmfStateInterval> currentEntryStatusIntervals = StateSystemUtils.queryHistoryRange(ssq, currentEntryStatusQuark, wrtStartTime, wrtEndTime);
                for(ITmfStateInterval currentEntryStatusInterval: currentEntryStatusIntervals) { // for each "status" value of the current entry
                    if (monitor.isCanceled()) {
                        return null;
                    }
                    if(currentEntryStatusInterval.getStateValue().isNull()) {
                        continue;
                    }
                    long startCurrentEntryStatus = Math.max(wrtStartTime, currentEntryStatusInterval.getStartTime());
                    long endCurrentEntryStatus = Math.min(wrtEndTime, currentEntryStatusInterval.getEndTime());
                    int currentEntryStatus = currentEntryStatusInterval.getStateValue().unboxInt();
                    if(currentEntryStatus == StateValues.PROCESS_STATUS_RUN_USERMODE || currentEntryStatus == StateValues.PROCESS_STATUS_RUN_SYSCALL) {
                        continue;
                    }

                    List<ITmfStateInterval> currentEntryCurrentCpuIntervals = StateSystemUtils.queryHistoryRange(ssq, currentEntryCurrentCpuQuark, startCurrentEntryStatus, endCurrentEntryStatus);
                    for(ITmfStateInterval currentEntryCurrentCpuInterval: currentEntryCurrentCpuIntervals) { // for each "current CPU" value of the current entry
                        if(currentEntryCurrentCpuInterval.getStateValue().isNull()) {
                            continue;
                        }
                        long startCurrentEntryCurrentCpu = Math.max(startCurrentEntryStatus, currentEntryCurrentCpuInterval.getStartTime());
                        long endCurrentEntryCurrentCpu = Math.min(endCurrentEntryStatus, currentEntryCurrentCpuInterval.getEndTime());
                        int currentCpu = currentEntryCurrentCpuInterval.getStateValue().unboxInt();
                        int currentCpuCurrentThreadQuark = ssq.getQuarkRelative(vmQuark, Attributes.CPUS, Integer.toString(currentCpu), Attributes.CURRENT_THREAD);

                        List<ITmfStateInterval> currentThreadIntervals = StateSystemUtils.queryHistoryRange(ssq, currentCpuCurrentThreadQuark, startCurrentEntryCurrentCpu,
                                endCurrentEntryCurrentCpu, resolution, monitor);
                        for (ITmfStateInterval currentThreadInterval : currentThreadIntervals) { // for each "current thread" value on the current CPU
                            if(currentThreadInterval.getStateValue().isNull()) {
                                continue;
                            }
                            long startCurrentThread = Math.max(startCurrentEntryCurrentCpu, currentThreadInterval.getStartTime());
                            long endCurrentThread = Math.min(endCurrentEntryCurrentCpu, currentThreadInterval.getEndTime());
                            int currentThread = currentThreadInterval.getStateValue().unboxInt();

                            int currentThreadStatusQuark = ssq.getQuarkRelative(vmQuark, Attributes.THREADS, Integer.toString(currentThread), Attributes.STATUS);
                            List<ITmfStateInterval> statusIntervals = StateSystemUtils.queryHistoryRange(ssq, currentThreadStatusQuark, startCurrentThread, endCurrentThread);
                            for(ITmfStateInterval statusInterval: statusIntervals) { // for each "status" value of the current thread
                                if(statusInterval.getStateValue().isNull()) {
                                    continue;
                                }
                                int status = statusInterval.getStateValue().unboxInt();
                                long startCurrentThreadStatus = Math.max(startCurrentThread, statusInterval.getStartTime());
                                long endCurrentThreadStatus = Math.min(endCurrentThread, statusInterval.getEndTime());
                                long time = startCurrentThreadStatus;
                                long duration = endCurrentThreadStatus - time;
                                if(status == StateValues.PROCESS_STATUS_RUN_SYSCALL || status == StateValues.PROCESS_STATUS_RUN_USERMODE) {
                                    /* TODO: Optimize this */
                                    for (ITimeGraphEntry en : vmPreemptEntry.getChildren()) {
                                        if (en instanceof VmPreemptEntry) {
                                            VmPreemptEntry e = (VmPreemptEntry) en;
                                            if (Integer.toString(currentThread).equals(e.getId())) {
                                                e.addEvent(new TimeEvent(e, time, duration, status));
                                                break;
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

            } else if (vmPreemptEntry.getType().equals(Type.OTHER_VM)) {

                int currentEntryCurrentCpuQuark = ssq.getQuarkRelative(fCurrentEntry.getQuark(), Attributes.CURRENT_CPU);
                int currentCpu;
                eventList = new ArrayList<>();

                List<ITmfStateInterval> currentEntryCurrentCpuIntervals = StateSystemUtils.queryHistoryRange(ssq, currentEntryCurrentCpuQuark, wrtStartTime, wrtEndTime);
                for(ITmfStateInterval currentEntryCurrentCpuInterval: currentEntryCurrentCpuIntervals) { // for each "current vCPU" value of the current entry
                    if(currentEntryCurrentCpuInterval.getStateValue().isNull()) {
                        continue;
                    }
                    if(currentEntryCurrentCpuInterval.getEndTime() < wrtStartTime || currentEntryCurrentCpuInterval.getStartTime() > wrtEndTime) {
                        continue;
                    }
                    long startCurrentEntryCurrentCpu = Math.max(wrtStartTime, currentEntryCurrentCpuInterval.getStartTime());
                    long endCurrentEntryCurrentCpu = Math.min(wrtEndTime, currentEntryCurrentCpuInterval.getEndTime());
                    currentCpu = currentEntryCurrentCpuInterval.getStateValue().unboxInt();
                    int currentCpuTidQuark = ssq.getQuarkRelative(ssq.getQuarkAbsolute(Attributes.VIRTUAL_MACHINES), fCurrentEntry.getVmName(),
                            Attributes.CPUS, Integer.toString(currentCpu), Attributes.TID);
                    if(ssq.querySingleState(endCurrentEntryCurrentCpu, currentCpuTidQuark).getStateValue().isNull()) {
                        continue;
                    }
//                    int currentCpuTid = ssq.querySingleState(endCurrentEntryCurrentCpu, currentCpuTidQuark).getStateValue().unboxInt(); // NOT SURE
//                    int vCpuCurrentCpuQuark = ssq.getQuarkRelative(ssq.getQuarkAbsolute(Attributes.THREADS), Integer.toString(currentCpuTid), Attributes.CURRENT_CPU);
                    int vCpuCurrentCpuQuark = ssq.getQuarkRelative(ssq.getQuarkAbsolute(Attributes.VIRTUAL_MACHINES), fCurrentEntry.getVmName(),
                            Attributes.CPUS, Integer.toString(currentCpu), Attributes.CURRENT_CPU);
                    List<ITmfStateInterval> vCpuCurrentCpuIntervals = StateSystemUtils.queryHistoryRange(ssq, vCpuCurrentCpuQuark, startCurrentEntryCurrentCpu,
                            endCurrentEntryCurrentCpu);
                    for(ITmfStateInterval vCpuCurrentCpuInterval: vCpuCurrentCpuIntervals) { // for each "current CPU" value of the "current vCPU"
                        if(vCpuCurrentCpuInterval.getStateValue().isNull()) {
                            continue;
                        }
                        if(vCpuCurrentCpuInterval.getEndTime() < wrtStartTime || vCpuCurrentCpuInterval.getStartTime() > wrtEndTime) {
                            continue;
                        }
//                        long startVCpuCurrentCpu = Math.max(currentEntryCurrentCpuInterval.getStartTime(), vCpuCurrentCpuInterval.getStartTime());
//                        long endVCpuCurrentCpu = Math.min(currentEntryCurrentCpuInterval.getEndTime(), vCpuCurrentCpuInterval.getEndTime());
                        long startVCpuCurrentCpu = Math.max(startCurrentEntryCurrentCpu, vCpuCurrentCpuInterval.getStartTime());
                        long endVCpuCurrentCpu = Math.min(endCurrentEntryCurrentCpu, vCpuCurrentCpuInterval.getEndTime());
                        int currentCpuCurrentThreadQuark = ssq.getQuarkRelative(ssq.getQuarkAbsolute(Attributes.CPUS),
                                Integer.toString(vCpuCurrentCpuInterval.getStateValue().unboxInt()), Attributes.CURRENT_THREAD);
                        List<ITmfStateInterval> currentCpuCurrentThreadIntervals = StateSystemUtils.queryHistoryRange(ssq, currentCpuCurrentThreadQuark,
                                startVCpuCurrentCpu, endVCpuCurrentCpu);
                        for(ITmfStateInterval currentCpuCurrentThreadInterval: currentCpuCurrentThreadIntervals) { // for each "current thread" of the current CPU
                            if(currentCpuCurrentThreadInterval.getStateValue().isNull()) {
                                continue;
                            }
                            if(!tidToVmName.containsKey(currentCpuCurrentThreadInterval.getStateValue().unboxInt())) {
                                continue;
                            }
                            if(!tidToVmName.get(currentCpuCurrentThreadInterval.getStateValue().unboxInt()).equals(vmPreemptEntry.getVmName())) { // if it's not a vCPU, continue
                                continue;
                            }

                            long startCpuCurrentThread = Math.max(startVCpuCurrentCpu, currentCpuCurrentThreadInterval.getStartTime());
                            long endCpuCurrentThread = Math.min(endVCpuCurrentCpu, currentCpuCurrentThreadInterval.getEndTime());
                            int tidOfVcpu = currentCpuCurrentThreadInterval.getStateValue().unboxInt();
                            int vCpuCurrentThreadQuark = ssq.getQuarkRelative(tidToVCpuQuark.get(tidOfVcpu), Attributes.CURRENT_THREAD);

                            List<ITmfStateInterval> vCpuCurrentThreadIntervals = StateSystemUtils.queryHistoryRange(ssq, vCpuCurrentThreadQuark, startCpuCurrentThread, endCpuCurrentThread);
                            for(ITmfStateInterval vCpuCurrentThreadInterval: vCpuCurrentThreadIntervals) { // for each current thread on the vCpu
                                if(vCpuCurrentThreadInterval.getStateValue().isNull()) {
                                    continue;
                                }
                                long startVcpuCurrentThread = Math.max(startCpuCurrentThread, vCpuCurrentThreadInterval.getStartTime());
                                long endVcpuCurrentThread = Math.min(endCpuCurrentThread, vCpuCurrentThreadInterval.getEndTime());
                                long duration = endVcpuCurrentThread - startVcpuCurrentThread;
                                int vCpuCurrentThread = vCpuCurrentThreadInterval.getStateValue().unboxInt();
                                // TODO optimize this
                                for(ITimeGraphEntry en: vmPreemptEntry.getChildren()) {
                                    VmPreemptEntry e = (VmPreemptEntry) en;
                                    if(Integer.toString(vCpuCurrentThread).equals(e.getId())) {
                                        e.addEvent(new TimeEvent(e, startVcpuCurrentThread, duration, StateValues.PROCESS_STATUS_RUN_USERMODE)); // TODO get status
                                        break;
                                    }
                                }
                            }
                        }
                    }
                }

            } else if (vmPreemptEntry.getType().equals(Type.HOST)) {

                int currentEntryCurrentCpuQuark = ssq.getQuarkRelative(fCurrentEntry.getQuark(), Attributes.CURRENT_CPU);

                int currentCpu;
                eventList = new ArrayList<>();

                List<ITmfStateInterval> currentEntryCurrentCpuIntervals = StateSystemUtils.queryHistoryRange(ssq, currentEntryCurrentCpuQuark, wrtStartTime, wrtEndTime);
                for(ITmfStateInterval currentEntryCurrentCpuInterval: currentEntryCurrentCpuIntervals) { // for each "current vCPU" value of the current entry
                    if(currentEntryCurrentCpuInterval.getStateValue().isNull()) {
                        continue;
                    }
                    if(currentEntryCurrentCpuInterval.getEndTime() < wrtStartTime || currentEntryCurrentCpuInterval.getStartTime() > wrtEndTime) {
                        continue;
                    }
                    long startCurrentEntryCurrentCpu = Math.max(wrtStartTime, currentEntryCurrentCpuInterval.getStartTime());
                    long endCurrentEntryCurrentCpu = Math.min(wrtEndTime, currentEntryCurrentCpuInterval.getEndTime());
                    currentCpu = currentEntryCurrentCpuInterval.getStateValue().unboxInt();
                    int currentCpuTidQuark = ssq.getQuarkRelative(ssq.getQuarkAbsolute(Attributes.VIRTUAL_MACHINES), fCurrentEntry.getVmName(),
                            Attributes.CPUS, Integer.toString(currentCpu), Attributes.TID);
                    if(ssq.querySingleState(endCurrentEntryCurrentCpu, currentCpuTidQuark).getStateValue().isNull()) {
                        continue;
                    }
                    int currentCpuTid = ssq.querySingleState(endCurrentEntryCurrentCpu, currentCpuTidQuark).getStateValue().unboxInt(); // TODO NOT SURE
//                    int vCpuCurrentCpuQuark = ssq.getQuarkRelative(ssq.getQuarkAbsolute(Attributes.THREADS), Integer.toString(currentCpuTid), Attributes.CURRENT_CPU);
                    int vCpuCurrentCpuQuark = ssq.getQuarkRelative(ssq.getQuarkAbsolute(Attributes.VIRTUAL_MACHINES), fCurrentEntry.getVmName(),
                            Attributes.CPUS, Integer.toString(currentCpu), Attributes.CURRENT_CPU);

//                    int vCpuCurrentCpuQuark = ssq.getQuarkRelative(currentEntryCurrentCpuQuark, Attributes.CURRENT_CPU);

                    List<ITmfStateInterval> vCpuCurrentCpuIntervals = StateSystemUtils.queryHistoryRange(ssq, vCpuCurrentCpuQuark, startCurrentEntryCurrentCpu,
                            endCurrentEntryCurrentCpu);
                    for(ITmfStateInterval vCpuCurrentCpuInterval: vCpuCurrentCpuIntervals) { // for each "current CPU" value of the "current vCPU"
                        if(vCpuCurrentCpuInterval.getStateValue().isNull()) {
                            continue;
                        }
                        if(vCpuCurrentCpuInterval.getEndTime() < wrtStartTime || vCpuCurrentCpuInterval.getStartTime() > wrtEndTime) {
                            continue;
                        }
                        long startVCpuCurrentCpu = Math.max(startCurrentEntryCurrentCpu, vCpuCurrentCpuInterval.getStartTime());
                        long endVCpuCurrentCpu = Math.min(endCurrentEntryCurrentCpu, vCpuCurrentCpuInterval.getEndTime());
                        int currentCpuCurrentThreadQuark = ssq.getQuarkRelative(ssq.getQuarkAbsolute(Attributes.CPUS),
                                Integer.toString(vCpuCurrentCpuInterval.getStateValue().unboxInt()), Attributes.CURRENT_THREAD);

                        List<ITmfStateInterval> currentCpuCurrentThreadIntervals = StateSystemUtils.queryHistoryRange(ssq, currentCpuCurrentThreadQuark,
                                startVCpuCurrentCpu, endVCpuCurrentCpu);
                        for(ITmfStateInterval currentCpuCurrentThreadInterval: currentCpuCurrentThreadIntervals) { // for each "current thread" of the current CPU
                            if(currentCpuCurrentThreadInterval.getStateValue().isNull()) {
                                continue;
                            }
                            if(currentCpuCurrentThreadInterval.getEndTime() < wrtStartTime || currentCpuCurrentThreadInterval.getStartTime() > wrtEndTime) {
                                continue;
                            }
                            int currentThread = currentCpuCurrentThreadInterval.getStateValue().unboxInt();
                            if(currentThread == currentCpuTid) {
                                continue;
                            }
                            long startCurrentCpuCurrentThread = Math.max(startVCpuCurrentCpu, currentCpuCurrentThreadInterval.getStartTime());
                            long endCurrentCpuCurrentThread = Math.min(endVCpuCurrentCpu, currentCpuCurrentThreadInterval.getEndTime());
                            long duration = endCurrentCpuCurrentThread - startCurrentCpuCurrentThread;
                            int currentThreadStatusQuark = ssq.getQuarkRelative(ssq.getQuarkAbsolute(Attributes.THREADS), Integer.toString(currentThread), Attributes.STATUS);
                            int status = ssq.querySingleState(startCurrentCpuCurrentThread, currentThreadStatusQuark).getStateValue().unboxInt();
                            for(ITimeGraphEntry en: vmPreemptEntry.getChildren()) { // TODO optimize this
                                VmPreemptEntry e = (VmPreemptEntry) en;
                                if(Integer.toString(currentThread).equals(e.getId())) {
                                    e.addEvent(new TimeEvent(e, startCurrentCpuCurrentThread, duration, status));
                                    break;
                                }
                            }
                        }
                    }
                }
            }
        } catch (AttributeNotFoundException e) {
            e.printStackTrace();
        } catch (TimeRangeException e) {
            e.printStackTrace();
        } catch (StateValueTypeException e) {
            e.printStackTrace();
        } catch (StateSystemDisposedException e) {
            /* Ignored */
        }

        return eventList;
    }
    // ------------------------------------------------------------------------
    // Internal
    // ------------------------------------------------------------------------

    @Override
    protected @Nullable List<ITimeEvent> getEventList(TimeGraphEntry entry,
            long startTime, long endTime, long resolution,
            IProgressMonitor monitor) {
        /*
         * The event list is built in the HorizontalLinksVisitor. This is called
         * only from the zoom thread and only for the CriticalPathBaseEntry.
         */
        return null;

        VmPreemptEntry vmPreemptEntry = (VmPreemptEntry) entry;
        ITmfStateSystem ssq = TmfStateSystemAnalysisModule.getStateSystem(vmPreemptEntry.getTrace(), VirtualMachineStateSystemModule.ID);
        if (ssq == null) {
            return Collections.EMPTY_LIST;
        }
        final long realStart = Math.max(startTime, ssq.getStartTime());
        final long realEnd = Math.min(endTime, ssq.getCurrentEndTime() + 1);
        if (realEnd <= realStart) {
            return null;
        }
        List<ITimeEvent> eventList = null;
        int quark = vmPreemptEntry.getQuark();

        try {
            if (vmPreemptEntry.getType().equals(Type.ENTRY_THREAD)) {

                int statusQuark = ssq.getQuarkRelative(quark, Attributes.STATUS);
                eventList = new ArrayList<>();
                //                List<ITmfStateInterval> currentEntryCurrentCpuIntervals = ssq.queryHistoryRange(currentEntryCurrentCpuQuark, wrtStartTime, wrtEndTime);//startTime, endTime);
//                for(ITmfStateInterval currentEntryCurrentCpuInterval: currentEntryCurrentCpuIntervals) {
//                    if(currentEntryCurrentCpuInterval.getStateValue().isNull()) {
//                        continue;
//                    }
//                    if(currentEntryCurrentCpuInterval.getEndTime() < wrtStartTime || currentEntryCurrentCpuInterval.getStartTime() > wrtEndTime) {
//                        continue;
//                    }
                    List<ITmfStateInterval> statusIntervals = StateSystemUtils.queryHistoryRange(ssq, statusQuark, wrtStartTime,
                            wrtEndTime, resolution, monitor);
                    for (ITmfStateInterval statusInterval : statusIntervals) {
                        if (monitor.isCanceled()) {
                            return null;
                        }
                        if(statusInterval.getEndTime() < wrtStartTime || statusInterval.getStartTime() > wrtEndTime) {
                            continue;
                        }
                        int status = statusInterval.getStateValue().unboxInt();
                        if (statusInterval.getStateValue().isNull()) {
//                          if (lastEndTime == -1 || time + duration >= endTime) {
//                              // add null event if it intersects the start or end time
//                              eventList.add(new NullTimeEvent(entry, time, duration));
//                              continue;
//                          }
                          continue;
                        }
                        long time = Math.max(wrtStartTime, statusInterval.getStartTime());
                        long end = Math.min(wrtEndTime, statusInterval.getEndTime());
                        long duration = end - time;
//                        long durationToStatusChange = statusInterval.getEndTime() - time;
//                        long durationToCurrentEntryCurrentCpuChange = currentEntryCurrentCpuInterval.getEndTime() - time;
//                        long duration = Math.min(durationToStatusChange, durationToCurrentEntryCurrentCpuChange);
//                        duration = Math.min(duration, wrtEndTime);

//                        currentEntryCurrentCpu = currentEntryCurrentCpuInterval.getStateValue().unboxInt();
//                        if(!vmPreemptEntry.getId().equals(fCurrentEntry.getId())) {
//                            currentCpuQuark = ssq.getQuarkRelative(quark, Attributes.CURRENT_CPU);
//                            currentCpu = ssq.querySingleState(time, currentCpuQuark).getStateValue().unboxInt();
//                            if(currentEntryCurrentCpu != currentCpu) {
//                                continue;
//                            }
                            if(status == StateValues.PROCESS_STATUS_RUN_SYSCALL || status == StateValues.PROCESS_STATUS_RUN_USERMODE) {
                                eventList.add(new TimeEvent(vmPreemptEntry, time, duration, status));
                            }
//                        }
//                        else /*if(virtualMachineEntry == fCurrentEntry)*/ {
//                            if(status == StateValues.PROCESS_STATUS_RUN_SYSCALL || status == StateValues.PROCESS_STATUS_RUN_USERMODE) {
//                                eventList.add(new TimeEvent(vmPreemptEntry, time, duration, status));
//                            }
//                        }
                    }
                }
//            }
        /* else if (vmPreemptEntry.getType().equals(Type.HOST_THREAD)) {

                int currentEntryCurrentCpuQuark = ssq.getQuarkRelative(fCurrentEntry.getQuark(), Attributes.CURRENT_CPU);
                int currentCpu;
                eventList = new ArrayList<ITimeEvent>();

                List<ITmfStateInterval> currentEntryCurrentCpuIntervals = ssq.queryHistoryRange(currentEntryCurrentCpuQuark, wrtStartTime, wrtEndTime);
                for(ITmfStateInterval currentEntryCurrentCpuInterval: currentEntryCurrentCpuIntervals) { // for each "current vCPU" value of the current entry
                    if(currentEntryCurrentCpuInterval.getStateValue().isNull()) {
                        continue;
                    }
                    if(currentEntryCurrentCpuInterval.getEndTime() < wrtStartTime || currentEntryCurrentCpuInterval.getStartTime() > wrtEndTime) {
                        continue;
                    }
                    long startCurrentEntryCurrentCpu = Math.max(wrtStartTime, currentEntryCurrentCpuInterval.getStartTime());
                    long endCurrentEntryCurrentCpu = Math.min(wrtEndTime, currentEntryCurrentCpuInterval.getEndTime());
                    currentCpu = currentEntryCurrentCpuInterval.getStateValue().unboxInt();
                    int currentCpuTidQuark = ssq.getQuarkRelative(ssq.getQuarkAbsolute(Attributes.VIRTUAL_MACHINES), fCurrentEntry.getVmName(),
                            Attributes.CPUS, Integer.toString(currentCpu), Attributes.TID);
                    int currentCpuTid = ssq.querySingleState(currentEntryCurrentCpuInterval.getStartTime(), currentCpuTidQuark).getStateValue().unboxInt(); // NOT SURE
                    int vCpuCurrentCpuQuark = ssq.getQuarkRelative(ssq.getQuarkAbsolute(Attributes.THREADS), Integer.toString(currentCpuTid), Attributes.CURRENT_CPU);
                    List<ITmfStateInterval> vCpuCurrentCpuIntervals = ssq.queryHistoryRange(vCpuCurrentCpuQuark, startCurrentEntryCurrentCpu,
                            endCurrentEntryCurrentCpu);
                    for(ITmfStateInterval vCpuCurrentCpuInterval: vCpuCurrentCpuIntervals) { // for each "current CPU" value of the "current vCPU"
                        if(vCpuCurrentCpuInterval.getStateValue().isNull()) {
                            continue;
                        }
                        if(vCpuCurrentCpuInterval.getEndTime() < wrtStartTime || vCpuCurrentCpuInterval.getStartTime() > wrtEndTime) {
                            continue;
                        }
                        long startVCpuCurrentCpu = Math.max(currentEntryCurrentCpuInterval.getStartTime(), vCpuCurrentCpuInterval.getStartTime());
                        long endVCpuCurrentCpu = Math.min(currentEntryCurrentCpuInterval.getEndTime(), vCpuCurrentCpuInterval.getEndTime());
                        int currentCpuCurrentThreadQuark = ssq.getQuarkRelative(ssq.getQuarkAbsolute(Attributes.CPUS),
                                Integer.toString(vCpuCurrentCpuInterval.getStateValue().unboxInt()), Attributes.CURRENT_THREAD);
                        List<ITmfStateInterval> currentCpuCurrentThreadIntervals = ssq.queryHistoryRange(currentCpuCurrentThreadQuark,
                                startVCpuCurrentCpu, endVCpuCurrentCpu);
                        for(ITmfStateInterval currentCpuCurrentThreadInterval: currentCpuCurrentThreadIntervals) { // for each "current thread" of the current CPU
                            if(currentCpuCurrentThreadInterval.getStateValue().isNull()) {
                                continue;
                            }
                            if(currentCpuCurrentThreadInterval.getEndTime() < wrtStartTime || currentCpuCurrentThreadInterval.getStartTime() > wrtEndTime) {
                                continue;
                            }
                            int currentThread = currentCpuCurrentThreadInterval.getStateValue().unboxInt();
                            if(!Integer.toString(currentThread).equals(vmPreemptEntry.getId())) {
                                continue;
                            }
                            long startCurrentCpuCurrentThread;
                            startCurrentCpuCurrentThread = Math.max(currentCpuCurrentThreadInterval.getStartTime(), vCpuCurrentCpuInterval.getStartTime());
                            startCurrentCpuCurrentThread = Math.max(startCurrentCpuCurrentThread, currentEntryCurrentCpuInterval.getStartTime());
                            long endCurrentCpuCurrentThread;
                            endCurrentCpuCurrentThread = Math.min(currentCpuCurrentThreadInterval.getEndTime(), vCpuCurrentCpuInterval.getEndTime());
                            endCurrentCpuCurrentThread = Math.min(endCurrentCpuCurrentThread, currentEntryCurrentCpuInterval.getEndTime());
                            int currentThreadStatusQuark = ssq.getQuarkRelative(ssq.getQuarkAbsolute(Attributes.THREADS), Integer.toString(currentThread), Attributes.STATUS);
                            long duration = endCurrentCpuCurrentThread - startCurrentCpuCurrentThread;
                            int status = ssq.querySingleState(currentCpuCurrentThreadInterval.getStartTime(), currentThreadStatusQuark).getStateValue().unboxInt();
                            eventList.add(new TimeEvent(vmPreemptEntry, startCurrentCpuCurrentThread, duration, status));
                        }
                    }
                }
            }*/
        } catch (AttributeNotFoundException e) {
            e.printStackTrace();
        } catch (TimeRangeException e) {
            e.printStackTrace();
        } catch (StateValueTypeException e) {
            e.printStackTrace();
        } catch (StateSystemDisposedException e) {
            /* Ignored */
        }
        return eventList;
    }

    private void setCurrentEntry(VirtualMachineViewEntry entry) {
        if (fCurrentEntry != entry) {
            fCurrentEntry = entry;
            loadObject(entry);
        }
    }

    @Override
    protected void buildEntryList(@NonNull ITmfTrace trace, @NonNull ITmfTrace parentTrace, @NonNull IProgressMonitor monitor) {
        /* This class uses a content provider instead */
    }

}
