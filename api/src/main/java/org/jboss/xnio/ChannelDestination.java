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

import java.nio.channels.Channel;
import org.jboss.xnio.channels.BoundChannel;

/**
 * A channel destination.  This is the inverse of {@code ChannelSource}; it is used to accept a single connection from a remote
 * peer.
 *
 * @param <A> the address type
 * @param <T> the channel type
 *
 * @since 1.2
 */
public interface ChannelDestination<A, T extends Channel> {
    /**
     * Accept a connection.  The bind listener will be called when the channel is bound; the open listener will be called
     * when the connection is accepted.  It is not guaranteed that the open listener will be called after the bind listener.
     *
     * @param openListener the listener which will be notified when the channel is open, or {@code null} for none
     * @param bindListener the listener which will be notified when the channel is bound locally, or {@code null} for none
     * @return the future connection
     */
    IoFuture<T> accept(ChannelListener<? super T> openListener, ChannelListener<? super BoundChannel<A>> bindListener);
}
