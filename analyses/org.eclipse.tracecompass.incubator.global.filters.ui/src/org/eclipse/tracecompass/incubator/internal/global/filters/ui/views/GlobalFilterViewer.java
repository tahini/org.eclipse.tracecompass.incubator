/*******************************************************************************
 * Copyright (c) 2018 École Polytechnique de Montréal
 *
 * All rights reserved. This program and the accompanying materials are
 * made available under the terms of the Eclipse Public License v1.0 which
 * accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/

package org.eclipse.tracecompass.incubator.internal.global.filters.ui.views;

import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.jface.layout.GridDataFactory;
import org.eclipse.jface.layout.GridLayoutFactory;
import org.eclipse.swt.SWT;
import org.eclipse.swt.dnd.DND;
import org.eclipse.swt.dnd.DragSource;
import org.eclipse.swt.dnd.DragSourceEvent;
import org.eclipse.swt.dnd.DragSourceListener;
import org.eclipse.swt.dnd.DropTarget;
import org.eclipse.swt.dnd.DropTargetEvent;
import org.eclipse.swt.dnd.DropTargetListener;
import org.eclipse.swt.dnd.TextTransfer;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.ExpandBar;
import org.eclipse.swt.widgets.ExpandItem;
import org.eclipse.tracecompass.incubator.lsp.ui.lspFilterTextbox.LspFilterTextbox;
import org.eclipse.tracecompass.incubator.lsp.ui.lspFilterTextbox.ValidListener;
import org.eclipse.tracecompass.internal.provisional.tmf.core.model.filters.TmfFilterAppliedSignal;
import org.eclipse.tracecompass.internal.provisional.tmf.core.model.filters.TraceCompassFilter;
import org.eclipse.tracecompass.tmf.core.component.ITmfComponent;
import org.eclipse.tracecompass.tmf.core.trace.ITmfTrace;
import org.eclipse.tracecompass.tmf.core.trace.TmfTraceManager;

import com.google.gson.Gson;

/**
 * The viewer class containing all the widgets and the user logic to manage the
 * global filters
 *
 * @author Genevieve Bastien
 */
@SuppressWarnings("restriction")
public class GlobalFilterViewer extends Composite {

    private static final Gson GSON = new Gson();

    private final ExpandBar fExpandBar;
    private final LinkedHashSet<String> fEnabledFilters = new LinkedHashSet<>();
    private final LinkedHashSet<String> fDisabledFilters = new LinkedHashSet<>();
    private final ITmfComponent fComponent;
    private final org.eclipse.swt.widgets.List fActiveArea;
    private final ExpandItem fActive;
    private final org.eclipse.swt.widgets.List fSavedArea;
    private final ExpandItem fSaved;
    private final LspFilterTextbox fLspFilterTextbox;

    /**
     * Deleted all selected items
     */
    public void deleteSelected() {
        String[] strings = fActiveArea.getSelection();
        for (String string : strings) {
            fEnabledFilters.remove(string);
        }
        strings = fSavedArea.getSelection();
        for (String string : strings) {
            fDisabledFilters.remove(string);
        }
        filtersUpdated();
    }

    @Override
    public void redraw() {
        String[] enabled = fEnabledFilters.toArray(new String[fEnabledFilters.size()]);
        fActiveArea.setItems(enabled);
        Point size = fActiveArea.computeSize(SWT.DEFAULT, SWT.DEFAULT);
        fActive.setHeight(size.y);
        String[] disabled = fDisabledFilters.toArray(new String[fDisabledFilters.size()]);
        fSavedArea.setItems(disabled);
        size = fSavedArea.computeSize(SWT.DEFAULT, SWT.DEFAULT);
        fSaved.setHeight(size.y);
        super.redraw();
    }

    /**
     * Constructor
     *
     * @param component
     *            The view or component this viewer is part of
     * @param parent
     *            The parent composite
     * @param style
     *            The SWT style for this viewer
     */
    public GlobalFilterViewer(ITmfComponent component, Composite parent, int style) {
        super(parent, style);
        fComponent = component;

        Map<String, Boolean> overrideParametersMap = new HashMap();
        overrideParametersMap.put("KeyListener", true); //$NON-NLS-1$
        fLspFilterTextbox = new LspFilterTextbox(parent, component.getName());
        fLspFilterTextbox.addValidListener(new ValidListener() {

            @Override
            public void valid() {
                String text = Objects.requireNonNull(fLspFilterTextbox.getText());
                fLspFilterTextbox.setText(""); //$NON-NLS-1$
                if (fEnabledFilters.contains(text) || fDisabledFilters.contains(text)) {
                    return;
                }
                fEnabledFilters.add(text);
                fSavedArea.setItems(fDisabledFilters.toArray(new String[fDisabledFilters.size()]));
                filtersUpdated();
            }

            @Override
            public void invalid() {
                // Do nothing
            }
        });

        fExpandBar = new ExpandBar(parent, SWT.V_SCROLL);
        parent.setLayout(GridLayoutFactory.fillDefaults().create());
        fExpandBar.setLayout(GridLayoutFactory.fillDefaults().create());
        fExpandBar.setLayoutData(GridDataFactory.fillDefaults().grab(true, true).create());
        fActive = new ExpandItem(fExpandBar, SWT.NONE, 0);
        fActive.setText("Active"); //$NON-NLS-1$
        fActive.setHeight(16);
        fActiveArea = new org.eclipse.swt.widgets.List(fExpandBar, SWT.MULTI);
        fActiveArea.setLayoutData(GridDataFactory.fillDefaults().grab(true, true).create());
        fActive.setControl(fActiveArea);
        fSaved = new ExpandItem(fExpandBar, SWT.NONE, 1);
        fSaved.setText("Saved"); //$NON-NLS-1$
        fSaved.setHeight(16);
        fSavedArea = new org.eclipse.swt.widgets.List(fExpandBar, SWT.MULTI);
        fSavedArea.setLayoutData(GridDataFactory.fillDefaults().grab(true, true).create());
        fSaved.setControl(fSavedArea);
        fActive.setExpanded(true);
        fSaved.setExpanded(true);

        DragSource activeSource = new DragSource(fActiveArea, DND.DROP_MOVE);
        activeSource.setTransfer(TextTransfer.getInstance());
        activeSource.addDragListener(new DragSourceListener() {

            @Override
            public void dragStart(@Nullable DragSourceEvent event) {
                if (event == null) {
                    return;
                }
                if (fActiveArea.getSelection().length == 0) {
                    event.doit = false;
                }
            }

            @Override
            public void dragSetData(@Nullable DragSourceEvent event) {
                if (event == null) {
                    return;
                }
                if (TextTransfer.getInstance().isSupportedType(event.dataType)) {
                    event.data = GSON.toJson(fActiveArea.getSelection());
                }
            }

            @Override
            public void dragFinished(@Nullable DragSourceEvent event) {
                // Don't need to do it
            }
        });
        DragSource savedSource = new DragSource(fSavedArea, DND.DROP_MOVE);
        savedSource.setTransfer(TextTransfer.getInstance());
        savedSource.addDragListener(new DragSourceListener() {

            @Override
            public void dragStart(@Nullable DragSourceEvent event) {
                if (event == null) {
                    return;
                }
                if (fSavedArea.getSelection().length == 0) {
                    event.doit = false;
                }
            }

            @Override
            public void dragSetData(@Nullable DragSourceEvent event) {
                if (event == null) {
                    return;
                }
                if (TextTransfer.getInstance().isSupportedType(event.dataType)) {
                    event.data = GSON.toJson(fSavedArea.getSelection());
                }
            }

            @Override
            public void dragFinished(@Nullable DragSourceEvent event) {
                // Don't need to do it
            }
        });

        DropTarget activeTarget = new DropTarget(fActiveArea, DND.DROP_MOVE | DND.DROP_DEFAULT);
        activeTarget.setTransfer(TextTransfer.getInstance());
        activeTarget.addDropListener(new DropTargetListener() {

            @Override
            public void dropAccept(@Nullable DropTargetEvent event) {

            }

            @Override
            public void drop(@Nullable DropTargetEvent event) {
                if (event == null) {
                    return;
                }
                String[] strings = GSON.fromJson(String.valueOf(event.data), String[].class);
                boolean updated = false;
                for (String string : strings) {
                    if (string != null && !fEnabledFilters.contains(string) && fDisabledFilters.contains(string)) {
                        updated = true;
                        fEnabledFilters.add(string);
                        fDisabledFilters.remove(string);
                    }
                }
                if (updated) {
                    filtersUpdated();
                }
            }

            @Override
            public void dragOver(@Nullable DropTargetEvent event) {

            }

            @Override
            public void dragOperationChanged(@Nullable DropTargetEvent event) {

            }

            @Override
            public void dragLeave(@Nullable DropTargetEvent event) {

            }

            @Override
            public void dragEnter(@Nullable DropTargetEvent event) {
                if (event == null) {
                    return;
                }
                if (event.detail == DND.DROP_DEFAULT) {
                    event.detail = DND.DROP_MOVE;
                }
            }
        });
        DropTarget savedTarget = new DropTarget(fSavedArea, DND.DROP_MOVE | DND.DROP_DEFAULT);
        savedTarget.setTransfer(TextTransfer.getInstance());
        savedTarget.addDropListener(new DropTargetListener() {

            @Override
            public void dropAccept(@Nullable DropTargetEvent event) {

            }

            @Override
            public void drop(@Nullable DropTargetEvent event) {
                if (event == null) {
                    return;
                }
                String[] strings = GSON.fromJson(String.valueOf(event.data), String[].class);
                boolean updated = false;
                for (String string : strings) {
                    if (string != null && fEnabledFilters.contains(string) && !fDisabledFilters.contains(string)) {
                        updated = true;
                        fDisabledFilters.add(string);
                        fEnabledFilters.remove(string);
                    }
                }
                if (updated) {
                    filtersUpdated();
                }
            }

            @Override
            public void dragOver(@Nullable DropTargetEvent event) {

            }

            @Override
            public void dragOperationChanged(@Nullable DropTargetEvent event) {

            }

            @Override
            public void dragLeave(@Nullable DropTargetEvent event) {

            }

            @Override
            public void dragEnter(@Nullable DropTargetEvent event) {
                if (event == null) {
                    return;
                }
                if (event.detail == DND.DROP_DEFAULT) {
                    event.detail = DND.DROP_MOVE;
                }
            }
        });
        layout(true);

        // Initialize the FilterTextBox widget
    }

    @Override
    public boolean setFocus() {
        return fLspFilterTextbox.setFocus();
    }

    private void filtersUpdated() {
        List<String> filter = fEnabledFilters.stream().map(f -> '(' + f + ')').collect(Collectors.toList());
        ITmfTrace trace = TmfTraceManager.getInstance().getActiveTrace();
        if (trace == null) {
            // No trace opened, can't filter
            return;
        }
        fComponent.broadcast(new TmfFilterAppliedSignal(fComponent, trace, TraceCompassFilter.fromRegex(filter, trace)));
        redraw();
    }

    void eventFilterApplied(Collection<String> regexes) {
        if (fEnabledFilters.containsAll(regexes)) {
            // regex already present, ignore
            return;
        }
        // Move enabled filters to the disabled list
        fEnabledFilters.forEach(f -> fDisabledFilters.add(f));
        // Add the new filter to the saved list
        fEnabledFilters.clear();
        if (!regexes.isEmpty()) {
            fEnabledFilters.addAll(regexes);
        }
        // Remove the regex from disabled filters in case it is there
        fDisabledFilters.removeAll(regexes);
        // redraw
        redraw();
    }
}
