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

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.RGB;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.tracecompass.incubator.internal.virtual.machine.analysis.core.data.VcpuStateValues;
import org.eclipse.tracecompass.incubator.internal.virtual.machine.analysis.core.module.StateValues;
import org.eclipse.tracecompass.incubator.internal.virtual.machine.analysis.ui.views.vcpuview.VirtualMachineCommon.Type;
import org.eclipse.tracecompass.incubator.internal.virtual.machine.analysis.ui.views.vcpuview.VirtualMachineViewEntry;
import org.eclipse.tracecompass.tmf.ui.widgets.timegraph.StateItem;
import org.eclipse.tracecompass.tmf.ui.widgets.timegraph.TimeGraphPresentationProvider;
import org.eclipse.tracecompass.tmf.ui.widgets.timegraph.model.ITimeEvent;
import org.eclipse.tracecompass.tmf.ui.widgets.timegraph.model.NullTimeEvent;
import org.eclipse.tracecompass.tmf.ui.widgets.timegraph.model.TimeEvent;

/**
 * Presentation provider for the Virtual Machine view, based on the generic TMF
 * presentation provider.
 *
 * @author Mohamad Gebai
 */
public class VMPreemptPresentationProvider extends TimeGraphPresentationProvider {

    private enum State {
        UNKNOWN(new RGB(100, 100, 100)),
        IDLE(new RGB(200, 200, 200)),
        USERMODE(new RGB(0, 200, 0)),
        WAIT_VMM(new RGB(200, 0, 0)),
        VCPU_PREEMPTED(new RGB(120, 40, 90)),
        THREAD_UNKNOWN(new RGB(100, 100, 100)),
        THREAD_WAIT_BLOCKED(new RGB(200, 200, 0)),
        THREAD_WAIT_FOR_CPU(new RGB(200, 100, 0)),
        THREAD_USERMODE(new RGB(0, 200, 0)),
        THREAD_SYSCALL(new RGB(0, 0, 200)),
        THREAD_INTERRUPTED(new RGB(200, 0, 100));

        private final RGB fRgb;

        private State(RGB rgb) {
            fRgb = rgb;
        }
    }

    /**
     * Default constructor
     */
    public VMPreemptPresentationProvider() {
        super();
    }

    private static State[] getStateValues() {
        return State.values();
    }

    private static State getStateForVcpu(int value) {
        if ((value & VcpuStateValues.VCPU_PREEMPT) > 0) {
            return State.VCPU_PREEMPTED;
        } else if ((value & VcpuStateValues.VCPU_VMM) > 0) {
            return State.WAIT_VMM;
        } else if (value == 2) {
            return State.USERMODE;
        } else if (value == 1) {
            return State.IDLE;
        } else {
            return State.UNKNOWN;
        }
    }

    private static @Nullable State getStateForThread(int value) {
        if (value == VcpuStateValues.VCPU_PREEMPT) {
            return null;
        }
        switch (value) {
        case StateValues.PROCESS_STATUS_RUN_USERMODE:
            return State.THREAD_USERMODE;
        case StateValues.PROCESS_STATUS_RUN_SYSCALL:
            return State.THREAD_SYSCALL;
        case StateValues.PROCESS_STATUS_WAIT_FOR_CPU:
            return State.THREAD_WAIT_FOR_CPU;
        case StateValues.PROCESS_STATUS_WAIT_BLOCKED:
            return State.THREAD_WAIT_BLOCKED;
        case StateValues.PROCESS_STATUS_INTERRUPTED:
            return State.THREAD_INTERRUPTED;
        case StateValues.PROCESS_STATUS_UNKNOWN:
        case StateValues.PROCESS_STATUS_WAIT_UNKNOWN:
            return State.THREAD_UNKNOWN;
        default:
            return null;
        }
    }

    private static @Nullable State getEventState(TimeEvent event) {
        if (event.hasValue()) {
            VirtualMachineViewEntry entry = (VirtualMachineViewEntry) event.getEntry();
            int value = event.getValue();

            if (entry.getType() == Type.VCPU) {
                return getStateForVcpu(value);
            } else if (entry.getType() == Type.THREAD) {
                return getStateForThread(value);
            }
        }
        return null;
    }

    @Override
    public int getStateTableIndex(@Nullable ITimeEvent event) {
        if (event == null) {
            return TRANSPARENT;
        }
        State state = getEventState((TimeEvent) event);
        if (state != null) {
            return state.ordinal();
        }
        if (event instanceof NullTimeEvent) {
            return INVISIBLE;
        }
        return TRANSPARENT;
    }

    @Override
    public StateItem[] getStateTable() {
        State[] states = getStateValues();
        StateItem[] stateTable = new StateItem[states.length];
        for (int i = 0; i < stateTable.length; i++) {
            State state = states[i];
            stateTable[i] = new StateItem(state.fRgb, state.toString());
        }
        return stateTable;
    }

    @Override
    public @Nullable String getEventName(@Nullable ITimeEvent event) {
        if (event == null) {
            return null;
        }
        State state = getEventState((TimeEvent) event);
        if (state != null) {
            return state.toString();
        }
        if (event instanceof NullTimeEvent) {
            return null;
        }
        return Messages.VmView_multipleStates;
    }

//    @Override
//    public Map<String, String> getEventHoverToolTipInfo(ITimeEvent event, long hoverTime) {
//
//        Map<String, String> retMap = new LinkedHashMap<>();
//        if (event instanceof TimeEvent && ((TimeEvent) event).hasValue()) {
//
//            TimeEvent tcEvent = (TimeEvent) event;
//            VirtualMachineEntry entry = (VirtualMachineEntry) event.getEntry();
//
//            try {
//                if (tcEvent.hasValue()) {
//                    // Check for type CPU
//                    if (entry.getType().equals(Type.VCPU)) {
//                        int status = tcEvent.getValue();
//
//                        if (status == StateValues.CPU_STATUS_IRQ) {
//                            // In IRQ state get the IRQ that caused the interruption
//                            ITmfStateSystem ss = entry.getTrace().getStateSystems().get(VirtualMachineExperiment.STATE_ID);
//                            int cpu = Integer.parseInt(entry.getId());
//
//                            List<ITmfStateInterval> fullState = ss.queryFullState(event.getTime());
//                            List<Integer> irqQuarks = ss.getQuarks(Attributes.RESOURCES, Attributes.IRQS, "*"); //$NON-NLS-1$
//
//                            for (int irqQuark : irqQuarks) {
//                                if (fullState.get(irqQuark).getStateValue().unboxInt() == cpu) {
//                                    ITmfStateInterval value = ss.querySingleState(event.getTime(), irqQuark);
//                                    if (!value.getStateValue().isNull()) {
//                                        int irq = Integer.parseInt(ss.getAttributeName(irqQuark));
//                                        retMap.put(Messages.ResourcesView_attributeIrqName, String.valueOf(irq));
//                                    }
//                                    break;
//                                }
//                            }
//
//                        } else if (status == StateValues.CPU_STATUS_SOFTIRQ) {
//                            // In SOFT_IRQ state get the SOFT_IRQ that caused the interruption
//                            ITmfStateSystem ss = entry.getTrace().getStateSystems().get(VirtualMachineExperiment.STATE_ID);
//                            int cpu = Integer.parseInt(entry.getId());
//
//                            List<ITmfStateInterval> fullState = ss.queryFullState(event.getTime());
//                            List<Integer> softIrqQuarks = ss.getQuarks(Attributes.RESOURCES, Attributes.SOFT_IRQS, "*"); //$NON-NLS-1$
//
//                            for (int softIrqQuark : softIrqQuarks) {
//                                if (fullState.get(softIrqQuark).getStateValue().unboxInt() == cpu) {
//                                    ITmfStateInterval value = ss.querySingleState(event.getTime(), softIrqQuark);
//                                    if (!value.getStateValue().isNull()) {
//                                        int softIrq = Integer.parseInt(ss.getAttributeName(softIrqQuark));
//                                        retMap.put(Messages.ResourcesView_attributeSoftIrqName, String.valueOf(softIrq));
//                                    }
//                                    break;
//                                }
//                            }
//
//                        } else if (status == StateValues.CPU_STATUS_RUN_USERMODE || status == StateValues.CPU_STATUS_RUN_SYSCALL) {
//                            // In running state get the current tid
//                            ITmfStateSystem ssq = entry.getTrace().getStateSystems().get(VirtualMachineExperiment.STATE_ID);
//
//
//                            retMap.put(Messages.ResourcesView_attributeHoverTime, Utils.formatTime(hoverTime, TimeFormat.CALENDAR, Resolution.NANOSEC));
//                            int cpuQuark = entry.getQuark();
//                            int currentThreadQuark = ssq.getQuarkRelative(cpuQuark, Attributes.CURRENT_THREAD);
//                            ITmfStateInterval interval = ssq.querySingleState(hoverTime, currentThreadQuark);
//                            if (!interval.getStateValue().isNull()) {
//                                ITmfStateValue value = interval.getStateValue();
//                                int currentThreadId = value.unboxInt();
//                                retMap.put(Messages.ResourcesView_attributeTidName, Integer.toString(currentThreadId));
//                                int execNameQuark = ssq.getQuarkAbsolute(Attributes.THREADS, Integer.toString(currentThreadId), Attributes.EXEC_NAME);
//                                interval = ssq.querySingleState(hoverTime, execNameQuark);
//                                if (!interval.getStateValue().isNull()) {
//                                    value = interval.getStateValue();
//                                    retMap.put(Messages.ResourcesView_attributeProcessName, value.unboxStr());
//                                }
//                                if (status == StateValues.CPU_STATUS_RUN_SYSCALL) {
//                                    int syscallQuark = ssq.getQuarkAbsolute(Attributes.THREADS, Integer.toString(currentThreadId), Attributes.SYSTEM_CALL);
//                                    interval = ssq.querySingleState(hoverTime, syscallQuark);
//                                    if (!interval.getStateValue().isNull()) {
//                                        value = interval.getStateValue();
//                                        retMap.put(Messages.ResourcesView_attributeSyscallName, value.unboxStr());
//                                    }
//                                }
//                            }
//                        }
//                    }
//                }
//
//            } catch (AttributeNotFoundException e) {
//                e.printStackTrace();
//            } catch (TimeRangeException e) {
//                e.printStackTrace();
//            } catch (StateValueTypeException e) {
//                e.printStackTrace();
//            } catch (StateSystemDisposedException e) {
//                /* Ignored */
//            }
//
//        }

//        return retMap;
//    }

    @NonNullByDefault({})
    @Override
    public void postDrawEvent(ITimeEvent event, Rectangle bounds, GC gc) {
//        ITmfTimeGraphDrawingHelper drawingHelper = getDrawingHelper();
//        if (bounds.width <= gc.getFontMetrics().getAverageCharWidth()) {
//            return;
//        }
//
//        if (!(event instanceof TimeEvent)) {
//            return;
//        }
//        TimeEvent tcEvent = (TimeEvent) event;
//        if (!tcEvent.hasValue()) {
//            return;
//        }
//
//        VirtualMachineEntry entry = (VirtualMachineEntry) event.getEntry();
//        if ( !(entry.getType().equals(Type.VCPU) || entry.getType().equals(Type.THREAD)) ) {
//            return;
//        }
//
//        int status = tcEvent.getValue();
//        if (status != StateValues.CPU_STATUS_RUN_USERMODE && status != StateValues.CPU_STATUS_RUN_SYSCALL && status != StateValues.VCPU_PREEMPT && status != StateValues.VCPU_VMM) {
//            return;
//        }
//
//        TmfStateSystemAnalysisModule module = entry.getTrace().getAnalysisModuleOfClass(VirtualMachineStateSystemModule.class, VirtualMachineStateSystemModule.ID);
//        ITmfStateSystem ss = module.getStateSystem();
//        long time = event.getTime();
//        try {
//            if(entry.getType().equals(Type.VCPU)) {
//                while (time < event.getTime() + event.getDuration()) {
//                    int vmQuark = entry.getQuark();
//                    int currentThreadQuark = ss.getQuarkRelative(vmQuark, Attributes.CPUS, entry.getId(), Attributes.CURRENT_THREAD);
//                    ITmfStateInterval tidInterval = ss.querySingleState(time, currentThreadQuark);
//                    if (!tidInterval.getStateValue().isNull()) {
//                        ITmfStateValue value = tidInterval.getStateValue();
//                        int currentThreadId = value.unboxInt();
//
//                        if (status == StateValues.CPU_STATUS_RUN_USERMODE && currentThreadId != fLastThreadId) {
//                            int execNameQuark = ss.getQuarkRelative(vmQuark, /*Attributes.CPUS, entry.getId(),*/ Attributes.THREADS, Integer.toString(currentThreadId), Attributes.EXEC_NAME);
//                            ITmfStateInterval interval = ss.querySingleState(time, execNameQuark);
//                            if (!interval.getStateValue().isNull()) {
//                                value = interval.getStateValue();
//                                gc.setForeground(gc.getDevice().getSystemColor(SWT.COLOR_WHITE));
//                                long startTime = Math.max(tidInterval.getStartTime(), event.getTime());
//                                long endTime = Math.min(tidInterval.getEndTime() + 1, event.getTime() + event.getDuration());
//                                if (drawingHelper.getXForTime(endTime) > bounds.x) {
//                                    int x = Math.max(drawingHelper.getXForTime(startTime), bounds.x);
//                                    int width = Math.min(drawingHelper.getXForTime(endTime), bounds.x + bounds.width) - x;
//                                    int drawn = Utils.drawText(gc, value.unboxStr(), x + 1, bounds.y - 2, width - 1, true, true);
//                                    if (drawn > 0) {
//                                        fLastThreadId = currentThreadId;
//                                    }
//                                }
//                            }
//                        } else if (status == StateValues.CPU_STATUS_RUN_SYSCALL) {
//                            int syscallQuark = ss.getQuarkRelative(vmQuark, /*Attributes.CPUS, entry.getId(),*/ Attributes.THREADS, Integer.toString(currentThreadId), Attributes.SYSTEM_CALL);
//                            ITmfStateInterval interval = ss.querySingleState(time, syscallQuark);
//                            if (!interval.getStateValue().isNull()) {
//                                value = interval.getStateValue();
//                                gc.setForeground(gc.getDevice().getSystemColor(SWT.COLOR_WHITE));
//                                long startTime = Math.max(tidInterval.getStartTime(), event.getTime());
//                                long endTime = Math.min(tidInterval.getEndTime() + 1, event.getTime() + event.getDuration());
//                                if (drawingHelper.getXForTime(endTime) > bounds.x) {
//                                    int x = Math.max(drawingHelper.getXForTime(startTime), bounds.x);
//                                    int width = Math.min(drawingHelper.getXForTime(endTime), bounds.x + bounds.width) - x;
//                                    Utils.drawText(gc, value.unboxStr().substring(4), x + 1, bounds.y - 2, width - 1, true, true);
//                                }
//                            }
//                        } else if (status == StateValues.VCPU_VMM) {
//                            // add kvm exit reason
//                        }
//
//                    }
//                    time = tidInterval.getEndTime() + 1;
//                    if (time < event.getTime() + event.getDuration()) {
//                        int x = drawingHelper.getXForTime(time);
//                        if (x >= bounds.x) {
//                            gc.setForeground(gc.getDevice().getSystemColor(SWT.COLOR_GRAY));
//                            gc.drawLine(x, bounds.y + 1, x, bounds.y + bounds.height - 2);
//                        }
//                    }
//                }
//            }
//            else if (entry.getType().equals(Type.THREAD)) {
////                while (time < event.getTime() + event.getDuration()) {
//                    int threadQuark = entry.getQuark();
//                    int nameQuark = ss.getQuarkRelative(threadQuark, Attributes.EXEC_NAME);
//                    ITmfStateInterval nameInterval = ss.querySingleState(time, nameQuark);
//                    if (!nameInterval.getStateValue().isNull()) {
//                        ITmfStateValue value = nameInterval.getStateValue();
////                        int currentThreadId = value.unboxInt();
//
////                        if (status == StateValues.CPU_STATUS_RUN_SYSCALL) {
////                            int syscallQuark = ss.getQuarkRelative(vmQuark, Attributes.THREADS, Integer.toString(currentThreadId), Attributes.SYSTEM_CALL);
////                            ITmfStateInterval interval = ss.querySingleState(time, syscallQuark);
////                            if (!interval.getStateValue().isNull()) {
////                                value = interval.getStateValue();
//                                if(!value.unboxStr().equals(fLastThreadName)) {
//                                    gc.setForeground(gc.getDevice().getSystemColor(SWT.COLOR_WHITE));
//                                    long startTime = Math.max(nameInterval.getStartTime(), event.getTime());
//                                    long endTime = Math.min(nameInterval.getEndTime() + 1, event.getTime() + event.getDuration());
//                                    if (drawingHelper.getXForTime(endTime) > bounds.x) {
//                                        int x = Math.max(drawingHelper.getXForTime(startTime), bounds.x);
//                                        int width = Math.min(drawingHelper.getXForTime(endTime), bounds.x + bounds.width) - x;
//                                        Utils.drawText(gc, value.unboxStr(), x + 1, bounds.y - 2, width - 1, true, true);
//                                    }
//                                    fLastThreadName = value.unboxStr();
//                                }
////                            }
////                        }
//                    }
//                    time = nameInterval.getEndTime() + 1;
//                    if (time < event.getTime() + event.getDuration()) {
//                        int x = drawingHelper.getXForTime(time);
//                        if (x >= bounds.x) {
//                            gc.setForeground(gc.getDevice().getSystemColor(SWT.COLOR_GRAY));
//                            gc.drawLine(x, bounds.y + 1, x, bounds.y + bounds.height - 2);
//                        }
//                    }
////                }
//            }
//        } catch (AttributeNotFoundException e) {
//            e.printStackTrace();
//        } catch (TimeRangeException e) {
//            e.printStackTrace();
//        } catch (StateValueTypeException e) {
//            e.printStackTrace();
//        } catch (StateSystemDisposedException e) {
//            /* Ignored */
//        }
    }

//    @Override
//    public void postDrawEntry(ITimeGraphEntry entry, Rectangle bounds, GC gc) {
//    }
}