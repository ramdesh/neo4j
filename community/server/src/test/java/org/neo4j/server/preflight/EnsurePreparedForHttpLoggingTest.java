/*
 * Copyright (c) 2002-2017 "Neo Technology,"
 * Network Engine for Objects in Lund AB [http://neotechnology.com]
 *
 * This file is part of Neo4j.
 *
 * Neo4j is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.neo4j.server.preflight;

import org.junit.Test;

import org.neo4j.kernel.configuration.Config;
import org.neo4j.server.configuration.ServerSettings;

import static java.util.Collections.singletonMap;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

public class EnsurePreparedForHttpLoggingTest
{
    @Test
    public void shouldNotRunIfLoggingIsEnabledButConfigFileIsNull()
    {
        // Given
        Config config = new Config( singletonMap( ServerSettings.http_logging_enabled.name(), "true" ) );
        EnsurePreparedForHttpLogging task = new EnsurePreparedForHttpLogging( config );

        // When
        boolean run = task.run();

        // Then
        assertFalse( run );
        assertEquals( "HTTP logging configuration file is not specified", task.getFailureMessage() );
    }
}