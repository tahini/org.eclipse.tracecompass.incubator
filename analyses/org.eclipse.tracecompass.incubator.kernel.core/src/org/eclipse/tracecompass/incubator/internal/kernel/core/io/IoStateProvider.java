/*******************************************************************************
 * Copyright (c) 2018 Ericsson
 *
 * All rights reserved. This program and the accompanying materials are
 * made available under the terms of the Eclipse Public License 2.0 which
 * accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/

package org.eclipse.tracecompass.incubator.internal.kernel.core.io;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.tracecompass.analysis.os.linux.core.event.aspect.LinuxTidAspect;
import org.eclipse.tracecompass.analysis.os.linux.core.trace.IKernelAnalysisEventLayout;
import org.eclipse.tracecompass.analysis.os.linux.core.trace.IKernelTrace;
import org.eclipse.tracecompass.incubator.internal.kernel.core.Activator;
import org.eclipse.tracecompass.statesystem.core.ITmfStateSystem;
import org.eclipse.tracecompass.statesystem.core.ITmfStateSystemBuilder;
import org.eclipse.tracecompass.statesystem.core.StateSystemBuilderUtils;
import org.eclipse.tracecompass.statesystem.core.exceptions.AttributeNotFoundException;
import org.eclipse.tracecompass.statesystem.core.exceptions.StateValueTypeException;
import org.eclipse.tracecompass.tmf.core.event.ITmfEvent;
import org.eclipse.tracecompass.tmf.core.statesystem.AbstractTmfStateProvider;
import org.eclipse.tracecompass.tmf.core.statesystem.ITmfStateProvider;
import org.eclipse.tracecompass.tmf.core.statesystem.TmfAttributePool;
import org.eclipse.tracecompass.tmf.core.trace.TmfTraceUtils;

import com.google.common.collect.ImmutableList;

/**
 * <p>
 * File Descriptor State Provider
 * </p>
 * <p>
 * This allows handling of generic file descriptors with common operations
 * </p>
 * <p>
 * Common elements
 * </p>
 * <ul>
 * <li>read</li>
 * <li>write</li>
 * <li>close</li>
 * </ul>
 *
 * @author Matthew Khouzam
 */
public class IoStateProvider extends AbstractTmfStateProvider {

    /**
     * ID of this state provider
     */
    public static final String ID = "org.eclipse.tracecompass.incubator.io.stateprovider"; //$NON-NLS-1$

    /**
     * TID field in state system
     */
    public static final String ATTRIBUTE_TID = "TID"; //$NON-NLS-1$
    /**
     * Resources field in state system
     */
    public static final String RESOURCES = "RES"; //$NON-NLS-1$
    /**
     * TID field in state system
     */
    public static final String ATTRIBUTE_FDTBL = "FDTBL"; //$NON-NLS-1$

    /**
     * Read entry
     */
    public static final String ATTRIBUTE_READ = "READ"; //$NON-NLS-1$
    /**
     * Write entry
     */
    public static final String ATTRIBUTE_WRITE = "WRITE"; //$NON-NLS-1$

    /** Various syscall names for different purposes */
    private static final Collection<String> OPEN_FROM_DISK = ImmutableList.of("open", "openat");  //$NON-NLS-1$//$NON-NLS-2$
    private static final Collection<String> OPEN_FROM_NET = ImmutableList.of("socket"); //$NON-NLS-1$
    private static final Collection<String> DUP_SYSCALLS = ImmutableList.of("fcntl", "dup", "dup2", "dup3");   //$NON-NLS-1$//$NON-NLS-2$//$NON-NLS-3$ //$NON-NLS-4$
    private static final Collection<String> SYNC_SYSCALLS = ImmutableList.of("sync", "sync_file_range", "fsync", "fdatasync");  //$NON-NLS-1$ //$NON-NLS-2$//$NON-NLS-3$ //$NON-NLS-4$
    private static final Collection<String> READ_SYSCALLS = ImmutableList.of("read", "recvmsg", "recvfrom", "readv", "pread", "pread64", "preadv"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$ //$NON-NLS-6$ //$NON-NLS-7$
    private static final Collection<String> WRITE_SYSCALLS = ImmutableList.of("write", "sendmsg", "sendto", "writev", "pwrite", "pwrite64", "pwritev");   //$NON-NLS-1$//$NON-NLS-2$ //$NON-NLS-3$//$NON-NLS-4$ //$NON-NLS-5$ //$NON-NLS-6$ //$NON-NLS-7$
    private static final Collection<String> CLOSE_SYSCALL = ImmutableList.of("close"); //$NON-NLS-1$
    private static final Collection<String> READ_WRITE_SYSCALLS = ImmutableList.of("splice", "senfile64");  //$NON-NLS-1$//$NON-NLS-2$
    private static final String LTTNG_STATEDUMP_FILE_DESCRIPTOR = "lttng_statedump_file_descriptor"; //$NON-NLS-1$


    /** Event fieLD names */
    private static final String FIELD_FILENAME = "filename"; //$NON-NLS-1$
    private static final String FIELD_PID = "pid"; //$NON-NLS-1$
    private static final String FIELD_OLDFD = "oldfd"; //$NON-NLS-1$
    private static final String FIELD_FILDES = "fildes"; //$NON-NLS-1$
    private static final String FIELD_DESCRIPTOR = "fd"; //$NON-NLS-1$
    private static final String FIELD_FDIN = "fd_in"; //$NON-NLS-1$
    private static final String FIELD_FDOUT = "fd_out"; //$NON-NLS-1$
    private static final String FIELD_LEN = "len"; //$NON-NLS-1$

    private static final String UNKNOWN_FILE = "<unknown>"; //$NON-NLS-1$

    private static final int VERSION = 3;



    private final Map<String, EventConsumer> fHandlers = new HashMap<>();
    private final IKernelAnalysisEventLayout fLayout;

    /*
     * Nullable to make the jdt be quiet
     */
    private final Map<Integer, @Nullable FdRequestWithPools> fToRead = new HashMap<>();
    private final Map<Integer, @Nullable FdRequestWithPools> fToWrite = new HashMap<>();
    /* Map a TID to the file being opened */
    private final Map<Integer, String> fOpening = new HashMap<>();
    /* Map a TID to the file descriptor being closed */
    private final Map<Integer, Long> fClosing = new HashMap<>();
    /* Map a quark to attribute pool */
    private final Map<Integer, TmfAttributePool> fPools = new HashMap<>();

    @FunctionalInterface
    private interface EventConsumer {
        void handleEvent(ITmfStateSystemBuilder ssb, ITmfEvent event, Integer tid);
    }

    private static class FdRequestWithPools {

        private final Long fFd;
        private final Integer fFdPoolQuark;
        private final Integer fTidPoolQuark;
        private final TmfAttributePool fFdPool;
        private final TmfAttributePool fTidPool;

        public FdRequestWithPools(Long fd, TmfAttributePool fdPool, Integer fdPoolQuark, TmfAttributePool tidPool, Integer tidPoolQuark) {
            fFd = fd;
            fFdPool = fdPool;
            fFdPoolQuark = fdPoolQuark;
            fTidPool = tidPool;
            fTidPoolQuark = tidPoolQuark;
        }
    }

    /**
     * Constructor
     *
     * @param trace
     *            The trace
     */
    public IoStateProvider(IKernelTrace trace) {
        super(trace, ID);
        fLayout = trace.getKernelEventLayout();

        for (String syscall : OPEN_FROM_DISK) {
            addEventHandler(getLayout().eventSyscallEntryPrefix() + syscall, this::openBegin);
            addEventHandler(getLayout().eventSyscallExitPrefix() + syscall, this::openEnd);
        }
        for (String syscall : OPEN_FROM_NET) {
            addEventHandler(getLayout().eventSyscallEntryPrefix() + syscall, this::netBegin);
            addEventHandler(getLayout().eventSyscallExitPrefix() + syscall, this::netEnd);
        }
        for (String syscall : DUP_SYSCALLS) {
            addEventHandler(getLayout().eventSyscallEntryPrefix() + syscall, this::dupBegin);
            addEventHandler(getLayout().eventSyscallExitPrefix() + syscall, this::dupEnd);
        }
        for (String syscall : SYNC_SYSCALLS) {
            addEventHandler(getLayout().eventSyscallEntryPrefix() + syscall, this::syncBegin);
            addEventHandler(getLayout().eventSyscallExitPrefix() + syscall, this::syncEnd);
        }
        for (String syscall : READ_SYSCALLS) {
            addEventHandler(getLayout().eventSyscallEntryPrefix() + syscall, this::readBegin);
            addEventHandler(getLayout().eventSyscallExitPrefix() + syscall, this::readEnd);
        }
        for (String syscall : WRITE_SYSCALLS) {
            addEventHandler(getLayout().eventSyscallEntryPrefix() + syscall, this::writeBegin);
            addEventHandler(getLayout().eventSyscallExitPrefix() + syscall, this::writeEnd);
        }
        for (String syscall : READ_WRITE_SYSCALLS) {
            addEventHandler(getLayout().eventSyscallEntryPrefix() + syscall, this::readWriteBegin);
            addEventHandler(getLayout().eventSyscallExitPrefix() + syscall, this::readWriteEnd);
        }
        for (String syscall : CLOSE_SYSCALL) {
            addEventHandler(getLayout().eventSyscallEntryPrefix() + syscall, this::closeBegin);
            addEventHandler(getLayout().eventSyscallExitPrefix() + syscall, this::closeEnd);
        }
        addEventHandler(LTTNG_STATEDUMP_FILE_DESCRIPTOR, this::statedumpHandle);
    }

    /**
     * Add a handler for entries
     *
     * @param eventName
     *            the name to handle
     * @param handler
     *            handler
     */
    protected final void addEventHandler(String eventName, EventConsumer handler) {
        fHandlers.put(eventName, handler);
    }

    @Override
    protected final void eventHandle(@NonNull ITmfEvent event) {
        EventConsumer eventHandler = fHandlers.get(event.getName());
        if (eventHandler == null) {
            return;
        }

        ITmfStateSystemBuilder ssb = getStateSystemBuilder();
        if (ssb == null) {
            return;
        }
        Integer tid = TmfTraceUtils.resolveIntEventAspectOfClassForEvent(event.getTrace(), LinuxTidAspect.class, event);
        if (tid == null) {
            return;
        }

        eventHandler.handleEvent(ssb, event, tid);
    }

    /**
     * Get the event layout of this trace. Many known concepts from the Linux
     * kernel may be exported under different names, depending on the tracer.
     *
     * @return The event layout
     */
    protected final IKernelAnalysisEventLayout getLayout() {
        return fLayout;
    }

    /**
     * Check if a file descriptor is valid. Has it been opened? if not, let's
     * ignore it for now.
     *
     * @param ssb
     *            State system
     * @param tid
     *            the TID that owns the file descriptor
     * @param fd
     *            the file descriptor
     * @return the file descriptor or null if invalid
     */
    protected static final @Nullable Long isValidFileDescriptor(ITmfStateSystem ssb, Integer tid, @Nullable Long fd) {
        if (fd == null) {
            return null;
        }
        int tidFileQuark = ssb.optQuarkAbsolute(ATTRIBUTE_TID, String.valueOf(tid), ATTRIBUTE_FDTBL, String.valueOf(fd));
        if (tidFileQuark == ITmfStateSystem.INVALID_ATTRIBUTE) {
            return null;
        }
        return ssb.queryOngoing(tidFileQuark) != null ? fd : null;
    }

    /**
     * @param ssb
     */
    private void openBegin(ITmfStateSystemBuilder ssb, ITmfEvent event, Integer tid) {
        String filename = event.getContent().getFieldValue(String.class, FIELD_FILENAME);
        fOpening.put(tid, filename != null ? filename : UNKNOWN_FILE);
    }

    private void openEnd(ITmfStateSystemBuilder ssb, ITmfEvent event, Integer tid) {
        String filename = fOpening.remove(tid);

        Long ret = event.getContent().getFieldValue(Long.class, fLayout.fieldSyscallRet());
        if (ret == null) {
            return;
        }

        if (ret > 0) {
            openFile(ssb, event.getTimestamp().toNanos(), tid, ret, filename);
        }
        // TODO: Do something about erroneous opens?

    }

    /**
     * @param ssb
     * @param event
     * @param tid
     */
    private void netBegin(ITmfStateSystemBuilder ssb, ITmfEvent event, Integer tid) {
        // TODO Support network IO
    }

    /**
     * @param ssb
     * @param event
     * @param tid
     */
    private void netEnd(ITmfStateSystemBuilder ssb, ITmfEvent event, Integer tid) {
        // TODO Support network IO
    }

    private void dupBegin(ITmfStateSystemBuilder ssb, ITmfEvent event, Integer tid) {
        Long oldFd = event.getContent().getFieldValue(Long.class, FIELD_OLDFD);
        if (oldFd == null) {
            // Maybe it's the dup syscall with the fildes field
            oldFd = event.getContent().getFieldValue(Long.class, FIELD_FILDES);
        }
        // TODO Handle the fcntl syscall
        if (oldFd == null) {
            return;
        }
        int oldFdQuark = ssb.optQuarkAbsolute(ATTRIBUTE_TID, String.valueOf(tid), ATTRIBUTE_FDTBL, String.valueOf(oldFd));
        // Get the file to dup
        String filename = UNKNOWN_FILE;
        if (oldFdQuark != ITmfStateSystem.INVALID_ATTRIBUTE) {
            Object fileObj = ssb.queryOngoing(oldFdQuark);
            if (fileObj instanceof String) {
                filename = (String) fileObj;
            }
        }
        fOpening.put(tid, filename);
    }

    private void dupEnd(ITmfStateSystemBuilder ssb, ITmfEvent event, Integer tid) {
        String filename = fOpening.remove(tid);
        // ret is the new fd, whether for a dup, dup2 or dup3 call
        Long newFd = event.getContent().getFieldValue(Long.class, fLayout.fieldSyscallRet());
        if (newFd == null || newFd < 0) {
            return;
        }

        // Close the previous fd if required, then add the new file
        long time = event.getTimestamp().toNanos();
        closeFile(ssb, time, tid, newFd);
        openFile(ssb, event.getTimestamp().toNanos(), tid, newFd, filename);
    }

    /**
     * @param ssb
     * @param event
     * @param tid
     */
    private void syncBegin(ITmfStateSystemBuilder ssb, ITmfEvent event, Integer tid) {
        // TODO Support sync, there should be disk requests in there, or at
        // least something
    }

    /**
     * @param ssb
     * @param event
     * @param tid
     */
    private void syncEnd(ITmfStateSystemBuilder ssb, ITmfEvent event, Integer tid) {
        // TODO Support sync, there should be disk requests in there, or at
        // least something
    }

    /**
     * @param ssb
     */
    private void readBegin(ITmfStateSystemBuilder ssb, ITmfEvent event, Integer tid) {
        Long fd = (event.getContent().getFieldValue(Long.class, FIELD_DESCRIPTOR));
        Long len = (event.getContent().getFieldValue(Long.class, FIELD_LEN));
        if (fd == null) {
            return;
        }
        startReadingFd(ssb, event.getTimestamp().toNanos(), tid, fd, len == null ? 0 : len);
    }

    private void readEnd(ITmfStateSystemBuilder ssb, ITmfEvent event, Integer tid) {
        long time = event.getTimestamp().toNanos();
        Long count = (event.getContent().getFieldValue(Long.class, getLayout().fieldSyscallRet()));
        readFromFd(ssb, time, tid, count == null ? 0L: count);
    }

    /**
     * @param ssb
     */
    private void writeBegin(ITmfStateSystemBuilder ssb, ITmfEvent event, Integer tid) {
        Long fd = (event.getContent().getFieldValue(Long.class, FIELD_DESCRIPTOR));
        Long len = (event.getContent().getFieldValue(Long.class, FIELD_LEN));
        if (fd == null) {
            return;
        }
        startWritingFd(ssb, event.getTimestamp().toNanos(), tid, fd, len == null ? 0 : len);
    }

    private void writeEnd(ITmfStateSystemBuilder ssb, ITmfEvent event, Integer tid) {
        long time = event.getTimestamp().toNanos();
        Long count = (event.getContent().getFieldValue(Long.class, getLayout().fieldSyscallRet()));
        writeToFd(ssb, time, tid, count == null ? 0L: count);
    }

    /**
     * @param ssb
     */
    private void readWriteBegin(ITmfStateSystemBuilder ssb, ITmfEvent event, Integer tid) {
        Long fdIn = event.getContent().getFieldValue(Long.class, FIELD_FDIN);
        Long fdOut = event.getContent().getFieldValue(Long.class, FIELD_FDOUT);
        if (fdIn == null || fdOut == null) {
            // We don't know about one of the files
            return;
        }
        startReadingFd(ssb, event.getTimestamp().toNanos(), tid, fdIn, 0L);
        startWritingFd(ssb, event.getTimestamp().toNanos(), tid, fdOut, 0L);
        // TODO add support of sendfile
    }

    private void readWriteEnd(ITmfStateSystemBuilder ssb, ITmfEvent event, Integer tid) {
        long time = event.getTimestamp().toNanos();
        Long count = (event.getContent().getFieldValue(Long.class, getLayout().fieldSyscallRet()));
        readFromFd(ssb, time, tid, count == null ? 0L: count);
        writeToFd(ssb, time, tid, count == null ? 0L: count);
    }

    private void closeBegin(ITmfStateSystemBuilder ssb, ITmfEvent event, Integer tid) {
        Long fd = (event.getContent().getFieldValue(Long.class, FIELD_DESCRIPTOR));
        fd = isValidFileDescriptor(ssb, tid, fd);
        if (fd == null) {
            return;
        }
        fClosing.put(tid, fd);
    }

    private void closeEnd(ITmfStateSystemBuilder ssb, ITmfEvent event, Integer tid) {
        try {
            Long ret = (event.getContent().getFieldValue(Long.class, getLayout().fieldSyscallRet()));
            Long fd = fClosing.remove(tid);
            if (ret == null || fd == null || ret < 0) {
                return;
            }
            closeFile(ssb, event.getTimestamp().toNanos(), tid, fd);
        } catch (StateValueTypeException e) {
            Activator.getInstance().logError(e.getMessage(), e);
        }
    }

    private static void closeFile(ITmfStateSystemBuilder ssb, long time, Integer tid, Long fd) {
        int fdQuark = ssb.getQuarkAbsoluteAndAdd(ATTRIBUTE_TID, String.valueOf(tid), ATTRIBUTE_FDTBL, String.valueOf(fd));
        ssb.removeAttribute(time, fdQuark);

        // TODO Close the file for this thread in the Resources section
    }

    private static void openFile(ITmfStateSystemBuilder ssb, long time, Integer tid, Long fd, @Nullable String filename) {
        int fdQuark = ssb.getQuarkAbsoluteAndAdd(ATTRIBUTE_TID, String.valueOf(tid), ATTRIBUTE_FDTBL, String.valueOf(fd));
        if (time < 0) {
            ssb.updateOngoingState(filename, fdQuark);
        } else {
            ssb.modifyAttribute(time, filename != null ? filename : UNKNOWN_FILE, fdQuark);
        }
    }

    private void startReadingFd(ITmfStateSystemBuilder ssb, long time, Integer tid, Long fd, Long count) {
        startRwFd(ssb, time, tid, fd, count, ATTRIBUTE_READ, fToRead);
    }

    private void startWritingFd(ITmfStateSystemBuilder ssb, long time, Integer tid, Long fd, Long count) {
        startRwFd(ssb, time, tid, fd, count, ATTRIBUTE_WRITE, fToWrite);
    }

    private void writeToFd(ITmfStateSystemBuilder ssb, long time, Integer tid, long count) {
        FdRequestWithPools fd = fToWrite.remove(tid);
        // No write was done
        if (fd == null) {
            return;
        }
        rwFromFd(ssb, time, tid, fd, count, ATTRIBUTE_WRITE);
    }

    private void readFromFd(ITmfStateSystemBuilder ssb, long time, Integer tid, long count) {
        FdRequestWithPools fd = fToRead.remove(tid);
        // No read was done
        if (fd == null) {
            return;
        }
        rwFromFd(ssb, time, tid, fd, count, ATTRIBUTE_READ);
    }

    private static void rwFromFd(ITmfStateSystemBuilder ssb, long time, Integer tid, FdRequestWithPools fd, Long count, String attribute) {
        Long validFd = isValidFileDescriptor(ssb, tid, fd.fFd);

        // Complete the attribute that are for pools and recycle those pools
        ssb.updateOngoingState(count > 0 ? count : (Object) null, fd.fFdPoolQuark);
        fd.fFdPool.recycle(fd.fFdPoolQuark, time);
        ssb.updateOngoingState(count > 0 ? count : (Object) null, fd.fTidPoolQuark);
        fd.fTidPool.recycle(fd.fTidPoolQuark, time);

        if (count <= 0) {
            // Return if the count < 0
            return;
        }

        if (validFd == null) {
            // The file is not opened in the state system, open it for this
            // thread
            openFile(ssb, time, tid, fd.fFd, null);
        }
        try {
            // Add the io specific to this file
            int fdQuark = ssb.getQuarkAbsoluteAndAdd(ATTRIBUTE_TID, String.valueOf(tid), ATTRIBUTE_FDTBL, String.valueOf(fd.fFd), attribute);
            StateSystemBuilderUtils.incrementAttributeLong(ssb, time, fdQuark, count);

            // Add the io for this thread
            int tidReadQuark = ssb.getQuarkAbsoluteAndAdd(ATTRIBUTE_TID, String.valueOf(tid), attribute);
            StateSystemBuilderUtils.incrementAttributeLong(ssb, time, tidReadQuark, count);
        } catch (StateValueTypeException | AttributeNotFoundException e) {
            Activator.getInstance().logError(e.getMessage(), e);
        }
    }

    private void startRwFd(ITmfStateSystemBuilder ssb, long time, Integer tid, Long fd, Long count, String attribute, Map<Integer, @Nullable FdRequestWithPools> tidMap) {
        try {
            // Many threads can share the same fd table, so there can be multiple io requests on the same fd
            // Add the io request under the proper fd attribute
            int fdQuark = ssb.getQuarkAbsoluteAndAdd(ATTRIBUTE_TID, String.valueOf(tid), ATTRIBUTE_FDTBL, String.valueOf(fd), attribute);
            TmfAttributePool fdPool = fPools.computeIfAbsent(fdQuark, q -> new TmfAttributePool(ssb, q));
            int availableFdQuark = fdPool.getAvailable();
            ssb.modifyAttribute(time, count, availableFdQuark);

            // Add the io request for this thread
            int tidReadQuark = ssb.getQuarkAbsoluteAndAdd(ATTRIBUTE_TID, String.valueOf(tid), attribute);
            StateSystemBuilderUtils.incrementAttributeLong(ssb, time, tidReadQuark, count);
            TmfAttributePool tidPool = fPools.computeIfAbsent(tidReadQuark, q -> new TmfAttributePool(ssb, q));
            int availableIoQuark = tidPool.getAvailable();
            ssb.modifyAttribute(time, count, availableIoQuark);

            tidMap.put(tid, new FdRequestWithPools(fd, fdPool, availableFdQuark, tidPool, availableIoQuark));

        } catch (StateValueTypeException | AttributeNotFoundException e) {
            Activator.getInstance().logError(e.getMessage(), e);
        }
    }

    /**
     * @param tid
     */
    private void statedumpHandle(ITmfStateSystemBuilder ssb, ITmfEvent event, Integer tid) {
        Long pid = (event.getContent().getFieldValue(Long.class, FIELD_PID));
        Long fd = (event.getContent().getFieldValue(Long.class, FIELD_DESCRIPTOR));
        String filename = (event.getContent().getFieldValue(String.class, FIELD_FILENAME));
        if (pid == null || fd == null || filename == null) {
            return;
        }
        openFile(ssb, -1, tid, fd, filename);
    }

    @Override
    public int getVersion() {
        return VERSION;
    }

    @Override
    public ITmfStateProvider getNewInstance() {
        return new IoStateProvider((IKernelTrace) getTrace());
    }
}
