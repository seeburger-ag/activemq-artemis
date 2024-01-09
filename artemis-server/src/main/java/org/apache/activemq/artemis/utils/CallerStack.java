/*
 * CallerStack.java
 *
 * created at 22.11.2005 by Rico Neubauer r.neubauer@seeburger.de
 *
 * Copyright (c) 2005 SEEBURGER AG, Germany. All Rights Reserved.
 */
package org.apache.activemq.artemis.utils;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * CallerStack.
 *
 * @author <a href="mailto:r.neubauer@seeburger.de">Rico Neubauer</a>
 * @since 22.11.2005
 */
public class CallerStack
{
    /**
     * My package.
     */
    private static final String myPackage = "org.apache.activemq.artemis.utils"; //CallerStack.class.getPackage().getName();

    /**
     * Unknown.
     */
    private static final String UNKNOWN = "Unknown";


    /**
     *
     */
    private CallerStack()
    {
        //
    }


    /**
     * Extracts information about the calling class.
     * Returns <code>"Unknown"</code> if information could not be obtained.
     * Will never return null.
     *
     * @see #getCallerInfo(String)
     * @param classInPackageToIgnore
     * @return information about the calling class; <code>"Unknown"</code> if information could not be obtained.
     */
    public static String getCallerInfo(Class<?> classInPackageToIgnore)
    {
        if (classInPackageToIgnore == null)
        {
            return getCallerInfo(myPackage);
        }
        return getCallerInfo(classInPackageToIgnore.getPackage().getName());
    }


    /**
     * Extracts information about the calling class.
     * Returns <code>"Unknown"</code> if information could not be obtained.
     * Will never return null.
     *
     * @see #getCallerInfo(Class)
     * @param packageToIgnore
     * @return information about the calling class; <code>"Unknown"</code> if information could not be obtained.
     */
    public static String getCallerInfo(String packageToIgnore)
    {
        // make sure our caller does not pass junk
        if (packageToIgnore == null || packageToIgnore.length() < 1)
        {
            packageToIgnore = myPackage;
        }
        packageToIgnore = packageToIgnore.toLowerCase();

        // get info about calling Class
        StackTraceElement[] stackTraceElements = new Throwable().getStackTrace();

        String callerInfo = null;
        for (int i = 0; i < stackTraceElements.length; i++)
        {
            StackTraceElement element = stackTraceElements[i];
            // get 1st element not contained in myPackage and not contained in passed packageToIgnore
            final String className = element.getClassName().toLowerCase(Locale.ROOT);
            if (callerInfo == null)
            {
                if (!(className.startsWith(myPackage)) && !(className.startsWith(packageToIgnore)) &&
                                !(className.startsWith("sun.reflect.")) &&
                                !(className.startsWith("java.lang.reflect."))) // ignore Reflection classes by default
                {
                    callerInfo = element.toString();
                }
            }
            else
            {
                // append found SEEBURGER classes if not already present
                if (className.contains("seeburger"))
                {
                    callerInfo += " - " + element.toString();
                }
            }
        }

        if (callerInfo == null)
        {
            callerInfo = UNKNOWN;
        }
        return callerInfo;
    }


    /**
     * Filters the {@link Throwable}'s stacktrace and return {@link StackTraceElement}s only matching the given prefix filter.
     *
     * @param original
     * @param prefix
     * @return filtered {@link StackTraceElement}s
     */
    public static StackTraceElement[] filterStackTrace(Throwable original, String prefix)
    {
        return filterStackTrace(original.getStackTrace(), prefix);
    }


    /**
     * Filters {@link StackTraceElement}s and return {@link StackTraceElement}s only matching the given prefix filter.
     *
     * @param original
     * @param prefix
     * @return filtered {@link StackTraceElement}s
     */
    public static StackTraceElement[] filterStackTrace(StackTraceElement[] original, String prefix)
    {
        List<StackTraceElement> stackTrace = new ArrayList<>();
        for (StackTraceElement stackTraceElement : original)
        {
            String className = stackTraceElement.getClassName();
            if (className.startsWith(prefix))
            {
                stackTrace.add(stackTraceElement);
            }
        }

        return stackTrace.toArray(new StackTraceElement[stackTrace.size()]);
    }

}
