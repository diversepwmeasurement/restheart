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

package org.restheart.graphql.datafetchers;

import org.bson.BsonArray;
import org.restheart.graphql.models.AggregationMapping;
import graphql.schema.GraphQLObjectType;

import graphql.schema.DataFetchingEnvironment;

public class GQLBatchAggregationDataFetcher extends GraphQLDataFetcher {

    public GQLBatchAggregationDataFetcher(AggregationMapping aggregationMapping) {
        super(aggregationMapping);
    }

    @Override
    public Object get(DataFetchingEnvironment environment) throws Exception {
        var aggregationMapping = (AggregationMapping) this.fieldMapping;

        var key = ((GraphQLObjectType) environment.getParentType()).getName() + "_" + aggregationMapping.getFieldName();

        var dataLoader = environment.getDataLoader(key);

        var aggregationList = aggregationMapping.getResolvedStagesAsList(environment);

        var bsonArray = new BsonArray();
        bsonArray.addAll(aggregationList);

        return dataLoader.load(bsonArray, environment);

    }
}
