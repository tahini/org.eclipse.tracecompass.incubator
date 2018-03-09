/**********************************************************************
 * Copyright (c) 2018 École Polytechnique de Montréal
 *
 * All rights reserved. This program and the accompanying materials are
 * made available under the terms of the Eclipse Public License v1.0 which
 * accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 **********************************************************************/

package org.eclipse.tracecompass.incubator.internal.callstack.core.instrumented.provider;

import java.util.Objects;

import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.tracecompass.incubator.analysis.core.model.IHostModel;
import org.eclipse.tracecompass.incubator.callstack.core.base.ICallStackElement;
import org.eclipse.tracecompass.incubator.internal.callstack.core.instrumented.InstrumentedCallStackElement;
import org.eclipse.tracecompass.internal.provisional.tmf.core.model.timegraph.TimeGraphEntryModel;

/**
 * {@link TimeGraphEntryModel} for the Flame chart data
 *
 * @author Geneviève Bastien
 */
@SuppressWarnings("restriction")
public class FlameChartEntryModel extends TimeGraphEntryModel {

    /**
     * An enumeration for the type of flame chart entries
     */
    public enum EntryType {
        /**
         * A descriptive entry, for example the one for the trace
         */
        TRACE,
        /**
         * Represent a group of the callstack analysis
         */
        LEVEL,
        /**
         * Represent an entry with function data, the actual callstack data
         */
        FUNCTION,
        /**
         * This entry will show the kernel statuses for the TID running the callstack.
         * Will not always be present
         */
        KERNEL
    }

    private final EntryType fEntryType;
    private final int fPid;
    private final boolean fIsSymbolKey;
    private final @Nullable ICallStackElement fCallStackElement;

    /**
     * Constructor
     *
     * @param id
     *            unique ID for this {@link FlameChartEntryModel}
     * @param parentId
     *            parent's ID to build the tree
     * @param name
     *            entry's name
     * @param startTime
     *            entry's start time
     * @param endTime
     *            entry's end time
     * @param entryType
     *            The type of this entry
     * @param pid
     *            entry's PID or TID if is a thread
     */
    public FlameChartEntryModel(long id, long parentId, String name, long startTime, long endTime, EntryType entryType, int pid) {
        super(id, parentId, name, startTime, endTime);
        fEntryType = entryType;
        fPid = pid;
        fCallStackElement = null;
        fIsSymbolKey = false;
    }

    /**
     * Constructor
     *
     * @param elementId
     *            unique ID for this {@link FlameChartEntryModel}
     * @param parentId
     *            parent's ID to build the tree
     * @param element
     *            the callstack element corresponding to this entry
     * @param name
     *            entry's name
     * @param startTime
     *            entry's start time
     * @param endTime
     *            entry's end time
     * @param entryType
     *            The type of this entry
     * @param pid
     *            entry's PID or TID if is a thread
     */
    public FlameChartEntryModel(long elementId, long parentId, ICallStackElement element, String name, long startTime, long endTime, EntryType entryType, int pid) {
        super(elementId, parentId, name, startTime, endTime);
        fEntryType = entryType;
        fPid = pid;
        fCallStackElement = element;
        fIsSymbolKey = element.isSymbolKeyElement();
    }

    /**
     * Getter for the entry type
     *
     * @return The type of entry.
     */
    public EntryType getEntryType() {
        return fEntryType;
    }

    /**
     * Get the ID of the process running at time
     *
     * @param time
     *            The time for which we want the pid
     * @return the process running at the requested time or
     *         {@link IHostModel#UNKNOWN_TID} if not found
     */
    public int getPid(long time) {
        ICallStackElement callStackElement = fCallStackElement;
        if ((callStackElement instanceof InstrumentedCallStackElement) && callStackElement.isLeaf()) {
            return ((InstrumentedCallStackElement) callStackElement).getCallStack().getSymbolKeyAt(time);
        }
        return IHostModel.UNKNOWN_TID;
    }

    /**
     * Get the ID of the thread running at time
     *
     * @param time
     *            The time for which we want the tid
     * @return the thread running at the requested time or
     *         {@link IHostModel#UNKNOWN_TID} if not found
     */
    public int getTid(long time) {
        ICallStackElement callStackElement = fCallStackElement;
        if ((callStackElement instanceof InstrumentedCallStackElement) && callStackElement.isLeaf()) {
            return ((InstrumentedCallStackElement) callStackElement).getCallStack().getThreadId(time);
        }
        return IHostModel.UNKNOWN_TID;
    }

    /**
     * Return whether this entry model represents a element that is a symbol key
     *
     * @return <code>true</code> if the element is the key to symbol resolution
     */
    public boolean isSymbolKeyGroup() {
        return fIsSymbolKey;
    }

    @Override
    public boolean equals(@Nullable Object obj) {
        if (!super.equals(obj)) {
            // nullness, class, name, ids
            return false;
        }
        if (!(obj instanceof FlameChartEntryModel)) {
            return false;
        }
        FlameChartEntryModel other = (FlameChartEntryModel) obj;
        return fEntryType == other.fEntryType && fPid == other.fPid;
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), fEntryType, fPid);
    }

    @Override
    public String toString() {
        return super.toString() + ' ' + getType();
    }

    private String getType() {
        switch (fEntryType) {
        case TRACE:
            return "TRACE"; //$NON-NLS-1$
        case LEVEL:
            return "LEVEL"; //$NON-NLS-1$
        case KERNEL:
            return "KERNEL"; //$NON-NLS-1$
        case FUNCTION:
            // The default
        default:
            return "FUNCTION"; //$NON-NLS-1$
        }
    }

}
