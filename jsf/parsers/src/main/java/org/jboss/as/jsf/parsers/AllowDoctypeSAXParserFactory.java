/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2016, Red Hat, Inc., and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
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

package org.jboss.as.jsf.parsers;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import javax.xml.validation.Schema;

import org.xml.sax.SAXException;
import org.xml.sax.SAXNotRecognizedException;
import org.xml.sax.SAXNotSupportedException;


public final class AllowDoctypeSAXParserFactory extends SAXParserFactory {
    //private static final Constructor<? extends SAXParserFactory> PLATFORM_FACTORY;
    private final SAXParserFactory actual;

    static {
        /*Thread thread = Thread.currentThread();
        ClassLoader old = thread.getContextClassLoader();

        thread.setContextClassLoader(ClassLoader.getSystemClassLoader());
        try {
            if (System.getProperty(SAXParserFactory.class.getName(), "").equals(__SAXParserFactory.class.getName())) {
                System.clearProperty(SAXParserFactory.class.getName());
            }
            SAXParserFactory factory = SAXParserFactory.newInstance();
            try {
                PLATFORM_FACTORY = factory.getClass().getConstructor();
            } catch (NoSuchMethodException e) {
                //throw __RedirectedUtils.wrapped(new NoSuchMethodError(e.getMessage()), e);
            }
            System.setProperty(SAXParserFactory.class.getName(), AllowDoctypeSAXParserFactory.class.getName());
        } finally {
            thread.setContextClassLoader(old);
        }*/

        System.clearProperty(SAXParserFactory.class.getName());
        System.setProperty(SAXParserFactory.class.getName(), AllowDoctypeSAXParserFactory.class.getName());
    }

    public AllowDoctypeSAXParserFactory() {
        /*Constructor<? extends SAXParserFactory> factory = PLATFORM_FACTORY;
        ClassLoader loader = Thread.currentThread().getContextClassLoader();
        try {
            if (loader != null) {
                Class<? extends SAXParserFactory> provider = __RedirectedUtils.loadProvider(SAXParserFactory.class, loader);
                if (provider != null)
                    factory = provider.getConstructor();
            }

            actual = factory.newInstance();
        } catch (InstantiationException e) {
            throw __RedirectedUtils.wrapped(new InstantiationError(e.getMessage()), e);
        } catch (IllegalAccessException e) {
            throw __RedirectedUtils.wrapped(new IllegalAccessError(e.getMessage()), e);
        } catch (InvocationTargetException e) {
            throw __RedirectedUtils.rethrowCause(e);
        } catch (NoSuchMethodException e) {
            throw __RedirectedUtils.wrapped(new NoSuchMethodError(e.getMessage()), e);
        }*/
        actual = SAXParserFactory.newInstance();
    }

    public SAXParser newSAXParser() throws ParserConfigurationException, SAXException {
        actual.setFeature("http://apache.org/xml/features/disallow-doctype-decl", false);
        return actual.newSAXParser();
    }

    public void setNamespaceAware(final boolean awareness) {
        actual.setNamespaceAware(awareness);
    }

    public void setValidating(final boolean validating) {
        actual.setValidating(validating);
    }

    public boolean isNamespaceAware() {
        return actual.isNamespaceAware();
    }

    public boolean isValidating() {
        return actual.isValidating();
    }

    public void setFeature(final String name, final boolean value) throws ParserConfigurationException, SAXNotRecognizedException, SAXNotSupportedException {
        actual.setFeature(name, value);
    }

    public boolean getFeature(final String name) throws ParserConfigurationException, SAXNotRecognizedException, SAXNotSupportedException {
        return actual.getFeature(name);
    }

    public Schema getSchema() {
        return actual.getSchema();
    }

    public void setSchema(final Schema schema) {
        actual.setSchema(schema);
    }

    public void setXIncludeAware(final boolean state) {
        actual.setXIncludeAware(state);
    }

    public boolean isXIncludeAware() {
        return actual.isXIncludeAware();
    }
}
