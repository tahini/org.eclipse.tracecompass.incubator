/*******************************************************************************
 * Copyright (c) 2017 École Polytechnique de Montréal
 *
 * All rights reserved. This program and the accompanying materials are
 * made available under the terms of the Eclipse Public License v1.0 which
 * accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/

package org.eclipse.tracecompass.incubator.web.analysis.core.event.matching;

import java.util.Objects;

import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.tracecompass.tmf.core.event.ITmfEvent;
import org.eclipse.tracecompass.tmf.core.event.ITmfEventField;
import org.eclipse.tracecompass.tmf.core.event.matching.IEventMatchingKey;
import org.eclipse.tracecompass.tmf.core.event.matching.ITmfMatchEventDefinition;
import org.eclipse.tracecompass.tmf.core.event.matching.TmfEventMatching.Direction;
import org.eclipse.tracecompass.tmf.core.trace.ITmfTrace;

/**
 * Matches LAMP threads
 *
 * @author Geneviève Bastien
 */
public class LampEventMatching implements ITmfMatchEventDefinition {

    private static class PhpMysqlConnection implements IEventMatchingKey {

        private final long fConnectionId;

        public PhpMysqlConnection(long connectionId) {
            fConnectionId = connectionId;
        }

        @Override
        public int hashCode() {
            return Objects.hash(fConnectionId);
        }

        @Override
        public boolean equals(@Nullable Object obj) {
            if (!(obj instanceof PhpMysqlConnection)) {
                return false;
            }
            return ((PhpMysqlConnection) obj).fConnectionId == fConnectionId;
        }

    }

    private static final String PHP_MYSQL_CONNECT = "ust_php:php_mysql_connect";
    private static final String MYSQL_RECV = "ust_mysql:command_start";

    @Override
    public IEventMatchingKey getEventKey(@Nullable ITmfEvent event) {
        if (event == null) {
            throw new NullPointerException("Event should not be null"); //$NON-NLS-1$
        }
        String name = event.getName();
        ITmfEventField content = event.getContent();
        if (name.equals(PHP_MYSQL_CONNECT)) {
            Long fieldValue = content.getFieldValue(Long.class, "connection_id");
            if (fieldValue == null) {
                throw new NullPointerException();
            }
            return new PhpMysqlConnection(fieldValue);
        }
        if (name.equals(MYSQL_RECV)) {
            Long fieldValue = content.getFieldValue(Long.class, "thread_id");
            if (fieldValue == null) {
                throw new NullPointerException();
            }
            return new PhpMysqlConnection(fieldValue);
        }
        throw new NullPointerException();
    }

    @Override
    public boolean canMatchTrace(@Nullable ITmfTrace trace) {
        return true;
    }

    @Override
    public @Nullable Direction getDirection(@Nullable ITmfEvent event) {
        if (event == null) {
            return null;
        }
        String eventName = event.getName();
        if (eventName.equals(PHP_MYSQL_CONNECT)) {
            return Direction.CAUSE;
        }
        if (eventName.equals(MYSQL_RECV)) {
            return Direction.EFFECT;
        }
        return null;
    }

}
