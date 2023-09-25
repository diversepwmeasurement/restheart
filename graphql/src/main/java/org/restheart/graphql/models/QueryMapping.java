/*-
 * ========================LICENSE_START=================================
 * restheart-graphql
 * %%
 * Copyright (C) 2020 - 2023 SoftInstigate
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 * =========================LICENSE_END==================================
 */
package org.restheart.graphql.models;

import graphql.schema.DataFetchingEnvironment;
import org.bson.BsonDocument;
import org.bson.BsonValue;
import org.dataloader.DataLoader;
import org.dataloader.DataLoaderFactory;
import org.dataloader.DataLoaderOptions;
import org.dataloader.stats.SimpleStatisticsCollector;
import org.restheart.exchange.QueryVariableNotBoundException;
import org.restheart.graphql.GraphQLService;
import org.restheart.graphql.datafetchers.GQLBatchDataFetcher;
import org.restheart.graphql.datafetchers.GQLQueryDataFetcher;
import org.restheart.graphql.datafetchers.GraphQLDataFetcher;
import org.restheart.graphql.dataloaders.QueryBatchLoader;

public class QueryMapping extends FieldMapping implements Batchable {
    private String db;
    private String collection;
    private BsonDocument find;
    private BsonDocument sort;
    private BsonValue limit;
    private BsonValue skip;
    private DataLoaderSettings dataLoaderSettings;

    private static int maxLimit = GraphQLService.DEFAULT_MAX_LIMIT;

    public static void setMaxLimit(int _maxLimit) {
        maxLimit = _maxLimit;
    }

    private QueryMapping(String fieldName, String db, String collection, BsonDocument find, BsonDocument sort, BsonValue limit, BsonValue skip, DataLoaderSettings dataLoaderSettings) {
        super(fieldName);
        this.db = db;
        this.collection = collection;
        this.find = find;
        this.sort = sort;
        this.limit = limit;
        this.skip = skip;
        this.dataLoaderSettings = dataLoaderSettings;
    }

    public static Builder newBuilder() {
        return new Builder();
    }

    @Override
    public GraphQLDataFetcher getDataFetcher() {
        return this.dataLoaderSettings.getBatching() ? new GQLBatchDataFetcher(this) : new GQLQueryDataFetcher(this);
    }

    @Override
    public DataLoader<BsonValue, BsonValue> getDataloader() {
        if (this.dataLoaderSettings.getCaching() || this.dataLoaderSettings.getBatching()) {
            var options = new DataLoaderOptions().setCacheKeyFunction(bsonValue -> String.valueOf(bsonValue.hashCode()));

            if (this.dataLoaderSettings.getMax_batch_size() > 0) {
                options.setMaxBatchSize(this.dataLoaderSettings.getMax_batch_size());
            }

            options.setBatchingEnabled(this.dataLoaderSettings.getBatching());
            options.setCachingEnabled(this.dataLoaderSettings.getCaching());

            options.setStatisticsCollector(() -> new SimpleStatisticsCollector());

            return DataLoaderFactory.newDataLoader(new QueryBatchLoader(this.db, this.collection), options);
        } else {
            return null;
        }
    }

    public String getDb() {
        return db;
    }

    public String getCollection() {
        return collection;
    }

    public BsonDocument getFind() {
        return find;
    }

    public BsonDocument getSort() {
        return sort;
    }

    public BsonValue getLimit() {
        return limit;
    }

    public BsonValue getSkip() {
        return skip;
    }

    public DataLoaderSettings getDataLoaderSettings() {
        return dataLoaderSettings;
    }

    public BsonDocument interpolateArgs(DataFetchingEnvironment env) throws IllegalAccessException, QueryVariableNotBoundException {
        var result = new BsonDocument();

        var fields = (QueryMapping.class).getDeclaredFields();
        for (var field: fields) {
            var value = field.get(this);

            if(value instanceof BsonDocument bsonDoc) {
                var ivalue = interpolateOperators(bsonDoc, env);

                // make sure limit does not exceed max-limit
                if (field.getName().equals("limit") && ivalue.asInt32().getValue() > maxLimit) {
                    throw new QueryVariableNotBoundException("Query variable cannot be greater than " + maxLimit);
                }

                result.put(field.getName(), ivalue);
            } else if (value instanceof BsonValue bsonVal && !bsonVal.isNull()) {
                result.put(field.getName(), bsonVal);
            }
        }

        return result;
    }

    public static class Builder {
        private String fieldName;
        private String db;
        private String collection;
        private BsonDocument find;
        private BsonDocument sort;
        private BsonValue limit;
        private BsonValue skip;
        private DataLoaderSettings dataLoaderSettings;

        private Builder() { }

        public Builder fieldName(String fieldName) {
            this.fieldName = fieldName;
            return this;
        }

        public Builder db(String db) {
            this.db = db;
            return this;
        }

        public Builder collection(String collection) {
            this.collection = collection;
            return this;
        }

        public Builder find(BsonDocument find) {
            this.find = find;
            return this;
        }

        public Builder sort(BsonDocument sort) {
            this.sort = sort;
            return this;
        }

        public Builder limit(BsonValue limit) {
            this.limit = limit;
            return this;
        }

        public Builder skip(BsonValue skip) {
            this.skip = skip;
            return this;
        }

        public Builder DataLoaderSettings(DataLoaderSettings settings) {
            this.dataLoaderSettings = settings;
            return this;
        }

        public QueryMapping build() {
            if (this.db == null) {
                throwIllegalException("db");
            }

            if (this.collection == null) {
                throwIllegalException("collection");
            }

            if (this.dataLoaderSettings == null) {
                this.dataLoaderSettings = DataLoaderSettings.newBuilder().build();
            }

            return new QueryMapping(this.fieldName, this.db, this.collection, this.find, this.sort, this.limit, this.skip, this.dataLoaderSettings);
        }

        private static void throwIllegalException(String varName) {
            throw new IllegalStateException(varName + "could not be null!");
        }
    }
}
