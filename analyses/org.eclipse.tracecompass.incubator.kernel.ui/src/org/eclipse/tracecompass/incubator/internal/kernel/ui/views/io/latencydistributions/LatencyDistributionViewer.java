package org.eclipse.tracecompass.incubator.internal.kernel.ui.views.io.latencydistributions;

import static org.eclipse.tracecompass.common.core.NonNullUtils.checkNotNull;

import java.util.ArrayList;
import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.tracecompass.internal.analysis.os.linux.core.inputoutput.InputOutputAnalysisModule;
import org.eclipse.tracecompass.tmf.core.trace.ITmfTrace;
import org.eclipse.tracecompass.tmf.core.trace.TmfTraceUtils;
import org.eclipse.tracecompass.tmf.ui.viewers.xycharts.TmfXYChartViewer;
import org.swtchart.Chart;
import org.swtchart.IBarSeries;
import org.swtchart.ISeries.SeriesType;
import org.swtchart.Range;

/**
 * Histogram Viewer implementation based on TmfBarChartViewer.
 *
 * @author Houssem Daoud
 * @since 2.0
 */
public class LatencyDistributionViewer extends TmfXYChartViewer {

    @SuppressWarnings("javadoc")
    public class Interval {
        private long minimum;
        private long maximum;
        private String label;
        public Interval(long min, long max){
            this.minimum = min;
            this.maximum = max;
            this.label = new String("[" + minimum + ", " + maximum + "["); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
        }
        public long getMin() {
            return minimum;
        }
        public long getMax() {
            return maximum;
        }
        public String getLabel() {
            return label;
        }
        public void printInterval(){
            System.out.println(label);
        }
    }

    int SECOND_TO_MILLISEOND = 1000000 ;

    private LatencyDistributionView mainview;
    private String title ="Latency Distribution chart"; //$NON-NLS-1$
    private String xTitle = "Latency"; //$NON-NLS-1$
    private String yTitle = "Number of requests"; //$NON-NLS-1$
    private String serieName = "Number of requests"; //$NON-NLS-1$
    private Long num_intervals;
    private ArrayList<Long> table = new ArrayList<>();
    private Long max;
    private Long min;
    private Long selectedMax = null;
    private Long selectedMin = null;
    private boolean displayNumbers = false;

    /**
     * @param parent parent widget
     * @param mainview mainview
     * @param title title of the chart
     * @param xLabel xLabel
     * @param yLabel yLabel
     * @param number_intervals number_intervals
     */
    public LatencyDistributionViewer(Composite parent, LatencyDistributionView mainview, String title, String xLabel, String yLabel, Long number_intervals) {
        super(parent, title, xLabel, yLabel);
        setMouseDrageProvider(null);
        setSelectionProvider(null);
        setMouseDragZoomProvider(null);
        setTooltipProvider(null);
        this.num_intervals = number_intervals;
        this.mainview = mainview;
    }

    @Override
    protected void updateContent() {
        getDisplay().asyncExec(new Runnable() {
            @Override
            public void run() {
                    ITmfTrace trace = getTrace();
                    trace = checkNotNull(trace);
                    final InputOutputAnalysisModule ioMod = TmfTraceUtils.getAnalysisModuleOfClass(trace, InputOutputAnalysisModule.class ,InputOutputAnalysisModule.ID);
                    if (ioMod == null ) {
                        return;
                    }
                    ioMod.schedule();
                    ioMod.waitForInitialization();
                    table = ioMod.getLatencyTables(getWindowStartTime(), getWindowEndTime());
                    if (table.isEmpty()){
                        clearContent();
                        return;
                    }

                    max = new Long(0);
                    min = Long.MAX_VALUE;
                    for (Long lat : table){
                        if (lat > max){
                            max = lat;
                        }
                        if (lat < min) {
                            min = lat;
                        }
                    }
                    mainview.setLimits(max, min);
                    selectedMax = null;
                    selectedMin = null;
                    drawChart() ;
            }
        });
    }

    void drawChart() {
        // Run in GUI thread to make sure that chart is ready after restart
        final Display display = getDisplay();
        if (display.isDisposed()) {
            return;
        }
        display.syncExec(new Runnable() {
            @Override
            public void run() {
                if (display.isDisposed()) {
                    return;
                }
                clearContent();

                ArrayList<Interval>intervals = new ArrayList<>();
                // if we have only one latency;
                Long effectiveMin;
                Long effectiveMax;
                effectiveMin = selectedMin == null ? min : selectedMin;
                effectiveMax = selectedMax == null ? max : selectedMax;
                if (effectiveMax == effectiveMin){
                    intervals.add(new Interval(effectiveMin, effectiveMax+1));
                } else {
                    intervals = getIntervalslimits(effectiveMax+1, effectiveMin, num_intervals);
                }

                double[] ySeries1 = new double[intervals.size()];
                for (Long lat : table){
                    for (int i = 0; i < intervals.size(); i++){
                        if (lat >= intervals.get(i).getMin() && lat < intervals.get(i).getMax()){
                            if (displayNumbers == false){
                                ySeries1[i] += (double)100/table.size();
                            } else {
                                ySeries1[i] += 1;
                            }
                        }
                    }
                }

                String[] intervalsLabels = new String[intervals.size()];
                for (int i = 0 ; i < intervals.size() ; i++ ){
                    intervalsLabels[i] = intervals.get(i).getLabel();
                }
                Chart chart = getSwtChart();

                // set titles
                chart.getTitle().setText(title);
                chart.getAxisSet().getXAxis(0).getTitle().setText(xTitle);
                chart.getAxisSet().getYAxis(0).getTitle().setText(yTitle);

                // set category
                chart.getAxisSet().getXAxis(0).enableCategory(true);
                chart.getAxisSet().getXAxis(0).setCategorySeries(intervalsLabels);

                // create bar series
                IBarSeries barSeries1 = (IBarSeries) chart.getSeriesSet().createSeries(SeriesType.BAR, serieName);
                barSeries1.setYSeries(ySeries1);
                barSeries1.setBarColor(Display.getDefault().getSystemColor(
                        SWT.COLOR_DARK_CYAN));
                barSeries1.getLabel().setVisible(true);
                barSeries1.getLabel().setFormat("#.##"); //$NON-NLS-1$

                // adjust the axis range
                if (num_intervals > 15) {
                    chart.getAxisSet().getXAxis(0).getTick().setTickLabelAngle(90);
                } else {
                    chart.getAxisSet().getXAxis(0).getTick().setTickLabelAngle(0);
                }
                chart.getAxisSet().getXAxis(0).adjustRange();
                if (displayNumbers == false){
                    chart.getAxisSet().getYAxis(0).setRange(new Range(0,100));
                } else {
                    chart.getAxisSet().getYAxis(0).adjustRange();
                }
                chart.redraw();
            }
        });
    }

    ArrayList<Interval> getIntervalslimits(Long maxvalue, Long minvalue, Long nb) {

        ArrayList<Interval> intervals = new ArrayList<>();
        //if we have many requests
        long size = maxvalue - minvalue;
        long n = nb;
        if (n > size){
            n = size; // the number of intervals should not be bigger than the range of latencies
        }
        for (int i = 0; i < n; i++) {
            long h0 = minvalue + size * i / n;
            long h1 = minvalue + size * (i + 1) / n;
            intervals.add(new Interval(h0, h1));
        }
        return intervals;
    }

    void setNumIntervals(Long number) {
        this.num_intervals = number;
        drawChart();
    }

    void setSelectedMin(Long sMin) {
        this.selectedMin = sMin;
        drawChart();
    }

    void setSelectedMax(Long sMax) {
        this.selectedMax = sMax;
        drawChart();
    }

    void setDisplayNumbers(boolean displayNumbers){
        this.displayNumbers = displayNumbers;
        drawChart();
    }
}
