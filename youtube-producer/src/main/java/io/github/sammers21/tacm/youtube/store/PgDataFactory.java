package io.github.sammers21.tacm.youtube.store;

import com.google.api.client.util.store.AbstractDataStoreFactory;
import com.google.api.client.util.store.DataStore;
import io.github.sammers21.twac.core.db.DbController;

import java.io.Serializable;
import java.util.Objects;

public class PgDataFactory extends AbstractDataStoreFactory {

    private final DbController dbController;

    public PgDataFactory(DbController dbController) {
        Objects.requireNonNull(dbController);
        this.dbController = dbController;
    }

    /**
     * @param id id стримера
     */
    @Override
    protected <V extends Serializable> DataStore<V> createDataStore(String id) {
        Objects.requireNonNull(id);
        return new PgDataStore<>(this, id, dbController.kv(id));
    }

}
