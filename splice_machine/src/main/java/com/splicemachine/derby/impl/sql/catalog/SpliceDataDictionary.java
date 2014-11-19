package com.splicemachine.derby.impl.sql.catalog;

import com.splicemachine.constants.SpliceConstants;
import com.splicemachine.derby.hbase.ManifestReader;
import com.splicemachine.derby.hbase.SpliceDriver;
import com.splicemachine.derby.impl.sql.catalog.upgrade.SpliceCatalogUpgradeScripts;
import com.splicemachine.derby.impl.sql.execute.sequence.SpliceSequence;
import com.splicemachine.derby.impl.sql.execute.sequence.SpliceSequenceKey;
import com.splicemachine.derby.impl.store.access.BaseSpliceTransaction;
import com.splicemachine.derby.impl.store.access.SpliceAccessManager;
import com.splicemachine.derby.impl.store.access.SpliceTransaction;
import com.splicemachine.derby.impl.store.access.SpliceTransactionManager;
import com.splicemachine.pipeline.exception.Exceptions;
import org.apache.derby.catalog.AliasInfo;
import org.apache.derby.catalog.UUID;
import org.apache.derby.iapi.db.Database;
import org.apache.derby.iapi.error.StandardException;
import org.apache.derby.iapi.services.cache.Cacheable;
import org.apache.derby.iapi.services.context.ContextService;
import org.apache.derby.iapi.services.monitor.Monitor;
import org.apache.derby.iapi.services.sanity.SanityManager;
import org.apache.derby.iapi.sql.conn.LanguageConnectionContext;
import org.apache.derby.iapi.sql.dictionary.*;
import org.apache.derby.iapi.sql.execute.ExecIndexRow;
import org.apache.derby.iapi.sql.execute.ExecRow;
import org.apache.derby.iapi.sql.execute.ScanQualifier;
import org.apache.derby.iapi.store.access.AccessFactory;
import org.apache.derby.iapi.store.access.TransactionController;
import org.apache.derby.iapi.types.*;
import org.apache.derby.impl.sql.catalog.*;
import org.apache.hadoop.hbase.client.HTableInterface;
import org.apache.log4j.Logger;
import com.splicemachine.derby.impl.sql.depend.SpliceDependencyManager;
import com.splicemachine.utils.SpliceLogUtils;
import java.util.Collections;
import java.util.List;
import java.util.Properties;

/**
 * @author Scott Fines
 *         Created on: 2/28/13
 */
public class SpliceDataDictionary extends DataDictionaryImpl {
	protected static final Logger LOG = Logger.getLogger(SpliceDataDictionary.class);
    public static final int SYSPRIMARYKEYS_CATALOG_NUM = 23;
    private volatile TabInfoImpl pkTable = null;
    private volatile TabInfoImpl statementHistoryTable = null;
    private volatile TabInfoImpl operationHistoryTable = null;
    private volatile TabInfoImpl taskHistoryTable = null;
    private Splice_DD_Version spliceSoftwareVersion;
    private HTableInterface spliceSequencesTable;

    public static final String SPLICE_DATA_DICTIONARY_VERSION = "SpliceDataDictionaryVersion";

    @Override
    protected SystemProcedureGenerator getSystemProcedures() {
        return new SpliceSystemProcedures(this);
    }

    @Override
    public SubKeyConstraintDescriptor getSubKeyConstraint(UUID constraintId,
                                                          int type) throws StandardException {
        if(type == DataDictionary.PRIMARYKEY_CONSTRAINT){
            DataValueDescriptor constraintIDOrderable = getIDValueAsCHAR(constraintId);

            TabInfoImpl ti = getPkTable();
            faultInTabInfo(ti);

            SYSPRIMARYKEYSRowFactory rf = (SYSPRIMARYKEYSRowFactory) ti.getCatalogRowFactory();
            ScanQualifier[][] scanQualifiers = exFactory.getScanQualifier(1);
            scanQualifiers[0][0].setQualifier(
               SYSPRIMARYKEYSRowFactory.SYSPRIMARYKEYS_CONSTRAINTID-1,
                    constraintIDOrderable,
                    Orderable.ORDER_OP_EQUALS,
                    false, false, false);
            return (SubKeyConstraintDescriptor)getDescriptorViaHeap(
                    null,scanQualifiers,ti,null,null);
        }
        /*If it's a foreign key or unique constraint, then just do the derby default*/
        return super.getSubKeyConstraint(constraintId,type);
    }

    @Override
    protected void addSubKeyConstraint(KeyConstraintDescriptor descriptor,
                                       TransactionController tc)
            throws StandardException {
        ExecRow row;
        TabInfoImpl	ti;

		/*
		** Foreign keys get a row in SYSFOREIGNKEYS, and
		** all others get a row in SYSKEYS.
		*/
        if (descriptor.getConstraintType()
                == DataDictionary.FOREIGNKEY_CONSTRAINT) {
            if (SanityManager.DEBUG) {
                if (!(descriptor instanceof ForeignKeyConstraintDescriptor)) {
                    SanityManager.THROWASSERT("descriptor not an fk descriptor, is "+
                            descriptor.getClass().getName());
                }
            }
            ForeignKeyConstraintDescriptor fkDescriptor =
                    (ForeignKeyConstraintDescriptor)descriptor;

            ti = getNonCoreTI(SYSFOREIGNKEYS_CATALOG_NUM);
            SYSFOREIGNKEYSRowFactory fkkeysRF = (SYSFOREIGNKEYSRowFactory)ti.getCatalogRowFactory();

            row = fkkeysRF.makeRow(fkDescriptor, null);

			/*
			** Now we need to bump the reference count of the
			** contraint that this FK references
			*/
            ReferencedKeyConstraintDescriptor refDescriptor =
                    fkDescriptor.getReferencedConstraint();

            refDescriptor.incrementReferenceCount();

            int[] colsToSet = new int[1];
            colsToSet[0] = SYSCONSTRAINTSRowFactory.SYSCONSTRAINTS_REFERENCECOUNT;

            updateConstraintDescriptor(refDescriptor,
                    refDescriptor.getUUID(),
                    colsToSet,
                    tc);
        }else if (descriptor.getConstraintType()
                ==DataDictionary.PRIMARYKEY_CONSTRAINT){
            ti = getPkTable();
            faultInTabInfo(ti);
            SYSPRIMARYKEYSRowFactory pkRF = (SYSPRIMARYKEYSRowFactory)ti.getCatalogRowFactory();

            row = pkRF.makeRow(descriptor,null);
        } else {
            ti = getNonCoreTI(SYSKEYS_CATALOG_NUM);
            SYSKEYSRowFactory keysRF = (SYSKEYSRowFactory) ti.getCatalogRowFactory();

            // build the row to be stuffed into SYSKEYS
            row = keysRF.makeRow(descriptor, null);
        }

        // insert row into catalog and all its indices
        ti.insertRow(row, tc);
    }

    private TabInfoImpl getPkTable() throws StandardException {
        if(pkTable ==null){
            pkTable = new TabInfoImpl(new SYSPRIMARYKEYSRowFactory(uuidFactory,exFactory,dvf));
        }
        initSystemIndexVariables(pkTable);
        return pkTable;
    }

	/**
	 * Initialize system catalogs. This is where Derby performs upgrade.
	 * This is where Splice updates (reloads) the system stored procedures
	 * when the <code>splice.updateSystemProcs</code> system property is set to true.
	 *
	 *	@param	tc				TransactionController
//	 *	@param	ddg				DataDescriptorGenerator
//	 *	@param	startParams		Properties
	 *
	 * 	@exception StandardException		Thrown on error
	 */
    @Override
	protected void updateSystemProcedures(TransactionController tc)
		throws StandardException {
//        BaseSpliceTransaction rawTransaction = ((SpliceTransactionManager) tc).getRawTransaction();
//        if(!rawTransaction.getActiveStateTxn().allowsWrites()){
//            assert rawTransaction instanceof SpliceTransaction: "Cannot update system procedures without elevating a transaction first";
//
//            SpliceTransaction transaction = (SpliceTransaction)rawTransaction;
//            transaction.elevate("dictionary".getBytes());
//        }
        super.updateSystemProcedures(tc);
    }

    private TabInfoImpl getStatementHistoryTable() throws StandardException {
        if (statementHistoryTable == null) {
            statementHistoryTable = new TabInfoImpl(new SYSSTATEMENTHISTORYRowFactory(uuidFactory,exFactory,dvf));
        }
        initSystemIndexVariables(statementHistoryTable);
        return statementHistoryTable;
    }

    private TabInfoImpl getOperationHistoryTable() throws StandardException {
        if (operationHistoryTable == null) {
            operationHistoryTable = new TabInfoImpl(new SYSOPERATIONHISTORYRowFactory(uuidFactory,exFactory,dvf));
        }
        initSystemIndexVariables(operationHistoryTable);
        return operationHistoryTable;
    }

    private TabInfoImpl getTaskHistoryTable() throws StandardException {
        if (taskHistoryTable == null) {
            taskHistoryTable = new TabInfoImpl(new SYSTASKHISTORYRowFactory(uuidFactory,exFactory,dvf));
        }
        initSystemIndexVariables(taskHistoryTable);
        return taskHistoryTable;
    }

    public void createFujiTables(TransactionController tc) throws StandardException{
        //create SYSSTATEMENTHISTORY
        makeCatalog(getStatementHistoryTable(), getSystemSchemaDescriptor(), tc);

        //create SYSOPERATIONHISTORY
        makeCatalog(getOperationHistoryTable(), getSystemSchemaDescriptor(), tc);

        //SYSTASKHISTORY
        makeCatalog(getTaskHistoryTable(), getSystemSchemaDescriptor(), tc);
    }

    @Override
    protected void createDictionaryTables(Properties params,
                                          TransactionController tc,
                                          DataDescriptorGenerator ddg)
            throws StandardException {
        super.createDictionaryTables(params, tc, ddg);

        //create SYSPRIMARYKEYS
        makeCatalog(getPkTable(), getSystemSchemaDescriptor(), tc);

        createFujiTables(tc);

    }
    
    public static void verifySetup() {
    }
    @Override
	public SchemaDescriptor locateSchemaRow(String schemaName,  TransactionController tc) throws StandardException {
    	/*
    	Cache cache = SpliceDriver.driver().getCache(SpliceConstants.SYSSCHEMAS_INDEX1_ID_CACHE);
    	Element element;
    	if ( (element = cache.get(schemaName)) != null) {
    		if (tc == null)
    				tc = getTransactionCompile();
    		tc.getActiveStateTxIdString();
    		if (element.getVersion() >= Long.parseLong(tc.getActiveStateTxIdString())) {
    			return (SchemaDescriptor) element.getObjectValue();
    		}
    	}
    	*/
		DataValueDescriptor		  schemaNameOrderable;
		TabInfoImpl					  ti = coreInfo[SYSSCHEMAS_CORE_NUM];

		schemaNameOrderable = new SQLVarchar(schemaName);

		ExecIndexRow keyRow = exFactory.getIndexableRow(1);
		keyRow.setColumn(1, schemaNameOrderable);
		
		// XXX - TODO Cache Lookup
		
		SchemaDescriptor desc = (SchemaDescriptor)
					getDescriptorViaIndex(
						SYSSCHEMASRowFactory.SYSSCHEMAS_INDEX1_ID,
						keyRow,
                  null,
						ti,
                  null,
                  null,
						false,
                        TransactionController.ISOLATION_REPEATABLE_READ,
						tc);
		
		return desc;
	}

    @Override
    protected void loadDictionaryTables(TransactionController tc,
                                        DataDescriptorGenerator ddg,
                                        Properties startParams)
            throws StandardException
    {
        super.loadDictionaryTables(tc, ddg, startParams);

        // Check splice data dictionary verion to decide if upgrade is necessary
        upgradeIfNecessary(tc, ddg, startParams);
    }

    private void upgradeIfNecessary (TransactionController tc,
                                    DataDescriptorGenerator ddg,
                                    Properties startParams) throws StandardException {

        Splice_DD_Version catalogVersion = (Splice_DD_Version)tc.getProperty(SPLICE_DATA_DICTIONARY_VERSION);
        if (needToUpgrade(catalogVersion)) {
            tc.elevate("dictionary");
            SpliceCatalogUpgradeScripts scripts = new SpliceCatalogUpgradeScripts(this, catalogVersion, tc);
            scripts.run();
            tc.setProperty(SPLICE_DATA_DICTIONARY_VERSION, spliceSoftwareVersion, true);
            tc.commit();
        }
    }

    private boolean needToUpgrade(Splice_DD_Version catalogVersion) {

        // Not sure about the current version, do not upgrade
        if (spliceSoftwareVersion == null) {
        	LOG.info("Splice Software Version = null");
            return false;
        } else {
        	LOG.info(String.format("Splice Software Version = %s", spliceSoftwareVersion.toString()));
        }

        // This is a pre-Fuji catalog, upgrade it.
        if (catalogVersion == null) {
        	LOG.info("Splice Catalog Version = null");
        	LOG.info("Upgrade needed since catalog version is null");
            return true;
        } else {
        	LOG.info(String.format("Splice Catalog Version = %s", catalogVersion.toString()));
        }

        // Compare software version and catalog version
        if (catalogVersion.toLong() < spliceSoftwareVersion.toLong()) {
        	LOG.info("Upgrade needed since catalog version < software version");
            return true;
        }
        return false;
    }

    /**
     * Overridden so that SQL functions implemented as system procedures
     * will be found if in the SYSFUN schema. Otherwise, the default
     * behavior would be to ignore these and only consider functions 
     * implicitly defined in {@link BaseDataDictionary#SYSFUN_FUNCTIONS},
     * which are not actually in the system catalog.
     */
    public java.util.List getRoutineList(String schemaID, String routineName, char nameSpace)
		throws StandardException {

		List list = super.getRoutineList(schemaID, routineName, nameSpace);
		if (list.isEmpty()) {
			if (schemaID.equals(SchemaDescriptor.SYSFUN_SCHEMA_UUID) &&
				(nameSpace == AliasInfo.ALIAS_NAME_SPACE_FUNCTION_AS_CHAR ||
				 nameSpace == AliasInfo.ALIAS_NAME_SPACE_AGGREGATE_AS_CHAR)) {
				AliasDescriptor ad = getAliasDescriptor(schemaID, routineName, nameSpace);
		        return ad == null ?
					Collections.EMPTY_LIST :
					Collections.singletonList(ad);
			}
		}
		return list;
	}

	@Override
	protected void setDependencyManager() {
		SpliceLogUtils.trace(LOG, "Initializing the Splice Dependency Manager");
		this.dmgr = new SpliceDependencyManager(this);
	}

    @Override
    public void boot(boolean create, Properties startParams) throws StandardException {
        SpliceLogUtils.trace(LOG, "boot with create=%s,startParams=%s",create,startParams);
        ManifestReader.SpliceMachineVersion spliceMachineVersion = (new ManifestReader()).createVersion();
        if (spliceMachineVersion.getRelease().compareToIgnoreCase("UNKNOWN") != 0) {
            spliceSoftwareVersion = new Splice_DD_Version(this, spliceMachineVersion.getMajorVersionNumber(),
                    spliceMachineVersion.getMinorVersionNumber(), spliceMachineVersion.getPatchVersionNumber());
        }
        if(create){
            SpliceAccessManager af = (SpliceAccessManager)  Monitor.findServiceModule(this, AccessFactory.MODULE);
            SpliceTransactionManager txnManager = (SpliceTransactionManager)af.getTransaction(ContextService.getFactory().getCurrentContextManager());
            ((SpliceTransaction)txnManager.getRawTransaction()).elevate("boot".getBytes());
            if (spliceSoftwareVersion != null) {
                txnManager.setProperty(SPLICE_DATA_DICTIONARY_VERSION, spliceSoftwareVersion, true);
            }
        }

        super.boot(create, startParams);
    }

	@Override
	public boolean canSupport(Properties startParams) {
		SpliceLogUtils.trace(LOG, "canSupport startParam=%s",startParams);
		return super.canSupport(startParams);
	}

    @Override
    public void startWriting(LanguageConnectionContext lcc) throws StandardException {
        BaseSpliceTransaction rawTransaction = ((SpliceTransactionManager) lcc.getTransactionExecute()).getRawTransaction();
        assert rawTransaction instanceof SpliceTransaction : "Programmer Error: Cannot perform a data dictionary write with a non-SpliceTransaction";
        SpliceTransaction txn = (SpliceTransaction)rawTransaction;
        /*
         * This is a bit of an awkward hack--at this stage, we need to ensure that the transaction
         * allows writes, but we don't really know where it's going, except to the data dictionary (and
         * therefore to system tables only)
         *
         * Thankfully, we only use the write-table transaction field to determine whether or not to
         * pause DDL operations, which can only occur against non-system tables. Since we are indicating
         * that this transaction will be writing to system tables, we don't have to worry about it.
         *
         * HOWEVER, it's possible that a transaction could modify both dictionary and non-dictionary tables.
         * In that situation, we don't want to confuse people with which table is being modified. So to do this,
         * we just only elevate the transaction if we absolutely have to.
         */
        if(!txn.allowsWrites())
            txn.elevate("dictionary".getBytes());
        super.startWriting(lcc);
    }

    @Override
	public void addDescriptor(TupleDescriptor td, TupleDescriptor parent,
			int catalogNumber, boolean duplicatesAllowed,
			TransactionController tc) throws StandardException {
		super.addDescriptor(td, parent, catalogNumber, duplicatesAllowed, tc);
	}


    @Override
    public void getCurrentValueAndAdvance (String sequenceUUIDstring, NumberDataValue returnValue)
            throws StandardException {

        try {
            RowLocation[] rowLocation = new RowLocation[1];
            SequenceDescriptor[] sequenceDescriptor = new SequenceDescriptor[1];

            LanguageConnectionContext llc = (LanguageConnectionContext)
                    ContextService.getContextOrNull(LanguageConnectionContext.CONTEXT_ID);

            TransactionController tc = llc.getTransactionExecute();
            computeSequenceRowLocation(tc, sequenceUUIDstring, rowLocation, sequenceDescriptor);

            byte[] rlBytes = rowLocation[0].getBytes();

            if (spliceSequencesTable == null) {
                spliceSequencesTable = SpliceAccessManager.getHTable(SpliceConstants.SEQUENCE_TABLE_NAME_BYTES);
            }

            long start = sequenceDescriptor[0].getStartValue();
            long increment = sequenceDescriptor[0].getIncrement();

            SpliceSequence sequence = SpliceDriver.driver().getSequencePool().
                    get(new SpliceSequenceKey(spliceSequencesTable,rlBytes, start, increment, 1l));

            returnValue.setValue(sequence.getNext());

        } catch (Exception e) {
            throw Exceptions.parseException(e);
        }
    }
	@Override
	public void addDescriptorArray(TupleDescriptor[] td,
			TupleDescriptor parent, int catalogNumber, boolean allowDuplicates,
			TransactionController tc) throws StandardException {
		// TODO Auto-generated method stub
		super.addDescriptorArray(td, parent, catalogNumber, allowDuplicates, tc);
	}

	@Override
	public RoleClosureIterator createRoleClosureIterator(
			TransactionController tc, String role, boolean inverse)
			throws StandardException {
		// TODO Auto-generated method stub
		return super.createRoleClosureIterator(tc, role, inverse);
	}

	@Override
	public void addSPSDescriptor(SPSDescriptor descriptor,
			TransactionController tc) throws StandardException {
		// TODO Auto-generated method stub
		super.addSPSDescriptor(descriptor, tc);
	}

	@Override
	public boolean activeConstraint(ConstraintDescriptor constraint)
			throws StandardException {
		// TODO Auto-generated method stub
		return super.activeConstraint(constraint);
	}

	@Override
	public void addConstraintDescriptor(ConstraintDescriptor descriptor,
			TransactionController tc) throws StandardException {
		// TODO Auto-generated method stub
		super.addConstraintDescriptor(descriptor, tc);
	}

	@Override
	public RowLocation[] computeAutoincRowLocations(TransactionController tc,
			TableDescriptor td) throws StandardException {
		// TODO Auto-generated method stub
		return super.computeAutoincRowLocations(tc, td);
	}

	@Override
	public void clearCaches() throws StandardException {
		// TODO Auto-generated method stub
		super.clearCaches();
	}

	@Override
	public void clearSequenceCaches() throws StandardException {
		// TODO Auto-generated method stub
		super.clearSequenceCaches();
	}

	@Override
	public void addTableDescriptorToOtherCache(TableDescriptor td, Cacheable c)
			throws StandardException {
		// TODO Auto-generated method stub
		super.addTableDescriptorToOtherCache(td, c);
	}

	@Override
	public boolean checkVersion(int requiredMajorVersion, String feature)
			throws StandardException {
		// TODO Auto-generated method stub
		return super.checkVersion(requiredMajorVersion, feature);
	}

	@Override
	protected void createSPSSet(TransactionController tc, boolean net,
			UUID schemaID) throws StandardException {
		// TODO Auto-generated method stub
		super.createSPSSet(tc, net, schemaID);
	}

	@Override
	public boolean addRemovePermissionsDescriptor(boolean add,
			PermissionsDescriptor perm, String grantee, TransactionController tc)
			throws StandardException {
		// TODO Auto-generated method stub
		return super.addRemovePermissionsDescriptor(add, perm, grantee, tc);
	}

	@Override
	public void createOrUpdateSystemProcedure(String schemaName,
			String procName, TransactionController tc) throws StandardException {
		// TODO Auto-generated method stub
		super.createOrUpdateSystemProcedure(schemaName, procName, tc);
	}

	@Override
	public void createOrUpdateAllSystemProcedures(TransactionController tc)
			throws StandardException {
		// TODO Auto-generated method stub
        tc.elevate("dictionary");
		super.createOrUpdateAllSystemProcedures(tc);
	}

	@Override
	public void disableIndexStatsRefresher() {
		// TODO Auto-generated method stub
		super.disableIndexStatsRefresher();
	}

	@Override
	public boolean doCreateIndexStatsRefresher() {
		// TODO Auto-generated method stub
		return super.doCreateIndexStatsRefresher();
	}

	@Override
	public void createIndexStatsRefresher(Database db, String dbName) {
		// TODO Auto-generated method stub
		super.createIndexStatsRefresher(db, dbName);
	}
    
    
}
