/*
 * JBoss, Home of Professional Open Source
 * Copyright 2008, JBoss Inc., and individual contributors as indicated
 * by the @authors tag. See the copyright.txt in the distribution for a
 * full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */

package org.jboss.xnio;

import java.io.Closeable;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.net.DatagramSocket;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.channels.Selector;
import java.nio.channels.Channel;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;
import java.util.concurrent.atomic.AtomicReference;
import java.util.zip.ZipFile;
import org.jboss.xnio.log.Logger;

import java.util.logging.Handler;

/**
 * General I/O utility methods.
 *
 * @apiviz.exclude
 */
public final class IoUtils {

    private static final Executor NULL_EXECUTOR = new Executor() {
        private final String string = String.format("null executor <%s>", Integer.toHexString(hashCode()));

        public void execute(final Runnable command) {
            // no operation
        }

        public String toString() {
            return string;
        }
    };
    private static final Executor DIRECT_EXECUTOR = new Executor() {
        private final String string = String.format("direct executor <%s>", Integer.toHexString(hashCode()));

        public void execute(final Runnable command) {
            command.run();
        }

        public String toString() {
            return string;
        }
    };
    private static final Closeable NULL_CLOSEABLE = new Closeable() {
        private final String string = String.format("null closeable <%s>", Integer.toHexString(hashCode()));
        public void close() throws IOException {
            // no operation
        }

        public String toString() {
            return string;
        }
    };
    private static final ChannelListener<Channel> NULL_LISTENER = new ChannelListener<Channel>() {
        public void handleEvent(final Channel channel) {
        }
    };
    private static final Cancellable NULL_CANCELLABLE = new Cancellable() {
        public Cancellable cancel() {
            return this;
        }
    };
    private static final ChannelListener.Setter<?> NULL_SETTER = new ChannelListener.Setter<Channel>() {
        public void set(final ChannelListener<? super Channel> channelListener) {
        }
    };
    private static final IoUtils.ResultNotifier RESULT_NOTIFIER = new IoUtils.ResultNotifier();

    private IoUtils() {}

    /**
     * Get the direct executor.  This is an executor that executes the provided task in the same thread.
     *
     * @return a direct executor
     */
    public static Executor directExecutor() {
        return DIRECT_EXECUTOR;
    }

    /**
     * Get the null executor.  This is an executor that never actually executes the provided task.
     *
     * @return a null executor
     */
    public static Executor nullExecutor() {
        return NULL_EXECUTOR;
    }

    /**
     * Get a closeable executor wrapper for an {@code ExecutorService}.  The given timeout is used to determine how long
     * the {@code close()} method will wait for a clean shutdown before the executor is shut down forcefully.
     *
     * @param executorService the executor service
     * @param timeout the timeout
     * @param unit the unit for the timeout
     * @return a new closeable executor
     */
    public static CloseableExecutor closeableExecutor(final ExecutorService executorService, final long timeout, final TimeUnit unit) {
        return new CloseableExecutor() {
            public void close() throws IOException {
                executorService.shutdown();
                try {
                    if (executorService.awaitTermination(timeout, unit)) {
                        return;
                    }
                    executorService.shutdownNow();
                    throw new IOException("Executor did not shut down cleanly (killed)");
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    executorService.shutdownNow();
                    throw new InterruptedIOException("Interrupted while awaiting executor shutdown");
                }
            }

            public void execute(final Runnable command) {
                executorService.execute(command);
            }
        };
    }

    /**
     * Get the null closeable.  This is a simple {@code Closeable} instance that does nothing when its {@code close()}
     * method is invoked.
     *
     * @return the null closeable
     */
    public static Closeable nullCloseable() {
        return NULL_CLOSEABLE;
    }

    private static final Logger closeLog = Logger.getLogger("org.jboss.xnio.safe-close");

    /**
     * Close a resource, logging an error if an error occurs.
     *
     * @param resource the resource to close
     */
    public static void safeClose(final Closeable resource) {
        try {
            if (resource != null) {
                resource.close();
            }
        } catch (Throwable t) {
            closeLog.trace(t, "Closing resource failed");
        }
    }

    /**
     * Close a resource, logging an error if an error occurs.
     *
     * @param resource the resource to close
     */
    public static void safeClose(final Socket resource) {
        try {
            if (resource != null) {
                resource.close();
            }
        } catch (Throwable t) {
            closeLog.trace(t, "Closing resource failed");
        }
    }

    /**
     * Close a resource, logging an error if an error occurs.
     *
     * @param resource the resource to close
     */
    public static void safeClose(final DatagramSocket resource) {
        try {
            if (resource != null) {
                resource.close();
            }
        } catch (Throwable t) {
            closeLog.trace(t, "Closing resource failed");
        }
    }

    /**
     * Close a resource, logging an error if an error occurs.
     *
     * @param resource the resource to close
     */
    public static void safeClose(final Selector resource) {
        try {
            if (resource != null) {
                resource.close();
            }
        } catch (Throwable t) {
            closeLog.trace(t, "Closing resource failed");
        }
    }

    /**
     * Close a resource, logging an error if an error occurs.
     *
     * @param resource the resource to close
     */
    public static void safeClose(final ServerSocket resource) {
        try {
            if (resource != null) {
                resource.close();
            }
        } catch (Throwable t) {
            closeLog.trace(t, "Closing resource failed");
        }
    }

    /**
     * Close a resource, logging an error if an error occurs.
     *
     * @param resource the resource to close
     */
    public static void safeClose(final ZipFile resource) {
        try {
            if (resource != null) {
                resource.close();
            }
        } catch (Throwable t) {
            closeLog.trace(t, "Closing resource failed");
        }
    }

    /**
     * Close a resource, logging an error if an error occurs.
     *
     * @param resource the resource to close
     */
    public static void safeClose(final Handler resource) {
        try {
            if (resource != null) {
                resource.close();
            }
        } catch (Throwable t) {
            closeLog.trace(t, "Closing resource failed");
        }
    }

    /**
     * Close a future resource, logging an error if an error occurs.  Attempts to cancel the operation if it is
     * still in progress.
     *
     * @param futureResource the resource to close
     */
    public static void safeClose(final IoFuture<? extends Closeable> futureResource) {
        futureResource.cancel().addNotifier(closingNotifier(), null);
    }

    private static final IoFuture.Notifier<Object, Closeable> ATTACHMENT_CLOSING_NOTIFIER = new IoFuture.Notifier<Object, Closeable>() {
        public void notify(final IoFuture<?> future, final Closeable attachment) {
            IoUtils.safeClose(attachment);
        }
    };

    private static final IoFuture.Notifier<Closeable, Void> CLOSING_NOTIFIER = new IoFuture.HandlingNotifier<Closeable, Void>() {
        public void handleDone(final Closeable result, final Void attachment) {
            IoUtils.safeClose(result);
        }
    };

    /**
     * Get a notifier that closes the attachment.
     *
     * @return a notifier which will close its attachment
     */
    public static IoFuture.Notifier<Object, Closeable> attachmentClosingNotifier() {
        return ATTACHMENT_CLOSING_NOTIFIER;
    }

    /**
     * Get a notifier that closes the result.
     *
     * @return a notifier which will close the result of the operation (if successful)
     */
    public static IoFuture.Notifier<Closeable, Void> closingNotifier() {
        return CLOSING_NOTIFIER;
    }

    /**
     * Get a notifier that runs the supplied action.
     *
     * @param runnable the notifier type
     * @param <T> the future type (not used)
     * @return a notifier which will run the given command
     */
    public static <T> IoFuture.Notifier<T, Void> runnableNotifier(final Runnable runnable) {
        return new IoFuture.Notifier<T, Void>() {
            public void notify(final IoFuture<? extends T> future, final Void attachment) {
                runnable.run();
            }
        };
    }

    /**
     * Get the result notifier.  This notifier will forward the result of the {@code IoFuture} to the attached
     * {@code Result}.
     *
     * @param <T> the result type
     * @return the notifier
     */
    @SuppressWarnings({ "unchecked" })
    public static <T> IoFuture.Notifier<T, Result<T>> resultNotifier() {
        return RESULT_NOTIFIER;
    }

    /**
     * Get the notifier that invokes the channel listener given as an attachment.
     *
     * @param <T> the channel type
     * @return the notifier
     */
    @SuppressWarnings({ "unchecked" })
    public static <T extends Channel> IoFuture.Notifier<T, ChannelListener<? super T>> channelListenerNotifier() {
        return CHANNEL_LISTENER_NOTIFIER;
    }

    private static final IoFuture.Notifier CHANNEL_LISTENER_NOTIFIER = new IoFuture.HandlingNotifier<Channel, ChannelListener<? super Channel>>() {
        @SuppressWarnings({ "unchecked" })
        public void handleDone(final Channel channel, final ChannelListener channelListener) {
            channelListener.handleEvent(channel);
        }
    };

    /**
     * Get a {@code java.util.concurrent}-style {@code Future} instance wrapper for an {@code IoFuture} instance.
     *
     * @param ioFuture the {@code IoFuture} to wrap
     * @return a {@code Future}
     */
    public static <T> Future<T> getFuture(final IoFuture<T> ioFuture) {
        return new Future<T>() {
            public boolean cancel(final boolean mayInterruptIfRunning) {
                ioFuture.cancel();
                return ioFuture.await() == IoFuture.Status.CANCELLED;
            }

            public boolean isCancelled() {
                return ioFuture.getStatus() == IoFuture.Status.CANCELLED;
            }

            public boolean isDone() {
                return ioFuture.getStatus() == IoFuture.Status.DONE;
            }

            public T get() throws InterruptedException, ExecutionException {
                try {
                    return ioFuture.getInterruptibly();
                } catch (IOException e) {
                    throw new ExecutionException(e);
                }
            }

            public T get(final long timeout, final TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
                try {
                    if (ioFuture.awaitInterruptibly(timeout, unit) == IoFuture.Status.WAITING) {
                        throw new TimeoutException("Operation timed out");
                    }
                    return ioFuture.getInterruptibly();
                } catch (IOException e) {
                    throw new ExecutionException(e);
                }
            }

            public String toString() {
                return String.format("java.util.concurrent.Future wrapper <%s> for %s", Integer.toHexString(hashCode()), ioFuture);
            }
        };
    }

    private static final IoFuture.Notifier<Object, CountDownLatch> COUNT_DOWN_NOTIFIER = new IoFuture.Notifier<Object, CountDownLatch>() {
        public void notify(final IoFuture<?> future, final CountDownLatch latch) {
            latch.countDown();
        }
    };

    /**
     * Wait for all the futures to complete.
     *
     * @param futures the futures to wait for
     */
    public static void awaitAll(IoFuture<?>... futures) {
        final int len = futures.length;
        final CountDownLatch cdl = new CountDownLatch(len);
        for (IoFuture<?> future : futures) {
            future.addNotifier(COUNT_DOWN_NOTIFIER, cdl);
        }
        boolean intr = false;
        try {
            while (cdl.getCount() > 0L) {
                try {
                    cdl.await();
                } catch (InterruptedException e) {
                    intr = true;
                }
            }
        } finally {
            if (intr) {
                Thread.currentThread().interrupt();
            }
        }
    }

    /**
     * Wait for all the futures to complete.
     *
     * @param futures the futures to wait for
     * @throws InterruptedException if the current thread is interrupted while waiting
     */
    public static void awaitAllInterruptibly(IoFuture<?>... futures) throws InterruptedException {
        final int len = futures.length;
        final CountDownLatch cdl = new CountDownLatch(len);
        for (IoFuture<?> future : futures) {
            future.addNotifier(COUNT_DOWN_NOTIFIER, cdl);
        }
        cdl.await();
    }

    /**
     * Create an {@code IoFuture} which wraps another {@code IoFuture}, but returns a different type.
     *
     * @param parent the original {@code IoFuture}
     * @param type the class of the new {@code IoFuture}
     * @param <I> the type of the original result
     * @param <O> the type of the wrapped result
     * @return a wrapper {@code IoFuture}
     */
    public static <I, O> IoFuture<? extends O> cast(final IoFuture<I> parent, final Class<O> type) {
        return new CastingIoFuture<O, I>(parent, type);
    }

    // nested classes

    private static class CastingIoFuture<O, I> implements IoFuture<O> {

        private final IoFuture<I> parent;
        private final Class<O> type;

        private CastingIoFuture(final IoFuture<I> parent, final Class<O> type) {
            this.parent = parent;
            this.type = type;
        }

        public IoFuture<O> cancel() {
            parent.cancel();
            return this;
        }

        public Status getStatus() {
            return parent.getStatus();
        }

        public Status await() {
            return parent.await();
        }

        public Status await(final long time, final TimeUnit timeUnit) {
            return parent.await(time, timeUnit);
        }

        public Status awaitInterruptibly() throws InterruptedException {
            return parent.awaitInterruptibly();
        }

        public Status awaitInterruptibly(final long time, final TimeUnit timeUnit) throws InterruptedException {
            return parent.awaitInterruptibly(time, timeUnit);
        }

        public O get() throws IOException, CancellationException {
            return type.cast(parent.get());
        }

        public O getInterruptibly() throws IOException, InterruptedException, CancellationException {
            return type.cast(parent.getInterruptibly());
        }

        public IOException getException() throws IllegalStateException {
            return parent.getException();
        }

        public <A> IoFuture<O> addNotifier(final Notifier<? super O, A> notifier, final A attachment) {
            parent.<A>addNotifier(new Notifier<I, A>() {
                public void notify(final IoFuture<? extends I> future, final A attachment) {
                    notifier.notify((IoFuture<O>)CastingIoFuture.this, attachment);
                }
            }, attachment);
            return this;
        }
    }

    private static final Logger listenerLog = Logger.getLogger("org.jboss.xnio.channel-listener");

    /**
     * Invoke a channel listener on a given channel, logging any errors.
     *
     * @param channel the channel
     * @param channelListener the channel listener
     * @param <T> the channel type
     * @return {@code true} if the listener completed successfully, or {@code false} if it failed
     */
    public static <T extends Channel> boolean invokeChannelListener(T channel, ChannelListener<? super T> channelListener) {
        if (channelListener != null) try {
            listenerLog.trace("Invoking listener %s on channel %s", channelListener, channel);
            channelListener.handleEvent(channel);
        } catch (Throwable t) {
            listenerLog.error(t, "A channel event listener threw an exception");
            return false;
        }
        return true;
    }

    /**
     * Invoke a channel listener on a given channel, logging any errors, using the given executor.
     *
     * @param executor the executor
     * @param channel the channel
     * @param channelListener the channel listener
     * @param <T> the channel type
     */
    public static <T extends Channel> void invokeChannelListener(Executor executor, T channel, ChannelListener<? super T> channelListener) {
        try {
            executor.execute(getChannelListenerTask(channel, channelListener));
        } catch (RejectedExecutionException ree) {
            invokeChannelListener(channel, channelListener);
        }
    }

    /**
     * Get a task which invokes the given channel listener on the given channel.
     *
     * @param channel the channel
     * @param channelListener the channel listener
     * @param <T> the channel type
     * @return the runnable task
     */
    public static <T extends Channel> Runnable getChannelListenerTask(final T channel, final ChannelListener<? super T> channelListener) {
        return new Runnable() {
            public void run() {
                invokeChannelListener(channel, channelListener);
            }
        };
    }

    private static ChannelListener<Channel> CLOSING_CHANNEL_LISTENER = new ChannelListener<Channel>() {
        public void handleEvent(final Channel channel) {
            IoUtils.safeClose(channel);
        }
    };

    /**
     * Get a channel listener which closes the channel when notified.
     *
     * @return the channel listener
     */
    public static ChannelListener<Channel> closingChannelListener() {
        return CLOSING_CHANNEL_LISTENER;
    }

    /**
     * Get a channel listener which does nothing.
     *
     * @return the null channel listener
     */
    public static ChannelListener<Channel> nullChannelListener() {
        return NULL_LISTENER;
    }

    /**
     * Get a setter based on an atomic reference field updater.  Used by channel implementations to avoid having to
     * define an anonymous class for each listener field.
     *
     * @param channel the channel
     * @param updater the updater
     * @param <T> the channel type
     * @param <C> the holding class
     * @return the setter
     */
    public static <T extends Channel, C> ChannelListener.Setter<T> getSetter(final C channel, final AtomicReferenceFieldUpdater<C, ChannelListener> updater) {
        return new ChannelListener.Setter<T>() {
            public void set(final ChannelListener<? super T> channelListener) {
                updater.set(channel, channelListener);
            }
        };
    }

    /**
     * Get a setter based on an atomic reference.  Used by channel implementations to avoid having to
     * define an anonymous class for each listener field.
     *
     * @param atomicReference the atomic reference
     * @param <T> the channel type
     * @return the setter
     */
    public static <T extends Channel> ChannelListener.Setter<T> getSetter(final AtomicReference<ChannelListener<? super T>> atomicReference) {
        return new ChannelListener.Setter<T>() {
            public void set(final ChannelListener<? super T> channelListener) {
                atomicReference.set(channelListener);
            }
        };
    }

    /**
     * Get a channel listener setter which delegates to the given target setter with a different channel type.
     *
     * @param target the target setter
     * @param realChannel the channel to send in to the listener
     * @param <T> the real channel type
     * @return the delegating setter
     */
    public static <T extends Channel> ChannelListener.Setter<T> getDelegatingSetter(final ChannelListener.Setter<? extends Channel> target, final T realChannel) {
        return target == null ? null : delegatingSetter(target, realChannel);
    }

    private static <T extends Channel, O extends Channel> DelegatingSetter<T, O> delegatingSetter(final ChannelListener.Setter<O> setter, final T realChannel) {
        return new DelegatingSetter<T,O>(setter, realChannel);
    }

    private static class DelegatingSetter<T extends Channel, O extends Channel> implements ChannelListener.Setter<T> {
        private final ChannelListener.Setter<O> setter;
        private final T realChannel;

        DelegatingSetter(final ChannelListener.Setter<O> setter, final T realChannel) {
            this.setter = setter;
            this.realChannel = realChannel;
        }

        public void set(final ChannelListener<? super T> channelListener) {
            setter.set(channelListener == null ? null : new DelegatingChannelListener<T, O>(channelListener, realChannel));
        }
    }

    private static class DelegatingChannelListener<T extends Channel, O extends Channel> implements ChannelListener<O> {

        private final ChannelListener<? super T> channelListener;
        private final T realChannel;

        public DelegatingChannelListener(final ChannelListener<? super T> channelListener, final T realChannel) {
            this.channelListener = channelListener;
            this.realChannel = realChannel;
        }

        public void handleEvent(final Channel channel) {
            channelListener.handleEvent(realChannel);
        }
    }

    /**
     * Get a channel listener setter which does nothing.
     *
     * @param <T> the channel type
     * @return a setter which does nothing
     */
    @SuppressWarnings({ "unchecked" })
    public static <T extends Channel> ChannelListener.Setter<T> nullSetter() {
        return (ChannelListener.Setter<T>) NULL_SETTER;
    }

    /**
     * Get a notifier which forwards the result to another {@code IoFuture}'s manager.
     *
     * @param <T> the channel type
     * @return the notifier
     */
    @SuppressWarnings({ "unchecked" })
    public static <T> IoFuture.Notifier<T, FutureResult<T>> getManagerNotifier() {
        return MANAGER_NOTIFIER;
    }

    private static final ManagerNotifier MANAGER_NOTIFIER = new ManagerNotifier();

    private static class ManagerNotifier<T extends Channel> extends IoFuture.HandlingNotifier<T, FutureResult<T>> {
        public void handleCancelled(final FutureResult<T> manager) {
            manager.setCancelled();
        }

        public void handleFailed(final IOException exception, final FutureResult<T> manager) {
            manager.setException(exception);
        }

        public void handleDone(final T result, final FutureResult<T> manager) {
            manager.setResult(result);
        }
    }

    /**
     * A channel source which tries to acquire a channel from a delegate channel source the given number of times before
     * giving up.
     *
     * @param delegate the delegate channel source
     * @param maxTries the number of times to retry
     * @param <T> the channel type
     * @return the retrying channel source
     */
    public static <T extends Channel> ChannelSource<T> getRetryingChannelSource(final ChannelSource<T> delegate, final int maxTries) {
        if (maxTries < 1) {
            throw new IllegalArgumentException("maxTries must be at least 1");
        }
        return new RetryingChannelSource<T>(maxTries, delegate);
    }

    private static class RetryingNotifier<T extends Channel> extends IoFuture.HandlingNotifier<T, Result<T>> {

        private volatile int remaining;
        private final int maxTries;
        private final Result<T> result;
        private final ChannelSource<T> delegate;
        private final ChannelListener<? super T> openListener;

        RetryingNotifier(final int maxTries, final Result<T> result, final ChannelSource<T> delegate, final ChannelListener<? super T> openListener) {
            this.maxTries = maxTries;
            this.result = result;
            this.delegate = delegate;
            this.openListener = openListener;
            remaining = maxTries;
        }

        public void handleFailed(final IOException exception, final Result<T> attachment) {
            if (remaining-- == 0) {
                result.setException(new IOException("Failed to create channel after " + maxTries + " tries", exception));
                return;
            }
            tryOne(attachment);
        }

        void tryOne(final Result<T> attachment) {
            final IoFuture<? extends T> ioFuture = delegate.open(openListener);
            ioFuture.addNotifier(this, attachment);
        }
    }

    private static class RetryingChannelSource<T extends Channel> implements ChannelSource<T> {

        private final int maxTries;
        private final ChannelSource<T> delegate;

        RetryingChannelSource(final int maxTries, final ChannelSource<T> delegate) {
            this.maxTries = maxTries;
            this.delegate = delegate;
        }

        public IoFuture<? extends T> open(final ChannelListener<? super T> openListener) {
            final FutureResult<T> result = new FutureResult<T>();
            final IoUtils.RetryingNotifier<T> notifier = new IoUtils.RetryingNotifier<T>(maxTries, result, delegate, openListener);
            notifier.tryOne(result);
            return result.getIoFuture();
        }
    }

    /**
     * A cancellable which closes the given resource on cancel.
     *
     * @param c the resource
     * @return the cancellable
     */
    public static Cancellable closingCancellable(final Closeable c) {
        return new ClosingCancellable(c);
    }

    private static class ClosingCancellable implements Cancellable {

        private final Closeable c;

        ClosingCancellable(final Closeable c) {
            this.c = c;
        }

        public Cancellable cancel() {
            safeClose(c);
            return this;
        }
    }

    /**
     * Get the null cancellable.
     *
     * @return the null cancellable
     */
    public static Cancellable nullCancellable() {
        return NULL_CANCELLABLE;
    }

    /**
     * Get a channel listener which executes a delegate channel listener via an executor.  If an exception occurs
     * submitting the task, the associated channel is closed.
     *
     * @param listener the listener to invoke
     * @param executor the executor with which to invoke the listener
     * @param <T> the channel type
     * @return a delegating channel listener
     */
    public static <T extends Channel> ChannelListener<T> executorChannelListener(final ChannelListener<T> listener, final Executor executor) {
        return new ChannelListener<T>() {
            public void handleEvent(final T channel) {
                try {
                    executor.execute(getChannelListenerTask(channel, listener));
                } catch (RejectedExecutionException e) {
                    listenerLog.error("Failed to submit task to executor: %s (closing %s)", e, channel);
                    safeClose(channel);
                }
            }
        };
    }

    private static class ResultNotifier<T> extends IoFuture.HandlingNotifier<T, Result<T>> {

        public void handleCancelled(final Result<T> result) {
            result.setCancelled();
        }

        public void handleFailed(final IOException exception, final Result<T> result) {
            result.setException(exception);
        }

        public void handleDone(final T value, final Result<T> result) {
            result.setResult(value);
        }
    }
}
