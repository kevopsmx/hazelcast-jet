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

package com.hazelcast.jet.sql.impl.parse;

import com.hazelcast.jet.sql.impl.validate.JetSqlOperatorTable;
import com.hazelcast.sql.impl.calcite.validate.HazelcastSqlOperatorTable;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import org.apache.calcite.runtime.CalciteContextException;
import org.apache.calcite.runtime.Resources;
import org.apache.calcite.sql.SqlCall;
import org.apache.calcite.sql.SqlDataTypeSpec;
import org.apache.calcite.sql.SqlDynamicParam;
import org.apache.calcite.sql.SqlIdentifier;
import org.apache.calcite.sql.SqlIntervalQualifier;
import org.apache.calcite.sql.SqlKind;
import org.apache.calcite.sql.SqlLiteral;
import org.apache.calcite.sql.SqlNode;
import org.apache.calcite.sql.SqlNodeList;
import org.apache.calcite.sql.SqlOperator;
import org.apache.calcite.sql.SqlSelect;
import org.apache.calcite.sql.SqlUtil;
import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.apache.calcite.sql.type.SqlTypeName;
import org.apache.calcite.sql.util.SqlVisitor;
import org.apache.calcite.sql.validate.SqlValidatorException;

import java.util.HashSet;
import java.util.Set;

import static com.hazelcast.jet.sql.impl.parse.ParserResource.RESOURCE;

// TODO: review and get in line with imdg after its freeze

/**
 * Visitor that throws exceptions for unsupported SQL features.
 */
@SuppressWarnings("checkstyle:ExecutableStatementCount")
public final class UnsupportedOperationVisitor implements SqlVisitor<Void> {

    public static final UnsupportedOperationVisitor INSTANCE = new UnsupportedOperationVisitor();

    /**
     * A set of {@link SqlKind} values that are supported without any additional validation.
     */
    private static final Set<SqlKind> SUPPORTED_KINDS;

    /**
     * A set of supported operators for functions.
     */
    private static final Set<SqlOperator> SUPPORTED_OPERATORS;

    static {
        // We define all supported features explicitly instead of getting them from predefined sets of SqlKind class.
        // This is needed to ensure that we do not miss any unsupported features when something is added to a new version
        // of Apache Calcite.
        SUPPORTED_KINDS = new HashSet<>();

        // Arithmetics
        SUPPORTED_KINDS.add(SqlKind.PLUS);
        SUPPORTED_KINDS.add(SqlKind.MINUS);
        SUPPORTED_KINDS.add(SqlKind.TIMES);
        SUPPORTED_KINDS.add(SqlKind.DIVIDE);
        SUPPORTED_KINDS.add(SqlKind.MOD);
        SUPPORTED_KINDS.add(SqlKind.PLUS_PREFIX);
        SUPPORTED_KINDS.add(SqlKind.MINUS_PREFIX);

        // Boolean logic predicates
        SUPPORTED_KINDS.add(SqlKind.AND);
        SUPPORTED_KINDS.add(SqlKind.OR);
        SUPPORTED_KINDS.add(SqlKind.NOT);

        // "IS" predicates
        SUPPORTED_KINDS.add(SqlKind.IS_NULL);
        SUPPORTED_KINDS.add(SqlKind.IS_NOT_NULL);
        SUPPORTED_KINDS.add(SqlKind.IS_FALSE);
        SUPPORTED_KINDS.add(SqlKind.IS_NOT_FALSE);
        SUPPORTED_KINDS.add(SqlKind.IS_TRUE);
        SUPPORTED_KINDS.add(SqlKind.IS_NOT_TRUE);

        // Comparisons predicates
        SUPPORTED_KINDS.add(SqlKind.EQUALS);
        SUPPORTED_KINDS.add(SqlKind.NOT_EQUALS);
        SUPPORTED_KINDS.add(SqlKind.LESS_THAN);
        SUPPORTED_KINDS.add(SqlKind.GREATER_THAN);
        SUPPORTED_KINDS.add(SqlKind.GREATER_THAN_OR_EQUAL);
        SUPPORTED_KINDS.add(SqlKind.LESS_THAN_OR_EQUAL);

        // Existential scalar subqueries
        SUPPORTED_KINDS.add(SqlKind.EXISTS);
        SUPPORTED_KINDS.add(SqlKind.SOME);
        SUPPORTED_KINDS.add(SqlKind.ALL);
        SUPPORTED_KINDS.add(SqlKind.IN);
        SUPPORTED_KINDS.add(SqlKind.NOT_IN);

        // Aggregates
        SUPPORTED_KINDS.add(SqlKind.SUM);
        SUPPORTED_KINDS.add(SqlKind.COUNT);
        SUPPORTED_KINDS.add(SqlKind.MIN);
        SUPPORTED_KINDS.add(SqlKind.MAX);
        SUPPORTED_KINDS.add(SqlKind.AVG);
        SUPPORTED_KINDS.add(SqlKind.SINGLE_VALUE);

        // Miscellaneous
        SUPPORTED_KINDS.add(SqlKind.AS);
        SUPPORTED_KINDS.add(SqlKind.BETWEEN);
        SUPPORTED_KINDS.add(SqlKind.CASE);
        SUPPORTED_KINDS.add(SqlKind.CAST);
        SUPPORTED_KINDS.add(SqlKind.CEIL);
        SUPPORTED_KINDS.add(SqlKind.DESCENDING);
        SUPPORTED_KINDS.add(SqlKind.EXTRACT);
        SUPPORTED_KINDS.add(SqlKind.FLOOR);
        SUPPORTED_KINDS.add(SqlKind.LIKE);
        SUPPORTED_KINDS.add(SqlKind.POSITION);
        SUPPORTED_KINDS.add(SqlKind.TIMESTAMP_ADD);

        SUPPORTED_KINDS.add(SqlKind.CREATE_TABLE);
        SUPPORTED_KINDS.add(SqlKind.DROP_TABLE);
        SUPPORTED_KINDS.add(SqlKind.COLUMN_DECL);

        SUPPORTED_KINDS.add(SqlKind.ROW);
        SUPPORTED_KINDS.add(SqlKind.VALUES);
        SUPPORTED_KINDS.add(SqlKind.INSERT);

        SUPPORTED_KINDS.add(SqlKind.COLLECTION_TABLE);
        SUPPORTED_KINDS.add(SqlKind.ARGUMENT_ASSIGNMENT);

        // Supported operators
        SUPPORTED_OPERATORS = new HashSet<>();

        // Concat
        SUPPORTED_OPERATORS.add(SqlStdOperatorTable.CONCAT);

        // Math
        SUPPORTED_OPERATORS.add(SqlStdOperatorTable.COS);
        SUPPORTED_OPERATORS.add(SqlStdOperatorTable.SIN);
        SUPPORTED_OPERATORS.add(SqlStdOperatorTable.TAN);
        SUPPORTED_OPERATORS.add(SqlStdOperatorTable.COT);
        SUPPORTED_OPERATORS.add(SqlStdOperatorTable.ACOS);
        SUPPORTED_OPERATORS.add(SqlStdOperatorTable.ASIN);
        SUPPORTED_OPERATORS.add(SqlStdOperatorTable.ATAN);
        SUPPORTED_OPERATORS.add(SqlStdOperatorTable.ATAN2);
        SUPPORTED_OPERATORS.add(SqlStdOperatorTable.SQRT);
        SUPPORTED_OPERATORS.add(SqlStdOperatorTable.EXP);
        SUPPORTED_OPERATORS.add(SqlStdOperatorTable.LN);
        SUPPORTED_OPERATORS.add(SqlStdOperatorTable.LOG10);
        SUPPORTED_OPERATORS.add(SqlStdOperatorTable.RAND);
        SUPPORTED_OPERATORS.add(SqlStdOperatorTable.ABS);
        SUPPORTED_OPERATORS.add(SqlStdOperatorTable.PI);
        SUPPORTED_OPERATORS.add(SqlStdOperatorTable.SIGN);
        SUPPORTED_OPERATORS.add(SqlStdOperatorTable.POWER);
        SUPPORTED_OPERATORS.add(SqlStdOperatorTable.DEGREES);
        SUPPORTED_OPERATORS.add(SqlStdOperatorTable.RADIANS);
        SUPPORTED_OPERATORS.add(SqlStdOperatorTable.ROUND);
        SUPPORTED_OPERATORS.add(SqlStdOperatorTable.TRUNCATE);

        // Strings
        SUPPORTED_OPERATORS.add(HazelcastSqlOperatorTable.ASCII);
        SUPPORTED_OPERATORS.add(HazelcastSqlOperatorTable.INITCAP);
        SUPPORTED_OPERATORS.add(HazelcastSqlOperatorTable.CHAR_LENGTH);
        SUPPORTED_OPERATORS.add(HazelcastSqlOperatorTable.CHARACTER_LENGTH);
        SUPPORTED_OPERATORS.add(HazelcastSqlOperatorTable.LENGTH);
        SUPPORTED_OPERATORS.add(HazelcastSqlOperatorTable.LOWER);
        SUPPORTED_OPERATORS.add(HazelcastSqlOperatorTable.UPPER);
        SUPPORTED_OPERATORS.add(HazelcastSqlOperatorTable.CONCAT);
        SUPPORTED_OPERATORS.add(HazelcastSqlOperatorTable.SUBSTRING);

        // Dates
        SUPPORTED_OPERATORS.add(SqlStdOperatorTable.CURRENT_DATE);
        SUPPORTED_OPERATORS.add(SqlStdOperatorTable.CURRENT_TIMESTAMP);
        SUPPORTED_OPERATORS.add(SqlStdOperatorTable.LOCALTIMESTAMP);
        SUPPORTED_OPERATORS.add(SqlStdOperatorTable.LOCALTIME);

        // Extensions
        SUPPORTED_OPERATORS.add(SqlOption.OPERATOR);
        SUPPORTED_OPERATORS.add(SqlShowExternalTables.OPERATOR);
        SUPPORTED_OPERATORS.add(JetSqlOperatorTable.FILE);
    }

    private UnsupportedOperationVisitor() {
    }

    @Override
    public Void visit(SqlCall call) {
        processCall(call);

        call.getOperator().acceptCall(this, call);

        return null;
    }

    @Override
    public Void visit(SqlNodeList nodeList) {
        for (int i = 0; i < nodeList.size(); i++) {
            SqlNode node = nodeList.get(i);

            node.accept(this);
        }

        return null;
    }

    @Override
    public Void visit(SqlIdentifier id) {
        // TODO:
        /*SqlValidatorTable table = catalogReader.getTable(id.names);
        if (table != null) {
            HazelcastTable hzTable = table.unwrap(HazelcastTable.class);
            if (hzTable != null) {
                Table target = hzTable.getTarget();
                if (target != null && !(target instanceof JetTable)) {
                    throw error(id, RESOURCE.custom(target.getClass().getSimpleName() + " is not supported"));
                }
            }
        }*/

        return null;
    }

    @Override
    public Void visit(SqlDataTypeSpec type) {
        // TODO: proper validation for types - do we need second (in addition to DDL) validation ?
        return null;
    }

    @Override
    public Void visit(SqlDynamicParam param) {
        return null;
    }

    @SuppressWarnings("checkstyle:CyclomaticComplexity")
    @Override
    public Void visit(SqlLiteral literal) {
        SqlTypeName typeName = literal.getTypeName();

        switch (typeName) {
            case BOOLEAN:
            case TINYINT:
            case SMALLINT:
            case INTEGER:
            case BIGINT:
            case DECIMAL:
            case REAL:
            case FLOAT:
            case DOUBLE:
            case CHAR:
            case VARCHAR:
            case DATE:
            case TIME:
            case TIME_WITH_LOCAL_TIME_ZONE:
            case TIMESTAMP:
            case TIMESTAMP_WITH_LOCAL_TIME_ZONE:
            case INTERVAL_YEAR:
            case INTERVAL_MONTH:
            case INTERVAL_YEAR_MONTH:
            case INTERVAL_DAY:
            case INTERVAL_DAY_HOUR:
            case INTERVAL_DAY_MINUTE:
            case INTERVAL_DAY_SECOND:
            case INTERVAL_HOUR:
            case INTERVAL_HOUR_MINUTE:
            case INTERVAL_HOUR_SECOND:
            case INTERVAL_MINUTE:
            case INTERVAL_MINUTE_SECOND:
            case INTERVAL_SECOND:
            case SYMBOL:
            case NULL:
                return null;

            default:
                throw error(literal, RESOURCE.error(typeName + " literals are not supported"));
        }
    }

    @Override
    public Void visit(SqlIntervalQualifier intervalQualifier) {
        return null;
    }

    @SuppressWarnings("checkstyle:CyclomaticComplexity")
    private void processCall(SqlCall call) {
        SqlKind kind = call.getKind();

        if (SUPPORTED_KINDS.contains(kind)) {
            return;
        }

        switch (kind) {
            case HINT:
                // TODO: Proper validation for hints
                break;

            case SELECT:
                processSelect((SqlSelect) call);
                break;

            case SCALAR_QUERY:
                // TODO: Perhaps we may add it to SUPPORTED_KINDS since we always decorrelate. Double-check it when working
                //  on subqueries.
                break;

            case OTHER:
            case OTHER_FUNCTION:
                processOther(call);
                break;

            case OTHER_DDL:
                processOtherDdl(call);
                break;

            default:
                throw unsupported(call, call.getKind());
        }
    }

    @SuppressFBWarnings(value = "UC_USELESS_VOID_METHOD", justification = "Not fully implemented yet")
    @SuppressWarnings({"checkstyle:EmptyBlock"})
    private void processSelect(SqlSelect select) {
        if (select.hasOrderBy()) {
            // TODO: Proper validation for ORDER BY (i.e. LIMIT/OFFSET)
        }

        if (select.getGroup() != null && select.getGroup().size() > 0) {
            // TODO: Proper validation for GROUP BY (i.e. grouping sets, etc).
        }

        if (select.getFetch() != null) {
            throw unsupported(select.getFetch(), "LIMIT");
        }

        if (select.getOffset() != null) {
            throw unsupported(select.getOffset(), "OFFSET");
        }
    }

    private void processOther(SqlCall call) {
        SqlOperator operator = call.getOperator();

        if (SUPPORTED_OPERATORS.contains(operator)) {
            return;
        }

        throw unsupported(call, operator.getName());
    }

    private void processOtherDdl(SqlCall call) {
        if (!(call instanceof SqlCreateJob) && !(call instanceof SqlDropJob)) {
            throw unsupported(call, "OTHER DDL class not supported: " + call.getClass().getSimpleName());
        }
    }

    private CalciteContextException unsupported(SqlNode node, SqlKind kind) {
        return unsupported(node, kind.sql.replace('_', ' '));
    }

    private CalciteContextException unsupported(SqlNode node, String name) {
        return error(node, RESOURCE.notSupported(name));
    }

    private CalciteContextException error(SqlNode node, Resources.ExInst<SqlValidatorException> err) {
        return SqlUtil.newContextException(node.getParserPosition(), err);
    }
}
