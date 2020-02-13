/*******************************************************************************
 * Copyright (c) 2013, 2015 Ericsson
 *
 * All rights reserved. This program and the accompanying materials are
 * made available under the terms of the Eclipse Public License v1.0 which
 * accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *   Bernd Hufmann - Initial API and implementation
 *******************************************************************************/
package org.eclipse.tracecompass.incubator.internal.kernel.ui.views.io.latencydistributions;

import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.SashForm;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.events.SelectionListener;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Scale;
import org.eclipse.swt.widgets.Spinner;
import org.eclipse.tracecompass.tmf.core.signal.TmfTraceSelectedSignal;
import org.eclipse.tracecompass.tmf.core.trace.ITmfTrace;
import org.eclipse.tracecompass.tmf.core.trace.TmfTraceManager;
import org.eclipse.tracecompass.tmf.ui.views.TmfView;

/**
 * View of request latencies
 *
 * @author Houssem Daoud
 * @since 2.0
 */
public class LatencyDistributionView extends TmfView {
    /** The view ID. */
    public static final String ID = "org.eclipse.tracecompass.analysis.os.linux.views.latencyhistogram"; //$NON-NLS-1$
    private Spinner spinner;
    private Scale scale;
    private Scale scale_1;
    private Spinner spinner_1;
    private Label lblMax;
    private Label lblMin;
    private Label label_1;
    private Label label;
    private Label lblPrecision;
    private Scale scale_2;
    private Spinner spinner_2;
    private Button checkbox;
    private LatencyDistributionViewer latencyDistributionViewer;
    private int num_intervals = 5 ;

    /**
     * Default Constructor
     */
    public LatencyDistributionView() {
        super(ID);
    }

    @Override
    public void createPartControl(Composite parent) {
        Composite composite = new Composite(parent, SWT.NONE);
        GridLayout gl_composite = new GridLayout(12, false);
        gl_composite.verticalSpacing = 1;
        composite.setLayout(gl_composite);
        lblMax = new Label(composite, SWT.NONE);
        lblMax.setText("Max"); //$NON-NLS-1$
        scale = new Scale(composite, SWT.NONE);
        GridData gd_scale = new GridData(SWT.LEFT, SWT.CENTER, false, false, 1, 1);
        gd_scale.widthHint = 100;
        scale.setLayoutData(gd_scale);
        spinner = new Spinner(composite, SWT.BORDER);
        GridData gd_text = new GridData(SWT.FILL, SWT.CENTER, false, false, 1, 1);
        spinner.setLayoutData(gd_text);
        label_1 = new Label(composite, SWT.SEPARATOR | SWT.VERTICAL);
        GridData gd_label_1 = new GridData(SWT.FILL, SWT.CENTER, true, false, 1, 1);
        gd_label_1.heightHint = 40;
        label_1.setLayoutData(gd_label_1);
        lblMin = new Label(composite, SWT.NONE);
        lblMin.setText("Min"); //$NON-NLS-1$
        scale_1 = new Scale(composite, SWT.NONE);
        GridData gd_scale_1 = new GridData(SWT.LEFT, SWT.CENTER, false, false, 1, 1);
        gd_scale_1.widthHint = 100;
        scale_1.setLayoutData(gd_scale_1);
        spinner_1 = new Spinner(composite, SWT.BORDER);
        GridData gd_text_1 = new GridData(SWT.FILL, SWT.CENTER, false, false, 1, 1);
        gd_text_1.widthHint = 60;
        spinner_1.setLayoutData(gd_text);
        label = new Label(composite, SWT.SEPARATOR | SWT.VERTICAL);
        GridData gd_label = new GridData(SWT.CENTER, SWT.CENTER, true, false, 1, 1);
        gd_label.heightHint = 40;
        label.setLayoutData(gd_label);
        lblPrecision = new Label(composite, SWT.NONE);
        lblPrecision.setText("Precision"); //$NON-NLS-1$
        scale_2 = new Scale(composite, SWT.NONE);
        GridData gd_scale_2 = new GridData(SWT.LEFT, SWT.CENTER, false, false, 1, 1);
        gd_scale_2.widthHint = 100;
        scale_2.setLayoutData(gd_scale_2);
        spinner_2 = new Spinner(composite, SWT.NONE);
        GridData gd_spinner_2 = new GridData(SWT.LEFT, SWT.CENTER, true, false, 1, 1);
        spinner_2.setLayoutData(gd_spinner_2);
        checkbox = new Button(composite, SWT.CHECK | SWT.BORDER);
        checkbox.setText("Numbers"); //$NON-NLS-1$
        GridData gd_checkbox = new GridData(SWT.LEFT, SWT.CENTER, true, false, 1, 1);
        checkbox.setLayoutData(gd_checkbox);
        SashForm sashForm = new SashForm(composite, SWT.BORDER);
        sashForm.setLayoutData(new GridData(SWT.FILL, SWT.FILL, false, true, 12, 1));

        latencyDistributionViewer = new LatencyDistributionViewer(sashForm, this, "Titre","xLabel","yLabel", new Long(num_intervals));  //$NON-NLS-1$//$NON-NLS-2$ //$NON-NLS-3$
        ITmfTrace trace = TmfTraceManager.getInstance().getActiveTrace();
        if (trace != null) {
            TmfTraceSelectedSignal signal = new TmfTraceSelectedSignal(this, trace);
            latencyDistributionViewer.traceSelected(signal);
        }

        scale.addSelectionListener(new SelectionListener() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                int perspectivevalue = scale.getSelection();
                spinner.setSelection(perspectivevalue);
                // change the maximum of MIN
                scale_1.setMaximum(perspectivevalue);
                spinner_1.setMaximum(perspectivevalue);
                latencyDistributionViewer.setSelectedMax(new Long(perspectivevalue));
            }
            @Override
            public void widgetDefaultSelected(SelectionEvent e) {
                // TODO Auto-generated method stub

            }
        });

        spinner.addSelectionListener(new SelectionListener() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                int perspectivevalue = spinner.getSelection();
                scale.setSelection(perspectivevalue);
                // change the maximum of MIN
                scale_1.setMaximum(perspectivevalue);
                spinner_1.setMaximum(perspectivevalue);
                latencyDistributionViewer.setSelectedMax(new Long(perspectivevalue));
            }
            @Override
            public void widgetDefaultSelected(SelectionEvent e) {
                // TODO Auto-generated method stub

            }
        });

        scale_1.addSelectionListener(new SelectionListener() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                int perspectivevalue = scale_1.getSelection();
                spinner_1.setSelection(perspectivevalue);
                // change the maximum of MIN
                scale.setMinimum(perspectivevalue);
                spinner.setMinimum(perspectivevalue);
                latencyDistributionViewer.setSelectedMin(new Long(perspectivevalue));
            }
            @Override
            public void widgetDefaultSelected(SelectionEvent e) {
                // TODO Auto-generated method stub

            }
        });

        spinner_1.addSelectionListener(new SelectionListener() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                int perspectivevalue = spinner_1.getSelection();
                scale_1.setSelection(perspectivevalue);
                // change the maximum of MIN
                scale.setMinimum(perspectivevalue);
                spinner.setMinimum(perspectivevalue);
                latencyDistributionViewer.setSelectedMin(new Long(perspectivevalue));
            }
            @Override
            public void widgetDefaultSelected(SelectionEvent e) {
                // TODO Auto-generated method stub

            }
        });

        spinner_2.setSelection(num_intervals);
        spinner_2.setMaximum(30);
        spinner_2.setMinimum(1);
        scale_2.setSelection(num_intervals);
        scale_2.setMaximum(30);
        scale_2.setMinimum(1);
        scale_2.addSelectionListener(new SelectionListener() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                int perspectivevalue = scale_2.getSelection();
                spinner_2.setSelection(perspectivevalue);
                latencyDistributionViewer.setNumIntervals(new Long(perspectivevalue));
            }
            @Override
            public void widgetDefaultSelected(SelectionEvent e) {
                // TODO Auto-generated method stub

            }
        });
        spinner_2.addSelectionListener(new SelectionListener() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                int perspectivevalue = spinner_2.getSelection();
                scale_2.setSelection(perspectivevalue);
                latencyDistributionViewer.setNumIntervals(new Long(perspectivevalue));
            }
            @Override
            public void widgetDefaultSelected(SelectionEvent e) {
                // TODO Auto-generated method stub

            }
        });

        checkbox.addSelectionListener(new SelectionListener() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                boolean selected = checkbox.getSelection();
                latencyDistributionViewer.setDisplayNumbers(selected);
            }
            @Override
            public void widgetDefaultSelected(SelectionEvent e) {
                // TODO Auto-generated method stub

            }
        });
    }

    @Override
    public void setFocus() {
        // TODO Auto-generated method stub

    }

    @SuppressWarnings("javadoc")
    public void setLimits(Long max, Long min) {
        spinner.setMaximum(max.intValue());
        spinner.setSelection(max.intValue());
        scale.setMaximum(max.intValue());
        scale.setSelection(max.intValue());
        spinner_1.setMinimum(min.intValue());
        spinner_1.setSelection(min.intValue());
        scale_1.setMinimum(min.intValue());
        scale_1.setSelection(min.intValue());
    }
}
