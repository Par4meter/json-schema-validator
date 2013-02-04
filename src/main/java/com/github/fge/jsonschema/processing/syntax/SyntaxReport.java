/*
 * Copyright (c) 2013, Francis Galiegue <fgaliegue@gmail.com>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the Lesser GNU General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * Lesser GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.github.fge.jsonschema.processing.syntax;

import com.github.fge.jsonschema.processing.LogLevel;
import com.github.fge.jsonschema.processing.ProcessingException;
import com.github.fge.jsonschema.ref.JsonPointer;
import com.github.fge.jsonschema.report.ListProcessingReport;
import com.github.fge.jsonschema.report.ProcessingMessage;
import com.github.fge.jsonschema.report.ProcessingReport;
import com.google.common.collect.Sets;

import java.util.Set;

final class SyntaxReport
    extends ListProcessingReport
{
    private final Set<JsonPointer> ignoredPaths = Sets.newLinkedHashSet();

    SyntaxReport()
    {
        setLogLevel(LogLevel.DEBUG);
    }

    public void addIgnoredPath(final JsonPointer pointer)
    {
        ignoredPaths.add(pointer);
    }

    public void injectMessages(final ProcessingReport report)
        throws ProcessingException
    {
        for (final ProcessingMessage message: messages)
            report.log(message);
    }

    public boolean hasIgnoredPath(final JsonPointer target)
    {
        for (final JsonPointer pointer: ignoredPaths)
            if (pointer.isParentOf(target))
                return true;

        return false;
    }
}