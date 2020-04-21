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

package com.hazelcast.jet.sql.parser;

import org.apache.calcite.sql.SqlCreate;
import org.apache.calcite.sql.SqlIdentifier;
import org.apache.calcite.sql.SqlKind;
import org.apache.calcite.sql.SqlNode;
import org.apache.calcite.sql.SqlNodeList;
import org.apache.calcite.sql.SqlOperator;
import org.apache.calcite.sql.SqlSpecialOperator;
import org.apache.calcite.sql.SqlWriter;
import org.apache.calcite.sql.parser.SqlParserPos;
import org.apache.calcite.util.ImmutableNullableList;

import javax.annotation.Nonnull;
import java.util.List;
import java.util.stream.Stream;

import static java.util.Objects.requireNonNull;

public class JetSqlCreateTable extends SqlCreate {

    private static final SqlSpecialOperator OPERATOR =
            new SqlSpecialOperator("CREATE FOREIGN TABLE", SqlKind.OTHER_DDL);

    private final SqlIdentifier name;
    private final SqlNodeList columns;
    private final SqlIdentifier server;
    private final SqlNodeList options;

    public JetSqlCreateTable(SqlParserPos pos,
                             SqlIdentifier name,
                             SqlNodeList columns,
                             SqlIdentifier server,
                             SqlNodeList options,
                             boolean replace) {
        super(OPERATOR, pos, replace, false);
        this.name = requireNonNull(name, "name should not be null");
        this.columns = requireNonNull(columns, "columns should not be null");
        this.server = requireNonNull(server, "server should not be null");
        this.options = requireNonNull(options, "options should not be null");
    }

    public String name() {
        return name.getSimple();
    }

    public Stream<SqlTableColumn> columns() {
        return columns.getList().stream().map(node -> (SqlTableColumn) node);
    }

    public String server() {
        return server.getSimple();
    }

    public Stream<SqlOption> options() {
        return options.getList().stream().map(node -> (SqlOption) node);
    }

    @Override
    @Nonnull
    public SqlOperator getOperator() {
        return OPERATOR;
    }

    @Override
    @Nonnull
    public List<SqlNode> getOperandList() {
        return ImmutableNullableList.of(name, columns, server, options);
    }

    @Override
    public void unparse(SqlWriter writer, int leftPrec, int rightPrec) {
        writer.keyword("CREATE");
        writer.keyword("FOREIGN");
        writer.keyword("TABLE");
        name.unparse(writer, leftPrec, rightPrec);
        if (columns != null) {
            SqlWriter.Frame frame = writer.startList("(", ")");
            for (SqlNode column : columns) {
                printIndent(writer);
                column.unparse(writer, 0, 0);
            }
            writer.newlineAndIndent();
            writer.endList(frame);
        }

        writer.newlineAndIndent();
        writer.keyword("SERVER");
        server.unparse(writer, leftPrec, rightPrec);

        if (options.size() > 0) {
            writer.newlineAndIndent();
            writer.keyword("OPTIONS");
            SqlWriter.Frame withFrame = writer.startList("(", ")");
            for (SqlNode property : options) {
                printIndent(writer);
                property.unparse(writer, leftPrec, rightPrec);
            }
            writer.newlineAndIndent();
            writer.endList(withFrame);
        }
    }

    private void printIndent(SqlWriter writer) {
        writer.sep(",", false);
        writer.newlineAndIndent();
        writer.print(" ");
    }
}