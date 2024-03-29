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

package org.jboss.xnio.channels;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.jboss.xnio.OptionMap;
import org.jboss.xnio.Buffers;
import org.jboss.xnio.ChannelListener;
import org.jboss.xnio.IoUtils;
import org.jboss.xnio.Options;
import org.jboss.xnio.SslClientAuthMode;
import org.jboss.xnio.Sequence;
import org.jboss.xnio.Xnio;
import org.jboss.xnio.log.Logger;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.WritableByteChannel;
import java.nio.channels.GatheringByteChannel;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.ScatteringByteChannel;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.Executor;
import java.net.InetSocketAddress;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;

/**
 * A utility class containing static methods to support channel usage.
 *
 * @apiviz.exclude
 */
public final class Channels {

    private Channels() {
    }

    /**
     * Create and install a stream channel listener which unwraps messages and passes them to a handler.  The listener
     * is installed into the given channel.  The user message handler may be set (and changed at any time)
     * via the returned setter.  The corresponding write side can be created via {@link #createMessageWriter(StreamSinkChannel, org.jboss.xnio.OptionMap)}.
     *
     * @param channel the stream source channel
     * @param optionMap the initial options
     * @return the setter for the new message handler
     * @see org.jboss.xnio.Options#MAX_INBOUND_MESSAGE_SIZE
     *
     * @since 2.0
     */
    public static MessageHandler.Setter createMessageReader(final StreamSourceChannel channel, final OptionMap optionMap) {
        final MessageStreamChannelListener listener = new MessageStreamChannelListener(optionMap);
        channel.getReadSetter().set(listener);
        return listener.getSetter();
    }

    /**
     * Create a writable message channel which wraps a stream sink channel using a simple length-body protocol.  The
     * corresponding read side can be created via {@link #createMessageReader(StreamSourceChannel, org.jboss.xnio.OptionMap)}.
     *
     * @param channel the stream sink channel
     * @param optionMap the initial options
     * @return the message channel
     * @see org.jboss.xnio.Options#MAX_OUTBOUND_MESSAGE_SIZE
     *
     * @since 2.0
     */
    public static WritableMessageChannel createMessageWriter(final StreamSinkChannel channel, final OptionMap optionMap) {
        return new StreamSinkMessageChannel(channel, optionMap);
    }

    private static final Logger sslLog = Logger.getLogger("org.jboss.xnio.ssl");

    /**
     * Create a SSL/TLS-enabled channel over a TCP channel.  Uses the given {@code SSLContext}, and uses the option map to configure
     * the parameters of the connection (including whether this side is the client or the server).  By default, the channel
     * will run in client mode.
     *
     * @param sslContext the SSL context to use
     * @param tcpChannel the TCP channel over which the connection is encapsulated
     * @param executor the executor to use for executing asynchronous tasks
     * @param optionMap the configuration options for the channel
     * @return the new SSL TCP channel
     * @see org.jboss.xnio.Options#SSL_CLIENT_AUTH_MODE
     * @see org.jboss.xnio.Options#SSL_USE_CLIENT_MODE
     * @see org.jboss.xnio.Options#SSL_ENABLE_SESSION_CREATION
     * @see org.jboss.xnio.Options#SSL_ENABLED_CIPHER_SUITES
     * @see org.jboss.xnio.Options#SSL_ENABLED_PROTOCOLS
     *
     * @since 2.0
     * @deprecated Use the methods on {@link Xnio} instead in order to support alternate, non-JSSE implementations.
     */
    public static SslTcpChannel createSslTcpChannel(final SSLContext sslContext, final TcpChannel tcpChannel, final Executor executor, final OptionMap optionMap) {
        return createSslTcpChannel(sslContext, tcpChannel, executor, optionMap, false);
    }

    private static SslTcpChannel createSslTcpChannel(final SSLContext sslContext, final TcpChannel tcpChannel, final Executor executor, final OptionMap optionMap, final boolean server) {
        final InetSocketAddress peerAddress = tcpChannel.getPeerAddress();
        final SSLEngine engine = sslContext.createSSLEngine(peerAddress.getHostName(), peerAddress.getPort());
        final boolean clientMode = optionMap.get(Options.SSL_USE_CLIENT_MODE, ! server);
        engine.setUseClientMode(clientMode);
        if (! clientMode) {
            final SslClientAuthMode clientAuthMode = optionMap.get(Options.SSL_CLIENT_AUTH_MODE);
            if (clientAuthMode != null) switch (clientAuthMode) {
                case NOT_REQUESTED:
                    engine.setNeedClientAuth(false);
                    engine.setWantClientAuth(false);
                    break;
                case REQUESTED:
                    engine.setWantClientAuth(true);
                    break;
                case REQUIRED:
                    engine.setNeedClientAuth(true);
                    break;
            }
        }
        engine.setEnableSessionCreation(optionMap.get(Options.SSL_ENABLE_SESSION_CREATION, true));
        final Sequence<String> cipherSuites = optionMap.get(Options.SSL_ENABLED_CIPHER_SUITES);
        if (cipherSuites != null) {
            final Set<String> supported = new HashSet<String>(Arrays.asList(engine.getSupportedCipherSuites()));
            final List<String> finalList = new ArrayList<String>();
            for (String name : cipherSuites) {
                if (supported.contains(name)) {
                    finalList.add(name);
                }
            }
            engine.setEnabledCipherSuites(finalList.toArray(new String[finalList.size()]));
        }
        final Sequence<String> protocols = optionMap.get(Options.SSL_ENABLED_PROTOCOLS);
        if (protocols != null) {
            final Set<String> supported = new HashSet<String>(Arrays.asList(engine.getSupportedProtocols()));
            final List<String> finalList = new ArrayList<String>();
            for (String name : protocols) {
                if (supported.contains(name)) {
                    finalList.add(name);
                }
            }
            engine.setEnabledProtocols(finalList.toArray(new String[finalList.size()]));
        }
        return new WrappingSslTcpChannel(tcpChannel, engine, executor);
    }

    /**
     * Create a SSL/TLS-enabled channel over a TCP channel.  Uses the given {@code SSLEngine} which should already be fully configured.
     *
     * @param tcpChannel the TCP channel
     * @param sslEngine the SSL engine
     * @param sslExecutor the SSL executor
     * @return the SSL TCP channel
     *
     * @since 2.1
     * @deprecated Use the methods on {@link Xnio} instead in order to support alternate, non-JSSE implementations.
     */
    public static SslTcpChannel createSslTcpChannel(final TcpChannel tcpChannel, final SSLEngine sslEngine, final Executor sslExecutor) {
        return new WrappingSslTcpChannel(tcpChannel, sslEngine, sslExecutor);
    }

    /**
     * Create a channel lister which wraps the incoming connection with an SSL connection.  By default, the channel
     * will run in server mode.
     *
     * @param sslContext the SSL context to use
     * @param sslChannelListener the SSL TCP channel listener which should be executed with the SSL connection
     * @param executor the executor to use for executing asynchronous tasks
     * @param optionMap the configuration options for the channel
     * @return the new SSL-enabled TCP channel listener
     * @see org.jboss.xnio.Options#SSL_CLIENT_AUTH_MODE
     * @see org.jboss.xnio.Options#SSL_USE_CLIENT_MODE
     * @see org.jboss.xnio.Options#SSL_ENABLE_SESSION_CREATION
     * @see org.jboss.xnio.Options#SSL_ENABLED_CIPHER_SUITES
     * @see org.jboss.xnio.Options#SSL_ENABLED_PROTOCOLS
     * @since 2.0
     * @deprecated Use the methods on {@link Xnio} instead in order to support alternate, non-JSSE implementations.
     */
    public static ChannelListener<TcpChannel> createSslTcpChannelListener(final SSLContext sslContext, final ChannelListener<? super SslTcpChannel> sslChannelListener, final Executor executor, final OptionMap optionMap) {
        return new ChannelListener<TcpChannel>() {
            public void handleEvent(final TcpChannel channel) {
                boolean ok = false;
                try {
                    sslChannelListener.handleEvent(createSslTcpChannel(sslContext, channel, executor, optionMap, true));
                    ok = true;
                } finally {
                    if (! ok) IoUtils.safeClose(channel);
                }
            }
        };
    }

    /**
     * Simple utility method to execute a blocking flush on a writable channel.  The method blocks until there are no
     * remaining bytes in the send queue.
     *
     * @param channel the writable channel
     * @throws IOException if an I/O exception occurs
     *
     * @since 2.0
     */
    public static void flushBlocking(SuspendableWriteChannel channel) throws IOException {
        while (! channel.flush()) {
            channel.awaitWritable();
        }
    }

    /**
     * Simple utility method to execute a blocking write shutdown on a writable channel.  The method blocks until the
     * channel's output side is fully shut down.
     *
     * @param channel the writable channel
     * @throws IOException if an I/O exception occurs
     *
     * @since 2.0
     */
    public static void shutdownWritesBlocking(SuspendableWriteChannel channel) throws IOException {
        while (! channel.shutdownWrites()) {
            channel.awaitWritable();
        }
    }

    /**
     * Simple utility method to execute a blocking write on a byte channel.  The method blocks until the bytes in the
     * buffer have been fully written.  To ensure that the data is sent, the {@link #flushBlocking(SuspendableWriteChannel)}
     * method should be called after all writes are complete.
     *
     * @param channel the channel to write on
     * @param buffer the data to write
     * @param <C> the channel type
     * @return the number of bytes written
     * @throws IOException if an I/O exception occurs
     * @since 1.2
     */
    public static <C extends WritableByteChannel & SuspendableWriteChannel> int writeBlocking(C channel, ByteBuffer buffer) throws IOException {
        int t = 0;
        while (buffer.hasRemaining()) {
            final int res = channel.write(buffer);
            if (res == 0) {
                channel.awaitWritable();
            } else {
                t += res;
            }
        }
        return t;
    }

    /**
     * Simple utility method to execute a blocking write on a byte channel with a timeout.  The method blocks until
     * either the bytes in the buffer have been fully written, or the timeout expires, whichever comes first.
     *
     * @param channel the channel to write on
     * @param buffer the data to write
     * @param time the amount of time to wait
     * @param unit the unit of time to wait
     * @param <C> the channel type
     * @return the number of bytes written
     * @throws IOException if an I/O exception occurs
     * @since 1.2
     */
    public static <C extends WritableByteChannel & SuspendableWriteChannel> int writeBlocking(C channel, ByteBuffer buffer, long time, TimeUnit unit) throws IOException {
        long remaining = unit.toMillis(time);
        long now = System.currentTimeMillis();
        int t = 0;
        while (buffer.hasRemaining() && remaining > 0L) {
            int res = channel.write(buffer);
            if (res == 0) {
                channel.awaitWritable(remaining, TimeUnit.MILLISECONDS);
                remaining -= Math.max(-now + (now = System.currentTimeMillis()), 0L);
            } else {
                t += res;
            }
        }
        return t;
    }

    /**
     * Simple utility method to execute a blocking write on a gathering byte channel.  The method blocks until the
     * bytes in the buffer have been fully written.
     *
     * @param channel the channel to write on
     * @param buffers the data to write
     * @param offs the index of the first buffer to write
     * @param len the number of buffers to write
     * @param <C> the channel type
     * @return the number of bytes written
     * @throws IOException if an I/O exception occurs
     * @since 1.2
     */
    public static <C extends GatheringByteChannel & SuspendableWriteChannel> long writeBlocking(C channel, ByteBuffer[] buffers, int offs, int len) throws IOException {
        long t = 0;
        while (Buffers.hasRemaining(buffers, offs, len)) {
            final long res = channel.write(buffers, offs, len);
            if (res == 0) {
                channel.awaitWritable();
            } else {
                t += res;
            }
        }
        return t;
    }

    /**
     * Simple utility method to execute a blocking write on a gathering byte channel with a timeout.  The method blocks until all
     * the bytes are written, or until the timeout occurs.
     *
     * @param channel the channel to write on
     * @param buffers the data to write
     * @param offs the index of the first buffer to write
     * @param len the number of buffers to write
     * @param time the amount of time to wait
     * @param unit the unit of time to wait
     * @param <C> the channel type
     * @return the number of bytes written
     * @throws IOException if an I/O exception occurs
     * @since 1.2
     */
    public static <C extends GatheringByteChannel & SuspendableWriteChannel> long writeBlocking(C channel, ByteBuffer[] buffers, int offs, int len, long time, TimeUnit unit) throws IOException {
        long remaining = unit.toMillis(time);
        long now = System.currentTimeMillis();
        long t = 0;
        while (Buffers.hasRemaining(buffers, offs, len) && remaining > 0L) {
            long res = channel.write(buffers, offs, len);
            if (res == 0) {
                channel.awaitWritable(remaining, TimeUnit.MILLISECONDS);
                remaining -= Math.max(-now + (now = System.currentTimeMillis()), 0L);
            } else {
                t += res;
            }
        }
        return t;
    }

    /**
     * Simple utility method to execute a blocking send on a message channel.  The method blocks until the message is written.
     *
     * @param channel the channel to write on
     * @param buffer the data to write
     * @param <C> the channel type
     * @throws IOException if an I/O exception occurs
     * @since 1.2
     */
    public static <C extends WritableMessageChannel> void sendBlocking(C channel, ByteBuffer buffer) throws IOException {
        while (! channel.send(buffer)) {
            channel.awaitWritable();
        }
    }

    /**
     * Simple utility method to execute a blocking send on a message channel with a timeout.  The method blocks until the channel
     * is writable, and then the message is written.
     *
     * @param channel the channel to write on
     * @param buffer the data to write
     * @param time the amount of time to wait
     * @param unit the unit of time to wait
     * @param <C> the channel type
     * @return the write result
     * @throws IOException if an I/O exception occurs
     * @since 1.2
     */
    public static <C extends WritableMessageChannel> boolean sendBlocking(C channel, ByteBuffer buffer, long time, TimeUnit unit) throws IOException {
        long remaining = unit.toMillis(time);
        long now = System.currentTimeMillis();
        while (remaining > 0L) {
            if (!channel.send(buffer)) {
                channel.awaitWritable(remaining, TimeUnit.MILLISECONDS);
                remaining -= Math.max(-now + (now = System.currentTimeMillis()), 0L);
            } else {
                return true;
            }
        }
        return false;
    }

    /**
     * Simple utility method to execute a blocking gathering send on a message channel.  The method blocks until the message is written.
     *
     * @param channel the channel to write on
     * @param buffers the data to write
     * @param offs the index of the first buffer to write
     * @param len the number of buffers to write
     * @param <C> the channel type
     * @throws IOException if an I/O exception occurs
     * @since 1.2
     */
    public static <C extends WritableMessageChannel> void sendBlocking(C channel, ByteBuffer[] buffers, int offs, int len) throws IOException {
        while (! channel.send(buffers, offs, len)) {
            channel.awaitWritable();
        }
    }

    /**
     * Simple utility method to execute a blocking gathering send on a message channel with a timeout.  The method blocks until either
     * the message is written or the timeout expires.
     *
     * @param channel the channel to write on
     * @param buffers the data to write
     * @param offs the index of the first buffer to write
     * @param len the number of buffers to write
     * @param time the amount of time to wait
     * @param unit the unit of time to wait
     * @param <C> the channel type
     * @return {@code true} if the message was written before the timeout
     * @throws IOException if an I/O exception occurs
     * @since 1.2
     */
    public static <C extends WritableMessageChannel> boolean sendBlocking(C channel, ByteBuffer[] buffers, int offs, int len, long time, TimeUnit unit) throws IOException {
        long remaining = unit.toMillis(time);
        long now = System.currentTimeMillis();
        while (remaining > 0L) {
            if (!channel.send(buffers, offs, len)) {
                channel.awaitWritable(remaining, TimeUnit.MILLISECONDS);
                remaining -= Math.max(-now + (now = System.currentTimeMillis()), 0L);
            } else {
                return true;
            }
        }
        return false;
    }

    /**
     * Simple utility method to execute a blocking read on a readable byte channel.  This method blocks until the
     * channel is readable, and then the message is read.
     *
     * @param channel the channel to read from
     * @param buffer the buffer into which bytes are to be transferred
     * @param <C> the channel type
     * @return the number of bytes read
     * @throws IOException if an I/O exception occurs
     * @since 1.2
     */
    public static <C extends ReadableByteChannel & SuspendableReadChannel> int readBlocking(C channel, ByteBuffer buffer) throws IOException {
        int res;
        while ((res = channel.read(buffer)) == 0 && buffer.hasRemaining()) {
            channel.awaitReadable();
        }
        return res;
    }

    /**
     * Simple utility method to execute a blocking read on a readable byte channel with a timeout.  This method blocks until the
     * channel is readable, and then the message is read.
     *
     * @param channel the channel to read from
     * @param buffer the buffer into which bytes are to be transferred
     * @param time the amount of time to wait
     * @param unit the unit of time to wait
     * @param <C> the channel type
     * @return the number of bytes read
     * @throws IOException if an I/O exception occurs
     * @since 1.2
     */
    public static <C extends ReadableByteChannel & SuspendableReadChannel> int readBlocking(C channel, ByteBuffer buffer, long time, TimeUnit unit) throws IOException {
        int res = channel.read(buffer);
        if (res == 0 && buffer.hasRemaining()) {
            channel.awaitReadable(time, unit);
            return channel.read(buffer);
        } else {
            return res;
        }
    }

    /**
     * Simple utility method to execute a blocking read on a scattering byte channel.  This method blocks until the
     * channel is readable, and then the message is read.
     *
     * @param channel the channel to read from
     * @param buffers the buffers into which bytes are to be transferred
     * @param offs the first buffer to use
     * @param len the number of buffers to use
     * @param <C> the channel type
     * @return the number of bytes read
     * @throws IOException if an I/O exception occurs
     * @since 1.2
     */
    public static <C extends ScatteringByteChannel & SuspendableReadChannel> long readBlocking(C channel, ByteBuffer[] buffers, int offs, int len) throws IOException {
        long res;
        while ((res = channel.read(buffers, offs, len)) == 0) {
            channel.awaitReadable();
        }
        return res;
    }

    /**
     * Simple utility method to execute a blocking read on a scattering byte channel with a timeout.  This method blocks until the
     * channel is readable, and then the message is read.
     *
     * @param channel the channel to read from
     * @param buffers the buffers into which bytes are to be transferred
     * @param offs the first buffer to use
     * @param len the number of buffers to use
     * @param time the amount of time to wait
     * @param unit the unit of time to wait
     * @param <C> the channel type
     * @return the number of bytes read
     * @throws IOException if an I/O exception occurs
     * @since 1.2
     */
    public static <C extends ScatteringByteChannel & SuspendableReadChannel> long readBlocking(C channel, ByteBuffer[] buffers, int offs, int len, long time, TimeUnit unit) throws IOException {
        long res = channel.read(buffers, offs, len);
        if (res == 0L && Buffers.hasRemaining(buffers, offs, len)) {
            channel.awaitReadable(time, unit);
            return channel.read(buffers, offs, len);
        } else {
            return res;
        }
    }

    /**
     * Simple utility method to execute a blocking receive on a readable message channel.  This method blocks until the
     * channel is readable, and then the message is received.
     *
     * @param channel the channel to read from
     * @param buffer the buffer into which bytes are to be transferred
     * @param <C> the channel type
     * @return the number of bytes read
     * @throws IOException if an I/O exception occurs
     * @since 1.2
     */
    public static <C extends ReadableMessageChannel> int receiveBlocking(C channel, ByteBuffer buffer) throws IOException {
        int res;
        while ((res = channel.receive(buffer)) == 0) {
            channel.awaitReadable();
        }
        return res;
    }

    /**
     * Simple utility method to execute a blocking receive on a readable message channel with a timeout.  This method blocks until the
     * channel is readable, and then the message is received.
     *
     * @param channel the channel to read from
     * @param buffer the buffer into which bytes are to be transferred
     * @param time the amount of time to wait
     * @param unit the unit of time to wait
     * @param <C> the channel type
     * @return the number of bytes read
     * @throws IOException if an I/O exception occurs
     * @since 1.2
     */
    public static <C extends ReadableMessageChannel> int receiveBlocking(C channel, ByteBuffer buffer, long time, TimeUnit unit) throws IOException {
        int res = channel.receive(buffer);
        if ((res) == 0) {
            channel.awaitReadable(time, unit);
            return channel.receive(buffer);
        } else {
            return res;
        }
    }

    /**
     * Simple utility method to execute a blocking receive on a readable message channel.  This method blocks until the
     * channel is readable, and then the message is received.
     *
     * @param channel the channel to read from
     * @param buffers the buffers into which bytes are to be transferred
     * @param offs the first buffer to use
     * @param len the number of buffers to use
     * @param <C> the channel type
     * @return the number of bytes read
     * @throws IOException if an I/O exception occurs
     * @since 1.2
     */
    public static <C extends ReadableMessageChannel> long receiveBlocking(C channel, ByteBuffer[] buffers, int offs, int len) throws IOException {
        long res;
        while ((res = channel.receive(buffers, offs, len)) == 0) {
            channel.awaitReadable();
        }
        return res;
    }

    /**
     * Simple utility method to execute a blocking receive on a readable message channel with a timeout.  This method blocks until the
     * channel is readable, and then the message is received.
     *
     * @param channel the channel to read from
     * @param buffers the buffers into which bytes are to be transferred
     * @param offs the first buffer to use
     * @param len the number of buffers to use
     * @param time the amount of time to wait
     * @param unit the unit of time to wait
     * @param <C> the channel type
     * @return the number of bytes read
     * @throws IOException if an I/O exception occurs
     * @since 1.2
     */
    public static <C extends ReadableMessageChannel> long receiveBlocking(C channel, ByteBuffer[] buffers, int offs, int len, long time, TimeUnit unit) throws IOException {
        long res = channel.receive(buffers, offs, len);
        if ((res) == 0) {
            channel.awaitReadable(time, unit);
            return channel.receive(buffers, offs, len);
        } else {
            return res;
        }
    }

}
