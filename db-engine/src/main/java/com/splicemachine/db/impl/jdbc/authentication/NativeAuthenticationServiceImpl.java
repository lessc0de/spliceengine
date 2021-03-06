/*
 * Apache Derby is a subproject of the Apache DB project, and is licensed under
 * the Apache License, Version 2.0 (the "License"); you may not use these files
 * except in compliance with the License. You may obtain a copy of the License at:
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed
 * under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR
 * CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 *
 * Splice Machine, Inc. has modified this file.
 *
 * All Splice Machine modifications are Copyright 2012 - 2016 Splice Machine, Inc.,
 * and are licensed to you under the License; you may not use this file except in
 * compliance with the License.
 *
 * Unless required by applicable law or agreed to in writing, software distributed
 * under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR
 * CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 *
 */

package com.splicemachine.db.impl.jdbc.authentication;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.SQLWarning;
import java.util.Arrays;
import java.util.Properties;

import javax.sql.DataSource;

import com.splicemachine.db.authentication.UserAuthenticator;
import com.splicemachine.db.catalog.SystemProcedures;
import com.splicemachine.db.catalog.UUID;
import com.splicemachine.db.iapi.error.SQLWarningFactory;
import com.splicemachine.db.iapi.error.StandardException;
import com.splicemachine.db.iapi.reference.Attribute;
import com.splicemachine.db.iapi.reference.Property;
import com.splicemachine.db.iapi.reference.SQLState;
import com.splicemachine.db.iapi.services.monitor.Monitor;
import com.splicemachine.db.iapi.services.property.PropertyUtil;
import com.splicemachine.db.iapi.sql.conn.ConnectionUtil;
import com.splicemachine.db.iapi.sql.conn.LanguageConnectionContext;
import com.splicemachine.db.iapi.sql.dictionary.DataDescriptorGenerator;
import com.splicemachine.db.iapi.sql.dictionary.DataDictionary;
import com.splicemachine.db.iapi.sql.dictionary.PasswordHasher;
import com.splicemachine.db.iapi.sql.dictionary.SchemaDescriptor;
import com.splicemachine.db.iapi.sql.dictionary.UserDescriptor;
import com.splicemachine.db.iapi.store.access.TransactionController;
import com.splicemachine.db.iapi.util.IdUtil;
import com.splicemachine.db.impl.jdbc.Util;
import com.splicemachine.db.jdbc.InternalDriver;

/**
 * <p>
 * This authentication service supports Derby NATIVE authentication.
 * </p>
 *
 * <p>
 * To activate this service, set the db.authentication.provider database
 * or system property to a value beginning with the token "NATIVE:".
 * </p>
 *
 * <p>
 * This service instantiates and calls the basic User authentication scheme at runtime.
 * </p>
 *
 * <p>
 * User credentials are defined in the SYSUSERS table.
 * </p>
 *
 */
public final class NativeAuthenticationServiceImpl
	extends AuthenticationServiceBase implements UserAuthenticator
{
    ///////////////////////////////////////////////////////////////////////////////////
    //
    // CONSTANTS
    //
    ///////////////////////////////////////////////////////////////////////////////////

    ///////////////////////////////////////////////////////////////////////////////////
    //
    // STATE
    //
    ///////////////////////////////////////////////////////////////////////////////////

    // temporary, used when bootstrapping a locally authenticated database
    private boolean _creatingCredentialsDB = false;
    
    private String      _credentialsDB;
    private boolean _authenticateDatabaseOperationsLocally;
    private long        _passwordLifetimeMillis = Property.AUTHENTICATION_NATIVE_PASSWORD_LIFETIME_DEFAULT;
    private double      _passwordExpirationThreshold = Property.AUTHENTICATION_PASSWORD_EXPIRATION_THRESHOLD_DEFAULT;
    private String      _badlyFormattedPasswordProperty;

    ///////////////////////////////////////////////////////////////////////////////////
    //
    // ModuleControl BEHAVIOR
    //
    ///////////////////////////////////////////////////////////////////////////////////

	/**
	 *  Check if we should activate this authentication service.
	 */
	public boolean canSupport(Properties properties)
    {
		if (!requireAuthentication(properties)) { return false; }

        if ( PropertyUtil.nativeAuthenticationEnabled( properties ) )
        {
            parseNativeSpecification( properties );

            return true;
        }
        else { return false; }
	}

    /**
     * <p>
     * Parse the specification of NATIVE authentication. It can take 3 forms:
     * </p>
     *
     * <ul>
     * <li><i>NATIVE:$credentialsDB</i> - Here $credentialsDB is the name of a Derby database.
     *  This means that all authentication should take place in $credentialsDB.</li>
     * <li><i>NATIVE:$credentialsDB:LOCAL</i>- This means that system-wide operations (like engine shutdown)
     *  are authenticated in $credentialsDB but connections to existing databases are authenticated
     *  in those databases.</li>
     * <li><i>NATIVE::LOCAL</i> - This means that connections to a given database are authenticated
     *  in that database.</li>
     * </ul>
     */
    private void    parseNativeSpecification( Properties properties )
    {
        // If we get here, we already know that the authentication provider property
        // begins with the NATIVE: token
        String authenticationProvider = PropertyUtil.getPropertyFromSet
            (
             properties,
             Property.AUTHENTICATION_PROVIDER_PARAMETER
             );

        _authenticateDatabaseOperationsLocally = PropertyUtil.localNativeAuthenticationEnabled( properties );

        // Everything between the first colon and the last colon is the name of a database
        int     dbNameStartIdx = authenticationProvider.indexOf( ":" ) + 1;
        int     dbNameEndIdx = _authenticateDatabaseOperationsLocally ?
            authenticationProvider.lastIndexOf( ":" )
            : authenticationProvider.length();

        if ( dbNameEndIdx > dbNameStartIdx )
        {
            _credentialsDB = authenticationProvider.substring( dbNameStartIdx, dbNameEndIdx );

            if ( _credentialsDB.length() == 0 ) { _credentialsDB = null; }
        }

        //
        // Let the application override password lifespans.
        //
        _badlyFormattedPasswordProperty = null;
        String passwordLifetimeString = PropertyUtil.getPropertyFromSet
            (
             properties,
             Property.AUTHENTICATION_NATIVE_PASSWORD_LIFETIME
             );
        if ( passwordLifetimeString != null )
        {
            Long    passwordLifetime = parsePasswordLifetime( passwordLifetimeString );

            if ( passwordLifetime != null ) { _passwordLifetimeMillis = passwordLifetime.longValue(); }
            else
            { _badlyFormattedPasswordProperty = Property.AUTHENTICATION_NATIVE_PASSWORD_LIFETIME; }
        }

        String  expirationThresholdString = PropertyUtil.getPropertyFromSet
            (
             properties,
             Property.AUTHENTICATION_PASSWORD_EXPIRATION_THRESHOLD
             );
        if ( expirationThresholdString != null )
        {
            Double  expirationThreshold = parsePasswordThreshold( expirationThresholdString );

            if ( expirationThreshold != null ) { _passwordExpirationThreshold = expirationThreshold.doubleValue(); }
            else
            { _badlyFormattedPasswordProperty = Property.AUTHENTICATION_PASSWORD_EXPIRATION_THRESHOLD; }
        }
        
    }

    /**
     * <p>
     * Return true if AUTHENTICATION_PROVIDER_PARAMETER was well formatted.
     * The property must have designated some database as the authentication authority.
     * </p>
     */
    private boolean validAuthenticationProvider()
        throws StandardException
    {
        // If there is no store, then we are booting a system-wide authentication service
        boolean     systemWideAuthentication = ( getServiceName() == null );

        if ( _credentialsDB != null )
        {
            // make sure that it is a well-formed database name
            if ( Monitor.getMonitor().getCanonicalServiceName( _credentialsDB ) == null )
            {
                throw StandardException.newException( SQLState.BAD_CREDENTIALS_DB_NAME, _credentialsDB );
            }
            else { return true; }
        }
        
        // must have a global credentials db for system-wide authentication
        if ( systemWideAuthentication ) { return false; }

        // so there is no credentials db specified and we are booting a database.
        // this is only allowed if we are authenticating locally in that database.
        return _authenticateDatabaseOperationsLocally;
    }

	/**
	 * @see com.splicemachine.db.iapi.services.monitor.ModuleControl#boot
	 * @exception StandardException upon failure to load/boot the expected
	 * authentication service.
	 */
	public void boot(boolean create, Properties properties)
	  throws StandardException
    {
		// first perform the initialization in our superclass
		super.boot( create, properties );

        if ( !validAuthenticationProvider() )
        {
            throw StandardException.newException( SQLState.BAD_NATIVE_AUTH_SPEC );
        }

        if ( _badlyFormattedPasswordProperty != null )
        {
            throw StandardException.newException
                ( SQLState.BAD_PASSWORD_LIFETIME, _badlyFormattedPasswordProperty );
        }

		// Initialize the MessageDigest class engine here
		// (we don't need to do that ideally, but there is some
		// overhead the first time it is instantiated.
		try {
			MessageDigest digestAlgorithm = MessageDigest.getInstance("SHA-1");
			digestAlgorithm.reset();

		} catch (NoSuchAlgorithmException nsae) {
			throw Monitor.exceptionStartingModule(nsae);
		}

        // bootstrap the creation of the initial username/password when the dbo creates a credentials db
		/*
		 * DB-2088: Below, there is a manual override that can be enabled to force the creation of the
		 * native credentials database after the Splice/Derby database has been created.
		 * This is useful for beta Splice customers that wish to enable AnA.
		 */
		if ((create || PropertyUtil.createNativeAuthenticationCredentialsDatabaseEnabled(properties)) &&
			authenticatingInThisService( getCanonicalServiceName()))
		{
			_creatingCredentialsDB = true;
		} else {
			_creatingCredentialsDB = false;
		}

		// Set ourselves as being ready, having loaded the proper
		// authentication scheme for this service
		//
		this.setAuthenticationService(this);
	}

    ///////////////////////////////////////////////////////////////////////////////////
    //
    // UserAuthenticator BEHAVIOR
    //
    ///////////////////////////////////////////////////////////////////////////////////

    /** Override behavior in superclass */
    public  String  getSystemCredentialsDatabaseName()    { return _credentialsDB; }
    /** Override behavior in superclass */

	/**
	 * Authenticate the passed-in user's credentials.
	 *
	 * @param userName		The user's name used to connect to JBMS system
	 * @param userPassword	The user's password used to connect to JBMS system
	 * @param databaseName	The database which the user wants to connect to.
	 * @param info			Additional jdbc connection info.
	 */
	public boolean	authenticateUser
        (
         String userName,
         String userPassword,
         String databaseName,
         Properties info
         )
        throws SQLException
	{
        try {
            // No "guest" user
            if ( userName == null ) { return false; }
            if ( userPassword == null ) { return false; }

            //
            // We must handle these cases:
            //
            // 1) Database name is null. This means that we are authenticating a system-wide
            // operation. The authentication must be done by the system-wide credentials database.
            //
            // 2) Database name is not null and authentication is NOT specified as local.
            // This means that we are authenticating a database-specific operation
            // in the system-wide credentials database. There are two subcases:
            //
            // 2a) The current database is NOT the credentials database. This reduces to case (1) above:
            // authentication must be performed in another database.
            //
            // 2b) The current database IS the credentials database. This reduces to case (3) below:
            // authentication must be performed in this database.
            //
            // 3) Database name is not null and authentication IS being performed locally in this database.
            // This means that we are authenticating a database-specific operation and performing the
            // authentication in this database.
            //

            if ( (databaseName == null) || !authenticatingInThisDatabase( databaseName ) )
            {
                return authenticateRemotely(  userName, userPassword, databaseName );
            }
            else
            {
                return authenticateLocally( userName, userPassword, databaseName );
            }
        }
        catch (StandardException se)
        {
            throw Util.generateCsSQLException(se);
        }
	}

    /**
     * <p>
     * Return true if we are authenticating in this database.
     * </p>
     */
    private boolean authenticatingInThisDatabase( String userVisibleDatabaseName )
        throws StandardException
    {
        return authenticatingInThisService( Monitor.getMonitor().getCanonicalServiceName( userVisibleDatabaseName ) );
    }

    /**
     * <p>
     * Return true if we are authenticating in this service.
     * </p>
     */
    private boolean authenticatingInThisService( String canonicalDatabaseName )
        throws StandardException
    {
        if ( _authenticateDatabaseOperationsLocally ) { return true; }
        else { return isCredentialsService( canonicalDatabaseName ); }
    }

    /**
     * <p>
     * Return true if the passed in service is the credentials database.
     * </p>
     */
    private boolean isCredentialsService( String canonicalDatabaseName )
        throws StandardException
    {
        String  canonicalCredentialsDBName = getCanonicalServiceName( _credentialsDB );

        String canonicalDB = Monitor.getMonitor().getCanonicalServiceName( canonicalDatabaseName );

        if ( canonicalCredentialsDBName == null ) { return false; }
        else { return canonicalCredentialsDBName.equals( canonicalDatabaseName ); }
    }

    /** Get the canonical name of the current database service */
    private String  getCanonicalServiceName()
        throws StandardException
    {
        return getCanonicalServiceName( getServiceName() );
    }

    /** Turn a service name into its normalized, standard form */
    private String  getCanonicalServiceName( String rawName )
        throws StandardException
    {
        return Monitor.getMonitor().getCanonicalServiceName( rawName );
    }

    ///////////////////////////////////////////////////////////////////////////////////
    //
    // AUTHENTICATE REMOTELY
    //
    ///////////////////////////////////////////////////////////////////////////////////

	/**
	 * Authenticate the passed-in credentials against another Derby database. This is done
     * by getting a connection to the credentials database using the supplied username
     * and password. If the connection attempts succeeds, then authentication succeeds.
	 *
	 * @param userName		The user's name used to connect to JBMS system
	 * @param userPassword	The user's password used to connect to JBMS system
	 * @param databaseName	The database which the user wants to connect to.
	 */
	private boolean	authenticateRemotely
        (
         String userName,
         String userPassword,
         String databaseName
         )
        throws StandardException, SQLWarning
	{
        // this catches the case when someone specifies db.authentication.provider=NATIVE::LOCAL
        // at the system level
        if ( _credentialsDB == null )
        {
            throw StandardException.newException( SQLState.BAD_NATIVE_AUTH_SPEC );
        }
        
        SQLWarning  warnings = null;

        try {
            Properties  properties = new Properties();
            properties.setProperty( Attribute.USERNAME_ATTR, userName );
            properties.setProperty( Attribute.PASSWORD_ATTR, userPassword );

            String  connectionURL = Attribute.PROTOCOL + _credentialsDB;

            Connection  conn = InternalDriver.activeDriver().connect( connectionURL, properties );
            
            warnings = conn.getWarnings();
            conn.close();
        }
        catch (SQLException se)
        {
            String  sqlState = se.getSQLState();

            if ( SQLState.LOGIN_FAILED.equals( sqlState ) ) { return false; }
            else if ( SQLState.DATABASE_NOT_FOUND.startsWith( sqlState ) )
            {
                throw StandardException.newException( SQLState.MISSING_CREDENTIALS_DB, _credentialsDB );
            }
            else { throw wrap( se ); }
        }

        // let warnings percolate up so that EmbedConnection can handle notifications
        // about expiring passwords
        if ( warnings != null ) { throw warnings; }

        // If we get here, then we successfully connected to the credentials database. Hooray.
        return true;
    }
    /** Call a setter method on a DataSource via reflection */
    private void callDataSourceSetter( DataSource ds, String methodName, String value )
        throws StandardException
    {
        try {
            ds.getClass().getMethod( methodName, new Class[] { String.class } ).invoke( ds, new Object[] { value } );
        } catch (Exception e)  { throw wrap( e ); }   
    }
    private StandardException wrap( Throwable t )   { return StandardException.plainWrapException( t ); }
    
    ///////////////////////////////////////////////////////////////////////////////////
    //
    // AUTHENTICATE LOCALLY
    //
    ///////////////////////////////////////////////////////////////////////////////////

	/**
	 * Authenticate the passed-in credentials against the local database.
	 *
	 * @param userName		The user's name used to connect to JBMS system
	 * @param userPassword	The user's password used to connect to JBMS system
	 * @param databaseName	The database which the user wants to connect to.
	 */
	private boolean	authenticateLocally
        (
         String userName,
         String userPassword,
         String databaseName
         )
        throws StandardException, SQLException
	{
        userName = IdUtil.getUserAuthorizationId( userName ) ;

        //
        // we expect to find a data dictionary
        //
        DataDictionary      dd = (DataDictionary) Monitor.getServiceModule( this, DataDictionary.MODULE );

        //
        // Special bootstrap code. If we are creating a credentials database, then
        // we store the DBO's initial credentials in it. We also turn on NATIVE LOCAL authentication
        // forever.
        //
        if ( _creatingCredentialsDB )
        {
            _creatingCredentialsDB = false;
            
            TransactionController   tc = getTransaction();

            createDBOUserIfDoesNotExist(userName, userPassword, dd, tc);
            createDBOSchemaIfDoesNotExist(userName, userPassword, dd, tc);

            tc.commit();
            
            return true;
        }
        
        UserDescriptor      userDescriptor = dd.getUser( userName );
        
        if ( userDescriptor == null )
        {
            //
            // Before returning, we pretend to evaluate the password.
            // This helps prevent blackhats from discovering legal usernames
            // by measuring how long password evaluation takes. For more context,
            // see the 2012-02-22 comment on DERBY-5539.
            //
            PasswordHasher          hasher = dd.makePasswordHasher( getDatabaseProperties() );
            
            hasher.hashPasswordIntoString( userName, userPassword ).toCharArray();

            return false;
        }
        
        PasswordHasher      hasher = new PasswordHasher( userDescriptor.getHashingScheme() );
        char[]                     candidatePassword = hasher.hashPasswordIntoString( userName, userPassword ).toCharArray();
        char[]                     actualPassword = userDescriptor.getAndZeroPassword();

        try {
            if ( (candidatePassword == null) || (actualPassword == null)) { return false; }
            if ( candidatePassword.length != actualPassword.length ) { return false; }
        
            for ( int i = 0; i < candidatePassword.length; i++ )
            {
                if ( candidatePassword[ i ] != actualPassword[ i ] ) { return false; }
            }
        } finally
        {
            if ( candidatePassword != null ) { Arrays.fill( candidatePassword, (char) 0 ); }
            if ( actualPassword != null ) { Arrays.fill( actualPassword, (char) 0 ); }
        }

        //
        // Password is good. Check whether the password has expired or will expire soon.
        //
        if ( _passwordLifetimeMillis > 0 )
        {
            long    passwordAge = System.currentTimeMillis() - userDescriptor.getLastModified().getTime();
            long    remainingLifetime = _passwordLifetimeMillis - passwordAge;

            //
            // Oops, the password has expired. Fail the authentication. Say nothing more
            // so that we give password crackers as little information as possible.
            //
            if ( remainingLifetime <= 0L )
            {
                // The DBO's password never expires.
                if ( !dd.getAuthorizationDatabaseOwner().equals( userName ) ) { return false; }
                else { remainingLifetime = 0L; }
            }

            long    expirationThreshold = (long) ( _passwordLifetimeMillis * _passwordExpirationThreshold );
            
            if ( remainingLifetime <= expirationThreshold )
            {
                if ( dd.getAuthorizationDatabaseOwner().equals( userName ) )
                {
                    throw SQLWarningFactory.newSQLWarning( SQLState.DBO_PASSWORD_EXPIRES_SOON, databaseName );
                }
                
                long    daysRemaining = remainingLifetime / Property.MILLISECONDS_IN_DAY;
                throw SQLWarningFactory.newSQLWarning
                    ( SQLState.PASSWORD_EXPIRES_SOON, Long.toString( daysRemaining ), databaseName );
            }
        }
        
        return true;
    }

	/**
	 * Create the DBO if it does not already exist in the local credentials database.
	 * @param userName		The DBO user's name used to connect to JBMS system
	 * @param userPassword	The DBO user's password used to connect to JBMS system
	 * @param dd			data dictionary to store the user
	 * @param tc			transaction for this operation
	 * @throws StandardException
	 * @throws SQLException
	 */
	private void createDBOUserIfDoesNotExist(String userName, String userPassword, DataDictionary dd, TransactionController tc)
		throws StandardException, SQLException {
		// Check if the DBO already exists which may happen if the manual override for
		// creation of the native credentials database is set.
		if (dd.getUser(userName) == null) {
			SystemProcedures.addUser( userName, userPassword, tc );
			// Change the system schemas to be owned by the user.  This is needed for upgrading
			// the Splice Machine 0.5 beta where the owner of the system schemas was APP.
			// Splice Machine 1.0+ has the SPLICE user as the DBO of the system schemas.
			SystemProcedures.updateSystemSchemaAuthorization(userName, tc);
		}
	}

	/**
	 * Create the default schema for the DBO if it does not already exist in the local credentials database.
	 * @param userName		The DBO user's name used to connect to JBMS system
	 * @param dd			data dictionary to store the DBO schema
	 * @param tc			transaction for this operation
	 * @throws StandardException
	 * @throws SQLException
	 */
	private void createDBOSchemaIfDoesNotExist(String userName, String userPassword, DataDictionary dd, TransactionController tc)
		throws StandardException, SQLException {
        LanguageConnectionContext lcc = ConnectionUtil.getCurrentLCC();
        DataDescriptorGenerator ddg = dd.getDataDescriptorGenerator();

        // Check if the DBO schema already exists which may happen if the manual override for
		// creation of the native credentials database is set.
		SchemaDescriptor sd = dd.getSchemaDescriptor(userName, tc, false);
		if (sd == null || sd.getUUID() == null) {
			UUID tmpSchemaId = dd.getUUIDFactory().createUUID();
			dd.startWriting(lcc);
	        sd = ddg.newSchemaDescriptor(userName, userName, tmpSchemaId);
	        dd.addDescriptor(sd, null, DataDictionary.SYSSCHEMAS_CATALOG_NUM, false, tc);
		}
	}
}
