package com.splicemachine.derby.impl.store.access;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.splicemachine.derby.impl.sql.execute.actions.WriteCursorConstantOperation;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.derby.iapi.error.StandardException;
import org.apache.derby.iapi.sql.Activation;
import org.apache.derby.iapi.sql.dictionary.ConglomerateDescriptor;
import org.apache.derby.iapi.sql.dictionary.DataDictionary;

/**
 * Cache ConglomerateDescriptor objects by ID, retrieve by Activation.
 */
public class ConglomerateDescriptorCache {

    public static final ConglomerateDescriptorCache INSTANCE = new ConglomerateDescriptorCache();
    private static final Log LOG = LogFactory.getLog(ConglomerateDescriptorCache.class);

    private Cache<Long, ConglomerateDescriptor> cache = CacheBuilder.newBuilder().maximumSize(100).build();

    public void invalidateAll() {
        LOG.info("invalidate all called, cache size = " + cache.size());
        cache.invalidateAll();
    }

    public ConglomerateDescriptor get(Activation activation) throws StandardException {
        long conglomerateId = ((WriteCursorConstantOperation) activation.getConstantAction()).getConglomerateId();
        ConglomerateDescriptor descriptor = cache.getIfPresent(conglomerateId);
        if (descriptor == null) {
            descriptor = lookup(activation.getLanguageConnectionContext().getDataDictionary(), conglomerateId);
            cache.put(conglomerateId, descriptor);
            LOG.info("added to cache, cache size = " + cache.size());
        }
        return descriptor;
    }

    public ConglomerateDescriptor get(DataDictionary dataDictionary, long conglomerateId) throws StandardException {
        ConglomerateDescriptor descriptor = cache.getIfPresent(conglomerateId);
        if (descriptor == null) {
            descriptor = lookup(dataDictionary, conglomerateId);
            cache.put(conglomerateId, descriptor);
            LOG.info("added to cache, cache size = " + cache.size());
        }
        return descriptor;
    }

    
    private ConglomerateDescriptor lookup(DataDictionary dataDictionary, long conglomerateId) throws StandardException {
        return dataDictionary.getConglomerateDescriptor(conglomerateId);
    }
    
}