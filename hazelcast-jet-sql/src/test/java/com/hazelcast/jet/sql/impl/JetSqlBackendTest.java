/*
 * Copyright (c) 2008-2020, Hazelcast, Inc. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.hazelcast.jet.sql.impl;

import com.google.common.collect.ImmutableMap;
import com.hazelcast.jet.sql.impl.JetPlan.CreateExternalMappingPlan;
import com.hazelcast.jet.sql.impl.JetPlan.DropExternalMappingPlan;
import com.hazelcast.jet.sql.impl.parse.SqlCreateExternalMapping;
import com.hazelcast.jet.sql.impl.parse.SqlDataType;
import com.hazelcast.jet.sql.impl.parse.SqlDropExternalMapping;
import com.hazelcast.jet.sql.impl.parse.SqlMappingColumn;
import com.hazelcast.jet.sql.impl.parse.SqlOption;
import com.hazelcast.jet.sql.impl.schema.MappingField;
import com.hazelcast.spi.impl.NodeEngine;
import com.hazelcast.sql.impl.calcite.parse.QueryParseResult;
import com.hazelcast.sql.impl.type.QueryDataType;
import junitparams.JUnitParamsRunner;
import junitparams.Parameters;
import org.apache.calcite.sql.SqlIdentifier;
import org.apache.calcite.sql.SqlLiteral;
import org.apache.calcite.sql.SqlNode;
import org.apache.calcite.sql.SqlNodeList;
import org.apache.calcite.sql.parser.SqlParserPos;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import static com.hazelcast.sql.impl.type.QueryDataType.INT;
import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;

@RunWith(JUnitParamsRunner.class)
public class JetSqlBackendTest {

    @InjectMocks
    private JetSqlBackend sqlBackend;

    @Mock
    private NodeEngine nodeEngine;

    @Mock
    private JetPlanExecutor planExecutor;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
    }

    @Test
    @Parameters({
            "true, false",
            "false, true"
    })
    public void test_createExternalMappingPlan(boolean replace, boolean ifNotExists) {
        // given
        SqlCreateExternalMapping node = new SqlCreateExternalMapping(
                identifier("mapping_name"),
                nodeList(column("column_name", INT, "external_column_name")),
                identifier("mapping_type"),
                nodeList(option("option_key", "option_value")),
                replace,
                ifNotExists,
                SqlParserPos.ZERO
        );
        QueryParseResult parseResult = new QueryParseResult(node, null, null, null);

        // when
        CreateExternalMappingPlan plan = (CreateExternalMappingPlan) sqlBackend.createPlan(null, parseResult, null);

        // then
        assertThat(plan.mapping().name()).isEqualTo("mapping_name");
        assertThat(plan.mapping().type()).isEqualTo("mapping_type");
        assertThat(plan.mapping().fields())
                .isEqualTo(singletonList(new MappingField("column_name", INT, "external_column_name")));
        assertThat(plan.mapping().options()).isEqualTo(ImmutableMap.of("option_key", "option_value"));
        assertThat(plan.replace()).isEqualTo(replace);
        assertThat(plan.ifNotExists()).isEqualTo(ifNotExists);
    }

    @Test
    @Parameters({
            "true",
            "false"
    })
    public void test_removeExternalMappingPlan(boolean ifExists) {
        // given
        SqlDropExternalMapping node = new SqlDropExternalMapping(
                identifier("mapping_name"),
                ifExists,
                SqlParserPos.ZERO
        );
        QueryParseResult parseResult = new QueryParseResult(node, null, null, null);

        // when
        DropExternalMappingPlan plan = (DropExternalMappingPlan) sqlBackend.createPlan(null, parseResult, null);

        // then
        assertThat(plan.name()).isEqualTo("mapping_name");
        assertThat(plan.ifExists()).isEqualTo(ifExists);
    }

    private static SqlNodeList nodeList(SqlNode... nodes) {
        return new SqlNodeList(asList(nodes), SqlParserPos.ZERO);
    }

    private static SqlMappingColumn column(String name, QueryDataType type, String externalName) {
        return new SqlMappingColumn(
                identifier(name),
                new SqlDataType(type, SqlParserPos.ZERO),
                identifier(externalName),
                SqlParserPos.ZERO
        );
    }

    private static SqlOption option(String key, String value) {
        return new SqlOption(identifier(key), SqlLiteral.createCharString(value, SqlParserPos.ZERO), SqlParserPos.ZERO);
    }

    private static SqlIdentifier identifier(String name) {
        return new SqlIdentifier(name, SqlParserPos.ZERO);
    }
}
