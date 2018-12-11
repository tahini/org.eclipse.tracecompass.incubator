/*******************************************************************************
 * Copyright (c) 2016 Ericsson
 *
 * All rights reserved. This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License v1.0 which
 * accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/
package org.eclipse.tracecompass.incubator.internal.callstack.ui.flamegraph;

import java.text.Format;
import java.text.NumberFormat;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.tracecompass.analysis.timing.ui.views.segmentstore.SubSecondTimeWithUnitFormat;
import org.eclipse.tracecompass.incubator.internal.callstack.ui.FlameViewPalette;
import org.eclipse.tracecompass.incubator.analysis.core.concepts.ICallStackSymbol;
import org.eclipse.tracecompass.tmf.core.symbols.SymbolProviderManager;
import org.eclipse.tracecompass.tmf.core.trace.ITmfTrace;
import org.eclipse.tracecompass.tmf.core.trace.TmfTraceManager;
import org.eclipse.tracecompass.tmf.ui.widgets.timegraph.StateItem;
import org.eclipse.tracecompass.tmf.ui.widgets.timegraph.TimeGraphPresentationProvider;
import org.eclipse.tracecompass.tmf.ui.widgets.timegraph.model.ITimeEvent;
import org.eclipse.tracecompass.tmf.ui.widgets.timegraph.model.NullTimeEvent;
import org.eclipse.tracecompass.tmf.ui.widgets.timegraph.model.TimeEvent;
import org.eclipse.tracecompass.tmf.ui.widgets.timegraph.widgets.Utils;

import com.google.common.collect.ImmutableMap;

/**
 * Presentation provider for the flame graph view, based on the generic TMF
 * presentation provider.
 *
 * @author Sonia Farrah
 */
public class FlameGraphPresentationProvider extends TimeGraphPresentationProvider {
    /** Number of colors used for flameGraph events */
    public static final int NUM_COLORS = 360;

    private static final Format FORMATTER = new SubSecondTimeWithUnitFormat();

    private @Nullable FlameGraphView fView;

    private @Nullable Integer fAverageCharWidth;

    private FlameViewPalette fFlameViewPalette;

    /**
     * Constructor
     */
    public FlameGraphPresentationProvider() {
        fFlameViewPalette = FlameViewPalette.getInstance();
    }

    @Override
    public StateItem[] getStateTable() {
        return fFlameViewPalette.getStateTable();
    }

    @Override
    public boolean displayTimesInTooltip() {
        return false;
    }

    @Override
    public String getStateTypeName() {
        return Objects.requireNonNull(Messages.FlameGraph_Depth);
    }

    @NonNullByDefault({})
    @Override
    public Map<String, String> getEventHoverToolTipInfo(ITimeEvent event, long hoverTime) {
        if (!(event instanceof FlamegraphEvent)) {
            return Collections.emptyMap();
        }
        ImmutableMap.Builder<String, String> builder = new ImmutableMap.Builder<>();
        ITmfTrace activeTrace = TmfTraceManager.getInstance().getActiveTrace();
        String funcSymbol = null;
        FlamegraphEvent fgEvent = (FlamegraphEvent) event;
        if (activeTrace != null) {
            Object symbol = fgEvent.getSymbol();
            if (symbol instanceof ICallStackSymbol) {
                funcSymbol = ((ICallStackSymbol) symbol).resolve(SymbolProviderManager.getInstance().getSymbolProviders(activeTrace));
            } else {
                funcSymbol = String.valueOf(symbol);
            }
        }
        builder.put(Messages.FlameGraph_Symbol, funcSymbol == null ? String.valueOf(fgEvent.getSymbol()) : funcSymbol + " (" + fgEvent.getSymbol() + ")"); //$NON-NLS-1$ //$NON-NLS-2$
        long nb = (fgEvent.getNumberOfCalls());
        builder.put(Messages.FlameGraph_NbCalls, NumberFormat.getIntegerInstance().format(nb)); // $NON-NLS-1$
        Map<String, String> tooltip = ((FlamegraphEvent) event).getTooltip(FORMATTER);
        builder.putAll(tooltip);
        return builder.build();
    }

    @Override
    public int getStateTableIndex(@Nullable ITimeEvent event) {
        if (event instanceof FlamegraphEvent) {
            FlamegraphEvent flameGraphEvent = (FlamegraphEvent) event;
            return FlameViewPalette.getIndexForValue(flameGraphEvent.getValue());
        } else if (event instanceof NullTimeEvent) {
            return INVISIBLE;
        } else if (event instanceof TimeEvent) {
            int cfIndex = fFlameViewPalette.getControlFlowIndex(event);
            if (cfIndex >= 0) {
                return cfIndex;
            }
        }
        return FlameViewPalette.MULTIPLE_STATE_INDEX;
    }

    @Override
    public void postDrawEvent(@Nullable ITimeEvent event, @Nullable Rectangle bounds, @Nullable GC gc) {
        if (bounds == null || gc == null) {
            return;
        }
        Integer averageCharWidth = fAverageCharWidth;
        if (averageCharWidth == null) {
            averageCharWidth = gc.getFontMetrics().getAverageCharWidth();
            fAverageCharWidth = averageCharWidth;
        }
        if (bounds.width <= averageCharWidth) {
            return;
        }
        if (!(event instanceof FlamegraphEvent)) {
            return;
        }
        String funcSymbol = ""; //$NON-NLS-1$
        ITmfTrace activeTrace = TmfTraceManager.getInstance().getActiveTrace();
        if (activeTrace != null) {
            FlamegraphEvent fgEvent = (FlamegraphEvent) event;
            Object symbol = fgEvent.getSymbol();
            if (symbol instanceof ICallStackSymbol) {
                funcSymbol = ((ICallStackSymbol) symbol).resolve(SymbolProviderManager.getInstance().getSymbolProviders(activeTrace));
            } else {
                funcSymbol = String.valueOf(symbol);
            }
        }
        gc.setForeground(gc.getDevice().getSystemColor(SWT.COLOR_WHITE));
        Utils.drawText(gc, funcSymbol, bounds.x, bounds.y, bounds.width, bounds.height, true, true);
    }

    /**
     * The flame graph view
     *
     * @return The flame graph view
     */
    public @Nullable FlameGraphView getView() {
        return fView;
    }

    /**
     * The flame graph view
     *
     * @param view
     *            The flame graph view
     */
    public void setView(FlameGraphView view) {
        fView = view;
    }

}
