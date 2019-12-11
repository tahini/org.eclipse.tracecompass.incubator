/*******************************************************************************
 * Copyright (c) 2019 École Polytechnique de Montréal
 *
 * All rights reserved. This program and the accompanying materials are
 * made available under the terms of the Eclipse Public License v1.0 which
 * accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/

package org.eclipse.tracecompass.incubator.analysis.core.weighted.tree;

import java.text.FieldPosition;
import java.text.Format;
import java.text.ParsePosition;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.tracecompass.analysis.timing.core.statistics.IStatistics;
import org.eclipse.tracecompass.common.core.format.DataSizeWithUnitFormat;
import org.eclipse.tracecompass.common.core.format.DataSpeedWithUnitFormat;
import org.eclipse.tracecompass.common.core.format.DecimalUnitFormat;
import org.eclipse.tracecompass.common.core.format.SubSecondTimeWithUnitFormat;
import org.eclipse.tracecompass.tmf.core.timestamp.ITmfTimestamp;

/**
 * An interface that classes and analyses providing weighted trees can
 * implement. This interface allows to add extra information about the specific
 * trees that it provides.
 *
 * The trees are associated with elements that are used to group them. The
 * elements can implement the {@link ITree} class if there is a hierarchy in the
 * groupings.
 *
 * @author Geneviève Bastien
 * @param <N>
 *            The type of objects represented by each node in the tree
 * @param <E>
 *            The type of elements used to group the trees
 * @param <T>
 *            The type of the tree provided
 */
public interface IWeightedTreeProvider<@NonNull N, E, @NonNull T extends WeightedTree<N>> {

    /**
     * The type of data that a value represents. Mostly for numeric value, as
     * the data type will help decide how to format the data to be displayed to
     * the user
     */
    public enum DataType {
        /**
         * Data represent a decimal number
         */
        NUMBER(new DecimalUnitFormat()),
        /**
         * Data represent a time in nanoseconds, can be negative
         */
        NANOSECONDS(SubSecondTimeWithUnitFormat.getInstance()),
        /**
         * Data represent a binary size, in bytes
         */
        BYTES(DataSizeWithUnitFormat.getInstance()),
        /**
         * Data represent a binary speed, in bytes/second
         */
        BINARY_SPEED(DataSpeedWithUnitFormat.getInstance()),
        /**
         * Any other type of data. Metric that use this data type may use
         * additional formatter.
         */
        OTHER(new Format() {

            /**
             *
             */
            private static final long serialVersionUID = 1L;

            @Override
            public StringBuffer format(@Nullable Object obj, @Nullable StringBuffer toAppendTo, @Nullable FieldPosition pos) {
                if (toAppendTo == null) {
                    return new StringBuffer(String.valueOf(obj));
                }
                return Objects.requireNonNull(toAppendTo.append(String.valueOf(obj)));
            }

            @Override
            public @Nullable Object parseObject(@Nullable String source, @Nullable ParsePosition pos) {
                return null;
            }

        });

        private Format fFormatter;

        private DataType(Format formatter) {
            fFormatter = formatter;
        }

        /**
         * Formats an object according to the specified formatter
         *
         * @param object
         *            The object to format
         * @return The formatted string
         */
        public String format(Object object) {
            return String.valueOf(fFormatter.format(object));
        }
    }

    /**
     * This class associate a title to a data type for tree metrics
     */
    public static class MetricType {
        private final String fTitle;
        private final DataType fDataType;
        private final @Nullable Format fFormatter;
        private final boolean fHasStatistics;

        /**
         * Constructor
         *
         * @param title
         *            The title of this metric (a string meant for end users)
         * @param dataType
         *            The type of data this metric represent
         * @param format
         *            The formatter for this metric. If <code>null</code>,
         *            formatting will use the {@link DataType}'s default
         *            formatter
         */
        public MetricType(String title, DataType dataType, @Nullable Format format) {
            this(title, dataType, format, false);
        }

        /**
         * Constructor
         *
         * @param title
         *            The title of this metric (a string meant for end users)
         * @param dataType
         *            The type of data this metric represent
         * @param format
         *            The formatter for this metric. If <code>null</code>,
         *            formatting will use the {@link DataType}'s default
         *            formatter
         * @param hasStatistics
         *            Whether this metric has statistics provided with it
         */
        public MetricType(String title, DataType dataType, @Nullable Format format, boolean hasStatistics) {
            fTitle = title;
            fDataType = dataType;
            fFormatter = format;
            fHasStatistics = hasStatistics;
        }

        /**
         * Get the title of this metric, for the user
         *
         * @return The title
         */
        public String getTitle() {
            return fTitle;
        }

        /**
         * Get the type of data of this metric
         *
         * @return The data type of the metric
         */
        public DataType getDataType() {
            return fDataType;
        }

        /**
         * Formats an object for this metric
         *
         * @param obj
         *            The object to format
         * @return The formatted string
         */
        public String format(Object obj) {
            if (fFormatter != null) {
                return Objects.requireNonNull(fFormatter.format(obj));
            }
            return fDataType.format(obj);
        }

        /**
         * Return whether this metric has statistics computed with it. If so,
         * then calling
         * {@link IWeightedTreeProvider#getStatistics(WeightedTree, int)} on
         * this metric's index should return a statistics object for a tree.
         *
         * @return Whether this metric has statistics computed for it
         */
        public boolean hasStatistics() {
            return fHasStatistics;
        }

    }

    /**
     * The default metric type for the tree's weight
     */
    MetricType WEIGHT_TYPE = new MetricType("Weight", DataType.NUMBER, null); //$NON-NLS-1$

    /**
     * Get a weighted tree provider for a time selection. It should have the
     * same parameters as the complete class, but its range should cover only
     * the requested time range. If the provider does not support selection
     * range, <code>null</code> should be returned.
     *
     * @param start
     *            The timestamp of the start of the range
     * @param end
     *            The timestamp of the end of the range
     * @return A weighted tree provider that spans the selected range, or
     *         <code>null</code> if range is not supported.
     */
    default @Nullable IWeightedTreeSet<N, E, T> getSelection(ITmfTimestamp start, ITmfTimestamp end) {
        return null;
    }

    /**
     * Get a weighted tree provider for a time selection. It should have the
     * same parameters as the complete class, but its range should cover only
     * the requested time range. If the provider does not support selection
     * range, <code>null</code> should be returned.
     *
     * @param start
     *            The timestamp of the start of the range
     * @param end
     *            The timestamp of the end of the range
     * @return A weighted tree provider that spans the selected range, or
     *         <code>null</code> if range is not supported.
     */
    IWeightedTreeSet<N, E, T> getTreeSet();

    /**
     * Get the metric type for the weight value. The default metric is called
     * "Weight" and is a number
     *
     * @return The metric type for the weight value.
     */
    default MetricType getWeightType() {
        return WEIGHT_TYPE;
    }

    /**
     * Get a list of additional metrics that are provided by this tree.
     *
     * @return A list of metrics provided by the trees, in addition to the
     *         weight
     */
    default List<MetricType> getAdditionalMetrics() {
        return Collections.emptyList();
    }

    /**
     * Get an additional metric for a tree. The metric index corresponds to the
     * position of the desired metric in the list of metric returned by the
     * {@link #getAdditionalMetrics()} method and the return value should be of
     * the proper data type
     *
     * @param object
     *            The tree object for which to get the metric
     * @param metricIndex
     *            The index in the list of the metric metric to get
     * @return The value of the metric for the tree in parameter
     */
    default Object getAdditionalMetric(T object, int metricIndex) {
        throw new UnsupportedOperationException("If the tree provider has metric, it should implement this method, or it should not be called"); //$NON-NLS-1$
    }

    /**
     * Get the statistics for a metric. The metric index corresponds to the
     * position of the desired metric in the list of metric returned by the
     * {@link #getAdditionalMetrics()} method and the return value should be of
     * the proper data type. If the index < 0, then the metric is the main
     * weight.
     *
     * @param object
     *            The tree object for which to get the metric
     * @param metricIndex
     *            The index in the list of the metric metric to get. If < 0,
     *            then the metric is the weight
     * @return The statistics for the metric of <code>null</code> if there are
     *         no statistics for this metric.
     */
    default @Nullable IStatistics<?> getStatistics(T object, int metricIndex) {
        if (metricIndex < 0) {
            if (getWeightType().hasStatistics()) {
                return object.getStatistics(metricIndex);
            }
            return null;
        }
        List<MetricType> metrics = getAdditionalMetrics();
        if (metricIndex >= metrics.size()) {
            return null;
        }
        MetricType metricType = metrics.get(metricIndex);
        if (!metricType.hasStatistics()) {
            return null;
        }
        return object.getStatistics(metricIndex);
    }

    /**
     * Return a list of additional data sets' titles. These sets will be
     * available by calling {@link WeightedTree#getExtraDataTrees(int)} on the
     * trees, where the index in the list is the parameter that the children set
     * should match
     *
     * @return The title of each child set
     */
    default List<String> getExtraDataSets() {
        return Collections.emptyList();
    }

    /**
     * Get a user-facing text to identify a tree object. By default, it is the
     * string representation of the object.
     *
     * @param tree
     *            The tree whose value to display
     * @return A user-facing string to identify this node
     */
    default String toDisplayString(T tree) {
        return String.valueOf(tree.getObject());
    }

    /**
     * A title for this tree provider. This title will be visible by users and
     * should describe what this tree provider's data represent.
     *
     * @return The title of this provider
     */
    String getTitle();

}
