/*******************************************************************************
 * Copyright (c) 2012, 2015 Ericsson, École Polytechnique de Montréal
 *
 * All rights reserved. This program and the accompanying materials are
 * made available under the terms of the Eclipse Public License v1.0 which
 * accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *   Patrick Tasse - Initial API and implementation
 *   Geneviève Bastien - Move code to provide base classes for time graph view
 *******************************************************************************/

package org.eclipse.tracecompass.incubator.internal.os.linux.analysis.ui.views.controlflow;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.RGB;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.widgets.Display;
import org.eclipse.tracecompass.analysis.os.linux.core.kernel.KernelAnalysisModule;
import org.eclipse.tracecompass.analysis.os.linux.core.kernel.StateValues;
import org.eclipse.tracecompass.analysis.os.linux.core.trace.IKernelAnalysisEventLayout;
import org.eclipse.tracecompass.analysis.os.linux.core.trace.IKernelTrace;
import org.eclipse.tracecompass.incubator.internal.os.linux.analysis.core.Activator;
import org.eclipse.tracecompass.internal.analysis.os.linux.core.kernel.Attributes;
import org.eclipse.tracecompass.internal.analysis.os.linux.ui.registry.LinuxStyle;
import org.eclipse.tracecompass.statesystem.core.ITmfStateSystem;
import org.eclipse.tracecompass.statesystem.core.exceptions.AttributeNotFoundException;
import org.eclipse.tracecompass.statesystem.core.exceptions.StateSystemDisposedException;
import org.eclipse.tracecompass.statesystem.core.exceptions.StateValueTypeException;
import org.eclipse.tracecompass.statesystem.core.exceptions.TimeRangeException;
import org.eclipse.tracecompass.statesystem.core.interval.ITmfStateInterval;
import org.eclipse.tracecompass.statesystem.core.statevalue.ITmfStateValue;
import org.eclipse.tracecompass.tmf.core.statesystem.TmfStateSystemAnalysisModule;
import org.eclipse.tracecompass.tmf.core.trace.ITmfTrace;
import org.eclipse.tracecompass.tmf.ui.widgets.timegraph.StateItem;
import org.eclipse.tracecompass.tmf.ui.widgets.timegraph.TimeGraphPresentationProvider;
import org.eclipse.tracecompass.tmf.ui.widgets.timegraph.model.ITimeEvent;
import org.eclipse.tracecompass.tmf.ui.widgets.timegraph.model.ITimeEventStyleStrings;
import org.eclipse.tracecompass.tmf.ui.widgets.timegraph.model.NullTimeEvent;
import org.eclipse.tracecompass.tmf.ui.widgets.timegraph.model.TimeEvent;
import org.eclipse.tracecompass.tmf.ui.widgets.timegraph.widgets.Utils;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

/**
 * Presentation provider for the control flow view
 */
@SuppressWarnings("restriction")
public class ControlFlowPresentationProvider extends TimeGraphPresentationProvider {

    private static final Map<Integer, StateItem> STATE_MAP;
    private static final List<StateItem> STATE_LIST;
    private static final long[] COLOR_SEED = { 0x0000ff, 0xff0000, 0x00ff00,
            0xff00ff, 0x00ffff, 0xffff00, 0x000000, 0xf07300
    };

    private static final int COLOR_MASK = 0xffffff;

    private static StateItem createState(LinuxStyle style) {
        int rgbInt = (int) style.toMap().getOrDefault(ITimeEventStyleStrings.fillColor(), 0);
        RGB color = new RGB(rgbInt >> 24 & 0xff, rgbInt >> 16 & 0xff, rgbInt >> 8 & 0xff);
        return new StateItem(color, style.getLabel()) {
            @Override
            public Map<String, Object> getStyleMap() {
                return style.toMap();
            }
        };
    }

    private final Map<Integer, StateItem> fFutexStateMap = new HashMap<>();
    private final Map<Long, Integer> fFutexIdMap = new HashMap<>();
    private final List<StateItem> fStateList = new ArrayList<>();
    private @NonNull StateItem[] fStateTable;
    private static class FutexStateItem extends StateItem {
        public FutexStateItem(RGB rgb){
            super(ImmutableMap.of(ITimeEventStyleStrings.fillColor(), rgb.red << 24 | rgb.green << 16 | rgb.blue << 8 | 0xab));
        }
    }

    static {
        ImmutableMap.Builder<Integer, StateItem> builder = new ImmutableMap.Builder<>();
        /*
         * ADD STATE MAPPING HERE
         */
        builder.put(StateValues.PROCESS_STATUS_UNKNOWN, createState(LinuxStyle.UNKNOWN));
        builder.put(StateValues.PROCESS_STATUS_RUN_USERMODE, createState(LinuxStyle.USERMODE));
        builder.put(StateValues.PROCESS_STATUS_RUN_SYSCALL, createState(LinuxStyle.SYSCALL));
        builder.put(StateValues.PROCESS_STATUS_INTERRUPTED, createState(LinuxStyle.INTERRUPTED));
        builder.put(StateValues.PROCESS_STATUS_WAIT_BLOCKED, createState(LinuxStyle.WAIT_BLOCKED));
        builder.put(StateValues.PROCESS_STATUS_WAIT_FOR_CPU, createState(LinuxStyle.WAIT_FOR_CPU));
        builder.put(StateValues.PROCESS_STATUS_WAIT_UNKNOWN, createState(LinuxStyle.WAIT_UNKNOWN));
        /*
         * DO NOT MODIFY AFTER
         */
        STATE_MAP = builder.build();
        STATE_LIST = ImmutableList.copyOf(STATE_MAP.values());
    }

    /**
     * Average width of the characters used for state labels. Is computed in the
     * first call to postDrawEvent(). Is null before that.
     */
    private Integer fAverageCharacterWidth = null;

    /**
     * Default constructor
     */
    public ControlFlowPresentationProvider() {
        super(PackageMessages.ControlFlowView_stateTypeName);
        fStateList.addAll(STATE_LIST);
        fStateTable = fStateList.toArray(new StateItem[fStateList.size()]);
    }

    @Override
    public StateItem[] getStateTable() {
        return fStateTable;
    }

    @Override
    public int getStateTableIndex(ITimeEvent event) {
        if (event instanceof TimeEvent && ((TimeEvent) event).hasValue()) {
            int status = ((TimeEvent) event).getValue();
            return fStateList.indexOf(getMatchingState(status));
        }
        if (event instanceof NullTimeEvent) {
            return INVISIBLE;
        }
        return TRANSPARENT;
    }

    @Override
    public String getEventName(ITimeEvent event) {
        if (event instanceof TimeEvent) {
            TimeEvent ev = (TimeEvent) event;
            if (ev.hasValue()) {
                return getMatchingState(ev.getValue()).getStateString();
            }
        }
        return PackageMessages.ControlFlowView_multipleStates;
    }

    private StateItem getMatchingState(int status) {
        if (status >= 10000) {
            return fFutexStateMap.getOrDefault(status, STATE_MAP.get(StateValues.PROCESS_STATUS_WAIT_UNKNOWN));
        }
        return STATE_MAP.getOrDefault(status, STATE_MAP.get(StateValues.PROCESS_STATUS_WAIT_UNKNOWN));
    }

    @Override
    public Map<String, String> getEventHoverToolTipInfo(ITimeEvent event) {
        Map<String, String> retMap = new LinkedHashMap<>();
        if (!(event instanceof TimeEvent) || !((TimeEvent) event).hasValue() ||
                !(event.getEntry() instanceof ControlFlowEntry)) {
            return retMap;
        }
        ControlFlowEntry entry = (ControlFlowEntry) event.getEntry();
        ITmfStateSystem ssq = TmfStateSystemAnalysisModule.getStateSystem(entry.getTrace(), KernelAnalysisModule.ID);
        if (ssq == null) {
            return retMap;
        }
        int tid = entry.getThreadId();

        try {
            // Find every CPU first, then get the current thread
            int cpusQuark = ssq.getQuarkAbsolute(Attributes.CPUS);
            List<Integer> cpuQuarks = ssq.getSubAttributes(cpusQuark, false);
            for (Integer cpuQuark : cpuQuarks) {
                int currentThreadQuark = ssq.getQuarkRelative(cpuQuark, Attributes.CURRENT_THREAD);
                ITmfStateInterval interval = ssq.querySingleState(event.getTime(), currentThreadQuark);
                if (!interval.getStateValue().isNull()) {
                    ITmfStateValue state = interval.getStateValue();
                    int currentThreadId = state.unboxInt();
                    if (tid == currentThreadId) {
                        retMap.put(PackageMessages.ControlFlowView_attributeCpuName, ssq.getAttributeName(cpuQuark));
                        break;
                    }
                }
            }

        } catch (AttributeNotFoundException | TimeRangeException | StateValueTypeException e) {
            Activator.getInstance().logError("Error in ControlFlowPresentationProvider", e); //$NON-NLS-1$
        } catch (StateSystemDisposedException e) {
            /* Ignored */
        }
        int status = ((TimeEvent) event).getValue();
        if (status == StateValues.PROCESS_STATUS_RUN_SYSCALL) {
            int syscallQuark = ssq.optQuarkRelative(entry.getThreadQuark(), Attributes.SYSTEM_CALL);
            if (syscallQuark == ITmfStateSystem.INVALID_ATTRIBUTE) {
                return retMap;
            }
            try {
                ITmfStateInterval value = ssq.querySingleState(event.getTime(), syscallQuark);
                if (!value.getStateValue().isNull()) {
                    ITmfStateValue state = value.getStateValue();
                    retMap.put(PackageMessages.ControlFlowView_attributeSyscallName, state.toString());
                }

            } catch (TimeRangeException e) {
                Activator.getInstance().logError("Error in ControlFlowPresentationProvider", e); //$NON-NLS-1$
            } catch (StateSystemDisposedException e) {
                /* Ignored */
            }
        }

        return retMap;
    }

    @Override
    public void postDrawEvent(ITimeEvent event, Rectangle bounds, GC gc) {
        if (fAverageCharacterWidth == null) {
            fAverageCharacterWidth = gc.getFontMetrics().getAverageCharWidth();
        }
        if (bounds.width <= fAverageCharacterWidth) {
            return;
        }
        if (!(event instanceof TimeEvent)) {
            return;
        }
        ControlFlowEntry entry = (ControlFlowEntry) event.getEntry();
        ITmfStateSystem ss = TmfStateSystemAnalysisModule.getStateSystem(entry.getTrace(), KernelAnalysisModule.ID);
        if (ss == null) {
            return;
        }
        int status = ((TimeEvent) event).getValue();

        if (status != StateValues.PROCESS_STATUS_RUN_SYSCALL) {
            return;
        }
        int syscallQuark = ss.optQuarkRelative(entry.getThreadQuark(), Attributes.SYSTEM_CALL);
        if (syscallQuark == ITmfStateSystem.INVALID_ATTRIBUTE) {
            return;
        }
        try {
            ITmfStateInterval value = ss.querySingleState(event.getTime(), syscallQuark);
            if (!value.getStateValue().isNull()) {
                ITmfStateValue state = value.getStateValue();
                gc.setForeground(gc.getDevice().getSystemColor(SWT.COLOR_WHITE));

                /*
                 * Remove the "sys_" or "syscall_entry_" or similar from what we
                 * draw in the rectangle. This depends on the trace's event
                 * layout.
                 */
                int beginIndex = 0;
                ITmfTrace trace = entry.getTrace();
                if (trace instanceof IKernelTrace) {
                    IKernelAnalysisEventLayout layout = ((IKernelTrace) trace).getKernelEventLayout();
                    beginIndex = layout.eventSyscallEntryPrefix().length();
                }

                Utils.drawText(gc, state.toString().substring(beginIndex), bounds.x, bounds.y, bounds.width, bounds.height, true, true);
            }
        } catch (TimeRangeException e) {
            Activator.getInstance().logError("Error in ControlFlowPresentationProvider", e); //$NON-NLS-1$
        } catch (StateSystemDisposedException e) {
            /* Ignored */
        }
    }

    /**
     * Get the state ID for the futex in parameter. If it is the first time this
     * futex is encountered, a new value will be added.
     *
     * @param futexId
     *            The ID of the futex to get
     * @return the value for this state
     */
    public synchronized int getFutexStateID(Long futexId) {
        Integer value = fFutexIdMap.get(futexId);
        if (value != null) {
            return value;
        }
        // Find a value for this name, start at 10000
        value = 10000;
        while (fFutexStateMap.get(value) != null) {
            value++;
        }
        addOrUpdateState(value, futexId);
        fFutexIdMap.put(futexId, value);
        Display.getDefault().asyncExec(new Runnable() {
            @Override
            public void run() {
                fireColorSettingsChanged();
            }
        });
        return value;
    }

    private synchronized void addOrUpdateState(int value, Long futexId) {
        // FIXME Allow this case
        if (value < 0) {
            return;
        }

        final RGB colorRGB = calcColor(futexId);

        FutexStateItem item = new FutexStateItem(colorRGB);
        fStateList.add(item);
        fStateTable = fStateList.toArray(new StateItem[fStateList.size()]);
        fFutexStateMap.put(value, item);

    }

    /*
     * This method will always return the same color for a same name, no matter
     * the value, so that different traces with the same XML analysis will
     * display identically states with the same name.
     */
    private static RGB calcColor(Long futexId) {
        long hash = futexId.hashCode(); // hashcodes can be Integer.MIN_VALUE.
        long base = COLOR_SEED[(int) (Math.abs(hash) % COLOR_SEED.length)];
        int x = (int) ((hash & COLOR_MASK) ^ base);
        final int r = (x >> 16) & 0xff;
        final int g = (x >> 8) & 0xff;
        final int b = x & 0xff;
        return new RGB(r, g, b);
    }

}
