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

/**
 * An exception that is thrown when an invalid option is specified for a {@link Configurable}.
 *
 * @apiviz.exclude
 */
public class UnsupportedOptionException extends IllegalArgumentException {
    private static final long serialVersionUID = 250195510855241708L;

    /**
     * Construct a {@code UnsupportedOptionException} instance.
     */
    public UnsupportedOptionException() {
    }

    /**
     * Construct a {@code UnsupportedOptionException} instance with the given message.
     *
     * @param message the message
     */
    public UnsupportedOptionException(final String message) {
        super(message);
    }

    /**
     * Construct a {@code UnsupportedOptionException} instance with the given message and cause.
     *
     * @param message the message
     * @param cause the cause
     */
    public UnsupportedOptionException(final String message, final Throwable cause) {
        super(message, cause);
    }

    /**
     * Construct a {@code UnsupportedOptionException} instance with the given cause.
     *
     * @param cause the cause
     */
    public UnsupportedOptionException(final Throwable cause) {
        super(cause);
    }
}
