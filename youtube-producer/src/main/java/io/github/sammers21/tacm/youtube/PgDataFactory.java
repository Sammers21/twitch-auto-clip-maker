package io.github.sammers21.tacm.youtube;

import com.google.api.client.util.store.AbstractDataStoreFactory;
import com.google.api.client.util.store.DataStore;
import io.github.sammers21.twac.core.db.DbController;

import java.io.IOException;
import java.io.Serializable;

public class PgDataFactory extends AbstractDataStoreFactory {

    private final DbController dbController;

    public PgDataFactory(DbController dbController) {
        this.dbController = dbController;
    }

    /**
     * @param id id стримера
     */
    @Override
    protected <V extends Serializable> DataStore<V> createDataStore(String id) throws IOException {
        return ;
    }


}
