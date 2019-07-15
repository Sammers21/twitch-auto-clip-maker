package io.github.sammers21.tacm.youtube.store;

import com.google.api.client.util.IOUtils;
import com.google.api.client.util.Preconditions;
import com.google.api.client.util.store.AbstractDataStore;
import com.google.api.client.util.store.DataStore;
import com.google.api.client.util.store.DataStoreFactory;
import io.github.sammers21.twac.core.db.KV;

import java.io.IOException;
import java.io.Serializable;
import java.util.Collection;
import java.util.Set;

public class PgDataStore<V extends Serializable> extends AbstractDataStore<V> {
    private final KV kv;

    /**
     * @param dataStoreFactory data store factory
     * @param id               data store ID
     */
    protected PgDataStore(DataStoreFactory dataStoreFactory, String id, KV kv) {
        super(dataStoreFactory, id);
        this.kv = kv;
    }

    @Override
    public synchronized Set<String> keySet() throws IOException {
        throw new IllegalStateException("keySet() not implemented");
    }

    @Override
    public synchronized Collection values() throws IOException {
        throw new IllegalStateException("values() not implemented");
    }

    @Override
    public V get(String key) throws IOException {
        return IOUtils.deserialize(kv.get(key));
    }

    @Override
    public PgDataStore<V> set(String key, V value) throws IOException {
        Preconditions.checkNotNull(key);
        Preconditions.checkNotNull(value);
        kv.put(key, IOUtils.serialize(value));
        return this;
    }

    @Override
    public synchronized DataStore clear() throws IOException {
        throw new IllegalStateException("clear() not implemented");
    }

    @Override
    public synchronized DataStore delete(String key) throws IOException {
        throw new IllegalStateException("delete(key) not implemented");
    }

}
