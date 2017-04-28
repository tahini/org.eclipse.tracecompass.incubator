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

import java.util.Comparator;
import java.util.List;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.tracecompass.incubator.internal.virtual.machine.analysis.core.trace.VirtualMachineExperiment;
import org.eclipse.tracecompass.incubator.internal.virtual.machine.analysis.ui.views.vcpuview.VirtualMachineCommon.Type;
import org.eclipse.tracecompass.tmf.ui.widgets.timegraph.model.ITimeEvent;
import org.eclipse.tracecompass.tmf.ui.widgets.timegraph.model.ITimeGraphEntry;
import org.eclipse.tracecompass.tmf.ui.widgets.timegraph.model.NullTimeEvent;
import org.eclipse.tracecompass.tmf.ui.widgets.timegraph.model.TimeGraphEntry;


/**
 * An entry, or row, in the resource view
 *
 * @author Mohamad Gebai
 */
public class VmPreemptEntry extends TimeGraphEntry {

    private static final Comparator<ITimeGraphEntry> COMPARATOR = new Comparator<ITimeGraphEntry>() {

        @Override
        public int compare(ITimeGraphEntry o1, ITimeGraphEntry o2) {

            int result = 0;

            if ((o1 instanceof VmPreemptEntry) && (o2 instanceof VmPreemptEntry)) {
                VmPreemptEntry entry1 = (VmPreemptEntry) o1;
                VmPreemptEntry entry2 = (VmPreemptEntry) o2;
                result = entry1.getType().compareTo(entry2.getType());
                if (result == 0) {
                    result = entry1.getId().compareTo(entry2.getId());
                }
            }
            return result;
        }
    };

//    /** Type of resource */
//    public static enum Type {
//        /** Null resources (filler rows, etc.) */
//        NULL,
//        /** Entries for VMs */
//        VM,
//        /** Entries for VCPUs */
//        VCPU,
//        /** Entries for Threads */
//        THREAD
//    }

    private static int sOrder = 0;
    private final String fId;
    private final String fVmName;
    private final VirtualMachineExperiment fTrace;
    private final Type fType;
    private final int fQuark;
    private final int fOrder;
    private long fExecutionTime;

    /**
     * Constructor
     *
     * @param quark
     *            The attribute quark matching the entry
     * @param trace
     *            The trace on which we are working
     * @param name
     *            The exec_name of this entry
     * @param vmName
     *            The VM name
     * @param startTime
     *            The start time of this entry lifetime
     * @param endTime
     *            The end time of this entry
     * @param type
     *            The type of this entry
     * @param id
     *            The id of this entry
     */
    public VmPreemptEntry(int quark, VirtualMachineExperiment trace, String name, String vmName, long startTime, long endTime, Type type, String id) {
        super(name, startTime, endTime);
        fId = id;
        fTrace = trace;
        fType = type;
        fQuark = quark;
        fVmName = vmName;
        fOrder = sOrder++;
        fExecutionTime = 0;
        sortChildren(COMPARATOR);
    }

    /**
     * Constructor
     *
     * @param trace
     *            The trace on which we are working
     * @param name
     *            The exec_name of this entry
     * @param startTime
     *            The start time of this entry lifetime
     * @param endTime
     *            The end time of this entry
     * @param id
     *            The id of this entry
     */
    public VmPreemptEntry(VirtualMachineExperiment trace, String name, long startTime, long endTime, String id) {
        this(-1, trace, name, name, startTime, endTime, Type.NULL, id);
    }

    /**
     * Constructor
     *
     * @param quark
     *            The attribute quark matching the entry
     * @param trace
     *            The trace on which we are working
     * @param startTime
     *            The start time of this entry lifetime
     * @param endTime
     *            The end time of this entry
     * @param type
     *            The type of this entry
     * @param id
     *            The id of this entry
     */
    public VmPreemptEntry(int quark, VirtualMachineExperiment trace, String vmName, long startTime, long endTime, Type type, String id) {
        this(quark, trace, type.toString() + " " + id, vmName, startTime, endTime, type, id); //$NON-NLS-1$
    }

    /**
     * Get the entry's id
     *
     * @return the entry's id
     */
    public String getId() {
        return fId;
    }

    public String getVmName() {
        return fVmName;
    }

    public int getOrder() {
        return fOrder;
    }

    public long getExecutionTime() {
        return fExecutionTime;
    }

    public void addExecutionTime(long duration) {
        fExecutionTime += duration;
    }


    @NonNullByDefault({})
    @Override
    public void setEventList(List<ITimeEvent> eventList) {
        super.setEventList(eventList);
        fExecutionTime = 0;
        if(eventList != null) {
            for(ITimeEvent te: eventList) {
                if(!(te instanceof NullTimeEvent)) {
                    addExecutionTime(te.getDuration());
                }
            }
        }
    }

    @Override
    public void addEvent(@Nullable ITimeEvent event) {
        super.addEvent(event);
        if(event != null && !(event instanceof NullTimeEvent)) {
            addExecutionTime(event.getDuration());
        }
    }


    /**
     * Get the entry's kernel trace
     *
     * @return the entry's kernel trace
     */
    public VirtualMachineExperiment getTrace() {
        return fTrace;
    }

    /**
     * Get the entry Type of this entry. Uses the inner Type enum.
     *
     * @return The entry type
     */
    public Type getType() {
        return fType;
    }

    /**
     * Retrieve the attribute quark that's represented by this entry.
     *
     * @return The integer quark The attribute quark matching the entry
     */
    public int getQuark() {
        return fQuark;
    }

    @Override
    public boolean hasTimeEvents() {
        if (fType == Type.NULL) {
            return false;
        }
        return true;
    }

//    /**
//     * Add a child to this entry of type ResourcesEntry
//     *
//     * @param entry
//     *            The entry to add
//     */
//    public void addChild(VmPreemptEntry entry) {
//        int index;
//        for (index = 0; index < getChildren().size(); index++) {
//            VmPreemptEntry other = (VmPreemptEntry) getChildren().get(index);
//            if (entry.getType().compareTo(other.getType()) < 0) {
//                break;
//            } else if (entry.getType().equals(other.getType())) {
//                if (entry.getId().compareTo(other.getId()) < 0) {
//                    break;
//                }
//            }
//        }
//
//        entry.setParent(this);
//        getChildren().add(index, entry);
//    }

}
