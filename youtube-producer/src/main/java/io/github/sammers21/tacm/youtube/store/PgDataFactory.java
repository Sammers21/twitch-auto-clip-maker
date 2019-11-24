package io.github.sammers21.tacm.youtube.store;

import com.google.api.client.util.store.AbstractDataStoreFactory;
import com.google.api.client.util.store.DataStore;
import io.github.sammers21.twac.core.db.DB;

import java.io.Serializable;
import java.util.Objects;

public class PgDataFactory extends AbstractDataStoreFactory {

    private final DB DB;

    public PgDataFactory(DB DB) {
        Objects.requireNonNull(DB);
        this.DB = DB;
    }

    /**
     * @param id id стримера
     */
    @Override
    protected <V extends Serializable> DataStore<V> createDataStore(String id) {
        Objects.requireNonNull(id);
        return new PgDataStore<>(this, id, DB.kv(id));
    }

}
