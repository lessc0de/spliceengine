package com.splicemachine.derby.impl.sql.compile;

import java.util.Properties;
import org.apache.derby.iapi.error.StandardException;
import org.apache.derby.iapi.sql.compile.CostEstimate;
import org.apache.derby.iapi.sql.compile.JoinStrategy;
import org.apache.derby.iapi.sql.compile.OptimizableList;
import org.apache.derby.iapi.sql.compile.OptimizablePredicateList;
import org.apache.derby.iapi.sql.compile.Optimizer;
import org.apache.derby.iapi.sql.compile.OptimizerFactory;
import org.apache.derby.iapi.sql.compile.RequiredRowOrdering;
import org.apache.derby.iapi.sql.conn.LanguageConnectionContext;
import org.apache.derby.iapi.sql.dictionary.DataDictionary;
import org.apache.derby.impl.sql.compile.OptimizerFactoryImpl;

public class SpliceLevel2OptimizerFactoryImpl extends OptimizerFactoryImpl {

	public void boot(boolean create, Properties startParams) throws StandardException {
		super.boot(create, startParams);
	}


	/**
	 * @see OptimizerFactory#supportsOptimizerTrace
	 */
	public boolean supportsOptimizerTrace() {
		return true;
	}

	public SpliceLevel2OptimizerFactoryImpl()  {
		
	}
	@Override
	public Optimizer getOptimizer(OptimizableList optimizableList,
			  OptimizablePredicateList predList,
			  DataDictionary dDictionary,
			  RequiredRowOrdering requiredRowOrdering,
			  int numTablesInQuery,
			  LanguageConnectionContext lcc) throws StandardException {
	/* Get/set up the array of join strategies.
	* See comment in boot().  If joinStrategySet
	* is null, then we may do needless allocations
	* in a multi-user environment if multiple
	* users find it null on entry.  However, 
	* assignment of array is atomic, so system
	* will be consistent even in rare case
	* where users get different arrays.
	*/
		if (joinStrategySet == null) { // Do not change order...
			JoinStrategy[] jss = new JoinStrategy[5];
			jss[0] = new NestedLoopJoinStrategy();
			jss[1] = new MergeSortJoinStrategy();
			jss[2] = new BroadcastJoinStrategy();
			jss[3] = new MergeJoinStrategy();
			jss[4] = new HashNestedLoopJoinStrategy();
			joinStrategySet = jss;
		}

return getOptimizerImpl(optimizableList,
		predList,
		dDictionary,
		requiredRowOrdering,
		numTablesInQuery,
		lcc);
}


	protected Optimizer getOptimizerImpl(
							  OptimizableList optimizableList,
							  OptimizablePredicateList predList,
							  DataDictionary dDictionary,
							  RequiredRowOrdering requiredRowOrdering,
							  int numTablesInQuery,
							  LanguageConnectionContext lcc) throws StandardException {

	return new SpliceLevel2OptimizerImpl(
						optimizableList,
						predList,
						dDictionary,
						ruleBasedOptimization,
						noTimeout,
						useStatistics,
						maxMemoryPerTable,
						joinStrategySet,
						lcc.getLockEscalationThreshold(),
						requiredRowOrdering,
						numTablesInQuery,
						lcc);
}

	/**
	 * @see OptimizerFactory#getCostEstimate
	 *
	 * @exception StandardException		Thrown on error
	 */
	public CostEstimate getCostEstimate() throws StandardException {
		return new SpliceCostEstimateImpl();
	}
}

