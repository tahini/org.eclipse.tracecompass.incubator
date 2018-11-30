/*******************************************************************************
 * Copyright (c) 2015, 2018 Ericsson, École Polytechnique de Montréal
 *
 * All rights reserved. This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License v1.0 which
 * accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/
package org.eclipse.tracecompass.incubator.internal.callstack.ui.views.weightedtree;

import java.text.DecimalFormat;
import java.text.FieldPosition;
import java.text.Format;
import java.text.ParsePosition;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.jface.action.IMenuManager;
import org.eclipse.jface.action.MenuManager;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.TreeViewer;
import org.eclipse.jface.viewers.Viewer;
import org.eclipse.jface.viewers.ViewerComparator;
import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Menu;
import org.eclipse.swt.widgets.Tree;
import org.eclipse.tracecompass.analysis.timing.ui.views.segmentstore.SubSecondTimeWithUnitFormat;
import org.eclipse.tracecompass.common.core.format.DataSizeWithUnitFormat;
import org.eclipse.tracecompass.common.core.format.DataSpeedWithUnitFormat;
import org.eclipse.tracecompass.incubator.analysis.core.concepts.WeightedTree;
import org.eclipse.tracecompass.incubator.callstack.core.base.ICallStackElement;
import org.eclipse.tracecompass.incubator.callstack.core.callgraph.IWeightedTreeProvider;
import org.eclipse.tracecompass.incubator.callstack.core.callgraph.IWeightedTreeProvider.DataType;
import org.eclipse.tracecompass.incubator.callstack.core.callgraph.IWeightedTreeProvider.MetricType;
import org.eclipse.tracecompass.tmf.core.analysis.IAnalysisModule;
import org.eclipse.tracecompass.tmf.core.signal.TmfSignalHandler;
import org.eclipse.tracecompass.tmf.core.signal.TmfWindowRangeUpdatedSignal;
import org.eclipse.tracecompass.tmf.core.timestamp.TmfTimestamp;
import org.eclipse.tracecompass.tmf.core.trace.ITmfTrace;
import org.eclipse.tracecompass.tmf.core.trace.TmfTraceUtils;
import org.eclipse.tracecompass.tmf.ui.viewers.tree.AbstractTmfTreeViewer;
import org.eclipse.tracecompass.tmf.ui.viewers.tree.ITmfTreeColumnDataProvider;
import org.eclipse.tracecompass.tmf.ui.viewers.tree.ITmfTreeViewerEntry;
import org.eclipse.tracecompass.tmf.ui.viewers.tree.TmfTreeColumnData;
import org.eclipse.tracecompass.tmf.ui.viewers.tree.TmfTreeColumnData.ITmfColumnPercentageProvider;
import org.eclipse.tracecompass.tmf.ui.viewers.tree.TmfTreeViewerEntry;

/**
 * An abstract tree viewer implementation for displaying a weighted tree
 *
 * @author Geneviève Bastien
 */
public class WeightedTreeViewer extends AbstractTmfTreeViewer {

    private static final Format TIME_FORMATTER = new SubSecondTimeWithUnitFormat();
    private static final Format DECIMAL_FORMATTER = new DecimalFormat("###,###.##"); //$NON-NLS-1$
    private static final Format DEFAULT_FORMATTER = new Format() {

        /** generated UID */
        private static final long serialVersionUID = -6004790434917065780L;

        @Override
        public @Nullable StringBuffer format(@Nullable Object obj, @Nullable StringBuffer toAppendTo, @Nullable FieldPosition pos) {
            return Objects.requireNonNull(toAppendTo).append(String.valueOf(obj));
        }

        @Override
        public @Nullable Object parseObject(@Nullable String source, @Nullable ParsePosition pos) {
            return source;
        }

    };

    // Order CCT children by decreasing length
    private static final Comparator<TreeNodeEntry> COMPARATOR = (o1, o2) -> Long.compare(o2.getTreeNode().getWeight(), o1.getTreeNode().getWeight());

    private MenuManager fTablePopupMenuManager;
    private String fAnalysisId;
    private Format fWeightFormatter = DECIMAL_FORMATTER;
    private boolean fInitialized = false;

    private static final String[] DEFAULT_COLUMN_NAMES = new String[] {
            Objects.requireNonNull(Messages.WeightedTreeViewer_Element)
    };

    /**
     * Constructor
     *
     * @param parent
     *            the parent composite
     * @param analysisId
     *            The ID of the analysis to use to fill this CCT
     */
    public WeightedTreeViewer(@Nullable Composite parent, String analysisId) {
        super(parent, false);
        fAnalysisId = analysisId;
        setLabelProvider(new WeightedTreeLabelProvider(Collections.emptyList()));
        fTablePopupMenuManager = new MenuManager();
        fTablePopupMenuManager.setRemoveAllWhenShown(true);
        fTablePopupMenuManager.addMenuListener(manager -> {
            TreeViewer viewer = getTreeViewer();
            ISelection selection = viewer.getSelection();
            if (selection instanceof IStructuredSelection) {
                IStructuredSelection sel = (IStructuredSelection) selection;
                if (manager != null) {
                    appendToTablePopupMenu(manager, sel);
                }
            }
        });
        Menu tablePopup = fTablePopupMenuManager.createContextMenu(getTreeViewer().getTree());
        Tree tree = getTreeViewer().getTree();
        tree.setMenu(tablePopup);
    }

    /** Provides label for the Segment Store tree viewer cells */
    private class WeightedTreeLabelProvider extends TreeLabelProvider {

        private final Map<Integer, Format> fFormatMap;

        public WeightedTreeLabelProvider(List<MetricType> list) {
            // The additional metrics start after the default columns
            int metricIndex = DEFAULT_COLUMN_NAMES.length;
            fFormatMap = new HashMap<>();
            for (MetricType metric : list) {
                fFormatMap.put(metricIndex, WeightedTreeViewer.getFormatterForType(metric.getDataType()));
                metricIndex++;
            }
        }

        @Override
        public String getColumnText(@Nullable Object element, int columnIndex) {
            String value = ""; //$NON-NLS-1$
            if (element instanceof HiddenTreeViewerEntry) {
                if (columnIndex == 0) {
                    value = ((HiddenTreeViewerEntry) element).getName();
                }
            } else if (element instanceof ElementEntry) {
                ElementEntry entry = (ElementEntry) element;
                if (columnIndex == 0) {
                    return String.valueOf(entry.getName());
                }
                value = StringUtils.EMPTY;
            } else if (element instanceof TreeNodeEntry) {
                TreeNodeEntry entry = (TreeNodeEntry) element;
                if (columnIndex == 0) {
                    return String.valueOf(entry.getName());
                }
                WeightedTree<?> callSite = entry.getTreeNode();
                if (columnIndex == 1) {
                    return String.valueOf(fWeightFormatter.format(callSite.getWeight()));
                }
                Format format = fFormatMap.get(columnIndex);
                if (format != null) {
                    return String.valueOf(format.format(entry.getMetric(columnIndex - DEFAULT_COLUMN_NAMES.length)));
                }
            }
            return Objects.requireNonNull(value);
        }

    }

    private static class WeightedPercentageProvider implements ITmfColumnPercentageProvider {

        @Override
        public double getPercentage(@Nullable Object data) {
            double value = 0;
            if (data instanceof TreeNodeEntry) {
                TreeNodeEntry entry = (TreeNodeEntry) data;

                WeightedTree<?> callSite = entry.getTreeNode();

                // Find the total length from the parent
                ITmfTreeViewerEntry parentEntry = entry;
                while (parentEntry != null && !(parentEntry instanceof ElementEntry)) {
                    parentEntry = parentEntry.getParent();
                }
                if (parentEntry != null) {
                    value = (double) callSite.getWeight() / ((ElementEntry) parentEntry).getTotalLength();
                }
            }
            return value;
        }
    }

    private static Format getFormatterForType(DataType type) {
        switch (type) {
        case BINARY_SPEED:
            return DataSpeedWithUnitFormat.getInstance();
        case BYTES:
            return DataSizeWithUnitFormat.getInstance();
        case NANOSECONDS:
            return TIME_FORMATTER;
        case NUMBER:
            return DECIMAL_FORMATTER;
        case OTHER:
            return DEFAULT_FORMATTER;
        default:
            // Fall back to default
            break;
        }
        return DEFAULT_FORMATTER;
    }

    @Override
    protected ITmfTreeColumnDataProvider getColumnDataProvider() {
        return new ITmfTreeColumnDataProvider() {

            @Override
            public List<TmfTreeColumnData> getColumnData() {
                // TODO, Ideally, since we have the analysisID, we could get an
                // empty analysis class. The metric methods should be static and
                // we could build it right away with an empty instance
                return Collections.emptyList();
            }

        };
    }

    /**
     * Get the additional columns from the treeProvider. This method returns
     * null if there are no additional columns, so nothing to change from
     * default
     */
    private static ITmfTreeColumnDataProvider getColumnDataProvider(IWeightedTreeProvider<?, WeightedTree<?>> treeProvider) {
        List<MetricType> additionalMetrics = treeProvider.getAdditionalMetrics();
        return new ITmfTreeColumnDataProvider() {

            @Override
            public List<@Nullable TmfTreeColumnData> getColumnData() {
                /* All columns are sortable */
                List<@Nullable TmfTreeColumnData> columns = new ArrayList<>(2 + additionalMetrics.size());
                // Add the default columns
                TmfTreeColumnData column = new TmfTreeColumnData(DEFAULT_COLUMN_NAMES[0]);
                column.setAlignment(SWT.LEFT);
                column.setComparator(new ViewerComparator() {
                    @Override
                    public int compare(@Nullable Viewer viewer, @Nullable Object e1, @Nullable Object e2) {
                        if ((!(e1 instanceof TreeNodeEntry)) || (!(e2 instanceof TreeNodeEntry))) {
                            return 0;
                        }

                        TreeNodeEntry n1 = (TreeNodeEntry) e1;
                        TreeNodeEntry n2 = (TreeNodeEntry) e2;

                        return n1.getName().compareTo(n2.getName());
                    }
                });
                columns.add(column);
                column = new TmfTreeColumnData(treeProvider.getWeightType().getTitle());
                column.setPercentageProvider(new WeightedPercentageProvider());
                column.setAlignment(SWT.RIGHT);
                column.setComparator(new ViewerComparator() {
                    @Override
                    public int compare(@Nullable Viewer viewer, @Nullable Object e1, @Nullable Object e2) {
                        if ((!(e1 instanceof TreeNodeEntry)) || (!(e2 instanceof TreeNodeEntry))) {
                            return 0;
                        }

                        TreeNodeEntry n1 = (TreeNodeEntry) e1;
                        TreeNodeEntry n2 = (TreeNodeEntry) e2;

                        WeightedTree<?> callsite1 = n1.getTreeNode();
                        WeightedTree<?> callsite2 = n2.getTreeNode();

                        return Long.compare(callsite1.getWeight(), callsite2.getWeight());
                    }
                });
                columns.add(column);
                // Create a column for each additional metric
                int metricIndex = 0;
                for (MetricType metric : additionalMetrics) {
                    column = new TmfTreeColumnData(metric.getTitle());
                    column.setAlignment(SWT.RIGHT);
                    int index = metricIndex;
                    switch (metric.getDataType()) {
                    case BINARY_SPEED: // Fall-through
                    case BYTES: // Fall-through
                    case NANOSECONDS: // Fall-through
                    case NUMBER:
                        // Add a number comparator
                        column.setComparator(new ViewerComparator() {
                            @Override
                            public int compare(@Nullable Viewer viewer, @Nullable Object e1, @Nullable Object e2) {
                                if ((!(e1 instanceof TreeNodeEntry)) || (!(e2 instanceof TreeNodeEntry))) {
                                    return 0;
                                }

                                Object metricValue1 = ((TreeNodeEntry) e1).getMetric(index);
                                Object metricValue2 = ((TreeNodeEntry) e2).getMetric(index);

                                if (metricValue1 instanceof Long && metricValue2 instanceof Long) {
                                    return Long.compare((Long) metricValue1, (Long) metricValue2);
                                }
                                if (metricValue1 instanceof Double && metricValue2 instanceof Double) {
                                    return Double.compare((Double) metricValue1, (Double) metricValue2);
                                }
                                if (metricValue1 instanceof Number && metricValue2 instanceof Number) {
                                    return Double.compare(((Number) metricValue1).doubleValue(), ((Number) metricValue2).doubleValue());
                                }
                                return (String.valueOf(metricValue1)).compareTo(String.valueOf(metricValue2));
                            }
                        });
                        break;
                    case OTHER:
                        // Add a string comparator
                        column.setComparator(new ViewerComparator() {
                            @Override
                            public int compare(@Nullable Viewer viewer, @Nullable Object e1, @Nullable Object e2) {
                                if ((!(e1 instanceof TreeNodeEntry)) || (!(e2 instanceof TreeNodeEntry))) {
                                    return 0;
                                }

                                Object metricValue1 = ((TreeNodeEntry) e1).getMetric(index);
                                Object metricValue2 = ((TreeNodeEntry) e2).getMetric(index);

                                return (String.valueOf(metricValue1)).compareTo(String.valueOf(metricValue2));
                            }
                        });
                        break;
                    default:
                        // No comparator
                        break;

                    }
                    columns.add(columns.size() - 1, column);
                    metricIndex++;
                }
                // Add a column for filler at the end
                column = new TmfTreeColumnData(""); //$NON-NLS-1$
                columns.add(column);
                return columns;
            }

        };
    }

    private Set<IWeightedTreeProvider<?, WeightedTree<?>>> getCallGraphs() {
        ITmfTrace trace = getTrace();
        if (trace != null) {
            Iterable<IWeightedTreeProvider> callgraphModules = TmfTraceUtils.getAnalysisModulesOfClass(trace, IWeightedTreeProvider.class);

            Set<IWeightedTreeProvider<?, WeightedTree<?>>> set = new HashSet<>();
            for (IWeightedTreeProvider<?, WeightedTree<?>> treeProvider : callgraphModules) {
                if (treeProvider instanceof IAnalysisModule) {
                    if (((IAnalysisModule) treeProvider).getId().equals(fAnalysisId)) {
                        set.add(treeProvider);
                    }
                }
            }
            return set;
        }
        return Collections.emptySet();
    }

    @Override
    public void initializeDataSource(ITmfTrace trace) {
        Set<IWeightedTreeProvider<?, WeightedTree<?>>> modules = getCallGraphs();

        modules.forEach(m -> {
            if (m instanceof IAnalysisModule) {
                ((IAnalysisModule) m).schedule();
            }
        });
        if (!modules.isEmpty() && !fInitialized) {
            initializeViewer(modules.iterator().next());
            fInitialized = true;
        }
    }

    /**
     * From a tree provider, initialize the viewer data/columns/label providers,
     * etc
     */
    private void initializeViewer(IWeightedTreeProvider<?, WeightedTree<?>> treeProvider) {
        MetricType weightType = treeProvider.getWeightType();
        fWeightFormatter = getFormatterForType(weightType.getDataType());
        ITmfTreeColumnDataProvider columns = getColumnDataProvider(treeProvider);
        Display.getDefault().asyncExec(() -> {
            setTreeColumns(columns.getColumnData());
            setLabelProvider(new WeightedTreeLabelProvider(treeProvider.getAdditionalMetrics()));
        });

    }

    /**
     * Method to add commands to the context sensitive menu.
     *
     * @param manager
     *            the menu manager
     * @param sel
     *            the current selection
     */
    protected void appendToTablePopupMenu(IMenuManager manager, IStructuredSelection sel) {

    }

    /**
     * Formats a double value string
     *
     * @param value
     *            a value to format
     * @return formatted value
     */
    protected static String toFormattedString(double value) {
        return String.format("%s", TIME_FORMATTER.format(value)); //$NON-NLS-1$
    }

    /**
     * Class for defining an entry in the statistics tree.
     */
    protected class ElementEntry extends TmfTreeViewerEntry {

        private final WeightedTree<?> fTree;
        private final Object fThisElement;
        private final IWeightedTreeProvider<?, WeightedTree<?>> fTreeProvider;
        private @Nullable List<ITmfTreeViewerEntry> fChildren;

        /**
         * Constructor
         *
         * @param tree
         *            The tree to display under this element
         * @param provider
         *            The tree provider for this entry
         */
        public ElementEntry(WeightedTree<?> tree, IWeightedTreeProvider<?, WeightedTree<?>> provider) {
            super(String.valueOf(tree.getObject()));
            fThisElement = tree.getObject();
            fTree = tree;
            fTreeProvider = provider;
        }

        /**
         * Constructor
         *
         * @param child
         *            The child element
         * @param tree
         *            The tree
         * @param provider
         *            The tree provider for this entry
         */
        public ElementEntry(ICallStackElement child, WeightedTree<?> tree, IWeightedTreeProvider<?, WeightedTree<?>> provider) {
            super(String.valueOf(tree.getObject()));
            fThisElement = child;
            fTree = tree;
            fTreeProvider = provider;
        }

        /**
         * Gets the statistics object
         *
         * @return statistics object
         */
        public Object getElement() {
            return fTree.getObject();
        }

        @Override
        public boolean hasChildren() {
            return true;
        }

        @Override
        public List<ITmfTreeViewerEntry> getChildren() {
            List<ITmfTreeViewerEntry> children = fChildren;
            if (children == null) {
                Object thisNode = fThisElement;
                if (thisNode instanceof ICallStackElement && !((ICallStackElement) thisNode).isLeaf()) {
                    children = getChildrenElements((ICallStackElement) thisNode);
                } else {
                    // TODO Plan for a hierarchy of elements
                    children = getChildrenTreeNodes();
                }
                fChildren = children;
            }
            return children;
        }

        /**
         * Get the total length for the callsites children of this element. This
         * is used for percentages
         *
         * @return The total length of the children callsites
         */
        public long getTotalLength() {
            List<ITmfTreeViewerEntry> childrenCallSites = getChildren();
            long length = 0L;
            for (ITmfTreeViewerEntry callsiteEntry : childrenCallSites) {
                length += ((TreeNodeEntry) callsiteEntry).getTreeNode().getWeight();
            }
            return length;
        }

        private List<ITmfTreeViewerEntry> getChildrenTreeNodes() {
            List<ITmfTreeViewerEntry> list = new ArrayList<>();
            for (WeightedTree<?> callsite : fTree.getChildren()) {
                list.add(new TreeNodeEntry(callsite, this, fTreeProvider));
            }
            return list;
        }

        private List<ITmfTreeViewerEntry> getChildrenElements(ICallStackElement element) {
            List<ITmfTreeViewerEntry> list = new ArrayList<>();
            for (ICallStackElement child : element.getChildrenElements()) {
                list.add(new ElementEntry(child, fTree, fTreeProvider));
            }
            return list;
        }

    }

    /**
     * Class for defining an entry in the statistics tree.
     */
    protected class TreeNodeEntry extends TmfTreeViewerEntry {

        private final WeightedTree<?> fTreeNode;
        private final IWeightedTreeProvider<?, WeightedTree<?>> fTreeProvider;
        private @Nullable List<ITmfTreeViewerEntry> fChildren = null;

        /**
         * Constructor
         *
         * @param callsite
         *            The callsite corresponding to this entry
         * @param parent
         *            The parent element
         * @param treeProvider
         *            The tree provider
         */
        public TreeNodeEntry(WeightedTree<?> callsite, TmfTreeViewerEntry parent, IWeightedTreeProvider<?, WeightedTree<?>> treeProvider) {
            super(treeProvider.toDisplayString(callsite));
            fTreeNode = callsite;
            this.setParent(parent);
            fTreeProvider = treeProvider;
        }

        /**
         * Gets the statistics object
         *
         * @return statistics object
         */
        public WeightedTree<?> getTreeNode() {
            return fTreeNode;
        }

        @Override
        public boolean hasChildren() {
            return !fTreeNode.getChildren().isEmpty();
        }

        /**
         * Get the corresponding metric for this node
         *
         * @param metricIndex
         *            The index of the metric to get
         * @return The metric for this tree node
         */
        public Object getMetric(int metricIndex) {
            return fTreeProvider.getAdditionalMetric(fTreeNode, metricIndex);
        }

        @Override
        public List<ITmfTreeViewerEntry> getChildren() {
            List<ITmfTreeViewerEntry> children = fChildren;
            if (children == null) {
                List<TreeNodeEntry> cctChildren = new ArrayList<>();
                for (WeightedTree<?> callsite : fTreeNode.getChildren()) {
                    TreeNodeEntry entry = new TreeNodeEntry(callsite, this, fTreeProvider);
                    int index = Collections.binarySearch(cctChildren, entry, COMPARATOR);
                    cctChildren.add((index < 0 ? -index - 1 : index), entry);
                }
                children = new ArrayList<>(cctChildren);
                fChildren = children;
            }
            return children;
        }

    }

    @Override
    protected @Nullable ITmfTreeViewerEntry updateElements(ITmfTrace trace, long start, long end, boolean isSelection) {

        Set<IWeightedTreeProvider<?, WeightedTree<?>>> modules = getCallGraphs();

        if (modules.isEmpty()) {
            return null;
        }
        modules.forEach(m -> {
            if (m instanceof IAnalysisModule) {
                ((IAnalysisModule) m).waitForCompletion();
            }
        });

        TmfTreeViewerEntry root = new TmfTreeViewerEntry(""); //$NON-NLS-1$
        List<ITmfTreeViewerEntry> entryList = root.getChildren();

        for (IWeightedTreeProvider<?, WeightedTree<?>> module : modules) {
            if (isSelection) {
                setStats(start, end, entryList, module, true, new NullProgressMonitor());
            }
            // Start, start to ensure the full callgraph will be returned
            setStats(start, start, entryList, module, false, new NullProgressMonitor());
        }
        return root;
    }

    /**
     * TODO: Implement this if necessary
     *
     * @param start
     * @param end
     * @param isSelection
     * @param monitor
     */
    private void setStats(long start, long end, List<ITmfTreeViewerEntry> entryList, IWeightedTreeProvider<?, WeightedTree<?>> module, boolean isSelection, IProgressMonitor monitor) {

        Collection<WeightedTree<?>> trees = null;
        if (start != end) {
            trees = module.getTrees(TmfTimestamp.fromNanos(start), TmfTimestamp.fromNanos(end));
        } else {
            trees = module.getTrees();
        }

        for (WeightedTree<?> tree : trees) {
            ElementEntry entry = new ElementEntry(tree, module);
            entryList.add(entry);
        }
    }

    @Override
    @TmfSignalHandler
    public void windowRangeUpdated(@Nullable TmfWindowRangeUpdatedSignal signal) {
        // Do nothing. We do not want to update the view and lose the selection
        // if the window range is updated with current selection outside of this
        // new range.
    }

    /**
     * Get the total column label
     *
     * @return the totals column label
     * @since 1.2
     */
    protected String getTotalLabel() {
        return Objects.requireNonNull(Messages.WeightedTreeViewer_LabelTotal);
    }

    /**
     * Get the selection column label
     *
     * @return The selection column label
     * @since 1.2
     */
    protected String getSelectionLabel() {
        return Objects.requireNonNull(Messages.WeightedTreeViewer_LabelSelection);
    }

    /**
     * Class to define a level in the tree that doesn't have any values.
     */
    protected class HiddenTreeViewerEntry extends TmfTreeViewerEntry {
        /**
         * Constructor
         *
         * @param name
         *            the name of the level
         */
        public HiddenTreeViewerEntry(String name) {
            super(name);
        }
    }

}
