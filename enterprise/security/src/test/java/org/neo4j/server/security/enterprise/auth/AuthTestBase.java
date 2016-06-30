/*
 * Copyright (c) 2002-2016 "Neo Technology,"
 * Network Engine for Objects in Lund AB [http://neotechnology.com]
 *
 * This file is part of Neo4j.
 *
 * Neo4j is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package org.neo4j.server.security.enterprise.auth;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.neo4j.graphdb.ResourceIterator;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.neo4j.server.security.enterprise.auth.PredefinedRolesBuilder.ADMIN;
import static org.neo4j.server.security.enterprise.auth.PredefinedRolesBuilder.ARCHITECT;
import static org.neo4j.server.security.enterprise.auth.PredefinedRolesBuilder.PUBLISHER;
import static org.neo4j.server.security.enterprise.auth.PredefinedRolesBuilder.READER;

abstract class AuthTestBase<S>
{
    protected boolean PWD_CHANGE_CHECK_FIRST = false;
    protected String CHANGE_PWD_ERR_MSG = AuthProcedures.PERMISSION_DENIED;
    protected String READ_OPS_NOT_ALLOWED = "Read operations are not allowed";
    protected String WRITE_OPS_NOT_ALLOWED = "Write operations are not allowed";
    protected String SCHEMA_OPS_NOT_ALLOWED = "Schema operations are not allowed";
    protected boolean HAS_ILLEGAL_ARGS_CHECK = false;
    protected boolean IS_EMBEDDED = true;

    protected String pwdReqErrMsg( String errMsg )
    {
        return PWD_CHANGE_CHECK_FIRST ?
               CHANGE_PWD_ERR_MSG : errMsg;
    }

    final String EMPTY_ROLE = "empty";

    S adminSubject;
    S schemaSubject;
    S writeSubject;
    S readSubject;
    S pwdSubject;
    S noneSubject;

    String[] initialUsers = { "adminSubject", "readSubject", "schemaSubject",
        "writeSubject", "pwdSubject", "noneSubject", "neo4j" };
    String[] initialRoles = { ADMIN, ARCHITECT, PUBLISHER, READER, EMPTY_ROLE };

    protected EnterpriseUserManager userManager;

    protected NeoInteractionLevel<S> neo;

    @Before
    public void setUp() throws Throwable
    {
        neo = setUpNeoServer();
        userManager = neo.getManager();

        userManager.newUser( "noneSubject", "abc", false );
        userManager.newUser( "pwdSubject", "abc", true );
        userManager.newUser( "adminSubject", "abc", false );
        userManager.newUser( "schemaSubject", "abc", false );
        userManager.newUser( "writeSubject", "abc", false );
        userManager.newUser( "readSubject", "123", false );
        // Currently admin role is created by default
        userManager.addUserToRole( "adminSubject", ADMIN );
        userManager.addUserToRole( "schemaSubject", ARCHITECT );
        userManager.addUserToRole( "writeSubject", PUBLISHER );
        userManager.addUserToRole( "readSubject", READER );
        userManager.newRole( EMPTY_ROLE );
        noneSubject = neo.login( "noneSubject", "abc" );
        pwdSubject = neo.login( "pwdSubject", "abc" );
        readSubject = neo.login( "readSubject", "123" );
        writeSubject = neo.login( "writeSubject", "abc" );
        schemaSubject = neo.login( "schemaSubject", "abc" );
        adminSubject = neo.login( "adminSubject", "abc" );
        executeQuery( writeSubject, "UNWIND range(0,2) AS number CREATE (:Node {number:number})" );
    }

    protected abstract NeoInteractionLevel<S> setUpNeoServer() throws Throwable;

    @After
    public void tearDown() throws Throwable
    {
        neo.tearDown();
    }

    protected String[] with( String[] strs, String... moreStr )
    {
        return Stream.concat( Arrays.stream(strs), Arrays.stream( moreStr ) ).toArray( String[]::new );
    }

    protected List<String> listOf( String... values )
    {
        return Stream.of( values ).collect( Collectors.toList() );
    }

    //------------- Helper functions---------------

    void testSuccessfulRead( S subject, int count )
    {
        testCallCount( subject, "MATCH (n) RETURN n", null, count );
    }

    void testFailRead( S subject, int count ) { testFailRead( subject, count, READ_OPS_NOT_ALLOWED ); }
    void testFailRead( S subject, int count, String errMsg )
    {
        assertCallFail( subject, "MATCH (n) RETURN n", errMsg );
    }

    void testSuccessfulWrite( S subject )
    {
        assertCallEmpty( subject, "CREATE (:Node)" );
    }

    void testFailWrite( S subject ) { testFailWrite( subject, WRITE_OPS_NOT_ALLOWED ); }
    void testFailWrite( S subject, String errMsg )
    {
        assertCallFail( subject, "CREATE (:Node)", errMsg );
    }

    void testSuccessfulSchema( S subject )
    {
        assertCallEmpty( subject, "CREATE INDEX ON :Node(number)" );
    }

    void testFailSchema( S subject ) { testFailSchema( subject, SCHEMA_OPS_NOT_ALLOWED ); }
    void testFailSchema( S subject, String errMsg )
    {
        assertCallFail( subject, "CREATE INDEX ON :Node(number)", errMsg );
    }

    void testFailCreateUser( S subject, String errMsg )
    {
        assertCallFail( subject, "CALL dbms.createUser('Craig', 'foo', false)", errMsg );
        assertCallFail( subject, "CALL dbms.createUser('Craig', '', false)", errMsg );
        assertCallFail( subject, "CALL dbms.createUser('', 'foo', false)", errMsg );
    }

    void testFailAddUserToRole( S subject, String username, String role, String errMsg )
    {
        assertCallFail( subject, "CALL dbms.addUserToRole('" + username + "', '" + role + "')", errMsg );
    }

    void testFailRemoveUserFromRole( S subject, String username, String role, String errMsg )
    {
        assertCallFail( subject, "CALL dbms.removeUserFromRole('" + username + "', '" + role + "')", errMsg );
    }

    void testFailDeleteUser( S subject, String username, String errMsg )
    {
        assertCallFail( subject, "CALL dbms.deleteUser('" + username + "')", errMsg );
    }

    void testSuccessfulListUsers( S subject, String[] users )
    {
        executeQuery( subject, "CALL dbms.listUsers() YIELD username",
                r -> assertKeyIsArray( r, "username", users ) );
    }

    void testFailListUsers( S subject, int count, String errMsg )
    {
        assertCallFail( subject, "CALL dbms.listUsers() YIELD username", errMsg );
    }

    void testSuccessfulListRoles( S subject, String[] roles )
    {
        executeQuery( subject, "CALL dbms.listRoles() YIELD role",
                r -> assertKeyIsArray( r, "role", roles ) );
    }

    void testFailListRoles( S subject, String errMsg )
    {
        assertCallFail( subject, "CALL dbms.listRoles() YIELD role", errMsg );
    }

    void testFailListUserRoles( S subject, String username, String errMsg )
    {
        assertCallFail( subject,
                "CALL dbms.listRolesForUser('" + username + "') YIELD value AS roles RETURN count(roles)",
                errMsg );
    }

    void testFailListRoleUsers( S subject, String roleName, String errMsg )
    {
        assertCallFail( subject,
                "CALL dbms.listUsersForRole('" + roleName + "') YIELD value AS users RETURN count(users)",
                errMsg );
    }

    void assertCallFail( S subject, String call, String partOfErrorMsg )
    {
        String err = assertCallEmpty( subject, call );
        assertThat( err, containsString( partOfErrorMsg ) );
    }

    void assertCallSuccess( S subject, String call )
    {
        String err = assertCallEmpty( subject, call );
        assertThat( err, equalTo( "" ) );
    }

    void assertCallSuccess( S subject, String call, Consumer<ResourceIterator<Map<String, Object>>> resultConsumer )
    {
        String err = neo.executeQuery( subject, call, null, resultConsumer );
        assertThat( err, equalTo( "" ) );
    }

    String assertCallEmpty( S subject, String call )
    {
        return neo.executeQuery( subject, call, null,
                ( res ) -> assertFalse( "Expected no results", res.hasNext()
            ) );
    }

    void testAuthenticated( S subject )
    {
        assertTrue( neo.isAuthenticated( subject ) );
    }

    void testUnAuthenticated( S subject )
    {
        assertFalse( neo.isAuthenticated( subject ) );
    }

    void testCallCount( S subject, String call, Map<String,Object> params,
            final int count )
    {
        String err =
            neo.executeQuery( subject, call, params,
                ( res ) -> {
                    int left = count;
                    while ( left > 0 )
                    {
                        assertTrue( "Expected " + count + " results, but got only " + (count - left), res.hasNext() );
                        res.next();
                        left--;
                    }
                    assertFalse( "Expected " + count + " results, but there are more ", res.hasNext() );
                }
            );
        assertNoError(err);
    }

    void executeQuery( S subject, String call )
    {
        neo.executeQuery( subject, call, null, r -> {} );
    }

    void executeQuery( S subject, String call, Consumer<ResourceIterator<Map<String, Object>>> resultConsumer )
    {
        neo.executeQuery( subject, call, null, resultConsumer );
    }

    boolean userHasRole( String user, String role )
    {
        return userManager.getRoleNamesForUser( user ).contains( role );
    }

    List<Object> getObjectsAsList( ResourceIterator<Map<String, Object>> r, String key )
    {
        return r.stream().map( s -> s.get( key ) ).collect( Collectors.toList() );
    }

    void assertKeyIs( ResourceIterator<Map<String, Object>> r, String key, String... items )
    {
        assertKeyIsArray( r, key, items );
    }

    void assertKeyIsArray( ResourceIterator<Map<String, Object>> r, String key, String[] items )
    {
        List<Object> results = getObjectsAsList( r, key );
        assertEquals( Arrays.asList( items ).size(), results.size() );
        Assert.assertThat( results, containsInAnyOrder( items ) );
    }

    protected void assertKeyIsMap( ResourceIterator<Map<String, Object>> r, String keyKey, String valueKey, Map<String,Object> expected )
    {
        List<Map<String, Object>> result = r.stream().collect( Collectors.toList() );

        assertEquals( "Results for should have size " + expected.size() + " but was " + result.size(),
                expected.size(), result.size() );

        for ( Map<String, Object> row : result )
        {
            String key = (String) row.get( keyKey );
            assertTrue( "Unexpected key '" + key + "'", expected.containsKey( key ) );

            Object objectValue = row.get( valueKey );
            if ( objectValue instanceof List )
            {
                List<String> value = (List<String>) objectValue;
                List<String> expectedValues = (List<String>) expected.get( key );
                assertEquals( "Results for '" + key + "' should have size " + expectedValues.size() + " but was " +
                        value.size(), value.size(), expectedValues.size() );
                assertThat( value, containsInAnyOrder( expectedValues.toArray() ) );
            }
            else
            {
                String value = objectValue.toString();
                String expectedValue = expected.get( key ).toString();
                assertTrue(
                        String.format( "Wrong value for '%s', expected '%s', got '%s'", key, expectedValue, value),
                        value.equals( expectedValue )
                    );
            }
        }
    }

    void assertNoError( String errMsg )
    {
        assertTrue( "Should not give error", errMsg.equals( "" ) );
    }
}
