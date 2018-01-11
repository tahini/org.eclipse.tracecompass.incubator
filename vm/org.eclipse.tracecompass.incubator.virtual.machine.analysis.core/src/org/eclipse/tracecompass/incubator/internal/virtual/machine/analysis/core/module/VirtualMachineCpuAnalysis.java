/*******************************************************************************
 * Copyright (c) 2014, 2015 École Polytechnique de Montréal
 *
 * All rights reserved. This program and the accompanying materials are
 * made available under the terms of the Eclipse Public License v1.0 which
 * accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *   Mohamad Gebai - Initial API and implementation
 *   Geneviève Bastien - Initial API and implementation
 *******************************************************************************/

package org.eclipse.tracecompass.incubator.internal.virtual.machine.analysis.core.module;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.ListenerList;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.tracecompass.analysis.os.linux.core.kernel.KernelAnalysisModule;
import org.eclipse.tracecompass.analysis.os.linux.core.kernel.KernelThreadInformationProvider;
import org.eclipse.tracecompass.analysis.os.linux.core.tid.TidAnalysisModule;
import org.eclipse.tracecompass.analysis.timing.core.segmentstore.IAnalysisProgressListener;
import org.eclipse.tracecompass.analysis.timing.core.segmentstore.ISegmentStoreProvider;
import org.eclipse.tracecompass.datastore.core.interval.IHTIntervalReader;
import org.eclipse.tracecompass.datastore.core.serialization.ISafeByteBufferWriter;
import org.eclipse.tracecompass.incubator.internal.virtual.machine.analysis.core.Activator;
import org.eclipse.tracecompass.incubator.internal.virtual.machine.analysis.core.data.VcpuStateValues;
import org.eclipse.tracecompass.incubator.internal.virtual.machine.analysis.core.data.VmAttributes;
import org.eclipse.tracecompass.internal.datastore.core.serialization.SafeByteBufferWrapper;
import org.eclipse.tracecompass.segmentstore.core.ISegment;
import org.eclipse.tracecompass.segmentstore.core.ISegmentStore;
import org.eclipse.tracecompass.segmentstore.core.SegmentStoreFactory;
import org.eclipse.tracecompass.segmentstore.core.segment.interfaces.INamedSegment;
import org.eclipse.tracecompass.statesystem.core.ITmfStateSystem;
import org.eclipse.tracecompass.statesystem.core.StateSystemUtils;
import org.eclipse.tracecompass.statesystem.core.exceptions.AttributeNotFoundException;
import org.eclipse.tracecompass.statesystem.core.exceptions.StateSystemDisposedException;
import org.eclipse.tracecompass.statesystem.core.interval.ITmfStateInterval;
import org.eclipse.tracecompass.statesystem.core.interval.TmfStateInterval;
import org.eclipse.tracecompass.statesystem.core.statevalue.ITmfStateValue;
import org.eclipse.tracecompass.statesystem.core.statevalue.ITmfStateValue.Type;
import org.eclipse.tracecompass.statesystem.core.statevalue.TmfStateValue;
import org.eclipse.tracecompass.tmf.core.analysis.IAnalysisModule;
import org.eclipse.tracecompass.tmf.core.segment.ISegmentAspect;
import org.eclipse.tracecompass.tmf.core.statesystem.ITmfStateProvider;
import org.eclipse.tracecompass.tmf.core.statesystem.TmfStateSystemAnalysisModule;
import org.eclipse.tracecompass.tmf.core.trace.ITmfTrace;
import org.eclipse.tracecompass.tmf.core.trace.TmfTraceManager;
import org.eclipse.tracecompass.tmf.core.trace.TmfTraceUtils;
import org.eclipse.tracecompass.tmf.core.trace.experiment.TmfExperiment;
import org.eclipse.tracecompass.tmf.core.trace.experiment.TmfExperimentUtils;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Multimap;
import com.google.common.collect.TreeMultimap;

/**
 * Module for the virtual machine CPU analysis. It tracks the status of the
 * virtual CPUs for each guest of the experiment.
 *
 * @author Mohamad Gebai
 * @author Geneviève Bastien
 */
public class VirtualMachineCpuAnalysis extends TmfStateSystemAnalysisModule implements ISegmentStoreProvider {

    static class VmWrongSegment implements INamedSegment {

        private final long fStart;
        private final long fEnd;
        private final boolean fReverse;
        private final String fEventName;

        /**
         *
         */
        private static final long serialVersionUID = 8278947524454044651L;

        public VmWrongSegment(long runTime, long time, String eventName) {
            fStart = Math.min(runTime, time);
            fEnd = Math.max(runTime, time);
            fEventName = eventName;
            fReverse = time < runTime;
        }

        public VmWrongSegment(long start, long end, byte reverse, String name) {
            fStart = start;
            fEnd = end;
            fReverse = reverse < 0;
            fEventName = name;
        }

        @Override
        public long getStart() {
            return (fReverse ? fEnd : fStart);
        }

        @Override
        public long getEnd() {
            return (fReverse ? fStart : fEnd);
        }

        @Override
        public int getSizeOnDisk() {
            return Long.BYTES * 2 + Byte.BYTES + SafeByteBufferWrapper.getStringSizeInBuffer(fEventName);
        }

        @Override
        public String getName() {
            return fEventName;
        }

        @Override
        public void writeSegment(ISafeByteBufferWriter buffer) {
            buffer.putLong(fStart);
            buffer.putLong(fEnd);
            buffer.put(fReverse ? (byte) 1 : (byte) -1);
            buffer.putString(fEventName);
        }
    }

    /** The ID of this analysis module */
    public static final String ID = "org.eclipse.tracecompass.incubator.virtual.machine.analysis.core.VirtualMachineAnalysisModule"; //$NON-NLS-1$

    // TODO: Update with event layout when requirements are back */
    static final Set<String> REQUIRED_EVENTS = ImmutableSet.of(
            // LttngStrings.SCHED_SWITCH
            );

    /* State value for a preempted virtual CPU */
    private static final ITmfStateValue VCPU_PREEMPT_VALUE = TmfStateValue.newValueInt(VcpuStateValues.VCPU_PREEMPT);

    private static final String SEGSTORE_SUFFIX = ".segments";
    private static final IHTIntervalReader<ISegment> SEGMENT_READER = buffer -> new VmWrongSegment(buffer.getLong(), buffer.getLong(), buffer.get(), buffer.getString());

    private static final ISegmentAspect SEGMENT_ASPECT_EVENT_NAME = new ISegmentAspect() {

        @Override
        public String getName() {
            return "Event Name";
        }

        @Override
        public String getHelpText() {
            return "Event Name";
        }

        @Override
        public @Nullable Comparator<?> getComparator() {
            return null;
        }

        @Override
        public @Nullable Object resolve(ISegment segment) {
            if (!(segment instanceof VmWrongSegment)) {
                return null;
            }
            return ((VmWrongSegment) segment).getName();
        }

    };

    private static final List<ISegmentAspect> SEGMENT_ASPECTS = ImmutableList.of(SEGMENT_ASPECT_EVENT_NAME);

    private final ListenerList fListeners = new ListenerList(ListenerList.IDENTITY);
    private @Nullable ISegmentStore<ISegment> fSegmentStore;

    /**
     * Constructor
     */
    public VirtualMachineCpuAnalysis() {
        super();
    }

    @Override
    protected boolean executeAnalysis(@Nullable final IProgressMonitor monitor) {
        try {
         // Initialize segment store
            ITmfTrace trace = getTrace();
            if (trace == null) {
                return false;
            }
            String filename = getId() + SEGSTORE_SUFFIX;
            /* See if the data file already exists on disk */
            String dir = TmfTraceManager.getSupplementaryFileDir(trace);
            final Path file = Paths.get(dir, filename);

            ISegmentStore<ISegment> segmentStore = SegmentStoreFactory.createOnDiskSegmentStore(file, SEGMENT_READER);
            fSegmentStore = segmentStore;

            boolean result = super.executeAnalysis(monitor);
            if (!result) {
                return false;
            }
            sendUpdate(segmentStore);
            return true;
        } catch (IOException e) {
            Activator.getInstance().logError("Error creating segment store", e); //$NON-NLS-1$
        }
        return false;
    }

    /**
     * Returns all the listeners
     *
     * @return latency listeners
     */
    protected Iterable<IAnalysisProgressListener> getListeners() {
        List<IAnalysisProgressListener> listeners = new ArrayList<>();
        for (Object listener : fListeners.getListeners()) {
            if (listener != null) {
                listeners.add((IAnalysisProgressListener) listener);
            }
        }
        return listeners;
    }

    private void sendUpdate(ISegmentStore<ISegment> segmentStore) {
        for (IAnalysisProgressListener listener : getListeners()) {
            listener.onComplete(this, segmentStore);
        }
    }

    @Override
    protected ITmfStateProvider createStateProvider() {
        ITmfTrace trace = getTrace();
        ISegmentStore<ISegment> segmentStore = fSegmentStore;
        if (!(trace instanceof TmfExperiment) || segmentStore == null) {
            throw new IllegalStateException();
        }

        return new VirtualMachineStateProvider((TmfExperiment) trace, segmentStore);
    }

    @Override
    protected @NonNull StateSystemBackendType getBackendType() {
        return StateSystemBackendType.FULL;
    }

    @Override
    public String getHelpText() {
        return Messages.getMessage(Messages.VirtualMachineCPUAnalysis_Help);
    }

    @Override
    protected Iterable<IAnalysisModule> getDependentAnalyses() {
        Set<IAnalysisModule> modules = new HashSet<>();
        /* Depends on the LTTng Kernel analysis modules */
        for (ITmfTrace trace : TmfTraceManager.getTraceSet(getTrace())) {
            for (TidAnalysisModule module : TmfTraceUtils.getAnalysisModulesOfClass(trace, TidAnalysisModule.class)) {
                modules.add(module);
            }
        }
        return modules;
    }

    private static Multimap<Integer, ITmfStateInterval> createThreadMultimap() {

        /*
         * Create the multimap for threads with the appropriate comparator
         * objects for keys and values
         */
        final Multimap<Integer, ITmfStateInterval> map = TreeMultimap.create(
                /* Key comparator. Keys do not have to be sorted, just use natural sorting*/
                Comparator.naturalOrder(),

                /* Value comparator */
                (arg0, arg1) -> {
                    if (arg1.getStateValue() == VCPU_PREEMPT_VALUE && arg0.getStateValue() != VCPU_PREEMPT_VALUE) {
                        /*
                         * For VCPU_PREEMPT state values, the state has to be
                         * after any other state that it overlaps, because those
                         * intervals usually decorate the other intervals.
                         */
                        if (((Long) arg0.getEndTime()).compareTo(arg1.getStartTime()) < 0) {
                            return -1;
                        }
                        return ((Long) arg0.getStartTime()).compareTo(arg1.getEndTime());
                    }
                    /* Otherwise, we use ordering by start time */
                    return (((Long) arg0.getStartTime()).compareTo(arg1.getStartTime()));
                });
        return map;
    }

    /**
     * Get the status intervals for the threads from a virtual machine. Those
     * intervals are correlated with the data from the virtual CPU's preemption
     * status.
     *
     * This method uses the Linux Kernel Analysis data for the thread's status
     * intervals.
     *
     * @param vmQuark
     *            The quark of the virtual machine
     * @param start
     *            The start time of the period to get the intervals from
     * @param end
     *            The end time of the period to get the intervals from
     * @param resolution
     *            The resolution
     * @param monitor
     *            A progress monitor for this task
     * @return A map of status intervals for the machine's threads, including
     *         preempted intervals. Intervals from the thread status and the CPU
     *         preemption status overlap and are ordered such that CPU
     *         preemption intervals are after any interval they overlap with
     */
    public Multimap<Integer, ITmfStateInterval> getUpdatedThreadIntervals(int vmQuark, long start, long end, long resolution, IProgressMonitor monitor) {

        final Multimap<Integer, ITmfStateInterval> map = createThreadMultimap();

        ITmfStateSystem ss = getStateSystem();
        if (ss == null) {
            return map;
        }
        ITmfTrace trace = getTrace();
        if (!(trace instanceof TmfExperiment)) {
            return map;
        }

        String vmHostId = ss.getAttributeName(vmQuark);
        KernelAnalysisModule kernelModule = TmfExperimentUtils.getAnalysisModuleOfClassForHost((TmfExperiment) trace, vmHostId, KernelAnalysisModule.class);
        if (kernelModule == null) {
            return map;
        }

        /*
         * Initialize the map with the original status intervals from the kernel
         * module
         */
        for (Integer tid : KernelThreadInformationProvider.getThreadIds(kernelModule)) {
            map.putAll(tid, KernelThreadInformationProvider.getStatusIntervalsForThread(kernelModule, tid, start, end, resolution, monitor));
            if (monitor.isCanceled()) {
                return map;
            }
        }

        try {
            /* Correlate thread information with virtual CPU information */
            for (Integer vcpuQuark : ss.getSubAttributes(vmQuark, false)) {
                Long virtualCPU = Long.parseLong(ss.getAttributeName(vcpuQuark));
                Integer statusQuark = ss.getQuarkRelative(vcpuQuark, VmAttributes.STATUS);

                for (ITmfStateInterval cpuInterval : StateSystemUtils.queryHistoryRange(ss, statusQuark, start, end - 1, resolution, monitor)) {
                    ITmfStateValue stateValue = cpuInterval.getStateValue();
                    if (stateValue.getType() == Type.INTEGER) {
                        int value = stateValue.unboxInt();
                        /*
                         * If the current CPU is either preempted or in
                         * hypervisor mode, add preempted intervals to running
                         * processes
                         */
                        if ((value & (VcpuStateValues.VCPU_PREEMPT | VcpuStateValues.VCPU_VMM)) == 0) {
                            continue;
                        }
                        Integer threadOnCpu = KernelThreadInformationProvider.getThreadOnCpu(kernelModule, virtualCPU, cpuInterval.getStartTime());
                        if (threadOnCpu != null) {
                            map.put(threadOnCpu, new TmfStateInterval(cpuInterval.getStartTime(), cpuInterval.getEndTime(), threadOnCpu, VcpuStateValues.VCPU_PREEMPT));
                        }
                    }
                }
            }
        } catch (AttributeNotFoundException | StateSystemDisposedException e) {
        }
        return map;
    }

    @Override
    public void addListener(IAnalysisProgressListener listener) {
        fListeners.add(listener);
    }

    @Override
    public void removeListener(IAnalysisProgressListener listener) {
        fListeners.remove(listener);
    }

    @Override
    public Iterable<ISegmentAspect> getSegmentAspects() {
        return SEGMENT_ASPECTS;
    }

    @Override
    public @Nullable ISegmentStore<ISegment> getSegmentStore() {
        return fSegmentStore;
    }

}
