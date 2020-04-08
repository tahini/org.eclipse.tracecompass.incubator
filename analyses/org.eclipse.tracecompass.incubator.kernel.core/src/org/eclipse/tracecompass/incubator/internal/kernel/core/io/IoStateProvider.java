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
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.commons.lang3.StringUtils;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.tracecompass.analysis.os.linux.core.event.aspect.LinuxTidAspect;
import org.eclipse.tracecompass.analysis.os.linux.core.trace.IKernelAnalysisEventLayout;
import org.eclipse.tracecompass.analysis.os.linux.core.trace.IKernelTrace;
import org.eclipse.tracecompass.incubator.internal.kernel.core.Activator;
import org.eclipse.tracecompass.statesystem.core.ITmfStateSystem;
import org.eclipse.tracecompass.statesystem.core.ITmfStateSystemBuilder;
import org.eclipse.tracecompass.statesystem.core.StateSystemBuilderUtils;
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
 * In the state system, there are 3 root attributes, with the following
 * structure
 * <ul>
 * <li>TID: This section contains the information for each thread: the
 * read/write requests for each. The FDTBL sub-attribute contains the quark of
 * the file descriptor table, that can be shared between multiple threads</li>
 * <li>FDTBL: This is an attribute pool that contains a list of all file
 * descriptor tables. They have their own attribute instead of being under the
 * threads because some threads can share the same fd table. Each thread has a
 * link to the attribute for the correct fd table.</li>
 * <li>RES: Contains the files</li>
 * </ul>
 * </p>
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
    private static final Collection<String> OPEN_FROM_NET = ImmutableList.of("socket", "accept", "accept4", "connect"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
    private static final Collection<String> DUP_SYSCALLS = ImmutableList.of("fcntl", "dup", "dup2", "dup3");   //$NON-NLS-1$//$NON-NLS-2$//$NON-NLS-3$ //$NON-NLS-4$
    private static final Collection<String> SYNC_SYSCALLS = ImmutableList.of("sync", "sync_file_range", "fsync", "fdatasync");  //$NON-NLS-1$ //$NON-NLS-2$//$NON-NLS-3$ //$NON-NLS-4$
    private static final Collection<String> READ_SYSCALLS = ImmutableList.of("read", "recvmsg", "recvfrom", "readv", "pread", "pread64", "preadv"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$ //$NON-NLS-6$ //$NON-NLS-7$
    private static final Collection<String> WRITE_SYSCALLS = ImmutableList.of("write", "sendmsg", "sendto", "writev", "pwrite", "pwrite64", "pwritev");   //$NON-NLS-1$//$NON-NLS-2$ //$NON-NLS-3$//$NON-NLS-4$ //$NON-NLS-5$ //$NON-NLS-6$ //$NON-NLS-7$
    private static final Collection<String> CLOSE_SYSCALL = ImmutableList.of("close"); //$NON-NLS-1$
    private static final Collection<String> READ_WRITE_SYSCALLS = ImmutableList.of("splice", "senfile64");  //$NON-NLS-1$//$NON-NLS-2$
    private static final Collection<String> CLONE_SYSCALLS = ImmutableList.of("clone");  //$NON-NLS-1$

    /** Event fieLD names */
    private static final String FIELD_FILENAME = "filename"; //$NON-NLS-1$
    private static final String FIELD_PID = "pid"; //$NON-NLS-1$
    private static final String FIELD_OLDFD = "oldfd"; //$NON-NLS-1$
    private static final String FIELD_FILDES = "fildes"; //$NON-NLS-1$
    private static final String FIELD_DESCRIPTOR = "fd"; //$NON-NLS-1$
    private static final String FIELD_FDIN = "fd_in"; //$NON-NLS-1$
    private static final String FIELD_FDOUT = "fd_out"; //$NON-NLS-1$
    private static final String FIELD_LEN = "len"; //$NON-NLS-1$
    private static final String FIELD_CLONE_FLAGS = "clone_flags"; //$NON-NLS-1$
    private static final String FIELD_CMD = "cmd"; //$NON-NLS-1$
    private static final String FIELD_STATEDUMP_FILE_TABLE = "file_table_address"; //$NON-NLS-1$
    private static final long CLONE_FILES_FLAG = 0x400;
    private static final long FCNTL_CMD_DUP = 0;
    private static final long FCNTL_CMD_DUP_CLOEXEC = 1030;

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
    /* Map a TID to the file descriptor connecting */
    private final Map<Integer, Long> fConnecting = new HashMap<>();
    /* Map a quark to attribute pool */
    private final Map<Integer, TmfAttributePool> fPools = new HashMap<>();
    /* Map a TID to whether to share the file table (true) or not (false) */
    private final Map<Integer, Boolean> fCloning = new HashMap<>();
    /* Map a file table address from statedump to a quark */
    private final Map<Long, Integer> fFdTblAddresses = new HashMap<>();
    private AtomicInteger fFdCount = new AtomicInteger(0);


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
        for (String syscall : CLONE_SYSCALLS) {
            addEventHandler(getLayout().eventSyscallEntryPrefix() + syscall, this::cloneBegin);
            addEventHandler(getLayout().eventSyscallExitPrefix() + syscall, this::cloneEnd);
        }
        String statedumpFileEvent = fLayout.eventStatedumpFileDescriptor();
        if (statedumpFileEvent != null) {
            addEventHandler(statedumpFileEvent, this::statedumpHandle);
        }
        String statedumpStateEvent = fLayout.eventStatedumpProcessState();
        if (statedumpStateEvent != null) {
            addEventHandler(statedumpStateEvent, this::statedumpProcessHandle);
        }
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

    private final @Nullable Long isValidFileDescriptor(ITmfStateSystemBuilder ssb, long time, Integer tid, @Nullable Long fd) {
        if (fd == null) {
            return null;
        }
        int fdTblQuark = getFdTblQuarkFor(ssb, time, tid);
        int tidFileQuark = ssb.optQuarkRelative(fdTblQuark, String.valueOf(fd));
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

        if (filename != null) {
            // Prepare the file access quark and save a temporary value, to be
            // udpated in case of failure
            int fileTidQuark = ssb.getQuarkAbsoluteAndAdd(RESOURCES, filename, String.valueOf(tid));
            ssb.modifyAttribute(event.getTimestamp().toNanos(), 0L, fileTidQuark);
        }
    }

    private void openEnd(ITmfStateSystemBuilder ssb, ITmfEvent event, Integer tid) {
        String filename = fOpening.remove(tid);

        Long ret = event.getContent().getFieldValue(Long.class, fLayout.fieldSyscallRet());
        if (ret == null) {
            return;
        }
        long time = event.getTimestamp().toNanos();

        if (ret >= 0) {
            openFile(ssb, time, tid, ret, filename);
        }

        // Add the file to the resources section, whether there was an error or not
        if (filename != null) {
            int fileTidQuark = ssb.getQuarkAbsoluteAndAdd(RESOURCES, filename, String.valueOf(tid));
            if (ret < 0) {
                // There was an error opening the file, put the return value in
                // this file's resource
                ssb.updateOngoingState(ret, fileTidQuark);
                ssb.modifyAttribute(time, null, fileTidQuark);
            } else {
                // successful open, reset fd to null for before, and update the
                // fd at current time
                ssb.updateOngoingState((Object) null, fileTidQuark);
                ssb.modifyAttribute(time, ret, fileTidQuark);
            }
        }

    }

    private static String getV4Or6Address(ITmfEvent event) {
        Object v4addr = event.getContent().getFieldValue(Object.class, "v4addr");
        Object v6addr = event.getContent().getFieldValue(Object.class, "v6addr");
        Integer family = event.getContent().getFieldValue(Integer.class, "family");
        String socketFamily = LinuxSocketFamily.getSocketFamily(family != null ? family : 0);
        String connectTo = UNKNOWN_FILE;
        if ((v4addr instanceof long[]) && (v6addr instanceof long[])) {
            long[] addr4 = (long[]) v4addr;
            long[] addr6 = (long[]) v6addr;
            connectTo = Objects.requireNonNull(addr4.length != 0 ? StringUtils.join(addr4, '.') : (addr6.length != 0) ? StringUtils.join(addr6, ':') : UNKNOWN_FILE);
        }
        return connectTo + ':' + ' ' + socketFamily;
    }

    /**
     * @param ssb
     * @param event
     * @param tid
     */
    private void netBegin(ITmfStateSystemBuilder ssb, ITmfEvent event, Integer tid) {
        if (event.getName().contains("connect")) { //$NON-NLS-1$
            // Connect a socket to some server
            Long fd = event.getContent().getFieldValue(Long.class, FIELD_DESCRIPTOR);
            if (fd == null) {
                // Invalid FD return
                return;
            }
            fOpening.put(tid, getV4Or6Address(event));
            fConnecting.put(tid, fd);
        }

    }

    /**
     * @param ssb
     * @param event
     * @param tid
     */
    private void netEnd(ITmfStateSystemBuilder ssb, ITmfEvent event, Integer tid) {
        Long ret = event.getContent().getFieldValue(Long.class, fLayout.fieldSyscallRet());
        if (ret == null || ret < 0) {
            // Error or no info, return
            return;
        }
        if (event.getName().contains("socket")) { //$NON-NLS-1$
            // This is just a socket being opened, save the fd
            openFile(ssb, event.getTimestamp().toNanos(), tid, ret, "Socket"); //$NON-NLS-1$
        }
        if (event.getName().contains("connect")) { //$NON-NLS-1$
            // This is just a socket being opened, save the fd
            Long fd = fConnecting.get(tid);
            String serverAddr = fOpening.get(tid);
            if (fd != null && serverAddr != null) {
                openFile(ssb, event.getTimestamp().toNanos(), tid, fd, serverAddr);
            }
        }
        if (event.getName().contains("accept")) { //$NON-NLS-1$
            // A new socket has been created for direct communication
            String serverAddr = getV4Or6Address(event);
            openFile(ssb, event.getTimestamp().toNanos(), tid, ret, serverAddr);
        }
    }

    private void dupBegin(ITmfStateSystemBuilder ssb, ITmfEvent event, Integer tid) {
        Long oldFd = event.getContent().getFieldValue(Long.class, FIELD_OLDFD);
        if (oldFd == null) {
            // Maybe it's the dup syscall with the fildes field
            oldFd = event.getContent().getFieldValue(Long.class, FIELD_FILDES);
        }
        if (oldFd == null) {
            // Maybe it's the fcntl system call with dup
            Long fd = event.getContent().getFieldValue(Long.class, FIELD_DESCRIPTOR);
            Long cmd = event.getContent().getFieldValue(Long.class, FIELD_CMD);
            if (fd != null && cmd != null &&
                    ((cmd & FCNTL_CMD_DUP) == 1 || (cmd & FCNTL_CMD_DUP_CLOEXEC) == 1)) {
                oldFd = fd;
            }
        }
        if (oldFd == null) {
            return;
        }
        int fdTblQuark = getFdTblQuarkFor(ssb, event.getTimestamp().toNanos(), tid);
        int oldFdQuark = ssb.optQuarkRelative(fdTblQuark, String.valueOf(oldFd));
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

        // If it's the fcntl system call, it may not be a dup, so ignore the filename is null
        if (event.getName().contains("fcntl") && filename == null) { //$NON-NLS-1$
            return;
        }

        // Close the previous fd if required, then add the new file
        long time = event.getTimestamp().toNanos();
        closeFile(ssb, time, tid, newFd);
        openFile(ssb, time, tid, newFd, filename);
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
        fd = isValidFileDescriptor(ssb, event.getTimestamp().toNanos(), tid, fd);
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

    /**
     * @param ssb
     */
    private void cloneBegin(ITmfStateSystemBuilder ssb, ITmfEvent event, Integer tid) {
        // The clone system call has a flag which tells whether to share the
        // file table with the parent or not
        Long flags = event.getContent().getFieldValue(Long.class, FIELD_CLONE_FLAGS);
        if (flags == null) {
            return;
        }
        /*
         * If the CLONE_FILES flag is set, then the file descriptor table will
         * be shared with the child, so we put true, otherwise false will copy
         * the file descriptor table
         */
        fCloning.put(tid, (flags & CLONE_FILES_FLAG) == 0 ? false : true);
    }

    /**
     * @param ssb
     * @param event
     * @param tid
     */
    private void cloneEnd(ITmfStateSystemBuilder ssb, ITmfEvent event, Integer tid) {
        try {
            Long ret = (event.getContent().getFieldValue(Long.class, getLayout().fieldSyscallRet()));
            Boolean cloneFiles = fCloning.remove(tid);
            if (ret == null || cloneFiles == null || ret <= 0) {
                return;
            }
            long time = event.getTimestamp().toNanos();
            int parentFdTblQuark = getFdTblQuarkFor(ssb, time, tid);
            // ret is the thread ID of the child
            if (cloneFiles) {
                // Simply point the fdtbl of the child to that of the parent
                String fdTblId = ssb.getAttributeName(parentFdTblQuark);
                try {
                    int fdTblNb = Integer.parseInt(fdTblId);
                    int tidFdTblQuark = ssb.getQuarkAbsoluteAndAdd(ATTRIBUTE_TID, String.valueOf(ret), ATTRIBUTE_FDTBL);
                    ssb.modifyAttribute(time, fdTblNb, tidFdTblQuark);
                } catch (NumberFormatException e) {
                    // wrong fd table
                }
                return;
            }
            // Otherwise, copy all the files from the parent to the child
            int childFdTblQuark = getFdTblQuarkFor(ssb, time, ret.intValue());
            for (Integer fdQuark : ssb.getSubAttributes(parentFdTblQuark, false)) {
                Object currentFile = ssb.queryOngoing(fdQuark);
                if (currentFile != null) {
                    // Copy this file to the child fd table
                    int childFdQuark = ssb.getQuarkRelativeAndAdd(childFdTblQuark, ssb.getAttributeName(fdQuark));
                    ssb.modifyAttribute(time, currentFile, childFdQuark);
                }
            }
        } catch (StateValueTypeException e) {
            Activator.getInstance().logError(e.getMessage(), e);
        }
    }

    /**
     * @param tid
     */
    private void statedumpHandle(ITmfStateSystemBuilder ssb, ITmfEvent event, Integer tid) {
        Long pid = (event.getContent().getFieldValue(Long.class, FIELD_PID));
        Long fileTblAddress = event.getContent().getFieldValue(Long.class, FIELD_STATEDUMP_FILE_TABLE);
        Long fd = (event.getContent().getFieldValue(Long.class, FIELD_DESCRIPTOR));
        String filename = (event.getContent().getFieldValue(String.class, FIELD_FILENAME));
        if ((pid == null && fileTblAddress == null) || fd == null || filename == null) {
            return;
        }

        // Pre 2.12 have the pid field not null, simply open the file for this thread
        if (pid != null) {
            openFile(ssb, -1, pid.intValue(), fd, filename);
            int fileTidQuark = ssb.getQuarkAbsoluteAndAdd(RESOURCES, filename, String.valueOf(tid));
            // successful open, reset fd to null for before, and update the
            // fd at current time
            ssb.updateOngoingState(fd, fileTidQuark);
        }

        // LTTng 2.12+ have the file table address field, add this file to that file table
        Integer tblAddressQuark = fFdTblAddresses.get(fileTblAddress);
        if (tblAddressQuark == null) {
            // No process statedump has advertised this file table address, we
            // wouldn't know which process it's for, log an error and return
            Activator.getInstance().logWarning("Statedump file descriptor has an address field which has not been declared. Make sure to enable the lttng_statedump_process_state event, or maybe there are lost events?"); //$NON-NLS-1$
            return;
        }

        // Add this file descriptor
        int fdQuark = ssb.getQuarkRelativeAndAdd(tblAddressQuark, String.valueOf(fd));
        ssb.updateOngoingState(filename, fdQuark);

        // TODO Handle the RES section when the statedump has the file table
        // address, we'll need to find the corresponding pid/tid

    }

    /**
     * @param tid
     */
    private void statedumpProcessHandle(ITmfStateSystemBuilder ssb, ITmfEvent event, Integer tid) {
        // As of LTTng 2.12, a field file_table_address has been added and the
        // fd statedump uses this field
        Long fileTblAddress = event.getContent().getFieldValue(Long.class, FIELD_STATEDUMP_FILE_TABLE);
        Long procTid = event.getContent().getFieldValue(Long.class, getLayout().fieldTid());
        if (procTid == null || fileTblAddress == null) {
            // Older version of lttng or no tid, ignore
            return;
        }
        Integer fdTblQuark = fFdTblAddresses.get(fileTblAddress);
        if (fdTblQuark != null) {
            // This file table already exists from another process, just add the link to the current statedumped thread
            String fdTblId = ssb.getAttributeName(fdTblQuark);
            try {
                int fdTblInt = Integer.parseInt(fdTblId);
                int tidFdTblQuark = ssb.getQuarkAbsoluteAndAdd(ATTRIBUTE_TID, String.valueOf(procTid), ATTRIBUTE_FDTBL);
                Object currentTblId = ssb.queryOngoing(tidFdTblQuark);
                if (currentTblId == null) {
                    ssb.updateOngoingState(fdTblInt, tidFdTblQuark);
                } else {
                    // FIXME: There can be running file requests for the thread that
                    // should be merged with the new file table
                    ssb.modifyAttribute(event.getTimestamp().toNanos(), fdTblInt, tidFdTblQuark);
                }
            } catch (NumberFormatException e) {
                Activator.getInstance().logError("The file table ID is not an integer: " + fdTblId); //$NON-NLS-1$
            }
            return;
        }
        int fdTblId = fFdCount.getAndIncrement();
        fdTblQuark = ssb.getQuarkAbsoluteAndAdd(ATTRIBUTE_FDTBL, String.valueOf(fdTblId));
        // Save the table address quark so file descriptor statedump can use it
        fFdTblAddresses.put(fileTblAddress, fdTblQuark);

        // Add a link to the file table number in the statedumped process
        int tidFdTblQuark = ssb.getQuarkAbsoluteAndAdd(ATTRIBUTE_TID, String.valueOf(procTid), ATTRIBUTE_FDTBL);
        Object currentTblId = ssb.queryOngoing(tidFdTblQuark);
        if (currentTblId == null) {
            ssb.updateOngoingState(fdTblId, tidFdTblQuark);
        } else {
            // FIXME: There can be running file requests for the thread that
            // should be merged with the new file table
            ssb.modifyAttribute(event.getTimestamp().toNanos(), fdTblId, tidFdTblQuark);
        }

    }

    private void closeFile(ITmfStateSystemBuilder ssb, long time, Integer tid, Long fd) {
        int fdTblQuark = getFdTblQuarkFor(ssb, time, tid);
        int fdQuark = ssb.getQuarkRelativeAndAdd(fdTblQuark, String.valueOf(fd));
        ssb.removeAttribute(time, fdQuark);

        // Close the file for this thread in the Resources section
        Object filename = ssb.queryOngoing(fdQuark);
        if (filename instanceof String) {
            int fileTidQuark = ssb.optQuarkAbsolute(RESOURCES, String.valueOf(filename), String.valueOf(tid));
            if (fileTidQuark != ITmfStateSystem.INVALID_ATTRIBUTE) {
                ssb.modifyAttribute(time, null, fileTidQuark);
            }
        }
    }

    private void openFile(ITmfStateSystemBuilder ssb, long time, Integer tid, Long fd, @Nullable String filename) {
        int fdTblQuark = getFdTblQuarkFor(ssb, time, tid);
        int fdQuark = ssb.getQuarkRelativeAndAdd(fdTblQuark, String.valueOf(fd));
        if (time < 0) {
            ssb.updateOngoingState(filename, fdQuark);
        } else {
            ssb.modifyAttribute(time, filename != null ? filename : UNKNOWN_FILE, fdQuark);
        }
    }

    private int getFdTblQuarkFor(ITmfStateSystemBuilder ssb, long time, Integer tid) {
        // The fdtbl quark under the tid contains the quark of the actual file descriptor table
        int tidFdQuark = ssb.getQuarkAbsoluteAndAdd(ATTRIBUTE_TID, String.valueOf(tid), ATTRIBUTE_FDTBL);
        Object fdTblQuarkObj = ssb.queryOngoing(tidFdQuark);
        if (fdTblQuarkObj instanceof Integer) {
            return ssb.getQuarkAbsoluteAndAdd(ATTRIBUTE_FDTBL, String.valueOf(fdTblQuarkObj));
        }
        // The file descriptor table does not exist yet, add it
        int fdTblNumber = fFdCount.getAndIncrement();
        int fdTblQuark = ssb.getQuarkAbsoluteAndAdd(ATTRIBUTE_FDTBL, String.valueOf(fdTblNumber));
        ssb.modifyAttribute(time, fdTblNumber, tidFdQuark);
        return fdTblQuark;
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

    private void rwFromFd(ITmfStateSystemBuilder ssb, long time, Integer tid, FdRequestWithPools fd, Long count, String attribute) {
        Long validFd = isValidFileDescriptor(ssb, time, tid, fd.fFd);

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
            int fdTblQuark = getFdTblQuarkFor(ssb, time, tid);
            int fdQuark = ssb.getQuarkRelativeAndAdd(fdTblQuark, String.valueOf(fd.fFd), attribute);
            StateSystemBuilderUtils.incrementAttributeLong(ssb, time, fdQuark, count);

            // Add the io for this thread
            int tidReadQuark = ssb.getQuarkAbsoluteAndAdd(ATTRIBUTE_TID, String.valueOf(tid), attribute);
            StateSystemBuilderUtils.incrementAttributeLong(ssb, time, tidReadQuark, count);
        } catch (StateValueTypeException e) {
            Activator.getInstance().logError(e.getMessage(), e);
        }
    }

    private void startRwFd(ITmfStateSystemBuilder ssb, long time, Integer tid, Long fd, Long count, String attribute, Map<Integer, @Nullable FdRequestWithPools> tidMap) {
        try {
            // Many threads can share the same fd table, so there can be multiple io requests on the same fd
            // Add the io request under the proper fd attribute
            int fdTblQuark = getFdTblQuarkFor(ssb, time, tid);
            int fdQuark = ssb.getQuarkRelativeAndAdd(fdTblQuark, String.valueOf(fd), attribute);
            TmfAttributePool fdPool = fPools.computeIfAbsent(fdQuark, q -> new TmfAttributePool(ssb, q));
            int availableFdQuark = fdPool.getAvailable();
            ssb.modifyAttribute(time, count, availableFdQuark);

            // Add the io request for this thread
            int tidReadQuark = ssb.getQuarkAbsoluteAndAdd(ATTRIBUTE_TID, String.valueOf(tid), attribute);
            TmfAttributePool tidPool = fPools.computeIfAbsent(tidReadQuark, q -> new TmfAttributePool(ssb, q));
            int availableIoQuark = tidPool.getAvailable();
            ssb.modifyAttribute(time, count, availableIoQuark);

            tidMap.put(tid, new FdRequestWithPools(fd, fdPool, availableFdQuark, tidPool, availableIoQuark));

        } catch (StateValueTypeException e) {
            Activator.getInstance().logError(e.getMessage(), e);
        }
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
