/*
 * Copyright 2004-2013 H2 Group. Multiple-Licensed under the H2 License,
 * Version 1.0, and under the Eclipse Public License, Version 1.0
 * (http://h2database.com/html/license.html).
 * Initial Developer: H2 Group
 */
package org.lealone.command;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.charset.Charset;
import java.text.Collator;
import java.util.ArrayList;
import java.util.HashSet;

import org.lealone.api.ErrorCode;
import org.lealone.api.Trigger;
import org.lealone.command.ddl.AlterIndexRename;
import org.lealone.command.ddl.AlterSchemaRename;
import org.lealone.command.ddl.AlterSequence;
import org.lealone.command.ddl.AlterTableAddConstraint;
import org.lealone.command.ddl.AlterTableAlterColumn;
import org.lealone.command.ddl.AlterTableDropConstraint;
import org.lealone.command.ddl.AlterTableRename;
import org.lealone.command.ddl.AlterTableRenameColumn;
import org.lealone.command.ddl.AlterTableSet;
import org.lealone.command.ddl.AlterUser;
import org.lealone.command.ddl.AlterView;
import org.lealone.command.ddl.Analyze;
import org.lealone.command.ddl.CreateAggregate;
import org.lealone.command.ddl.CreateConstant;
import org.lealone.command.ddl.CreateFunctionAlias;
import org.lealone.command.ddl.CreateIndex;
import org.lealone.command.ddl.CreateRole;
import org.lealone.command.ddl.CreateSchema;
import org.lealone.command.ddl.CreateSequence;
import org.lealone.command.ddl.CreateTable;
import org.lealone.command.ddl.CreateTableData;
import org.lealone.command.ddl.CreateTrigger;
import org.lealone.command.ddl.CreateUser;
import org.lealone.command.ddl.CreateUserDataType;
import org.lealone.command.ddl.CreateView;
import org.lealone.command.ddl.DeallocateProcedure;
import org.lealone.command.ddl.DefineCommand;
import org.lealone.command.ddl.DefineCommandWrapper;
import org.lealone.command.ddl.DropAggregate;
import org.lealone.command.ddl.DropConstant;
import org.lealone.command.ddl.DropDatabase;
import org.lealone.command.ddl.DropFunctionAlias;
import org.lealone.command.ddl.DropIndex;
import org.lealone.command.ddl.DropRole;
import org.lealone.command.ddl.DropSchema;
import org.lealone.command.ddl.DropSequence;
import org.lealone.command.ddl.DropTable;
import org.lealone.command.ddl.DropTrigger;
import org.lealone.command.ddl.DropUser;
import org.lealone.command.ddl.DropUserDataType;
import org.lealone.command.ddl.DropView;
import org.lealone.command.ddl.GrantRevoke;
import org.lealone.command.ddl.PrepareProcedure;
import org.lealone.command.ddl.SetComment;
import org.lealone.command.ddl.TruncateTable;
import org.lealone.command.dml.BackupCommand;
import org.lealone.command.dml.Call;
import org.lealone.command.dml.Delete;
import org.lealone.command.dml.ExecuteProcedure;
import org.lealone.command.dml.Explain;
import org.lealone.command.dml.Insert;
import org.lealone.command.dml.Merge;
import org.lealone.command.dml.NoOperation;
import org.lealone.command.dml.Query;
import org.lealone.command.dml.RunScriptCommand;
import org.lealone.command.dml.ScriptCommand;
import org.lealone.command.dml.Select;
import org.lealone.command.dml.SelectOrderBy;
import org.lealone.command.dml.SelectUnion;
import org.lealone.command.dml.Set;
import org.lealone.command.dml.TransactionCommand;
import org.lealone.command.dml.Update;
import org.lealone.dbobject.DbObject;
import org.lealone.dbobject.FunctionAlias;
import org.lealone.dbobject.Procedure;
import org.lealone.dbobject.Right;
import org.lealone.dbobject.Schema;
import org.lealone.dbobject.Sequence;
import org.lealone.dbobject.User;
import org.lealone.dbobject.UserAggregate;
import org.lealone.dbobject.UserDataType;
import org.lealone.dbobject.constraint.ConstraintReferential;
import org.lealone.dbobject.index.Index;
import org.lealone.dbobject.table.Column;
import org.lealone.dbobject.table.FunctionTable;
import org.lealone.dbobject.table.IndexColumn;
import org.lealone.dbobject.table.RangeTable;
import org.lealone.dbobject.table.Table;
import org.lealone.dbobject.table.TableFilter;
import org.lealone.dbobject.table.TableFilter.TableFilterVisitor;
import org.lealone.dbobject.table.TableView;
import org.lealone.engine.Constants;
import org.lealone.engine.Database;
import org.lealone.engine.Session;
import org.lealone.engine.SetTypes;
import org.lealone.expression.Aggregate;
import org.lealone.expression.Alias;
import org.lealone.expression.CompareLike;
import org.lealone.expression.Comparison;
import org.lealone.expression.ConditionAndOr;
import org.lealone.expression.ConditionExists;
import org.lealone.expression.ConditionIn;
import org.lealone.expression.ConditionInSelect;
import org.lealone.expression.ConditionNot;
import org.lealone.expression.Expression;
import org.lealone.expression.ExpressionColumn;
import org.lealone.expression.ExpressionList;
import org.lealone.expression.Function;
import org.lealone.expression.FunctionCall;
import org.lealone.expression.JavaAggregate;
import org.lealone.expression.JavaFunction;
import org.lealone.expression.Operation;
import org.lealone.expression.Parameter;
import org.lealone.expression.Rownum;
import org.lealone.expression.SequenceValue;
import org.lealone.expression.Subquery;
import org.lealone.expression.TableFunction;
import org.lealone.expression.ValueExpression;
import org.lealone.expression.Variable;
import org.lealone.expression.Wildcard;
import org.lealone.message.DbException;
import org.lealone.result.SortOrder;
import org.lealone.util.MathUtils;
import org.lealone.util.New;
import org.lealone.util.StatementBuilder;
import org.lealone.util.StringUtils;
import org.lealone.value.CompareMode;
import org.lealone.value.DataType;
import org.lealone.value.Value;
import org.lealone.value.ValueBoolean;
import org.lealone.value.ValueBytes;
import org.lealone.value.ValueDate;
import org.lealone.value.ValueDecimal;
import org.lealone.value.ValueInt;
import org.lealone.value.ValueLong;
import org.lealone.value.ValueNull;
import org.lealone.value.ValueString;
import org.lealone.value.ValueTime;
import org.lealone.value.ValueTimestamp;

/**
 * The parser is used to convert a SQL statement string to an command object.
 */
public class Parser {

    // used during the tokenizer phase
    private static final int CHAR_END = 1, CHAR_VALUE = 2, CHAR_QUOTED = 3;
    private static final int CHAR_NAME = 4, CHAR_SPECIAL_1 = 5, CHAR_SPECIAL_2 = 6;
    private static final int CHAR_STRING = 7, CHAR_DOT = 8, CHAR_DOLLAR_QUOTED_STRING = 9;

    // this are token types
    protected static final int KEYWORD = 1, IDENTIFIER = 2, PARAMETER = 3, END = 4, VALUE = 5;
    private static final int EQUAL = 6, BIGGER_EQUAL = 7, BIGGER = 8;
    private static final int SMALLER = 9, SMALLER_EQUAL = 10, NOT_EQUAL = 11, AT = 12;
    private static final int MINUS = 13, PLUS = 14, STRING_CONCAT = 15;
    private static final int OPEN = 16, CLOSE = 17, NULL = 18, TRUE = 19, FALSE = 20;
    private static final int CURRENT_TIMESTAMP = 21, CURRENT_DATE = 22, CURRENT_TIME = 23, ROWNUM = 24;

    protected final Database database;
    protected final Session session;

    private int[] characterTypes;
    protected int currentTokenType;
    protected String currentToken;
    private boolean currentTokenQuoted;
    private Value currentValue;
    protected String sqlCommand;
    private String originalSQL;
    private char[] sqlCommandChars;
    private int lastParseIndex;
    protected int parseIndex;
    private CreateView createView;
    protected Prepared currentPrepared;
    private Select currentSelect;
    private ArrayList<Parameter> parameters;
    private String schemaName;
    private ArrayList<String> expectedList;
    private boolean rightsChecked;
    private boolean recompileAlways;
    private ArrayList<Parameter> indexedParameterList;
    private final boolean identifiersToUpper;

    public Parser(Session session) {
        database = session.getDatabase();
        this.identifiersToUpper = database.getSettings().databaseToUpper;
        this.session = session;
    }

    /**
     * Parse the statement and prepare it for execution.
     *
     * @param sql the SQL statement to parse
     * @return the prepared object
     */
    public Prepared prepare(String sql) {
        Prepared p = parse(sql);
        p.prepare();
        if (currentTokenType != END) {
            throw getSyntaxError();
        }
        return p;
    }

    /**
     * Parse a statement or a list of statements, and prepare it for execution.
     *
     * @param sql the SQL statement to parse
     * @return the command object
     */
    public Command prepareCommand(String sql) {
        try {
            Prepared p = parse(sql);
            p.prepare();
            Command c = createCommand(p, sql);
            if (isToken(";")) {
                String remaining = originalSQL.substring(parseIndex);
                if (remaining.trim().length() != 0) {
                    CommandList list = new CommandList(this, sql, c, remaining);
                    // list.addCommand(c);
                    // do {
                    // c = parseCommand();
                    // list.addCommand(c);
                    // } while(currentToken.equals(";"));
                    c = list;
                }
            } else if (currentTokenType != END) {
                throw getSyntaxError();
            }
            return c;
        } catch (DbException e) {
            throw e.addSQL(originalSQL);
        }
    }

    /**
     * Parse the statement, but don't prepare it for execution.
     *
     * @param sql the SQL statement to parse
     * @return the prepared object
     */
    protected Prepared parse(String sql) {
        Prepared p;
        try {
            // first, try the fast variant
            p = parse(sql, false);
        } catch (DbException e) {
            if (e.getErrorCode() == ErrorCode.SYNTAX_ERROR_1) {
                // now, get the detailed exception
                p = parse(sql, true);
            } else {
                throw e.addSQL(sql);
            }
        }
        p.setPrepareAlways(recompileAlways);
        p.setParameterList(parameters);

        if (p.isDDL()) {
            p = createDefineCommandWrapper(session, (DefineCommand) p);
        }
        return p;
    }

    private Prepared parse(String sql, boolean withExpectedList) {
        initialize(sql);
        if (withExpectedList) {
            expectedList = New.arrayList();
        } else {
            expectedList = null;
        }
        parameters = New.arrayList();
        currentSelect = null;
        currentPrepared = null;
        createView = null;
        recompileAlways = false;
        indexedParameterList = null;
        read();
        return parsePrepared();
    }

    protected Prepared parsePrepared(char first) {
        return null;
    }

    protected Prepared parsePrepared() {
        int start = lastParseIndex;
        Prepared c = null;
        String token = currentToken;
        if (token.length() == 0) {
            c = new NoOperation(session);
        } else {
            char first = token.charAt(0);
            c = parsePrepared(first);

            if (c == null)
                switch (first) {
                case '?':
                    // read the ? as a parameter
                    readTerm();
                    // this is an 'out' parameter - set a dummy value
                    parameters.get(0).setValue(ValueNull.INSTANCE);
                    read("=");
                    read("CALL");
                    c = parseCall();
                    break;
                case '(':
                    c = parseSelect();
                    break;
                case 'a':
                case 'A':
                    if (readIf("ALTER")) {
                        c = parseAlter();
                    } else if (readIf("ANALYZE")) {
                        c = parseAnalyze();
                    }
                    break;
                case 'b':
                case 'B':
                    if (readIf("BACKUP")) {
                        c = parseBackup();
                    } else if (readIf("BEGIN")) {
                        c = parseBegin();
                    }
                    break;
                case 'c':
                case 'C':
                    if (readIf("COMMIT")) {
                        c = parseCommit();
                    } else if (readIf("CREATE")) {
                        c = parseCreate();
                    } else if (readIf("CALL")) {
                        c = parseCall();
                    } else if (readIf("CHECKPOINT")) {
                        c = parseCheckpoint();
                    } else if (readIf("COMMENT")) {
                        c = parseComment();
                    }
                    break;
                case 'd':
                case 'D':
                    if (readIf("DELETE")) {
                        c = parseDelete();
                    } else if (readIf("DROP")) {
                        c = parseDrop();
                    } else if (readIf("DECLARE")) {
                        // support for DECLARE GLOBAL TEMPORARY TABLE...
                        c = parseCreate();
                    } else if (readIf("DEALLOCATE")) {
                        c = parseDeallocate();
                    }
                    break;
                case 'e':
                case 'E':
                    if (readIf("EXPLAIN")) {
                        c = parseExplain();
                    } else if (readIf("EXECUTE")) {
                        c = parseExecute();
                    }
                    break;
                case 'f':
                case 'F':
                    if (isToken("FROM")) {
                        c = parseSelect();
                    }
                    break;
                case 'g':
                case 'G':
                    if (readIf("GRANT")) {
                        c = parseGrantRevoke(CommandInterface.GRANT);
                    }
                    break;
                case 'h':
                case 'H':
                    if (readIf("HELP")) {
                        c = parseHelp();
                    }
                    break;
                case 'i':
                case 'I':
                    if (readIf("INSERT")) {
                        c = parseInsert();
                    }
                    break;
                case 'm':
                case 'M':
                    if (readIf("MERGE")) {
                        c = parseMerge();
                    }
                    break;
                case 'p':
                case 'P':
                    if (readIf("PREPARE")) {
                        c = parsePrepare();
                    }
                    break;
                case 'r':
                case 'R':
                    if (readIf("ROLLBACK")) {
                        c = parseRollback();
                    } else if (readIf("REVOKE")) {
                        c = parseGrantRevoke(CommandInterface.REVOKE);
                    } else if (readIf("RUNSCRIPT")) {
                        c = parseRunScript();
                    } else if (readIf("RELEASE")) {
                        c = parseReleaseSavepoint();
                    }
                    break;
                case 's':
                case 'S':
                    if (isToken("SELECT")) {
                        c = parseSelect();
                    } else if (readIf("SET")) {
                        c = parseSet();
                    } else if (readIf("SAVEPOINT")) {
                        c = parseSavepoint();
                    } else if (readIf("SCRIPT")) {
                        c = parseScript();
                    } else if (readIf("SHUTDOWN")) {
                        c = parseShutdown();
                    } else if (readIf("SHOW")) {
                        c = parseShow();
                    }
                    break;
                case 't':
                case 'T':
                    if (readIf("TRUNCATE")) {
                        c = parseTruncate();
                    }
                    break;
                case 'u':
                case 'U':
                    if (readIf("UPDATE")) {
                        c = parseUpdate();
                    }
                    break;
                case 'v':
                case 'V':
                    if (readIf("VALUES")) {
                        c = parseValues();
                    }
                    break;
                case 'w':
                case 'W':
                    if (readIf("WITH")) {
                        c = parseWith();
                    }
                    break;
                default:
                    throw getSyntaxError();
                }
            if (indexedParameterList != null) {
                for (int i = 0, size = indexedParameterList.size(); i < size; i++) {
                    if (indexedParameterList.get(i) == null) {
                        indexedParameterList.set(i, new Parameter(i));
                    }
                }
                parameters = indexedParameterList;
            }
            if (readIf("{")) {
                do {
                    int index = (int) readLong() - 1;
                    if (index < 0 || index >= parameters.size()) {
                        throw getSyntaxError();
                    }
                    Parameter p = parameters.get(index);
                    if (p == null) {
                        throw getSyntaxError();
                    }
                    read(":");
                    Expression expr = readExpression();
                    expr = expr.optimize(session);
                    p.setValue(expr.getValue(session));
                } while (readIf(","));
                read("}");
                for (Parameter p : parameters) {
                    p.checkSet();
                }
                parameters.clear();
            }
        }
        if (c == null) {
            throw getSyntaxError();
        }
        setSQL(c, null, start);
        return c;
    }

    protected DbException getSyntaxError() {
        if (expectedList == null || expectedList.isEmpty()) {
            return DbException.getSyntaxError(sqlCommand, parseIndex);
        }
        StatementBuilder buff = new StatementBuilder();
        for (String e : expectedList) {
            buff.appendExceptFirst(", ");
            buff.append(e);
        }
        return DbException.getSyntaxError(sqlCommand, parseIndex, buff.toString());
    }

    private Prepared parseBackup() {
        BackupCommand command = new BackupCommand(session);
        read("TO");
        command.setFileName(readExpression());
        return command;
    }

    private Prepared parseAnalyze() {
        Analyze command = new Analyze(session);
        if (readIf("SAMPLE_SIZE")) {
            command.setTop(getPositiveInt());
        }
        return command;
    }

    private TransactionCommand parseBegin() {
        TransactionCommand command;
        if (!readIf("WORK")) {
            readIf("TRANSACTION");
        }
        command = new TransactionCommand(session, CommandInterface.BEGIN);
        return command;
    }

    private TransactionCommand parseCommit() {
        TransactionCommand command;
        if (readIf("TRANSACTION")) {
            command = new TransactionCommand(session, CommandInterface.COMMIT_TRANSACTION);
            command.setTransactionName(readUniqueIdentifier());
            return command;
        }
        command = new TransactionCommand(session, CommandInterface.COMMIT);
        readIf("WORK");
        return command;
    }

    private TransactionCommand parseShutdown() {
        int type = CommandInterface.SHUTDOWN;
        if (readIf("IMMEDIATELY")) {
            type = CommandInterface.SHUTDOWN_IMMEDIATELY;
        } else if (readIf("COMPACT")) {
            type = CommandInterface.SHUTDOWN_COMPACT;
        } else if (readIf("DEFRAG")) {
            type = CommandInterface.SHUTDOWN_DEFRAG;
        } else {
            readIf("SCRIPT");
        }
        return new TransactionCommand(session, type);
    }

    private TransactionCommand parseRollback() {
        TransactionCommand command;
        if (readIf("TRANSACTION")) {
            command = new TransactionCommand(session, CommandInterface.ROLLBACK_TRANSACTION);
            command.setTransactionName(readUniqueIdentifier());
            return command;
        }
        if (readIf("TO")) {
            read("SAVEPOINT");
            command = new TransactionCommand(session, CommandInterface.ROLLBACK_TO_SAVEPOINT);
            command.setSavepointName(readUniqueIdentifier());
        } else {
            readIf("WORK");
            command = new TransactionCommand(session, CommandInterface.ROLLBACK);
        }
        return command;
    }

    private Prepared parsePrepare() {
        if (readIf("COMMIT")) {
            TransactionCommand command = new TransactionCommand(session, CommandInterface.PREPARE_COMMIT);
            command.setTransactionName(readUniqueIdentifier());
            return command;
        }
        String procedureName = readAliasIdentifier();
        if (readIf("(")) {
            ArrayList<Column> list = New.arrayList();
            for (int i = 0;; i++) {
                Column column = parseColumnForTable("C" + i, true);
                list.add(column);
                if (readIf(")")) {
                    break;
                }
                read(",");
            }
        }
        read("AS");
        Prepared prep = parsePrepared();
        PrepareProcedure command = new PrepareProcedure(session);
        command.setProcedureName(procedureName);
        command.setPrepared(prep);
        return command;
    }

    private TransactionCommand parseSavepoint() {
        TransactionCommand command = new TransactionCommand(session, CommandInterface.SAVEPOINT);
        command.setSavepointName(readUniqueIdentifier());
        return command;
    }

    private Prepared parseReleaseSavepoint() {
        Prepared command = new NoOperation(session);
        readIf("SAVEPOINT");
        readUniqueIdentifier();
        return command;
    }

    private Schema getSchema(String schemaName) {
        if (schemaName == null) {
            return null;
        }
        Schema schema = database.findSchema(schemaName);
        if (schema == null) {
            if (equalsToken("SESSION", schemaName)) {
                // for local temporary tables
                schema = database.getSchema(session.getCurrentSchemaName());
            } else if (database.getMode().sysDummy1 && "SYSIBM".equals(schemaName)) {
                // IBM DB2 and Apache Derby compatibility: SYSIBM.SYSDUMMY1
            } else {
                throw DbException.get(ErrorCode.SCHEMA_NOT_FOUND_1, schemaName);
            }
        }
        return schema;
    }

    protected Schema getSchema() {
        return getSchema(schemaName);
    }

    protected Column readTableColumn(TableFilter filter) {
        String tableAlias = null;
        String columnName = readColumnIdentifier();
        if (readIf(".")) {
            tableAlias = columnName;
            columnName = readColumnIdentifier();
            if (readIf(".")) {
                String schema = tableAlias;
                tableAlias = columnName;
                columnName = readColumnIdentifier();
                if (readIf(".")) {
                    String catalogName = schema;
                    schema = tableAlias;
                    tableAlias = columnName;
                    columnName = readColumnIdentifier();
                    if (!equalsToken(catalogName, database.getShortName())) {
                        throw DbException.get(ErrorCode.DATABASE_NOT_FOUND_1, catalogName);
                    }
                }
                if (!equalsToken(schema, filter.getTable().getSchema().getName())) {
                    throw DbException.get(ErrorCode.SCHEMA_NOT_FOUND_1, schema);
                }
            }
            if (!equalsToken(tableAlias, filter.getTableAlias())) {
                throw DbException.get(ErrorCode.TABLE_OR_VIEW_NOT_FOUND_1, tableAlias);
            }
        }
        if (database.getSettings().rowId) {
            if (Column.ROWID.equals(columnName)) {
                return filter.getRowIdColumn();
            }
        }
        return filter.getTable().getColumn(columnName);
    }

    private Update parseUpdate() {
        Update command = createUpdate(session);
        currentPrepared = command;
        int start = lastParseIndex;
        TableFilter filter = readSimpleTableFilter();
        command.setTableFilter(filter);
        read("SET");
        if (readIf("(")) {
            ArrayList<Column> columns = New.arrayList();
            do {
                Column column = readTableColumn(filter);
                columns.add(column);
            } while (readIf(","));
            read(")");
            read("=");
            Expression expression = readExpression();
            if (columns.size() == 1) {
                // the expression is parsed as a simple value
                command.setAssignment(columns.get(0), expression);
            } else {
                for (int i = 0, size = columns.size(); i < size; i++) {
                    Column column = columns.get(i);
                    Function f = Function.getFunction(database, "ARRAY_GET");
                    f.setParameter(0, expression);
                    f.setParameter(1, ValueExpression.get(ValueInt.get(i + 1)));
                    f.doneWithParameters();
                    command.setAssignment(column, f);
                }
            }
        } else {
            do {
                Column column = readTableColumn(filter);
                read("=");
                Expression expression;
                if (readIf("DEFAULT")) {
                    expression = ValueExpression.getDefault();
                } else {
                    expression = readExpression();
                }
                command.setAssignment(column, expression);
            } while (readIf(","));
        }
        if (readIf("WHERE")) {
            Expression condition = readExpression();
            command.setCondition(condition);
        }
        if (readIf("LIMIT")) {
            Expression limit = readTerm().optimize(session);
            command.setLimit(limit);
        }
        setSQL(command, "UPDATE", start);
        return command;
    }

    private TableFilter readSimpleTableFilter() {
        Table table = readTableOrView();
        String alias = null;
        if (readIf("AS")) {
            alias = readAliasIdentifier();
        } else if (currentTokenType == IDENTIFIER) {
            if (!equalsToken("SET", currentToken)) {
                // SET is not a keyword (PostgreSQL supports it as a table name)
                alias = readAliasIdentifier();
            }
        }
        return new TableFilter(session, table, alias, rightsChecked, currentSelect);
    }

    private Delete parseDelete() {
        Delete command = createDelete(session);
        Expression limit = null;
        if (readIf("TOP")) {
            limit = readTerm().optimize(session);
        }
        currentPrepared = command;
        int start = lastParseIndex;
        readIf("FROM");
        TableFilter filter = readSimpleTableFilter();

        command.setTableFilter(filter);
        if (readIf("WHERE")) {
            Expression condition = readExpression();
            command.setCondition(condition);
        }
        if (readIf("LIMIT") && limit == null) {
            limit = readTerm().optimize(session);
        }
        command.setLimit(limit);
        setSQL(command, "DELETE", start);
        return command;
    }

    private IndexColumn[] parseIndexColumnList() {
        ArrayList<IndexColumn> columns = New.arrayList();
        do {
            IndexColumn column = new IndexColumn();
            column.columnName = readColumnIdentifier();
            if (readIf(".")) {
                column.columnFamilyName = column.columnName;
                column.columnName = readColumnIdentifier();
            }
            columns.add(column);
            if (readIf("ASC")) {
                // ignore
            } else if (readIf("DESC")) {
                column.sortType = SortOrder.DESCENDING;
            }
            if (readIf("NULLS")) {
                if (readIf("FIRST")) {
                    column.sortType |= SortOrder.NULLS_FIRST;
                } else {
                    read("LAST");
                    column.sortType |= SortOrder.NULLS_LAST;
                }
            }
        } while (readIf(","));
        read(")");
        return columns.toArray(new IndexColumn[columns.size()]);
    }

    private String[] parseColumnList() {
        ArrayList<String> columns = New.arrayList();
        do {
            String columnName = readColumnIdentifier();
            columns.add(columnName);
        } while (readIfMore());
        return columns.toArray(new String[columns.size()]);
    }

    private Column[] parseColumnList(Table table) {
        ArrayList<Column> columns = New.arrayList();
        HashSet<Column> set = New.hashSet();
        if (!readIf(")")) {
            do {
                Column column = parseColumn(table);
                if (!set.add(column)) {
                    throw DbException.get(ErrorCode.DUPLICATE_COLUMN_NAME_1, column.getSQL());
                }
                columns.add(column);
            } while (readIfMore());
        }
        return columns.toArray(new Column[columns.size()]);
    }

    protected Column parseColumn(Table table) {
        String columnName = readColumnIdentifier();

        if (database.getSettings().rowId && Column.ROWID.equals(columnName)) {
            return table.getRowIdColumn();
        }
        return table.getColumn(columnName);
    }

    protected boolean readIfMore() {
        if (readIf(",")) {
            return !readIf(")");
        }
        read(")");
        return false;
    }

    private Prepared parseHelp() {
        StringBuilder buff = new StringBuilder("SELECT * FROM INFORMATION_SCHEMA.HELP");
        int i = 0;
        ArrayList<Value> paramValues = New.arrayList();
        while (currentTokenType != END) {
            String s = currentToken;
            read();
            if (i == 0) {
                buff.append(" WHERE ");
            } else {
                buff.append(" AND ");
            }
            i++;
            buff.append("UPPER(TOPIC) LIKE ?");
            paramValues.add(ValueString.get("%" + s + "%"));
        }
        return prepare(session, buff.toString(), paramValues);
    }

    private Prepared parseShow() {
        ArrayList<Value> paramValues = New.arrayList();
        StringBuilder buff = new StringBuilder("SELECT ");
        if (readIf("CLIENT_ENCODING")) {
            // for PostgreSQL compatibility
            buff.append("'UNICODE' AS CLIENT_ENCODING FROM DUAL");
        } else if (readIf("DEFAULT_TRANSACTION_ISOLATION")) {
            // for PostgreSQL compatibility
            buff.append("'read committed' AS DEFAULT_TRANSACTION_ISOLATION FROM DUAL");
        } else if (readIf("DATESTYLE")) {
            // for PostgreSQL compatibility
            buff.append("'ISO' AS DATESTYLE FROM DUAL");
        } else if (readIf("SERVER_VERSION")) {
            // for PostgreSQL compatibility
            buff.append("'8.1.4' AS SERVER_VERSION FROM DUAL");
        } else if (readIf("SERVER_ENCODING")) {
            // for PostgreSQL compatibility
            buff.append("'UTF8' AS SERVER_ENCODING FROM DUAL");
        } else if (readIf("TABLES")) {
            // for MySQL compatibility
            String schema = Constants.SCHEMA_MAIN;
            if (readIf("FROM")) {
                schema = readUniqueIdentifier();
            }
            buff.append("TABLE_NAME, TABLE_SCHEMA FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_SCHEMA=? ORDER BY TABLE_NAME");
            paramValues.add(ValueString.get(schema));
        } else if (readIf("COLUMNS")) {
            // for MySQL compatibility
            read("FROM");
            String tableName = readIdentifierWithSchema();
            String schemaName = getSchema().getName();
            paramValues.add(ValueString.get(tableName));
            if (readIf("FROM")) {
                schemaName = readUniqueIdentifier();
            }
            buff.append("C.COLUMN_NAME FIELD, " + "C.TYPE_NAME || '(' || C.NUMERIC_PRECISION || ')' TYPE, "
                    + "C.IS_NULLABLE \"NULL\", " + "CASE (SELECT MAX(I.INDEX_TYPE_NAME) FROM "
                    + "INFORMATION_SCHEMA.INDEXES I " + "WHERE I.TABLE_SCHEMA=C.TABLE_SCHEMA "
                    + "AND I.TABLE_NAME=C.TABLE_NAME " + "AND I.COLUMN_NAME=C.COLUMN_NAME)"
                    + "WHEN 'PRIMARY KEY' THEN 'PRI' " + "WHEN 'UNIQUE INDEX' THEN 'UNI' ELSE '' END KEY, "
                    + "IFNULL(COLUMN_DEFAULT, 'NULL') DEFAULT " + "FROM INFORMATION_SCHEMA.COLUMNS C "
                    + "WHERE C.TABLE_NAME=? AND C.TABLE_SCHEMA=? " + "ORDER BY C.ORDINAL_POSITION");
            paramValues.add(ValueString.get(schemaName));
        } else if (readIf("DATABASES") || readIf("SCHEMAS")) {
            // for MySQL compatibility
            buff.append("SCHEMA_NAME FROM INFORMATION_SCHEMA.SCHEMATA");
        }
        boolean b = session.getAllowLiterals();
        try {
            // need to temporarily enable it, in case we are in
            // ALLOW_LITERALS_NUMBERS mode
            session.setAllowLiterals(true);
            return prepare(session, buff.toString(), paramValues);
        } finally {
            session.setAllowLiterals(b);
        }
    }

    private static Prepared prepare(Session s, String sql, ArrayList<Value> paramValues) {
        Prepared prep = s.prepare(sql);
        ArrayList<Parameter> params = prep.getParameters();
        if (params != null) {
            for (int i = 0, size = params.size(); i < size; i++) {
                Parameter p = params.get(i);
                p.setValue(paramValues.get(i));
            }
        }
        return prep;
    }

    private boolean isSelect() {
        int start = lastParseIndex;
        while (readIf("(")) {
            // need to read ahead, it could be a nested union:
            // ((select 1) union (select 1))
        }
        boolean select = isToken("SELECT") || isToken("FROM");
        parseIndex = start;
        read();
        return select;
    }

    private Merge parseMerge() {
        Merge command = createMerge(session);
        currentPrepared = command;
        read("INTO");
        Table table = readTableOrView();
        command.setTable(table);
        if (readIf("(")) {
            if (isSelect()) {
                command.setQuery(parseSelect());
                read(")");
                return command;
            }
            Column[] columns = parseColumnList(table);
            command.setColumns(columns);
        }
        if (readIf("KEY")) {
            read("(");
            Column[] keys = parseColumnList(table);
            command.setKeys(keys);
        }
        if (readIf("VALUES")) {
            do {
                ArrayList<Expression> values = New.arrayList();
                read("(");
                if (!readIf(")")) {
                    do {
                        if (readIf("DEFAULT")) {
                            values.add(null);
                        } else {
                            values.add(readExpression());
                        }
                    } while (readIfMore());
                }
                command.addRow(values.toArray(new Expression[values.size()]));
            } while (readIf(","));
        } else {
            command.setQuery(parseSelect());
        }
        return command;
    }

    private Insert parseInsert() {
        Insert command = createInsert(session);
        currentPrepared = command;
        read("INTO");
        Table table = readTableOrView();
        command.setTable(table);
        Column[] columns = null;
        if (readIf("(")) {
            if (isSelect()) {
                command.setQuery(parseSelect());
                read(")");
                return command;
            }
            columns = parseColumnList(table);
            command.setColumns(columns);
        }
        if (readIf("DIRECT")) {
            command.setInsertFromSelect(true);
        }
        if (readIf("SORTED")) {
            command.setSortedInsertMode(true);
        }
        if (readIf("DEFAULT")) {
            read("VALUES");
            Expression[] expr = {};
            command.addRow(expr);
        } else if (readIf("VALUES")) {
            read("(");
            do {
                ArrayList<Expression> values = New.arrayList();
                if (!readIf(")")) {
                    do {
                        if (readIf("DEFAULT")) {
                            values.add(null);
                        } else {
                            values.add(readExpression());
                        }
                    } while (readIfMore());
                }
                command.addRow(values.toArray(new Expression[values.size()]));
                // the following condition will allow (..),; and (..);
            } while (readIf(",") && readIf("("));
        } else if (readIf("SET")) {
            if (columns != null) {
                throw getSyntaxError();
            }
            ArrayList<Column> columnList = New.arrayList();
            ArrayList<Expression> values = New.arrayList();
            do {
                columnList.add(parseColumn(table));
                read("=");
                Expression expression;
                if (readIf("DEFAULT")) {
                    expression = ValueExpression.getDefault();
                } else {
                    expression = readExpression();
                }
                values.add(expression);
            } while (readIf(","));
            command.setColumns(columnList.toArray(new Column[columnList.size()]));
            command.addRow(values.toArray(new Expression[values.size()]));
        } else {
            command.setQuery(parseSelect());
        }
        return command;
    }

    private TableFilter readTableFilter(boolean fromOuter) {
        Table table;
        String alias = null;
        if (readIf("(")) {
            if (isSelect()) {
                Query query = parseSelectUnion();
                read(")");
                query.setParameterList(New.arrayList(parameters));
                query.init();
                Session s;
                if (createView != null) {
                    s = database.getSystemSession();
                } else {
                    s = session;
                }
                alias = session.getNextSystemIdentifier(sqlCommand);
                table = TableView.createTempView(s, session.getUser(), alias, query, currentSelect);
            } else {
                TableFilter top;
                if (database.getSettings().nestedJoins) {
                    top = readTableFilter(false);
                    top = readJoin(top, currentSelect, false, false);
                    top = getNested(top);
                } else {
                    top = readTableFilter(fromOuter);
                    top = readJoin(top, currentSelect, false, fromOuter);
                }
                read(")");
                alias = readFromAlias(null);
                if (alias != null) {
                    top.setAlias(alias);
                }
                return top;
            }
        } else if (readIf("VALUES")) {
            table = parseValuesTable().getTable();
        } else {
            String tableName = readIdentifierWithSchema(null);
            Schema schema = getSchema();
            boolean foundLeftBracket = readIf("(");
            if (foundLeftBracket && readIf("INDEX")) {
                // Sybase compatibility with "select * from test (index table1_index)"
                readIdentifierWithSchema(null);
                read(")");
                foundLeftBracket = false;
            }
            if (foundLeftBracket) {
                Schema mainSchema = database.getSchema(Constants.SCHEMA_MAIN);
                if (equalsToken(tableName, RangeTable.NAME)) {
                    Expression min = readExpression();
                    read(",");
                    Expression max = readExpression();
                    read(")");
                    table = new RangeTable(mainSchema, min, max, false);
                } else {
                    Expression expr = readFunction(schema, tableName);
                    if (!(expr instanceof FunctionCall)) {
                        throw getSyntaxError();
                    }
                    FunctionCall call = (FunctionCall) expr;
                    if (!call.isDeterministic()) {
                        recompileAlways = true;
                    }
                    table = new FunctionTable(mainSchema, session, expr, call);
                }
            } else if (equalsToken("DUAL", tableName)) {
                table = getDualTable(false);
            } else if (database.getMode().sysDummy1 && equalsToken("SYSDUMMY1", tableName)) {
                table = getDualTable(false);
            } else {
                table = readTableOrView(tableName);
            }
        }
        alias = readFromAlias(alias);
        return new TableFilter(session, table, alias, rightsChecked, currentSelect);
    }

    private String readFromAlias(String alias) {
        if (readIf("AS")) {
            alias = readAliasIdentifier();
        } else if (currentTokenType == IDENTIFIER) {
            // left and right are not keywords (because they are functions as
            // well)
            if (!isToken("LEFT") && !isToken("RIGHT") && !isToken("FULL")) {
                alias = readAliasIdentifier();
            }
        }
        return alias;
    }

    private Prepared parseTruncate() {
        read("TABLE");
        Table table = readTableOrView();
        TruncateTable command = new TruncateTable(session);
        command.setTable(table);
        return command;
    }

    protected boolean readIfExists(boolean ifExists) {
        if (readIf("IF")) {
            read("EXISTS");
            ifExists = true;
        }
        return ifExists;
    }

    private Prepared parseComment() {
        int type = 0;
        read("ON");
        boolean column = false;
        if (readIf("TABLE") || readIf("VIEW")) {
            type = DbObject.TABLE_OR_VIEW;
        } else if (readIf("COLUMN")) {
            column = true;
            type = DbObject.TABLE_OR_VIEW;
        } else if (readIf("CONSTANT")) {
            type = DbObject.CONSTANT;
        } else if (readIf("CONSTRAINT")) {
            type = DbObject.CONSTRAINT;
        } else if (readIf("ALIAS")) {
            type = DbObject.FUNCTION_ALIAS;
        } else if (readIf("INDEX")) {
            type = DbObject.INDEX;
        } else if (readIf("ROLE")) {
            type = DbObject.ROLE;
        } else if (readIf("SCHEMA")) {
            type = DbObject.SCHEMA;
        } else if (readIf("SEQUENCE")) {
            type = DbObject.SEQUENCE;
        } else if (readIf("TRIGGER")) {
            type = DbObject.TRIGGER;
        } else if (readIf("USER")) {
            type = DbObject.USER;
        } else if (readIf("DOMAIN")) {
            type = DbObject.USER_DATATYPE;
        } else {
            throw getSyntaxError();
        }
        SetComment command = new SetComment(session);
        String objectName;
        if (column) {
            // can't use readIdentifierWithSchema() because
            // it would not read schema.table.column correctly
            // if the db name is equal to the schema name
            ArrayList<String> list = New.arrayList();
            do {
                list.add(readUniqueIdentifier());
            } while (readIf("."));
            schemaName = session.getCurrentSchemaName();
            if (list.size() == 4) {
                if (!equalsToken(database.getShortName(), list.get(0))) {
                    throw DbException.getSyntaxError(sqlCommand, parseIndex, "database name");
                }
                list.remove(0);
            }
            if (list.size() == 3) {
                schemaName = list.get(0);
                list.remove(0);
            }
            if (list.size() != 2) {
                throw DbException.getSyntaxError(sqlCommand, parseIndex, "table.column");
            }
            objectName = list.get(0);
            command.setColumn(true);
            command.setColumnName(list.get(1));
        } else {
            objectName = readIdentifierWithSchema();
        }
        command.setSchemaName(schemaName);
        command.setObjectName(objectName);
        command.setObjectType(type);
        read("IS");
        command.setCommentExpression(readExpression());
        return command;
    }

    protected Prepared parseDrop() {
        if (readIf("TABLE")) {
            boolean ifExists = readIfExists(false);
            String tableName = readIdentifierWithSchema();
            DropTable command = new DropTable(session, getSchema());
            command.setTableName(tableName);
            while (readIf(",")) {
                tableName = readIdentifierWithSchema();
                DropTable next = new DropTable(session, getSchema());
                next.setTableName(tableName);
                command.addNextDropTable(next);
            }
            ifExists = readIfExists(ifExists);
            command.setIfExists(ifExists);
            if (readIf("CASCADE")) {
                command.setDropAction(ConstraintReferential.CASCADE);
                readIf("CONSTRAINTS");
            } else if (readIf("RESTRICT")) {
                command.setDropAction(ConstraintReferential.RESTRICT);
            } else if (readIf("IGNORE")) {
                command.setDropAction(ConstraintReferential.SET_DEFAULT);
            }
            return command;
        } else if (readIf("INDEX")) {
            boolean ifExists = readIfExists(false);
            String indexName = readIdentifierWithSchema();
            DropIndex command = new DropIndex(session, getSchema());
            command.setIndexName(indexName);
            ifExists = readIfExists(ifExists);
            command.setIfExists(ifExists);
            return command;
        } else if (readIf("USER")) {
            boolean ifExists = readIfExists(false);
            DropUser command = new DropUser(session);
            command.setUserName(readUniqueIdentifier());
            ifExists = readIfExists(ifExists);
            readIf("CASCADE");
            command.setIfExists(ifExists);
            return command;
        } else if (readIf("SEQUENCE")) {
            boolean ifExists = readIfExists(false);
            String sequenceName = readIdentifierWithSchema();
            DropSequence command = new DropSequence(session, getSchema());
            command.setSequenceName(sequenceName);
            ifExists = readIfExists(ifExists);
            command.setIfExists(ifExists);
            return command;
        } else if (readIf("CONSTANT")) {
            boolean ifExists = readIfExists(false);
            String constantName = readIdentifierWithSchema();
            DropConstant command = new DropConstant(session, getSchema());
            command.setConstantName(constantName);
            ifExists = readIfExists(ifExists);
            command.setIfExists(ifExists);
            return command;
        } else if (readIf("TRIGGER")) {
            boolean ifExists = readIfExists(false);
            String triggerName = readIdentifierWithSchema();
            DropTrigger command = new DropTrigger(session, getSchema());
            command.setTriggerName(triggerName);
            ifExists = readIfExists(ifExists);
            command.setIfExists(ifExists);
            return command;
        } else if (readIf("VIEW")) {
            boolean ifExists = readIfExists(false);
            String viewName = readIdentifierWithSchema();
            DropView command = new DropView(session, getSchema());
            command.setViewName(viewName);
            ifExists = readIfExists(ifExists);
            command.setIfExists(ifExists);
            Integer dropAction = parseCascadeOrRestrict();
            if (dropAction != null) {
                command.setDropAction(dropAction);
            }
            return command;
        } else if (readIf("ROLE")) {
            boolean ifExists = readIfExists(false);
            DropRole command = new DropRole(session);
            command.setRoleName(readUniqueIdentifier());
            ifExists = readIfExists(ifExists);
            command.setIfExists(ifExists);
            return command;
        } else if (readIf("ALIAS")) {
            boolean ifExists = readIfExists(false);
            String aliasName = readIdentifierWithSchema();
            DropFunctionAlias command = new DropFunctionAlias(session, getSchema());
            command.setAliasName(aliasName);
            ifExists = readIfExists(ifExists);
            command.setIfExists(ifExists);
            return command;
        } else if (readIf("SCHEMA")) {
            boolean ifExists = readIfExists(false);
            DropSchema command = new DropSchema(session);
            command.setSchemaName(readUniqueIdentifier());
            ifExists = readIfExists(ifExists);
            command.setIfExists(ifExists);
            return command;
        } else if (readIf("ALL")) {
            read("OBJECTS");
            DropDatabase command = new DropDatabase(session);
            command.setDropAllObjects(true);
            if (readIf("DELETE")) {
                read("FILES");
                command.setDeleteFiles(true);
            }
            return command;
        } else if (readIf("DOMAIN")) {
            return parseDropUserDataType();
        } else if (readIf("TYPE")) {
            return parseDropUserDataType();
        } else if (readIf("DATATYPE")) {
            return parseDropUserDataType();
        } else if (readIf("AGGREGATE")) {
            return parseDropAggregate();
        }
        throw getSyntaxError();
    }

    private DropUserDataType parseDropUserDataType() {
        boolean ifExists = readIfExists(false);
        DropUserDataType command = new DropUserDataType(session);
        command.setTypeName(readUniqueIdentifier());
        ifExists = readIfExists(ifExists);
        command.setIfExists(ifExists);
        return command;
    }

    private DropAggregate parseDropAggregate() {
        boolean ifExists = readIfExists(false);
        DropAggregate command = new DropAggregate(session);
        command.setName(readUniqueIdentifier());
        ifExists = readIfExists(ifExists);
        command.setIfExists(ifExists);
        return command;
    }

    private TableFilter readJoin(TableFilter top, Select command, boolean nested, boolean fromOuter) {
        boolean joined = false;
        TableFilter last = top;
        boolean nestedJoins = database.getSettings().nestedJoins;
        while (true) {
            if (readIf("RIGHT")) {
                readIf("OUTER");
                read("JOIN");
                joined = true;
                // the right hand side is the 'inner' table usually
                TableFilter newTop = readTableFilter(fromOuter);
                newTop = readJoin(newTop, command, nested, true);
                Expression on = null;
                if (readIf("ON")) {
                    on = readExpression();
                }
                if (nestedJoins) {
                    top = getNested(top);
                    newTop.addJoin(top, true, false, on);
                } else {
                    newTop.addJoin(top, true, false, on);
                }
                top = newTop;
                last = newTop;
            } else if (readIf("LEFT")) {
                readIf("OUTER");
                read("JOIN");
                joined = true;
                TableFilter join = readTableFilter(true);
                if (nestedJoins) {
                    join = readJoin(join, command, true, true);
                } else {
                    top = readJoin(top, command, false, true);
                }
                Expression on = null;
                if (readIf("ON")) {
                    on = readExpression();
                }
                top.addJoin(join, true, false, on);
                last = join;
            } else if (readIf("FULL")) {
                throw getSyntaxError();
            } else if (readIf("INNER")) {
                read("JOIN");
                joined = true;
                TableFilter join = readTableFilter(fromOuter);
                top = readJoin(top, command, false, false);
                Expression on = null;
                if (readIf("ON")) {
                    on = readExpression();
                }
                if (nestedJoins) {
                    top.addJoin(join, false, false, on);
                } else {
                    top.addJoin(join, fromOuter, false, on);
                }
                last = join;
            } else if (readIf("JOIN")) {
                joined = true;
                TableFilter join = readTableFilter(fromOuter);
                top = readJoin(top, command, false, false);
                Expression on = null;
                if (readIf("ON")) {
                    on = readExpression();
                }
                if (nestedJoins) {
                    top.addJoin(join, false, false, on);
                } else {
                    top.addJoin(join, fromOuter, false, on);
                }
                last = join;
            } else if (readIf("CROSS")) {
                read("JOIN");
                joined = true;
                TableFilter join = readTableFilter(fromOuter);
                if (nestedJoins) {
                    top.addJoin(join, false, false, null);
                } else {
                    top.addJoin(join, fromOuter, false, null);
                }
                last = join;
            } else if (readIf("NATURAL")) {
                read("JOIN");
                joined = true;
                TableFilter join = readTableFilter(fromOuter);
                Column[] tableCols = last.getTable().getColumns();
                Column[] joinCols = join.getTable().getColumns();
                String tableSchema = last.getTable().getSchema().getName();
                String joinSchema = join.getTable().getSchema().getName();
                Expression on = null;
                for (Column tc : tableCols) {
                    String tableColumnName = tc.getFullName();
                    for (Column c : joinCols) {
                        String joinColumnName = c.getFullName();
                        if (equalsToken(tableColumnName, joinColumnName)) {
                            join.addNaturalJoinColumn(c);
                            Expression tableExpr = new ExpressionColumn(database, tableSchema, last.getTableAlias(),
                                    tc.getColumnFamilyName(), tableColumnName);
                            Expression joinExpr = new ExpressionColumn(database, joinSchema, join.getTableAlias(),
                                    c.getColumnFamilyName(), joinColumnName);
                            Expression equal = new Comparison(session, Comparison.EQUAL, tableExpr, joinExpr);
                            if (on == null) {
                                on = equal;
                            } else {
                                on = new ConditionAndOr(ConditionAndOr.AND, on, equal);
                            }
                        }
                    }
                }
                if (nestedJoins) {
                    top.addJoin(join, false, nested, on);
                } else {
                    top.addJoin(join, fromOuter, false, on);
                }
                last = join;
            } else {
                break;
            }
        }
        if (nested && joined) {
            top = getNested(top);
        }
        return top;
    }

    private TableFilter getNested(TableFilter n) {
        String joinTable = Constants.PREFIX_JOIN + parseIndex;
        TableFilter top = new TableFilter(session, getDualTable(true), joinTable, rightsChecked, currentSelect);
        top.addJoin(n, false, true, null);
        return top;
    }

    private Prepared parseExecute() {
        ExecuteProcedure command = new ExecuteProcedure(session);
        String procedureName = readAliasIdentifier();
        Procedure p = session.getProcedure(procedureName);
        if (p == null) {
            throw DbException.get(ErrorCode.FUNCTION_ALIAS_NOT_FOUND_1, procedureName);
        }
        command.setProcedure(p);
        if (readIf("(")) {
            for (int i = 0;; i++) {
                command.setExpression(i, readExpression());
                if (readIf(")")) {
                    break;
                }
                read(",");
            }
        }
        return command;
    }

    private DeallocateProcedure parseDeallocate() {
        readIf("PLAN");
        String procedureName = readAliasIdentifier();
        DeallocateProcedure command = new DeallocateProcedure(session);
        command.setProcedureName(procedureName);
        return command;
    }

    private Explain parseExplain() {
        Explain command = new Explain(session);
        if (readIf("ANALYZE")) {
            command.setExecuteCommand(true);
        } else {
            if (readIf("PLAN")) {
                readIf("FOR");
            }
        }
        if (isToken("SELECT") || isToken("FROM") || isToken("(")) {
            command.setCommand(parseSelect());
        } else if (readIf("DELETE")) {
            command.setCommand(parseDelete());
        } else if (readIf("UPDATE")) {
            command.setCommand(parseUpdate());
        } else if (readIf("INSERT")) {
            command.setCommand(parseInsert());
        } else if (readIf("MERGE")) {
            command.setCommand(parseMerge());
        } else if (readIf("WITH")) {
            command.setCommand(parseWith());
        } else {
            throw getSyntaxError();
        }
        return command;
    }

    private Query parseSelect() {
        int paramIndex = parameters.size();
        Query command = parseSelectUnion();
        ArrayList<Parameter> params = New.arrayList();
        for (int i = paramIndex, size = parameters.size(); i < size; i++) {
            params.add(parameters.get(i));
        }
        command.setParameterList(params);
        command.init();
        return command;
    }

    private Query parseSelectUnion() {
        int start = lastParseIndex;
        Query command = parseSelectSub();
        return parseSelectUnionExtension(command, start, false);
    }

    private Query parseSelectUnionExtension(Query command, int start, boolean unionOnly) {
        while (true) {
            if (readIf("UNION")) {
                SelectUnion union = new SelectUnion(session, command);
                if (readIf("ALL")) {
                    union.setUnionType(SelectUnion.UNION_ALL);
                } else {
                    readIf("DISTINCT");
                    union.setUnionType(SelectUnion.UNION);
                }
                union.setRight(parseSelectSub());
                command = union;
            } else if (readIf("MINUS") || readIf("EXCEPT")) {
                SelectUnion union = new SelectUnion(session, command);
                union.setUnionType(SelectUnion.EXCEPT);
                union.setRight(parseSelectSub());
                command = union;
            } else if (readIf("INTERSECT")) {
                SelectUnion union = new SelectUnion(session, command);
                union.setUnionType(SelectUnion.INTERSECT);
                union.setRight(parseSelectSub());
                command = union;
            } else {
                break;
            }
        }
        if (!unionOnly) {
            parseEndOfQuery(command);
        }
        setSQL(command, null, start);
        return command;
    }

    private void parseEndOfQuery(Query command) {
        if (readIf("ORDER")) {
            read("BY");
            Select oldSelect = currentSelect;
            if (command instanceof Select) {
                currentSelect = (Select) command;
            }
            ArrayList<SelectOrderBy> orderList = New.arrayList();
            do {
                boolean canBeNumber = true;
                if (readIf("=")) {
                    canBeNumber = false;
                }
                SelectOrderBy order = new SelectOrderBy();
                Expression expr = readExpression();
                if (canBeNumber && expr instanceof ValueExpression && expr.getType() == Value.INT) {
                    order.columnIndexExpr = expr;
                } else if (expr instanceof Parameter) {
                    recompileAlways = true;
                    order.columnIndexExpr = expr;
                } else {
                    order.expression = expr;
                }
                if (readIf("DESC")) {
                    order.descending = true;
                } else {
                    readIf("ASC");
                }
                if (readIf("NULLS")) {
                    if (readIf("FIRST")) {
                        order.nullsFirst = true;
                    } else {
                        read("LAST");
                        order.nullsLast = true;
                    }
                }
                orderList.add(order);
            } while (readIf(","));
            command.setOrder(orderList);
            currentSelect = oldSelect;
        }
        if (database.getMode().supportOffsetFetch) {
            // make sure aggregate functions will not work here
            Select temp = currentSelect;
            currentSelect = null;

            // http://sqlpro.developpez.com/SQL2008/
            if (readIf("OFFSET")) {
                command.setOffset(readExpression().optimize(session));
                if (!readIf("ROW")) {
                    read("ROWS");
                }
            }
            if (readIf("FETCH")) {
                if (!readIf("FIRST")) {
                    read("NEXT");
                }
                if (readIf("ROW")) {
                    command.setLimit(ValueExpression.get(ValueInt.get(1)));
                } else {
                    Expression limit = readExpression().optimize(session);
                    command.setLimit(limit);
                    if (!readIf("ROW")) {
                        read("ROWS");
                    }
                }
                read("ONLY");
            }

            currentSelect = temp;
        }
        if (readIf("LIMIT")) {
            Select temp = currentSelect;
            // make sure aggregate functions will not work here
            currentSelect = null;
            Expression limit = readExpression().optimize(session);
            command.setLimit(limit);
            if (readIf("OFFSET")) {
                Expression offset = readExpression().optimize(session);
                command.setOffset(offset);
            } else if (readIf(",")) {
                // MySQL: [offset, ] rowcount
                Expression offset = limit;
                limit = readExpression().optimize(session);
                command.setOffset(offset);
                command.setLimit(limit);
            }
            if (readIf("SAMPLE_SIZE")) {
                command.setSampleSize(getPositiveInt());
            }
            currentSelect = temp;
        }
        if (readIf("FOR")) {
            if (readIf("UPDATE")) {
                if (readIf("OF")) {
                    do {
                        readIdentifierWithSchema();
                    } while (readIf(","));
                } else if (readIf("NOWAIT")) {
                    // TODO parser: select for update nowait: should not wait
                } else if (readIf("WITH")) {
                    // Hibernate / Derby support
                    read("RR");
                }
                command.setForUpdate(true);
            } else if (readIf("READ") || readIf("FETCH")) {
                read("ONLY");
                if (readIf("WITH")) {
                    read("RS");
                }
            }
        }
    }

    private Query parseSelectSub() {
        if (readIf("(")) {
            Query command = parseSelectUnion();
            read(")");
            return command;
        }
        Select select = parseSelectSimple();
        return select;
    }

    private void parseSelectSimpleFromPart(Select command) {
        do {
            TableFilter filter = readTableFilter(false);
            parseJoinTableFilter(filter, command);
        } while (readIf(","));
    }

    private void parseJoinTableFilter(TableFilter top, final Select command) {
        top = readJoin(top, command, false, top.isJoinOuter());
        command.addTableFilter(top, true);
        boolean isOuter = false;
        while (true) {
            TableFilter n = top.getNestedJoin();
            if (n != null) {
                n.visit(new TableFilterVisitor() {
                    @Override
                    public void accept(TableFilter f) {
                        command.addTableFilter(f, false);
                    }
                });
            }
            TableFilter join = top.getJoin();
            if (join == null) {
                break;
            }
            isOuter = isOuter | join.isJoinOuter();
            if (isOuter) {
                command.addTableFilter(join, false);
            } else {
                // make flat so the optimizer can work better
                Expression on = join.getJoinCondition();
                if (on != null) {
                    command.addCondition(on);
                }
                join.removeJoinCondition();
                top.removeJoin();
                command.addTableFilter(join, true);
            }
            top = join;
        }
    }

    private void parseSelectSimpleSelectPart(Select command) {
        Select temp = currentSelect;
        // make sure aggregate functions will not work in TOP and LIMIT
        currentSelect = null;
        if (readIf("TOP")) {
            // can't read more complex expressions here because
            // SELECT TOP 1 +? A FROM TEST could mean
            // SELECT TOP (1+?) A FROM TEST or
            // SELECT TOP 1 (+?) AS A FROM TEST
            Expression limit = readTerm().optimize(session);
            command.setLimit(limit);
        } else if (readIf("LIMIT")) {
            Expression offset = readTerm().optimize(session);
            command.setOffset(offset);
            Expression limit = readTerm().optimize(session);
            command.setLimit(limit);
        }
        currentSelect = temp;
        if (readIf("DISTINCT")) {
            command.setDistinct(true);
        } else {
            readIf("ALL");
        }
        ArrayList<Expression> expressions = New.arrayList();
        do {
            if (readIf("*")) {
                expressions.add(new Wildcard(null, null));
            } else {
                Expression expr = readExpression();
                if (readIf("AS") || currentTokenType == IDENTIFIER) {
                    String alias = readAliasIdentifier();
                    boolean aliasColumnName = database.getSettings().aliasColumnName;
                    aliasColumnName |= database.getMode().aliasColumnName;
                    expr = new Alias(expr, alias, aliasColumnName);
                }
                expressions.add(expr);
            }
        } while (readIf(","));
        command.setExpressions(expressions);
    }

    private Select parseSelectSimple() {
        boolean fromFirst;
        if (readIf("SELECT")) {
            fromFirst = false;
        } else if (readIf("FROM")) {
            fromFirst = true;
        } else {
            throw getSyntaxError();
        }
        Select command = createSelect(session);
        int start = lastParseIndex;
        Select oldSelect = currentSelect;
        currentSelect = command;
        currentPrepared = command;
        if (fromFirst) {
            parseSelectSimpleFromPart(command);
            read("SELECT");
            parseSelectSimpleSelectPart(command);
        } else {
            parseSelectSimpleSelectPart(command);
            if (!readIf("FROM")) {
                // select without FROM: convert to SELECT ... FROM
                // SYSTEM_RANGE(1,1)
                Table dual = getDualTable(false);
                TableFilter filter = new TableFilter(session, dual, null, rightsChecked, currentSelect);
                command.addTableFilter(filter, true);
            } else {
                parseSelectSimpleFromPart(command);
            }
        }
        if (readIf("WHERE")) {
            Expression condition = readExpression();
            command.addCondition(condition);
        }
        // the group by is read for the outer select (or not a select)
        // so that columns that are not grouped can be used
        currentSelect = oldSelect;
        if (readIf("GROUP")) {
            read("BY");
            command.setGroupQuery();
            ArrayList<Expression> list = New.arrayList();
            do {
                Expression expr = readExpression();
                list.add(expr);
            } while (readIf(","));
            command.setGroupBy(list);
        }
        currentSelect = command;
        if (readIf("HAVING")) {
            command.setGroupQuery();
            Expression condition = readExpression();
            command.setHaving(condition);
        }
        command.setParameterList(parameters);
        currentSelect = oldSelect;
        setSQL(command, "SELECT", start);
        return command;
    }

    private Table getDualTable(boolean noColumns) {
        Schema main = database.findSchema(Constants.SCHEMA_MAIN);
        Expression one = ValueExpression.get(ValueLong.get(1));
        return new RangeTable(main, one, one, noColumns);
    }

    private void setSQL(Prepared command, String start, int startIndex) {
        String sql = originalSQL.substring(startIndex, lastParseIndex).trim();
        if (start != null) {
            sql = start + " " + sql;
        }
        command.setSQL(sql);
    }

    protected Expression readExpression() {
        Expression r = readAnd();
        while (readIf("OR")) {
            r = new ConditionAndOr(ConditionAndOr.OR, r, readAnd());
        }
        return r;
    }

    private Expression readAnd() {
        Expression r = readCondition();
        while (readIf("AND")) {
            r = new ConditionAndOr(ConditionAndOr.AND, r, readCondition());
        }
        return r;
    }

    private Expression readCondition() {
        if (readIf("NOT")) {
            return new ConditionNot(readCondition());
        }
        if (readIf("EXISTS")) {
            read("(");
            Query query = parseSelect();
            // can not reduce expression because it might be a union except
            // query with distinct
            read(")");
            return new ConditionExists(query);
        }
        Expression r = readConcat();
        while (true) {
            // special case: NOT NULL is not part of an expression (as in CREATE
            // TABLE TEST(ID INT DEFAULT 0 NOT NULL))
            int backup = parseIndex;
            boolean not = false;
            if (readIf("NOT")) {
                not = true;
                if (isToken("NULL")) {
                    // this really only works for NOT NULL!
                    parseIndex = backup;
                    currentToken = "NOT";
                    break;
                }
            }
            if (readIf("LIKE")) {
                Expression b = readConcat();
                Expression esc = null;
                if (readIf("ESCAPE")) {
                    esc = readConcat();
                }
                recompileAlways = true;
                r = new CompareLike(database, r, b, esc, false);
            } else if (readIf("REGEXP")) {
                Expression b = readConcat();
                r = new CompareLike(database, r, b, null, true);
            } else if (readIf("IS")) {
                if (readIf("NOT")) {
                    if (readIf("NULL")) {
                        r = new Comparison(session, Comparison.IS_NOT_NULL, r, null);
                    } else if (readIf("DISTINCT")) {
                        read("FROM");
                        r = new Comparison(session, Comparison.EQUAL_NULL_SAFE, r, readConcat());
                    } else {
                        r = new Comparison(session, Comparison.NOT_EQUAL_NULL_SAFE, r, readConcat());
                    }
                } else if (readIf("NULL")) {
                    r = new Comparison(session, Comparison.IS_NULL, r, null);
                } else if (readIf("DISTINCT")) {
                    read("FROM");
                    r = new Comparison(session, Comparison.NOT_EQUAL_NULL_SAFE, r, readConcat());
                } else {
                    r = new Comparison(session, Comparison.EQUAL_NULL_SAFE, r, readConcat());
                }
            } else if (readIf("IN")) {
                read("(");
                if (readIf(")")) {
                    r = ValueExpression.get(ValueBoolean.get(false));
                } else {
                    if (isSelect()) {
                        Query query = parseSelect();
                        r = new ConditionInSelect(database, r, query, false, Comparison.EQUAL);
                    } else {
                        ArrayList<Expression> v = New.arrayList();
                        Expression last;
                        do {
                            last = readExpression();
                            v.add(last);
                        } while (readIf(","));
                        if (v.size() == 1 && (last instanceof Subquery)) {
                            Subquery s = (Subquery) last;
                            Query q = s.getQuery();
                            r = new ConditionInSelect(database, r, q, false, Comparison.EQUAL);
                        } else {
                            r = new ConditionIn(database, r, v);
                        }
                    }
                    read(")");
                }
            } else if (readIf("BETWEEN")) {
                Expression low = readConcat();
                read("AND");
                Expression high = readConcat();
                Expression condLow = new Comparison(session, Comparison.SMALLER_EQUAL, low, r);
                Expression condHigh = new Comparison(session, Comparison.BIGGER_EQUAL, high, r);
                r = new ConditionAndOr(ConditionAndOr.AND, condLow, condHigh);
            } else {
                int compareType = getCompareType(currentTokenType);
                if (compareType < 0) {
                    break;
                }
                read();
                if (readIf("ALL")) {
                    read("(");
                    Query query = parseSelect();
                    r = new ConditionInSelect(database, r, query, true, compareType);
                    read(")");
                } else if (readIf("ANY") || readIf("SOME")) {
                    read("(");
                    Query query = parseSelect();
                    r = new ConditionInSelect(database, r, query, false, compareType);
                    read(")");
                } else {
                    Expression right = readConcat();
                    if (readIf("(") && readIf("+") && readIf(")")) {
                        // support for a subset of old-fashioned Oracle outer
                        // join with (+)
                        if (r instanceof ExpressionColumn && right instanceof ExpressionColumn) {
                            ExpressionColumn leftCol = (ExpressionColumn) r;
                            ExpressionColumn rightCol = (ExpressionColumn) right;
                            ArrayList<TableFilter> filters = currentSelect.getTopFilters();
                            for (TableFilter f : filters) {
                                while (f != null) {
                                    leftCol.mapColumns(f, 0);
                                    rightCol.mapColumns(f, 0);
                                    f = f.getJoin();
                                }
                            }
                            TableFilter leftFilter = leftCol.getTableFilter();
                            TableFilter rightFilter = rightCol.getTableFilter();
                            r = new Comparison(session, compareType, r, right);
                            if (leftFilter != null && rightFilter != null) {
                                int idx = filters.indexOf(rightFilter);
                                if (idx >= 0) {
                                    filters.remove(idx);
                                    leftFilter.addJoin(rightFilter, true, false, r);
                                } else {
                                    rightFilter.mapAndAddFilter(r);
                                }
                                r = ValueExpression.get(ValueBoolean.get(true));
                            }
                        }
                    } else {
                        r = new Comparison(session, compareType, r, right);
                    }
                }
            }
            if (not) {
                r = new ConditionNot(r);
            }
        }
        return r;
    }

    private Expression readConcat() {
        Expression r = readSum();
        while (true) {
            if (readIf("||")) {
                r = new Operation(Operation.CONCAT, r, readSum());
            } else if (readIf("~")) {
                if (readIf("*")) {
                    Function function = Function.getFunction(database, "CAST");
                    function.setDataType(new Column("X", Value.STRING_IGNORECASE));
                    function.setParameter(0, r);
                    r = function;
                }
                r = new CompareLike(database, r, readSum(), null, true);
            } else if (readIf("!~")) {
                if (readIf("*")) {
                    Function function = Function.getFunction(database, "CAST");
                    function.setDataType(new Column("X", Value.STRING_IGNORECASE));
                    function.setParameter(0, r);
                    r = function;
                }
                r = new ConditionNot(new CompareLike(database, r, readSum(), null, true));
            } else {
                return r;
            }
        }
    }

    private Expression readSum() {
        Expression r = readFactor();
        while (true) {
            if (readIf("+")) {
                r = new Operation(Operation.PLUS, r, readFactor());
            } else if (readIf("-")) {
                r = new Operation(Operation.MINUS, r, readFactor());
            } else {
                return r;
            }
        }
    }

    private Expression readFactor() {
        Expression r = readTerm();
        while (true) {
            if (readIf("*")) {
                r = new Operation(Operation.MULTIPLY, r, readTerm());
            } else if (readIf("/")) {
                r = new Operation(Operation.DIVIDE, r, readTerm());
            } else if (readIf("%")) {
                r = new Operation(Operation.MODULUS, r, readTerm());
            } else {
                return r;
            }
        }
    }

    private Expression readAggregate(int aggregateType) {
        if (currentSelect == null) {
            throw getSyntaxError();
        }
        currentSelect.setGroupQuery();
        Expression r;
        if (aggregateType == Aggregate.COUNT) {
            if (readIf("*")) {
                r = new Aggregate(Aggregate.COUNT_ALL, null, currentSelect, false);
            } else {
                boolean distinct = readIf("DISTINCT");
                Expression on = readExpression();
                if (on instanceof Wildcard && !distinct) {
                    // PostgreSQL compatibility: count(t.*)
                    r = new Aggregate(Aggregate.COUNT_ALL, null, currentSelect, false);
                } else {
                    r = new Aggregate(Aggregate.COUNT, on, currentSelect, distinct);
                }
            }
        } else if (aggregateType == Aggregate.GROUP_CONCAT) {
            boolean distinct = readIf("DISTINCT");
            Aggregate agg = new Aggregate(Aggregate.GROUP_CONCAT, readExpression(), currentSelect, distinct);
            if (readIf("ORDER")) {
                read("BY");
                agg.setOrder(parseSimpleOrderList());
            }
            if (readIf("SEPARATOR")) {
                agg.setSeparator(readExpression());
            }
            r = agg;
        } else {
            boolean distinct = readIf("DISTINCT");
            r = new Aggregate(aggregateType, readExpression(), currentSelect, distinct);
        }
        read(")");
        return r;
    }

    private ArrayList<SelectOrderBy> parseSimpleOrderList() {
        ArrayList<SelectOrderBy> orderList = New.arrayList();
        do {
            SelectOrderBy order = new SelectOrderBy();
            Expression expr = readExpression();
            order.expression = expr;
            if (readIf("DESC")) {
                order.descending = true;
            } else {
                readIf("ASC");
            }
            orderList.add(order);
        } while (readIf(","));
        return orderList;
    }

    private JavaFunction readJavaFunction(Schema schema, String functionName) {
        FunctionAlias functionAlias = null;
        if (schema != null) {
            functionAlias = schema.findFunction(functionName);
        } else {
            functionAlias = findFunctionAlias(session.getCurrentSchemaName(), functionName);
        }
        if (functionAlias == null) {
            throw DbException.get(ErrorCode.FUNCTION_NOT_FOUND_1, functionName);
        }
        Expression[] args;
        ArrayList<Expression> argList = New.arrayList();
        int numArgs = 0;
        while (!readIf(")")) {
            if (numArgs++ > 0) {
                read(",");
            }
            argList.add(readExpression());
        }
        args = new Expression[numArgs];
        argList.toArray(args);
        JavaFunction func = new JavaFunction(functionAlias, args);
        return func;
    }

    private JavaAggregate readJavaAggregate(UserAggregate aggregate) {
        ArrayList<Expression> params = New.arrayList();
        do {
            params.add(readExpression());
        } while (readIf(","));
        read(")");
        Expression[] list = new Expression[params.size()];
        params.toArray(list);
        JavaAggregate agg = new JavaAggregate(aggregate, list, currentSelect);
        currentSelect.setGroupQuery();
        return agg;
    }

    private int getAggregateType(String name) {
        if (!identifiersToUpper) {
            // if not yet converted to uppercase, do it now
            name = StringUtils.toUpperEnglish(name);
        }
        return Aggregate.getAggregateType(name);
    }

    private Expression readFunction(Schema schema, String name) {
        if (schema != null) {
            return readJavaFunction(schema, name);
        }
        int agg = getAggregateType(name);
        if (agg >= 0) {
            return readAggregate(agg);
        }
        Function function = Function.getFunction(database, name);
        if (function == null) {
            UserAggregate aggregate = database.findAggregate(name);
            if (aggregate != null) {
                return readJavaAggregate(aggregate);
            }
            return readJavaFunction(null, name);
        }
        switch (function.getFunctionType()) {
        case Function.CAST: {
            function.setParameter(0, readExpression());
            read("AS");
            Column type = parseColumnWithType(null);
            function.setDataType(type);
            read(")");
            break;
        }
        case Function.CONVERT: {
            if (database.getMode().swapConvertFunctionParameters) {
                Column type = parseColumnWithType(null);
                function.setDataType(type);
                read(",");
                function.setParameter(0, readExpression());
                read(")");
            } else {
                function.setParameter(0, readExpression());
                read(",");
                Column type = parseColumnWithType(null);
                function.setDataType(type);
                read(")");
            }
            break;
        }
        case Function.EXTRACT: {
            function.setParameter(0, ValueExpression.get(ValueString.get(currentToken)));
            read();
            read("FROM");
            function.setParameter(1, readExpression());
            read(")");
            break;
        }
        case Function.DATE_ADD:
        case Function.DATE_DIFF: {
            if (Function.isDatePart(currentToken)) {
                function.setParameter(0, ValueExpression.get(ValueString.get(currentToken)));
                read();
            } else {
                function.setParameter(0, readExpression());
            }
            read(",");
            function.setParameter(1, readExpression());
            read(",");
            function.setParameter(2, readExpression());
            read(")");
            break;
        }
        case Function.SUBSTRING: {
            function.setParameter(0, readExpression());
            if (!readIf(",")) {
                read("FROM");
            }
            function.setParameter(1, readExpression());
            if (readIf("FOR") || readIf(",")) {
                function.setParameter(2, readExpression());
            }
            read(")");
            break;
        }
        case Function.POSITION: {
            // can't read expression because IN would be read too early
            function.setParameter(0, readConcat());
            if (!readIf(",")) {
                read("IN");
            }
            function.setParameter(1, readExpression());
            read(")");
            break;
        }
        case Function.TRIM: {
            Expression space = null;
            if (readIf("LEADING")) {
                function = Function.getFunction(database, "LTRIM");
                if (!readIf("FROM")) {
                    space = readExpression();
                    read("FROM");
                }
            } else if (readIf("TRAILING")) {
                function = Function.getFunction(database, "RTRIM");
                if (!readIf("FROM")) {
                    space = readExpression();
                    read("FROM");
                }
            } else if (readIf("BOTH")) {
                if (!readIf("FROM")) {
                    space = readExpression();
                    read("FROM");
                }
            }
            Expression p0 = readExpression();
            if (readIf(",")) {
                space = readExpression();
            } else if (readIf("FROM")) {
                space = p0;
                p0 = readExpression();
            }
            function.setParameter(0, p0);
            if (space != null) {
                function.setParameter(1, space);
            }
            read(")");
            break;
        }
        case Function.TABLE:
        case Function.TABLE_DISTINCT: {
            int i = 0;
            ArrayList<Column> columns = New.arrayList();
            do {
                String columnName = readAliasIdentifier();
                Column column = parseColumnWithType(columnName);
                columns.add(column);
                read("=");
                function.setParameter(i, readExpression());
                i++;
            } while (readIf(","));
            read(")");
            TableFunction tf = (TableFunction) function;
            tf.setColumns(columns);
            break;
        }
        case Function.ROW_NUMBER:
            read(")");
            read("OVER");
            read("(");
            read(")");
            return new Rownum(currentSelect == null ? currentPrepared : currentSelect);
        default:
            if (!readIf(")")) {
                int i = 0;
                do {
                    function.setParameter(i++, readExpression());
                } while (readIf(","));
                read(")");
            }
        }
        function.doneWithParameters();
        return function;
    }

    private Function readFunctionWithoutParameters(String name) {
        if (readIf("(")) {
            read(")");
        }
        Function function = Function.getFunction(database, name);
        function.doneWithParameters();
        return function;
    }

    private Expression readWildcardOrSequenceValue(String schema, String objectName) {
        if (readIf("*")) {
            return new Wildcard(schema, objectName);
        }
        if (schema == null) {
            schema = session.getCurrentSchemaName();
        }
        if (readIf("NEXTVAL")) {
            Sequence sequence = findSequence(schema, objectName);
            if (sequence != null) {
                return new SequenceValue(sequence);
            }
        } else if (readIf("CURRVAL")) {
            Sequence sequence = findSequence(schema, objectName);
            if (sequence != null) {
                Function function = Function.getFunction(database, "CURRVAL");
                function.setParameter(0, ValueExpression.get(ValueString.get(sequence.getSchema().getName())));
                function.setParameter(1, ValueExpression.get(ValueString.get(sequence.getName())));
                function.doneWithParameters();
                return function;
            }
        }
        return null;
    }

    private Expression readTermObjectDot(String objectName) {
        Expression expr = readWildcardOrSequenceValue(null, objectName);
        if (expr != null) {
            return expr;
        }
        String name = readColumnIdentifier();
        Schema s = database.findSchema(objectName);
        if (s != null && readIf("(")) {
            // only if the token before the dot is a valid schema name,
            // otherwise the old style Oracle outer join doesn't work:
            // t.x = t2.x(+)
            return readFunction(s, name);
        } else if (readIf(".")) {
            String schema = objectName;
            objectName = name;
            expr = readWildcardOrSequenceValue(schema, objectName);
            if (expr != null) {
                return expr;
            }
            name = readColumnIdentifier();
            if (readIf("(")) {
                String databaseName = schema;
                if (!equalsToken(database.getShortName(), databaseName)) {
                    throw DbException.get(ErrorCode.DATABASE_NOT_FOUND_1, databaseName);
                }
                schema = objectName;
                return readFunction(database.getSchema(schema), name);
            } else if (readIf(".")) {
                String databaseName = schema;
                schema = objectName;
                objectName = name;
                expr = readWildcardOrSequenceValue(schema, objectName);
                if (expr != null) {
                    return expr;
                }
                name = readColumnIdentifier();
                if (readIf(".")) {
                    if (!equalsToken(database.getShortName(), databaseName)) {
                        throw DbException.get(ErrorCode.DATABASE_NOT_FOUND_1, databaseName);
                    }
                    String columnFamilyName = name;
                    name = readColumnIdentifier();
                    return new ExpressionColumn(database, schema, objectName, columnFamilyName, name);
                } else {
                    String columnFamilyName = objectName;
                    objectName = schema;
                    schema = databaseName;
                    return new ExpressionColumn(database, schema, objectName, columnFamilyName, name);
                }
            } else {
                String columnFamilyName = objectName;
                objectName = schema;
                schema = null;
                return new ExpressionColumn(database, schema, objectName, columnFamilyName, name);
            }
        }
        return new ExpressionColumn(database, null, null, objectName, name);
    }

    private Expression readTerm() {
        Expression r;
        switch (currentTokenType) {
        case AT:
            read();
            r = new Variable(session, readAliasIdentifier());
            if (readIf(":=")) {
                Expression value = readExpression();
                Function function = Function.getFunction(database, "SET");
                function.setParameter(0, r);
                function.setParameter(1, value);
                r = function;
            }
            break;
        case PARAMETER:
            // there must be no space between ? and the number
            boolean indexed = Character.isDigit(sqlCommandChars[parseIndex]);
            read();
            Parameter p;
            if (indexed && currentTokenType == VALUE && currentValue.getType() == Value.INT) {
                if (indexedParameterList == null) {
                    if (parameters == null) {
                        // this can occur when parsing expressions only (for example check constraints)
                        throw getSyntaxError();
                    } else if (parameters.size() > 0) {
                        throw DbException.get(ErrorCode.CANNOT_MIX_INDEXED_AND_UNINDEXED_PARAMS);
                    }
                    indexedParameterList = New.arrayList();
                }
                int index = currentValue.getInt() - 1;
                if (index < 0 || index >= Constants.MAX_PARAMETER_INDEX) {
                    throw DbException.getInvalidValueException("parameter index", index);
                }
                if (indexedParameterList.size() <= index) {
                    indexedParameterList.ensureCapacity(index + 1);
                    while (indexedParameterList.size() <= index) {
                        indexedParameterList.add(null);
                    }
                }
                p = indexedParameterList.get(index);
                if (p == null) {
                    p = new Parameter(index);
                    indexedParameterList.set(index, p);
                }
                read();
            } else {
                if (indexedParameterList != null) {
                    throw DbException.get(ErrorCode.CANNOT_MIX_INDEXED_AND_UNINDEXED_PARAMS);
                }
                p = new Parameter(parameters.size());
            }
            parameters.add(p);
            r = p;
            break;
        case KEYWORD:
            if (isToken("SELECT") || isToken("FROM")) {
                Query query = parseSelect();
                r = new Subquery(query);
            } else {
                throw getSyntaxError();
            }
            break;
        case IDENTIFIER:
            String name = currentToken;
            if (currentTokenQuoted) {
                read();
                if (readIf("(")) {
                    r = readFunction(null, name);
                } else if (readIf(".")) {
                    r = readTermObjectDot(name);
                } else {
                    r = new ExpressionColumn(database, null, null, name);
                }
            } else {
                read();
                if (readIf(".")) {
                    r = readTermObjectDot(name);
                } else if (equalsToken("CASE", name)) {
                    // CASE must be processed before (,
                    // otherwise CASE(3) would be a function call, which it is
                    // not
                    if (isToken("WHEN")) {
                        r = readWhen(null);
                    } else {
                        Expression left = readExpression();
                        r = readWhen(left);
                    }
                } else if (readIf("(")) {
                    r = readFunction(null, name);
                } else if (equalsToken("CURRENT_USER", name)) {
                    r = readFunctionWithoutParameters("USER");
                } else if (equalsToken("CURRENT", name)) {
                    if (readIf("TIMESTAMP")) {
                        r = readFunctionWithoutParameters("CURRENT_TIMESTAMP");
                    } else if (readIf("TIME")) {
                        r = readFunctionWithoutParameters("CURRENT_TIME");
                    } else if (readIf("DATE")) {
                        r = readFunctionWithoutParameters("CURRENT_DATE");
                    } else {
                        r = new ExpressionColumn(database, null, null, name);
                    }
                } else if (equalsToken("NEXT", name) && readIf("VALUE")) {
                    read("FOR");
                    Sequence sequence = readSequence();
                    r = new SequenceValue(sequence);
                } else if (currentTokenType == VALUE && currentValue.getType() == Value.STRING) {
                    if (equalsToken("DATE", name)) {
                        String date = currentValue.getString();
                        read();
                        r = ValueExpression.get(ValueDate.parse(date));
                    } else if (equalsToken("TIME", name)) {
                        String time = currentValue.getString();
                        read();
                        r = ValueExpression.get(ValueTime.parse(time));
                    } else if (equalsToken("TIMESTAMP", name)) {
                        String timestamp = currentValue.getString();
                        read();
                        r = ValueExpression.get(ValueTimestamp.parse(timestamp));
                    } else if (equalsToken("X", name)) {
                        read();
                        byte[] buffer = StringUtils.convertHexToBytes(currentValue.getString());
                        r = ValueExpression.get(ValueBytes.getNoCopy(buffer));
                    } else if (equalsToken("E", name)) {
                        String text = currentValue.getString();
                        // the PostgreSQL ODBC driver uses
                        // LIKE E'PROJECT\\_DATA' instead of LIKE 'PROJECT\_DATA'
                        // N: SQL-92 "National Language" strings
                        text = StringUtils.replaceAll(text, "\\\\", "\\");
                        read();
                        r = ValueExpression.get(ValueString.get(text));
                    } else if (equalsToken("N", name)) {
                        // SQL-92 "National Language" strings
                        String text = currentValue.getString();
                        read();
                        r = ValueExpression.get(ValueString.get(text));
                    } else {
                        r = new ExpressionColumn(database, null, null, name);
                    }
                } else {
                    r = new ExpressionColumn(database, null, null, name);
                }
            }
            break;
        case MINUS:
            read();
            if (currentTokenType == VALUE) {
                r = ValueExpression.get(currentValue.negate());
                if (r.getType() == Value.LONG && r.getValue(session).getLong() == Integer.MIN_VALUE) {
                    // convert Integer.MIN_VALUE to type 'int'
                    // (Integer.MAX_VALUE+1 is of type 'long')
                    r = ValueExpression.get(ValueInt.get(Integer.MIN_VALUE));
                } else if (r.getType() == Value.DECIMAL
                        && r.getValue(session).getBigDecimal().compareTo(ValueLong.MIN_BD) == 0) {
                    // convert Long.MIN_VALUE to type 'long'
                    // (Long.MAX_VALUE+1 is of type 'decimal')
                    r = ValueExpression.get(ValueLong.get(Long.MIN_VALUE));
                }
                read();
            } else {
                r = new Operation(Operation.NEGATE, readTerm(), null);
            }
            break;
        case PLUS:
            read();
            r = readTerm();
            break;
        case OPEN:
            read();
            if (readIf(")")) {
                r = new ExpressionList(new Expression[0]);
            } else {
                r = readExpression();
                if (readIf(",")) {
                    ArrayList<Expression> list = New.arrayList();
                    list.add(r);
                    while (!readIf(")")) {
                        r = readExpression();
                        list.add(r);
                        if (!readIf(",")) {
                            read(")");
                            break;
                        }
                    }
                    Expression[] array = new Expression[list.size()];
                    list.toArray(array);
                    r = new ExpressionList(array);
                } else {
                    read(")");
                }
            }
            break;
        case TRUE:
            read();
            r = ValueExpression.get(ValueBoolean.get(true));
            break;
        case FALSE:
            read();
            r = ValueExpression.get(ValueBoolean.get(false));
            break;
        case CURRENT_TIME:
            read();
            r = readFunctionWithoutParameters("CURRENT_TIME");
            break;
        case CURRENT_DATE:
            read();
            r = readFunctionWithoutParameters("CURRENT_DATE");
            break;
        case CURRENT_TIMESTAMP: {
            Function function = Function.getFunction(database, "CURRENT_TIMESTAMP");
            read();
            if (readIf("(")) {
                if (!readIf(")")) {
                    function.setParameter(0, readExpression());
                    read(")");
                }
            }
            function.doneWithParameters();
            r = function;
            break;
        }
        case ROWNUM:
            read();
            if (readIf("(")) {
                read(")");
            }
            r = new Rownum(currentSelect == null ? currentPrepared : currentSelect);
            break;
        case NULL:
            read();
            r = ValueExpression.getNull();
            break;
        case VALUE:
            r = ValueExpression.get(currentValue);
            read();
            break;
        default:
            throw getSyntaxError();
        }
        if (readIf("[")) {
            Function function = Function.getFunction(database, "ARRAY_GET");
            function.setParameter(0, r);
            r = readExpression();
            r = new Operation(Operation.PLUS, r, ValueExpression.get(ValueInt.get(1)));
            function.setParameter(1, r);
            r = function;
            read("]");
        }
        if (readIf("::")) {
            // PostgreSQL compatibility
            if (readIf("REGCLASS")) {
                FunctionAlias f = findFunctionAlias(Constants.SCHEMA_MAIN, "PG_GET_OID");
                if (f == null) {
                    throw getSyntaxError();
                }
                Expression[] args = { r };
                JavaFunction func = new JavaFunction(f, args);
                r = func;
            } else {
                Column col = parseColumnWithType(null);
                Function function = Function.getFunction(database, "CAST");
                function.setDataType(col);
                function.setParameter(0, r);
                r = function;
            }
        }
        return r;
    }

    private Expression readWhen(Expression left) {
        if (readIf("END")) {
            readIf("CASE");
            return ValueExpression.getNull();
        }
        if (readIf("ELSE")) {
            Expression elsePart = readExpression();
            read("END");
            readIf("CASE");
            return elsePart;
        }
        readIf("WHEN");
        Expression when = readExpression();
        if (left != null) {
            when = new Comparison(session, Comparison.EQUAL, left, when);
        }
        read("THEN");
        Expression then = readExpression();
        Expression elsePart = readWhen(left);
        Function function = Function.getFunction(session.getDatabase(), "CASEWHEN");
        function.setParameter(0, when);
        function.setParameter(1, then);
        function.setParameter(2, elsePart);
        function.doneWithParameters();
        return function;
    }

    private int getPositiveInt() {
        int v = getInt();
        if (v < 0) {
            throw DbException.getInvalidValueException("positive integer", v);
        }
        return v;
    }

    private int getInt() {
        boolean minus = false;
        if (currentTokenType == MINUS) {
            minus = true;
            read();
        } else if (currentTokenType == PLUS) {
            read();
        }
        if (currentTokenType != VALUE || currentValue.getType() != Value.INT) {
            throw DbException.getSyntaxError(sqlCommand, parseIndex, "integer");
        }
        int i = currentValue.getInt();
        read();
        return minus ? -i : i;
    }

    private long readLong() {
        boolean minus = false;
        if (currentTokenType == MINUS) {
            minus = true;
            read();
        }
        if (currentTokenType != VALUE || (currentValue.getType() != Value.INT && currentValue.getType() != Value.LONG)) {
            throw DbException.getSyntaxError(sqlCommand, parseIndex, "long");
        }
        long i = currentValue.getLong();
        read();
        return minus ? -i : i;
    }

    private boolean readBooleanSetting() {
        if (currentTokenType == VALUE) {
            boolean result = currentValue.getBoolean().booleanValue();
            read();
            return result;
        }
        if (readIf("TRUE") || readIf("ON")) {
            return true;
        } else if (readIf("FALSE") || readIf("OFF")) {
            return false;
        } else {
            throw getSyntaxError();
        }
    }

    protected String readString() {
        Expression expr = readExpression().optimize(session);
        if (!(expr instanceof ValueExpression)) {
            throw DbException.getSyntaxError(sqlCommand, parseIndex, "string");
        }
        String s = expr.getValue(session).getString();
        return s;
    }

    private String readIdentifierWithSchema(String defaultSchemaName) {
        if (currentTokenType != IDENTIFIER) {
            throw DbException.getSyntaxError(sqlCommand, parseIndex, "identifier");
        }
        String s = currentToken;
        read();
        schemaName = defaultSchemaName;
        if (readIf(".")) {
            schemaName = s;
            if (currentTokenType != IDENTIFIER) {
                throw DbException.getSyntaxError(sqlCommand, parseIndex, "identifier");
            }
            s = currentToken;
            read();
        }
        if (equalsToken(".", currentToken)) {
            if (equalsToken(schemaName, database.getShortName())) {
                read(".");
                schemaName = s;
                if (currentTokenType != IDENTIFIER) {
                    throw DbException.getSyntaxError(sqlCommand, parseIndex, "identifier");
                }
                s = currentToken;
                read();
            }
        }
        return s;
    }

    protected String readIdentifierWithSchema() {
        return readIdentifierWithSchema(session.getCurrentSchemaName());
    }

    private String readAliasIdentifier() {
        return readColumnIdentifier();
    }

    protected String readUniqueIdentifier() {
        return readColumnIdentifier();
    }

    protected String readColumnIdentifier() {
        if (currentTokenType != IDENTIFIER) {
            throw DbException.getSyntaxError(sqlCommand, parseIndex, "identifier");
        }
        String s = currentToken;
        read();
        return s;
    }

    private String readColumnIdentifier(Table table) {
        if (currentTokenType != IDENTIFIER) {
            throw DbException.getSyntaxError(sqlCommand, parseIndex, "identifier");
        }
        String s = currentToken;
        read();
        if (table.supportsColumnFamily() && readIf(".")) {
            if (currentTokenType != IDENTIFIER) {
                throw DbException.getSyntaxError(sqlCommand, parseIndex, "identifier");
            }
            s = s + "." + currentToken;

            read();
        }
        return s;
    }

    protected void read(String expected) {
        if (currentTokenQuoted || !equalsToken(expected, currentToken)) {
            addExpected(expected);
            throw getSyntaxError();
        }
        read();
    }

    protected boolean readIf(String token) {
        if (!currentTokenQuoted && equalsToken(token, currentToken)) {
            read();
            return true;
        }
        addExpected(token);
        return false;
    }

    private boolean isToken(String token) {
        boolean result = equalsToken(token, currentToken) && !currentTokenQuoted;
        if (result) {
            return true;
        }
        addExpected(token);
        return false;
    }

    protected boolean equalsToken(String a, String b) {
        if (a == null) {
            return b == null;
        } else if (a.equals(b)) {
            return true;
        } else if (!identifiersToUpper && a.equalsIgnoreCase(b)) {
            return true;
        }
        return false;
    }

    private void addExpected(String token) {
        if (expectedList != null) {
            expectedList.add(token);
        }
    }

    protected void read() {
        currentTokenQuoted = false;
        if (expectedList != null) {
            expectedList.clear();
        }
        int[] types = characterTypes;
        lastParseIndex = parseIndex;
        int i = parseIndex;
        int type = types[i];
        while (type == 0) {
            type = types[++i];
        }
        int start = i;
        char[] chars = sqlCommandChars;
        char c = chars[i++];
        currentToken = "";
        switch (type) {
        case CHAR_NAME:
            while (true) {
                type = types[i];
                if (type != CHAR_NAME && type != CHAR_VALUE) {
                    break;
                }
                i++;
            }
            currentToken = StringUtils.fromCacheOrNew(sqlCommand.substring(start, i));
            currentTokenType = getTokenType(currentToken);
            parseIndex = i;
            return;
        case CHAR_QUOTED: {
            String result = null;
            while (true) {
                for (int begin = i;; i++) {
                    if (chars[i] == '\"') {
                        if (result == null) {
                            result = sqlCommand.substring(begin, i);
                        } else {
                            result += sqlCommand.substring(begin - 1, i);
                        }
                        break;
                    }
                }
                if (chars[++i] != '\"') {
                    break;
                }
                i++;
            }
            currentToken = StringUtils.fromCacheOrNew(result);
            parseIndex = i;
            currentTokenQuoted = true;
            currentTokenType = IDENTIFIER;
            return;
        }
        case CHAR_SPECIAL_2:
            if (types[i] == CHAR_SPECIAL_2) {
                i++;
            }
            currentToken = sqlCommand.substring(start, i);
            currentTokenType = getSpecialType(currentToken);
            parseIndex = i;
            return;
        case CHAR_SPECIAL_1:
            currentToken = sqlCommand.substring(start, i);
            currentTokenType = getSpecialType(currentToken);
            parseIndex = i;
            return;
        case CHAR_VALUE:
            if (c == '0' && chars[i] == 'X') {
                // hex number
                long number = 0;
                start += 2;
                i++;
                while (true) {
                    c = chars[i];
                    if ((c < '0' || c > '9') && (c < 'A' || c > 'F')) {
                        checkLiterals(false);
                        currentValue = ValueInt.get((int) number);
                        currentTokenType = VALUE;
                        currentToken = "0";
                        parseIndex = i;
                        return;
                    }
                    number = (number << 4) + c - (c >= 'A' ? ('A' - 0xa) : ('0'));
                    if (number > Integer.MAX_VALUE) {
                        readHexDecimal(start, i);
                        return;
                    }
                    i++;
                }
            }
            long number = c - '0';
            while (true) {
                c = chars[i];
                if (c < '0' || c > '9') {
                    if (c == '.') {
                        readDecimal(start, i);
                        break;
                    }
                    if (c == 'E') {
                        readDecimal(start, i);
                        break;
                    }
                    checkLiterals(false);
                    currentValue = ValueInt.get((int) number);
                    currentTokenType = VALUE;
                    currentToken = "0";
                    parseIndex = i;
                    break;
                }
                number = number * 10 + (c - '0');
                if (number > Integer.MAX_VALUE) {
                    readDecimal(start, i);
                    break;
                }
                i++;
            }
            return;
        case CHAR_DOT:
            if (types[i] != CHAR_VALUE) {
                currentTokenType = KEYWORD;
                currentToken = ".";
                parseIndex = i;
                return;
            }
            readDecimal(i - 1, i);
            return;
        case CHAR_STRING: {
            String result = null;
            while (true) {
                for (int begin = i;; i++) {
                    if (chars[i] == '\'') {
                        if (result == null) {
                            result = sqlCommand.substring(begin, i);
                        } else {
                            result += sqlCommand.substring(begin - 1, i);
                        }
                        break;
                    }
                }
                if (chars[++i] != '\'') {
                    break;
                }
                i++;
            }
            currentToken = "'";
            checkLiterals(true);
            currentValue = ValueString.get(StringUtils.fromCacheOrNew(result));
            parseIndex = i;
            currentTokenType = VALUE;
            return;
        }
        case CHAR_DOLLAR_QUOTED_STRING: {
            String result = null;
            int begin = i - 1;
            while (types[i] == CHAR_DOLLAR_QUOTED_STRING) {
                i++;
            }
            result = sqlCommand.substring(begin, i);
            currentToken = "'";
            checkLiterals(true);
            currentValue = ValueString.get(StringUtils.fromCacheOrNew(result));
            parseIndex = i;
            currentTokenType = VALUE;
            return;
        }
        case CHAR_END:
            currentToken = "";
            currentTokenType = END;
            parseIndex = i;
            return;
        default:
            throw getSyntaxError();
        }
    }

    private void checkLiterals(boolean text) {
        if (!session.getAllowLiterals()) {
            int allowed = database.getAllowLiterals();
            if (allowed == Constants.ALLOW_LITERALS_NONE || (text && allowed != Constants.ALLOW_LITERALS_ALL)) {
                throw DbException.get(ErrorCode.LITERALS_ARE_NOT_ALLOWED);
            }
        }
    }

    private void readHexDecimal(int start, int i) {
        char[] chars = sqlCommandChars;
        char c;
        do {
            c = chars[++i];
        } while ((c >= '0' && c <= '9') || (c >= 'A' && c <= 'F'));
        parseIndex = i;
        String sub = sqlCommand.substring(start, i);
        BigDecimal bd = new BigDecimal(new BigInteger(sub, 16));
        checkLiterals(false);
        currentValue = ValueDecimal.get(bd);
        currentTokenType = VALUE;
    }

    private void readDecimal(int start, int i) {
        char[] chars = sqlCommandChars;
        int[] types = characterTypes;
        // go until the first non-number
        while (true) {
            int t = types[i];
            if (t != CHAR_DOT && t != CHAR_VALUE) {
                break;
            }
            i++;
        }
        boolean containsE = false;
        if (chars[i] == 'E' || chars[i] == 'e') {
            containsE = true;
            i++;
            if (chars[i] == '+' || chars[i] == '-') {
                i++;
            }
            if (types[i] != CHAR_VALUE) {
                throw getSyntaxError();
            }
            while (types[++i] == CHAR_VALUE) {
                // go until the first non-number
            }
        }
        parseIndex = i;
        String sub = sqlCommand.substring(start, i);
        checkLiterals(false);
        if (!containsE && sub.indexOf('.') < 0) {
            BigInteger bi = new BigInteger(sub);
            if (bi.compareTo(ValueLong.MAX) <= 0) {
                currentValue = ValueLong.get(bi.longValue());
                currentTokenType = VALUE;
                return;
            }
        }
        BigDecimal bd;
        try {
            bd = new BigDecimal(sub);
        } catch (NumberFormatException e) {
            throw DbException.get(ErrorCode.DATA_CONVERSION_ERROR_1, e, sub);
        }
        currentValue = ValueDecimal.get(bd);
        currentTokenType = VALUE;
    }

    public Session getSession() {
        return session;
    }

    private void initialize(String sql) {
        if (sql == null) {
            sql = "";
        }
        originalSQL = sql;
        sqlCommand = sql;
        int len = sql.length() + 1;
        char[] command = new char[len];
        int[] types = new int[len];
        len--;
        sql.getChars(0, len, command, 0);
        boolean changed = false;
        command[len] = ' ';
        int startLoop = 0;
        int lastType = 0;
        for (int i = 0; i < len; i++) {
            char c = command[i];
            int type = 0;
            switch (c) {
            case '/':
                if (command[i + 1] == '*') {
                    // block comment
                    changed = true;
                    command[i] = ' ';
                    command[i + 1] = ' ';
                    startLoop = i;
                    i += 2;
                    checkRunOver(i, len, startLoop);
                    while (command[i] != '*' || command[i + 1] != '/') {
                        command[i++] = ' ';
                        checkRunOver(i, len, startLoop);
                    }
                    command[i] = ' ';
                    command[i + 1] = ' ';
                    i++;
                } else if (command[i + 1] == '/') {
                    // single line comment
                    changed = true;
                    startLoop = i;
                    while (true) {
                        c = command[i];
                        if (c == '\n' || c == '\r' || i >= len - 1) {
                            break;
                        }
                        command[i++] = ' ';
                        checkRunOver(i, len, startLoop);
                    }
                } else {
                    type = CHAR_SPECIAL_1;
                }
                break;
            case '-':
                if (command[i + 1] == '-') {
                    // single line comment
                    changed = true;
                    startLoop = i;
                    while (true) {
                        c = command[i];
                        if (c == '\n' || c == '\r' || i >= len - 1) {
                            break;
                        }
                        command[i++] = ' ';
                        checkRunOver(i, len, startLoop);
                    }
                } else {
                    type = CHAR_SPECIAL_1;
                }
                break;
            case '$':
                if (command[i + 1] == '$' && (i == 0 || command[i - 1] <= ' ')) {
                    // dollar quoted string
                    changed = true;
                    command[i] = ' ';
                    command[i + 1] = ' ';
                    startLoop = i;
                    i += 2;
                    checkRunOver(i, len, startLoop);
                    while (command[i] != '$' || command[i + 1] != '$') {
                        types[i++] = CHAR_DOLLAR_QUOTED_STRING;
                        checkRunOver(i, len, startLoop);
                    }
                    command[i] = ' ';
                    command[i + 1] = ' ';
                    i++;
                } else {
                    if (lastType == CHAR_NAME || lastType == CHAR_VALUE) {
                        // $ inside an identifier is supported
                        type = CHAR_NAME;
                    } else {
                        // but not at the start, to support PostgreSQL $1
                        type = CHAR_SPECIAL_1;
                    }
                }
                break;
            case '(':
            case ')':
            case '{':
            case '}':
            case '*':
            case ',':
            case ';':
            case '+':
            case '%':
            case '?':
            case '@':
            case ']':
                type = CHAR_SPECIAL_1;
                break;
            case '!':
            case '<':
            case '>':
            case '|':
            case '=':
            case ':':
            case '~':
                type = CHAR_SPECIAL_2;
                break;
            case '.':
                type = CHAR_DOT;
                break;
            case '\'':
                type = types[i] = CHAR_STRING;
                startLoop = i;
                while (command[++i] != '\'') {
                    checkRunOver(i, len, startLoop);
                }
                break;
            case '[':
                if (database.getMode().squareBracketQuotedNames) {
                    // SQL Server alias for "
                    command[i] = '"';
                    changed = true;
                    type = types[i] = CHAR_QUOTED;
                    startLoop = i;
                    while (command[++i] != ']') {
                        checkRunOver(i, len, startLoop);
                    }
                    command[i] = '"';
                } else {
                    type = CHAR_SPECIAL_1;
                }
                break;
            case '`':
                // MySQL alias for ", but not case sensitive
                command[i] = '"';
                changed = true;
                type = types[i] = CHAR_QUOTED;
                startLoop = i;
                while (command[++i] != '`') {
                    checkRunOver(i, len, startLoop);
                    c = command[i];
                    command[i] = Character.toUpperCase(c);
                }
                command[i] = '"';
                break;
            case '\"':
                type = types[i] = CHAR_QUOTED;
                startLoop = i;
                while (command[++i] != '\"') {
                    checkRunOver(i, len, startLoop);
                }
                break;
            case '_':
                type = CHAR_NAME;
                break;
            default:
                if (c >= 'a' && c <= 'z') {
                    if (identifiersToUpper) {
                        command[i] = (char) (c - ('a' - 'A'));
                        changed = true;
                    }
                    type = CHAR_NAME;
                } else if (c >= 'A' && c <= 'Z') {
                    type = CHAR_NAME;
                } else if (c >= '0' && c <= '9') {
                    type = CHAR_VALUE;
                } else {
                    if (c <= ' ' || Character.isSpaceChar(c)) {
                        // whitespace
                    } else if (Character.isJavaIdentifierPart(c)) {
                        type = CHAR_NAME;
                        if (identifiersToUpper) {
                            char u = Character.toUpperCase(c);
                            if (u != c) {
                                command[i] = u;
                                changed = true;
                            }
                        }
                    } else {
                        type = CHAR_SPECIAL_1;
                    }
                }
            }
            types[i] = type;
            lastType = type;
        }
        sqlCommandChars = command;
        types[len] = CHAR_END;
        characterTypes = types;
        if (changed) {
            sqlCommand = new String(command);
        }
        parseIndex = 0;
    }

    private void checkRunOver(int i, int len, int startLoop) {
        if (i >= len) {
            parseIndex = startLoop;
            throw getSyntaxError();
        }
    }

    private int getSpecialType(String s) {
        char c0 = s.charAt(0);
        if (s.length() == 1) {
            switch (c0) {
            case '?':
            case '$':
                return PARAMETER;
            case '@':
                return AT;
            case '+':
                return PLUS;
            case '-':
                return MINUS;
            case '{':
            case '}':
            case '*':
            case '/':
            case '%':
            case ';':
            case ',':
            case ':':
            case '[':
            case ']':
            case '~':
                return KEYWORD;
            case '(':
                return OPEN;
            case ')':
                return CLOSE;
            case '<':
                return SMALLER;
            case '>':
                return BIGGER;
            case '=':
                return EQUAL;
            default:
                break;
            }
        } else if (s.length() == 2) {
            switch (c0) {
            case ':':
                if ("::".equals(s)) {
                    return KEYWORD;
                } else if (":=".equals(s)) {
                    return KEYWORD;
                }
                break;
            case '>':
                if (">=".equals(s)) {
                    return BIGGER_EQUAL;
                }
                break;
            case '<':
                if ("<=".equals(s)) {
                    return SMALLER_EQUAL;
                } else if ("<>".equals(s)) {
                    return NOT_EQUAL;
                }
                break;
            case '!':
                if ("!=".equals(s)) {
                    return NOT_EQUAL;
                } else if ("!~".equals(s)) {
                    return KEYWORD;
                }
                break;
            case '|':
                if ("||".equals(s)) {
                    return STRING_CONCAT;
                }
                break;
            }
        }
        throw getSyntaxError();
    }

    private int getTokenType(String s) {
        int len = s.length();
        if (len == 0) {
            throw getSyntaxError();
        }
        if (!identifiersToUpper) {
            // if not yet converted to uppercase, do it now
            s = StringUtils.toUpperEnglish(s);
        }
        return getSaveTokenType(s, database.getMode().supportOffsetFetch);
    }

    private boolean isKeyword(String s) {
        if (!identifiersToUpper) {
            // if not yet converted to uppercase, do it now
            s = StringUtils.toUpperEnglish(s);
        }
        return isKeyword(s, false);
    }

    /**
     * Checks if this string is a SQL keyword.
     *
     * @param s the token to check
     * @param supportOffsetFetch if OFFSET and FETCH are keywords
     * @return true if it is a keyword
     */
    public static boolean isKeyword(String s, boolean supportOffsetFetch) {
        if (s == null || s.length() == 0) {
            return false;
        }
        return getSaveTokenType(s, supportOffsetFetch) != IDENTIFIER;
    }

    private static int getSaveTokenType(String s, boolean supportOffsetFetch) {
        switch (s.charAt(0)) {
        case 'C':
            if (s.equals("CURRENT_TIMESTAMP")) {
                return CURRENT_TIMESTAMP;
            } else if (s.equals("CURRENT_TIME")) {
                return CURRENT_TIME;
            } else if (s.equals("CURRENT_DATE")) {
                return CURRENT_DATE;
            }
            return getKeywordOrIdentifier(s, "CROSS", KEYWORD);
        case 'D':
            return getKeywordOrIdentifier(s, "DISTINCT", KEYWORD);
        case 'E':
            if ("EXCEPT".equals(s)) {
                return KEYWORD;
            }
            return getKeywordOrIdentifier(s, "EXISTS", KEYWORD);
        case 'F':
            if ("FROM".equals(s)) {
                return KEYWORD;
            } else if ("FOR".equals(s)) {
                return KEYWORD;
            } else if ("FULL".equals(s)) {
                return KEYWORD;
            } else if (supportOffsetFetch && "FETCH".equals(s)) {
                return KEYWORD;
            }
            return getKeywordOrIdentifier(s, "FALSE", FALSE);
        case 'G':
            return getKeywordOrIdentifier(s, "GROUP", KEYWORD);
        case 'H':
            return getKeywordOrIdentifier(s, "HAVING", KEYWORD);
        case 'I':
            if ("INNER".equals(s)) {
                return KEYWORD;
            } else if ("INTERSECT".equals(s)) {
                return KEYWORD;
            }
            return getKeywordOrIdentifier(s, "IS", KEYWORD);
        case 'J':
            return getKeywordOrIdentifier(s, "JOIN", KEYWORD);
        case 'L':
            if ("LIMIT".equals(s)) {
                return KEYWORD;
            }
            return getKeywordOrIdentifier(s, "LIKE", KEYWORD);
        case 'M':
            return getKeywordOrIdentifier(s, "MINUS", KEYWORD);
        case 'N':
            if ("NOT".equals(s)) {
                return KEYWORD;
            } else if ("NATURAL".equals(s)) {
                return KEYWORD;
            }
            return getKeywordOrIdentifier(s, "NULL", NULL);
        case 'O':
            if ("ON".equals(s)) {
                return KEYWORD;
            } else if (supportOffsetFetch && "OFFSET".equals(s)) {
                return KEYWORD;
            }
            return getKeywordOrIdentifier(s, "ORDER", KEYWORD);
        case 'P':
            return getKeywordOrIdentifier(s, "PRIMARY", KEYWORD);
        case 'R':
            return getKeywordOrIdentifier(s, "ROWNUM", ROWNUM);
        case 'S':
            if (s.equals("SYSTIMESTAMP")) {
                return CURRENT_TIMESTAMP;
            } else if (s.equals("SYSTIME")) {
                return CURRENT_TIME;
            } else if (s.equals("SYSDATE")) {
                return CURRENT_TIMESTAMP;
            }
            return getKeywordOrIdentifier(s, "SELECT", KEYWORD);
        case 'T':
            if ("TODAY".equals(s)) {
                return CURRENT_DATE;
            }
            return getKeywordOrIdentifier(s, "TRUE", TRUE);
        case 'U':
            if ("UNIQUE".equals(s)) {
                return KEYWORD;
            }
            return getKeywordOrIdentifier(s, "UNION", KEYWORD);
        case 'W':
            return getKeywordOrIdentifier(s, "WHERE", KEYWORD);
        default:
            return IDENTIFIER;
        }
    }

    private static int getKeywordOrIdentifier(String s1, String s2, int keywordType) {
        if (s1.equals(s2)) {
            return keywordType;
        }
        return IDENTIFIER;
    }

    private Column parseColumnForTable(String columnName, boolean defaultNullable) {
        Column column;
        boolean isIdentity = false;
        if (readIf("IDENTITY") || readIf("BIGSERIAL")) {
            column = new Column(columnName, Value.LONG);
            column.setOriginalSQL("IDENTITY");
            parseAutoIncrement(column);
            // PostgreSQL compatibility
            if (!database.getMode().serialColumnIsNotPK) {
                column.setPrimaryKey(true);
            }
        } else if (readIf("SERIAL")) {
            column = new Column(columnName, Value.INT);
            column.setOriginalSQL("SERIAL");
            parseAutoIncrement(column);
            // PostgreSQL compatibility
            if (!database.getMode().serialColumnIsNotPK) {
                column.setPrimaryKey(true);
            }
        } else {
            column = parseColumnWithType(columnName);
        }
        if (readIf("NOT")) {
            read("NULL");
            column.setNullable(false);
        } else if (readIf("NULL")) {
            column.setNullable(true);
        } else {
            // domains may be defined as not nullable
            column.setNullable(defaultNullable & column.isNullable());
        }
        if (readIf("AS")) {
            if (isIdentity) {
                getSyntaxError();
            }
            Expression expr = readExpression();
            column.setComputedExpression(expr);
        } else if (readIf("DEFAULT")) {
            Expression defaultExpression = readExpression();
            column.setDefaultExpression(session, defaultExpression);
        } else if (readIf("GENERATED")) {
            if (!readIf("ALWAYS")) {
                read("BY");
                read("DEFAULT");
            }
            read("AS");
            read("IDENTITY");
            long start = 1, increment = 1;
            if (readIf("(")) {
                read("START");
                readIf("WITH");
                start = readLong();
                readIf(",");
                if (readIf("INCREMENT")) {
                    readIf("BY");
                    increment = readLong();
                }
                read(")");
            }
            column.setPrimaryKey(true);
            column.setAutoIncrement(true, start, increment);
        }
        if (readIf("NOT")) {
            read("NULL");
            column.setNullable(false);
        } else {
            readIf("NULL");
        }
        if (readIf("AUTO_INCREMENT") || readIf("BIGSERIAL") || readIf("SERIAL")) {
            parseAutoIncrement(column);
            if (readIf("NOT")) {
                read("NULL");
            }
        } else if (readIf("IDENTITY")) {
            parseAutoIncrement(column);
            column.setPrimaryKey(true);
            if (readIf("NOT")) {
                read("NULL");
            }
        }
        if (readIf("NULL_TO_DEFAULT")) {
            column.setConvertNullToDefault(true);
        }
        if (readIf("SEQUENCE")) {
            Sequence sequence = readSequence();
            column.setSequence(sequence);
        }
        if (readIf("SELECTIVITY")) {
            int value = getPositiveInt();
            column.setSelectivity(value);
        }
        String comment = readCommentIf();
        if (comment != null) {
            column.setComment(comment);
        }
        return column;
    }

    private void parseAutoIncrement(Column column) {
        long start = 1, increment = 1;
        if (readIf("(")) {
            start = readLong();
            if (readIf(",")) {
                increment = readLong();
            }
            read(")");
        }
        column.setAutoIncrement(true, start, increment);
    }

    private String readCommentIf() {
        if (readIf("COMMENT")) {
            readIf("IS");
            return readString();
        }
        return null;
    }

    private Column parseColumnWithType(String columnName) {
        String original = currentToken;
        boolean regular = false;
        if (readIf("LONG")) {
            if (readIf("RAW")) {
                original += " RAW";
            }
        } else if (readIf("DOUBLE")) {
            if (readIf("PRECISION")) {
                original += " PRECISION";
            }
        } else if (readIf("CHARACTER")) {
            if (readIf("VARYING")) {
                original += " VARYING";
            }
        } else {
            regular = true;
        }
        long precision = -1;
        int displaySize = -1;
        int scale = -1;
        String comment = null;
        Column templateColumn = null;
        DataType dataType;
        if (!identifiersToUpper) {
            original = StringUtils.toUpperEnglish(original);
        }
        UserDataType userDataType = database.findUserDataType(original);
        if (userDataType != null) {
            templateColumn = userDataType.getColumn();
            dataType = DataType.getDataType(templateColumn.getType());
            comment = templateColumn.getComment();
            original = templateColumn.getOriginalSQL();
            precision = templateColumn.getPrecision();
            displaySize = templateColumn.getDisplaySize();
            scale = templateColumn.getScale();
        } else {
            dataType = DataType.getTypeByName(original);
            if (dataType == null) {
                throw DbException.get(ErrorCode.UNKNOWN_DATA_TYPE_1, currentToken);
            }
        }
        if (database.getIgnoreCase() && dataType.type == Value.STRING
                && !equalsToken("VARCHAR_CASESENSITIVE", original)) {
            original = "VARCHAR_IGNORECASE";
            dataType = DataType.getTypeByName(original);
        }
        if (regular) {
            read();
        }
        precision = precision == -1 ? dataType.defaultPrecision : precision;
        displaySize = displaySize == -1 ? dataType.defaultDisplaySize : displaySize;
        scale = scale == -1 ? dataType.defaultScale : scale;
        if (dataType.supportsPrecision || dataType.supportsScale) {
            if (readIf("(")) {
                if (!readIf("MAX")) {
                    long p = readLong();
                    if (readIf("K")) {
                        p *= 1024;
                    } else if (readIf("M")) {
                        p *= 1024 * 1024;
                    } else if (readIf("G")) {
                        p *= 1024 * 1024 * 1024;
                    }
                    if (p > Long.MAX_VALUE) {
                        p = Long.MAX_VALUE;
                    }
                    original += "(" + p;
                    // Oracle syntax
                    readIf("CHAR");
                    if (dataType.supportsScale) {
                        if (readIf(",")) {
                            scale = getInt();
                            original += ", " + scale;
                        } else {
                            // special case: TIMESTAMP(5) actually means TIMESTAMP(23, 5)
                            if (dataType.type == Value.TIMESTAMP) {
                                scale = MathUtils.convertLongToInt(p);
                                p = precision;
                            } else {
                                scale = 0;
                            }
                        }
                    }
                    precision = p;
                    displaySize = MathUtils.convertLongToInt(precision);
                    original += ")";
                }
                read(")");
            }
        } else if (readIf("(")) {
            // Support for MySQL: INT(11), MEDIUMINT(8) and so on.
            // Just ignore the precision.
            getPositiveInt();
            read(")");
        }
        if (readIf("FOR")) {
            read("BIT");
            read("DATA");
            if (dataType.type == Value.STRING) {
                dataType = DataType.getTypeByName("BINARY");
            }
        }
        // MySQL compatibility
        readIf("UNSIGNED");
        int type = dataType.type;
        if (scale > precision) {
            throw DbException.get(ErrorCode.INVALID_VALUE_2, Integer.toString(scale), "scale (precision = " + precision
                    + ")");
        }
        Column column = new Column(columnName, type, precision, scale, displaySize);
        if (templateColumn != null) {
            column.setNullable(templateColumn.isNullable());
            column.setDefaultExpression(session, templateColumn.getDefaultExpression());
            int selectivity = templateColumn.getSelectivity();
            if (selectivity != Constants.SELECTIVITY_DEFAULT) {
                column.setSelectivity(selectivity);
            }
            Expression checkConstraint = templateColumn.getCheckConstraint(session, columnName);
            if (checkConstraint != null) {
                column.addCheckConstraint(session, checkConstraint);
            }
        }
        column.setComment(comment);
        column.setOriginalSQL(original);
        return column;
    }

    protected Prepared parseCreate() {
        boolean orReplace = false;
        if (readIf("OR")) {
            read("REPLACE");
            orReplace = true;
        }
        boolean force = readIf("FORCE");
        if (readIf("VIEW")) {
            return parseCreateView(force, orReplace);
        } else if (readIf("ALIAS")) {
            return parseCreateFunctionAlias(force);
        } else if (readIf("SEQUENCE")) {
            return parseCreateSequence();
        } else if (readIf("USER")) {
            return parseCreateUser();
        } else if (readIf("TRIGGER")) {
            return parseCreateTrigger(force);
        } else if (readIf("ROLE")) {
            return parseCreateRole();
        } else if (readIf("SCHEMA")) {
            return parseCreateSchema();
        } else if (readIf("CONSTANT")) {
            return parseCreateConstant();
        } else if (readIf("DOMAIN")) {
            return parseCreateUserDataType();
        } else if (readIf("TYPE")) {
            return parseCreateUserDataType();
        } else if (readIf("DATATYPE")) {
            return parseCreateUserDataType();
        } else if (readIf("AGGREGATE")) {
            return parseCreateAggregate(force);
        }
        // tables or linked tables
        boolean memory = false, cached = false, dynamicTable = true;
        if (readIf("MEMORY")) {
            memory = true;
        } else if (readIf("CACHED")) {
            cached = true;
        }

        if (readIf("STATIC")) {
            dynamicTable = false;
        } else if (readIf("DYNAMIC")) {
            dynamicTable = true;
        }
        if (readIf("LOCAL")) {
            read("TEMPORARY");
            read("TABLE");
            return parseCreateTable(true, false, cached, dynamicTable);
        } else if (readIf("GLOBAL")) {
            read("TEMPORARY");
            read("TABLE");
            return parseCreateTable(true, true, cached, dynamicTable);
        } else if (readIf("TEMP") || readIf("TEMPORARY")) {
            read("TABLE");
            return parseCreateTable(true, true, cached, dynamicTable);
        } else if (readIf("TABLE")) {
            if (!cached && !memory) {
                cached = database.getDefaultTableType() == Table.TYPE_CACHED;
            }
            return parseCreateTable(false, false, cached, dynamicTable);
        } else {
            boolean hash = false, primaryKey = false, unique = false;
            String indexName = null;
            Schema oldSchema = null;
            boolean ifNotExists = false;
            if (readIf("PRIMARY")) {
                read("KEY");
                if (readIf("HASH")) {
                    hash = true;
                }
                primaryKey = true;
                if (!isToken("ON")) {
                    ifNotExists = readIfNoExists();
                    indexName = readIdentifierWithSchema(null);
                    oldSchema = getSchema();
                }
            } else {
                if (readIf("UNIQUE")) {
                    unique = true;
                }
                if (readIf("HASH")) {
                    hash = true;
                }
                if (readIf("INDEX")) {
                    if (!isToken("ON")) {
                        ifNotExists = readIfNoExists();
                        indexName = readIdentifierWithSchema(null);
                        oldSchema = getSchema();
                    }
                } else {
                    throw getSyntaxError();
                }
            }
            read("ON");
            String tableName = readIdentifierWithSchema();
            checkSchema(oldSchema);
            CreateIndex command = new CreateIndex(session, getSchema());
            command.setIfNotExists(ifNotExists);
            command.setHash(hash);
            command.setPrimaryKey(primaryKey);
            command.setTableName(tableName);
            command.setUnique(unique);
            command.setIndexName(indexName);
            command.setComment(readCommentIf());
            read("(");
            command.setIndexColumns(parseIndexColumnList());
            return command;
        }
    }

    private boolean addRoleOrRight(GrantRevoke command) {
        if (readIf("SELECT")) {
            command.addRight(Right.SELECT);
            return false;
        } else if (readIf("DELETE")) {
            command.addRight(Right.DELETE);
            return false;
        } else if (readIf("INSERT")) {
            command.addRight(Right.INSERT);
            return false;
        } else if (readIf("UPDATE")) {
            command.addRight(Right.UPDATE);
            return false;
        } else if (readIf("ALL")) {
            command.addRight(Right.ALL);
            return false;
        } else if (readIf("CONNECT")) {
            // ignore this right
            return false;
        } else if (readIf("RESOURCE")) {
            // ignore this right
            return false;
        } else {
            command.addRoleName(readUniqueIdentifier());
            return true;
        }
    }

    private GrantRevoke parseGrantRevoke(int operationType) {
        GrantRevoke command = new GrantRevoke(session);
        command.setOperationType(operationType);
        boolean isRoleBased = addRoleOrRight(command);
        while (readIf(",")) {
            boolean next = addRoleOrRight(command);
            if (next != isRoleBased) {
                throw DbException.get(ErrorCode.ROLES_AND_RIGHT_CANNOT_BE_MIXED);
            }
        }
        if (!isRoleBased) {
            if (readIf("ON")) {
                do {
                    Table table = readTableOrView();
                    command.addTable(table);
                } while (readIf(","));
            }
        }
        if (operationType == CommandInterface.GRANT) {
            read("TO");
        } else {
            read("FROM");
        }
        command.setGranteeName(readUniqueIdentifier());
        return command;
    }

    private Select parseValues() {
        Select command = createSelect(session);
        currentSelect = command;
        TableFilter filter = parseValuesTable();
        ArrayList<Expression> list = New.arrayList();
        list.add(new Wildcard(null, null));
        command.setExpressions(list);
        command.addTableFilter(filter, true);
        command.init();
        return command;
    }

    private TableFilter parseValuesTable() {
        Schema mainSchema = database.getSchema(Constants.SCHEMA_MAIN);
        TableFunction tf = (TableFunction) Function.getFunction(database, "TABLE");
        ArrayList<Column> columns = New.arrayList();
        ArrayList<ArrayList<Expression>> rows = New.arrayList();
        do {
            int i = 0;
            ArrayList<Expression> row = New.arrayList();
            boolean multiColumn = readIf("(");
            do {
                Expression expr = readExpression();
                expr = expr.optimize(session);
                int type = expr.getType();
                long prec;
                int scale, displaySize;
                Column column;
                String columnName = "C" + (i + 1);
                if (rows.isEmpty()) {
                    if (type == Value.UNKNOWN) {
                        type = Value.STRING;
                    }
                    DataType dt = DataType.getDataType(type);
                    prec = dt.defaultPrecision;
                    scale = dt.defaultScale;
                    displaySize = dt.defaultDisplaySize;
                    column = new Column(columnName, type, prec, scale, displaySize);
                    columns.add(column);
                }
                prec = expr.getPrecision();
                scale = expr.getScale();
                displaySize = expr.getDisplaySize();
                if (i >= columns.size()) {
                    throw DbException.get(ErrorCode.COLUMN_COUNT_DOES_NOT_MATCH);
                }
                Column c = columns.get(i);
                type = Value.getHigherOrder(c.getType(), type);
                prec = Math.max(c.getPrecision(), prec);
                scale = Math.max(c.getScale(), scale);
                displaySize = Math.max(c.getDisplaySize(), displaySize);
                column = new Column(columnName, type, prec, scale, displaySize);
                columns.set(i, column);
                row.add(expr);
                i++;
            } while (multiColumn && readIf(","));
            if (multiColumn) {
                read(")");
            }
            rows.add(row);
        } while (readIf(","));
        int columnCount = columns.size();
        int rowCount = rows.size();
        for (int i = 0; i < rowCount; i++) {
            if (rows.get(i).size() != columnCount) {
                throw DbException.get(ErrorCode.COLUMN_COUNT_DOES_NOT_MATCH);
            }
        }
        for (int i = 0; i < columnCount; i++) {
            Column c = columns.get(i);
            if (c.getType() == Value.UNKNOWN) {
                c = new Column(c.getName(), Value.STRING, 0, 0, 0);
                columns.set(i, c);
            }
            Expression[] array = new Expression[rowCount];
            for (int j = 0; j < rowCount; j++) {
                array[j] = rows.get(j).get(i);
            }
            ExpressionList list = new ExpressionList(array);
            tf.setParameter(i, list);
        }
        tf.setColumns(columns);
        tf.doneWithParameters();
        Table table = new FunctionTable(mainSchema, session, tf, tf);
        TableFilter filter = new TableFilter(session, table, null, rightsChecked, currentSelect);
        return filter;
    }

    private Call parseCall() {
        Call command = new Call(session);
        currentPrepared = command;
        command.setExpression(readExpression());
        return command;
    }

    private CreateRole parseCreateRole() {
        CreateRole command = new CreateRole(session);
        command.setIfNotExists(readIfNoExists());
        command.setRoleName(readUniqueIdentifier());
        return command;
    }

    private CreateSchema parseCreateSchema() {
        CreateSchema command = new CreateSchema(session);
        command.setIfNotExists(readIfNoExists());
        command.setSchemaName(readUniqueIdentifier());
        if (readIf("AUTHORIZATION")) {
            command.setAuthorization(readUniqueIdentifier());
        } else {
            command.setAuthorization(session.getUser().getName());
        }
        return command;
    }

    private CreateSequence parseCreateSequence() {
        boolean ifNotExists = readIfNoExists();
        String sequenceName = readIdentifierWithSchema();
        CreateSequence command = new CreateSequence(session, getSchema());
        command.setIfNotExists(ifNotExists);
        command.setSequenceName(sequenceName);
        while (true) {
            if (readIf("START")) {
                readIf("WITH");
                command.setStartWith(readExpression());
            } else if (readIf("INCREMENT")) {
                readIf("BY");
                command.setIncrement(readExpression());
            } else if (readIf("CACHE")) {
                command.setCacheSize(readExpression());
            } else if (readIf("BELONGS_TO_TABLE")) {
                command.setBelongsToTable(true);
            } else {
                break;
            }
        }
        return command;
    }

    protected boolean readIfNoExists() {
        if (readIf("IF")) {
            read("NOT");
            read("EXISTS");
            return true;
        }
        return false;
    }

    private CreateConstant parseCreateConstant() {
        boolean ifNotExists = readIfNoExists();
        String constantName = readIdentifierWithSchema();
        Schema schema = getSchema();
        if (isKeyword(constantName)) {
            throw DbException.get(ErrorCode.CONSTANT_ALREADY_EXISTS_1, constantName);
        }
        read("VALUE");
        Expression expr = readExpression();
        CreateConstant command = new CreateConstant(session, schema);
        command.setConstantName(constantName);
        command.setExpression(expr);
        command.setIfNotExists(ifNotExists);
        return command;
    }

    private CreateAggregate parseCreateAggregate(boolean force) {
        boolean ifNotExists = readIfNoExists();
        CreateAggregate command = new CreateAggregate(session);
        command.setForce(force);
        String name = readIdentifierWithSchema();
        if (isKeyword(name) || Function.getFunction(database, name) != null || getAggregateType(name) >= 0) {
            throw DbException.get(ErrorCode.FUNCTION_ALIAS_ALREADY_EXISTS_1, name);
        }
        command.setName(name);
        command.setSchema(getSchema());
        command.setIfNotExists(ifNotExists);
        read("FOR");
        command.setJavaClassMethod(readUniqueIdentifier());
        return command;
    }

    private CreateUserDataType parseCreateUserDataType() {
        boolean ifNotExists = readIfNoExists();
        CreateUserDataType command = new CreateUserDataType(session);
        command.setTypeName(readUniqueIdentifier());
        read("AS");
        Column col = parseColumnForTable("VALUE", true);
        if (readIf("CHECK")) {
            Expression expr = readExpression();
            col.addCheckConstraint(session, expr);
        }
        col.rename(null);
        command.setColumn(col);
        command.setIfNotExists(ifNotExists);
        return command;
    }

    private CreateTrigger parseCreateTrigger(boolean force) {
        boolean ifNotExists = readIfNoExists();
        String triggerName = readIdentifierWithSchema(null);
        Schema schema = getSchema();
        boolean insteadOf, isBefore;
        if (readIf("INSTEAD")) {
            read("OF");
            isBefore = true;
            insteadOf = true;
        } else if (readIf("BEFORE")) {
            insteadOf = false;
            isBefore = true;
        } else {
            read("AFTER");
            insteadOf = false;
            isBefore = false;
        }
        int typeMask = 0;
        boolean onRollback = false;
        do {
            if (readIf("INSERT")) {
                typeMask |= Trigger.INSERT;
            } else if (readIf("UPDATE")) {
                typeMask |= Trigger.UPDATE;
            } else if (readIf("DELETE")) {
                typeMask |= Trigger.DELETE;
            } else if (readIf("SELECT")) {
                typeMask |= Trigger.SELECT;
            } else if (readIf("ROLLBACK")) {
                onRollback = true;
            } else {
                throw getSyntaxError();
            }
        } while (readIf(","));
        read("ON");
        String tableName = readIdentifierWithSchema();
        checkSchema(schema);
        CreateTrigger command = new CreateTrigger(session, getSchema());
        command.setForce(force);
        command.setTriggerName(triggerName);
        command.setIfNotExists(ifNotExists);
        command.setInsteadOf(insteadOf);
        command.setBefore(isBefore);
        command.setOnRollback(onRollback);
        command.setTypeMask(typeMask);
        command.setTableName(tableName);
        if (readIf("FOR")) {
            read("EACH");
            read("ROW");
            command.setRowBased(true);
        } else {
            command.setRowBased(false);
        }
        if (readIf("QUEUE")) {
            command.setQueueSize(getPositiveInt());
        }
        command.setNoWait(readIf("NOWAIT"));
        read("CALL");
        command.setTriggerClassName(readUniqueIdentifier());
        return command;
    }

    private CreateUser parseCreateUser() {
        CreateUser command = new CreateUser(session);
        command.setIfNotExists(readIfNoExists());
        command.setUserName(readUniqueIdentifier());
        command.setComment(readCommentIf());
        if (readIf("PASSWORD")) {
            command.setPassword(readExpression());
        } else if (readIf("SALT")) {
            command.setSalt(readExpression());
            read("HASH");
            command.setHash(readExpression());
        } else if (readIf("IDENTIFIED")) {
            read("BY");
            // uppercase if not quoted
            command.setPassword(ValueExpression.get(ValueString.get(readColumnIdentifier())));
        } else {
            throw getSyntaxError();
        }
        if (readIf("ADMIN")) {
            command.setAdmin(true);
        }
        return command;
    }

    private CreateFunctionAlias parseCreateFunctionAlias(boolean force) {
        boolean ifNotExists = readIfNoExists();
        String aliasName = readIdentifierWithSchema();
        if (isKeyword(aliasName) || Function.getFunction(database, aliasName) != null
                || getAggregateType(aliasName) >= 0) {
            throw DbException.get(ErrorCode.FUNCTION_ALIAS_ALREADY_EXISTS_1, aliasName);
        }
        CreateFunctionAlias command = new CreateFunctionAlias(session, getSchema());
        command.setForce(force);
        command.setAliasName(aliasName);
        command.setIfNotExists(ifNotExists);
        command.setDeterministic(readIf("DETERMINISTIC"));
        if (readIf("AS")) {
            command.setSource(readString());
        } else {
            read("FOR");
            command.setJavaClassMethod(readUniqueIdentifier());
        }
        return command;
    }

    private Query parseWith() {
        readIf("RECURSIVE");
        String tempViewName = readIdentifierWithSchema();
        Schema schema = getSchema();
        Table recursiveTable;
        read("(");
        ArrayList<Column> columns = New.arrayList();
        String[] cols = parseColumnList();
        for (String c : cols) {
            columns.add(new Column(c, Value.STRING));
        }
        Table old = session.findLocalTempTable(tempViewName);
        if (old != null) {
            if (!(old instanceof TableView)) {
                throw DbException.get(ErrorCode.TABLE_OR_VIEW_ALREADY_EXISTS_1, tempViewName);
            }
            TableView tv = (TableView) old;
            if (!tv.isTableExpression()) {
                throw DbException.get(ErrorCode.TABLE_OR_VIEW_ALREADY_EXISTS_1, tempViewName);
            }
            session.removeLocalTempTable(old);
        }
        CreateTableData data = new CreateTableData();
        data.id = database.allocateObjectId();
        data.columns = columns;
        data.tableName = tempViewName;
        data.temporary = true;
        data.persistData = true;
        data.persistIndexes = false;
        data.create = true;
        data.session = session;
        recursiveTable = schema.createTable(data);
        session.addLocalTempTable(recursiveTable);
        String querySQL;
        try {
            read("AS");
            read("(");
            Query withQuery = parseSelect();
            read(")");
            withQuery.prepare();
            querySQL = StringUtils.fromCacheOrNew(withQuery.getPlanSQL());
        } finally {
            session.removeLocalTempTable(recursiveTable);
        }
        int id = database.allocateObjectId();
        TableView view = new TableView(schema, id, tempViewName, querySQL, null, cols, session, true);
        view.setTableExpression(true);
        view.setTemporary(true);
        session.addLocalTempTable(view);
        view.setOnCommitDrop(true);
        Query q = parseSelect();
        q.setPrepareAlways(true);
        return q;
    }

    private CreateView parseCreateView(boolean force, boolean orReplace) {
        boolean ifNotExists = readIfNoExists();
        String viewName = readIdentifierWithSchema();
        CreateView command = new CreateView(session, getSchema());
        this.createView = command;
        command.setViewName(viewName);
        command.setIfNotExists(ifNotExists);
        command.setComment(readCommentIf());
        command.setOrReplace(orReplace);
        command.setForce(force);
        if (readIf("(")) {
            String[] cols = parseColumnList();
            command.setColumnNames(cols);
        }
        String select = StringUtils.fromCacheOrNew(sqlCommand.substring(parseIndex));
        read("AS");
        try {
            Query query = parseSelect();
            query.prepare();
            command.setSelect(query);
        } catch (DbException e) {
            if (force) {
                command.setSelectSQL(select);
                while (currentTokenType != END) {
                    read();
                }
            } else {
                throw e;
            }
        }
        return command;
    }

    private TransactionCommand parseCheckpoint() {
        TransactionCommand command;
        if (readIf("SYNC")) {
            command = new TransactionCommand(session, CommandInterface.CHECKPOINT_SYNC);
        } else {
            command = new TransactionCommand(session, CommandInterface.CHECKPOINT);
        }
        return command;
    }

    protected Prepared parseAlter() {
        if (readIf("TABLE")) {
            return parseAlterTable();
        } else if (readIf("USER")) {
            return parseAlterUser();
        } else if (readIf("INDEX")) {
            return parseAlterIndex();
        } else if (readIf("SCHEMA")) {
            return parseAlterSchema();
        } else if (readIf("SEQUENCE")) {
            return parseAlterSequence();
        } else if (readIf("VIEW")) {
            return parseAlterView();
        }
        throw getSyntaxError();
    }

    private void checkSchema(Schema old) {
        if (old != null && getSchema() != old) {
            throw DbException.get(ErrorCode.SCHEMA_NAME_MUST_MATCH);
        }
    }

    private AlterIndexRename parseAlterIndex() {
        String indexName = readIdentifierWithSchema();
        Schema old = getSchema();
        AlterIndexRename command = new AlterIndexRename(session);
        command.setOldIndex(getSchema().getIndex(indexName));
        read("RENAME");
        read("TO");
        String newName = readIdentifierWithSchema(old.getName());
        checkSchema(old);
        command.setNewName(newName);
        return command;
    }

    private AlterView parseAlterView() {
        AlterView command = new AlterView(session);
        String viewName = readIdentifierWithSchema();
        Table tableView = getSchema().findTableOrView(session, viewName);
        if (!(tableView instanceof TableView)) {
            throw DbException.get(ErrorCode.VIEW_NOT_FOUND_1, viewName);
        }
        TableView view = (TableView) tableView;
        command.setView(view);
        read("RECOMPILE");
        return command;
    }

    private AlterSchemaRename parseAlterSchema() {
        String schemaName = readIdentifierWithSchema();
        Schema old = getSchema();
        AlterSchemaRename command = new AlterSchemaRename(session);
        command.setOldSchema(getSchema(schemaName));
        read("RENAME");
        read("TO");
        String newName = readIdentifierWithSchema(old.getName());
        checkSchema(old);
        command.setNewName(newName);
        return command;
    }

    protected AlterSequence parseAlterSequence() {
        String sequenceName = readIdentifierWithSchema();
        Sequence sequence = getSchema().getSequence(sequenceName);
        AlterSequence command = new AlterSequence(session, sequence.getSchema());
        command.setSequence(sequence);
        if (readIf("RESTART")) {
            read("WITH");
            command.setStartWith(readExpression());
        }
        if (readIf("INCREMENT")) {
            read("BY");
            command.setIncrement(readExpression());
        }
        return command;
    }

    private AlterUser parseAlterUser() {
        String userName = readUniqueIdentifier();
        if (readIf("SET")) {
            AlterUser command = new AlterUser(session);
            command.setType(CommandInterface.ALTER_USER_SET_PASSWORD);
            command.setUser(database.getUser(userName));
            if (readIf("PASSWORD")) {
                command.setPassword(readExpression());
            } else if (readIf("SALT")) {
                command.setSalt(readExpression());
                read("HASH");
                command.setHash(readExpression());
            } else {
                throw getSyntaxError();
            }
            return command;
        } else if (readIf("RENAME")) {
            read("TO");
            AlterUser command = new AlterUser(session);
            command.setType(CommandInterface.ALTER_USER_RENAME);
            command.setUser(database.getUser(userName));
            String newName = readUniqueIdentifier();
            command.setNewName(newName);
            return command;
        } else if (readIf("ADMIN")) {
            AlterUser command = new AlterUser(session);
            command.setType(CommandInterface.ALTER_USER_ADMIN);
            User user = database.getUser(userName);
            command.setUser(user);
            if (readIf("TRUE")) {
                command.setAdmin(true);
            } else if (readIf("FALSE")) {
                command.setAdmin(false);
            } else {
                throw getSyntaxError();
            }
            return command;
        }
        throw getSyntaxError();
    }

    private void readIfEqualOrTo() {
        if (!readIf("=")) {
            readIf("TO");
        }
    }

    private Prepared parseSet() {
        if (readIf("@")) {
            Set command = new Set(session, SetTypes.VARIABLE);
            command.setString(readAliasIdentifier());
            readIfEqualOrTo();
            command.setExpression(readExpression());
            return command;
        } else if (readIf("AUTOCOMMIT")) {
            readIfEqualOrTo();
            boolean value = readBooleanSetting();
            int setting = value ? CommandInterface.SET_AUTOCOMMIT_TRUE : CommandInterface.SET_AUTOCOMMIT_FALSE;
            return new TransactionCommand(session, setting);
        } else if (readIf("MVCC")) {
            readIfEqualOrTo();
            boolean value = readBooleanSetting();
            Set command = new Set(session, SetTypes.MVCC);
            command.setInt(value ? 1 : 0);
            return command;
        } else if (readIf("EXCLUSIVE")) {
            readIfEqualOrTo();
            Set command = new Set(session, SetTypes.EXCLUSIVE);
            command.setExpression(readExpression());
            return command;
        } else if (readIf("IGNORECASE")) {
            readIfEqualOrTo();
            boolean value = readBooleanSetting();
            Set command = new Set(session, SetTypes.IGNORECASE);
            command.setInt(value ? 1 : 0);
            return command;
        } else if (readIf("PASSWORD")) {
            readIfEqualOrTo();
            AlterUser command = new AlterUser(session);
            command.setType(CommandInterface.ALTER_USER_SET_PASSWORD);
            command.setUser(session.getUser());
            command.setPassword(readExpression());
            return command;
        } else if (readIf("SALT")) {
            readIfEqualOrTo();
            AlterUser command = new AlterUser(session);
            command.setType(CommandInterface.ALTER_USER_SET_PASSWORD);
            command.setUser(session.getUser());
            command.setSalt(readExpression());
            read("HASH");
            command.setHash(readExpression());
            return command;
        } else if (readIf("MODE")) {
            readIfEqualOrTo();
            Set command = new Set(session, SetTypes.MODE);
            command.setString(readAliasIdentifier());
            return command;
        } else if (readIf("COMPRESS_LOB")) {
            readIfEqualOrTo();
            Set command = new Set(session, SetTypes.COMPRESS_LOB);
            if (currentTokenType == VALUE) {
                command.setString(readString());
            } else {
                command.setString(readUniqueIdentifier());
            }
            return command;
        } else if (readIf("DATABASE")) {
            readIfEqualOrTo();
            read("COLLATION");
            return parseSetCollation();
        } else if (readIf("COLLATION")) {
            readIfEqualOrTo();
            return parseSetCollation();
        } else if (readIf("BINARY_COLLATION")) {
            readIfEqualOrTo();
            return parseSetBinaryCollation();
        } else if (readIf("CLUSTER")) {
            readIfEqualOrTo();
            Set command = new Set(session, SetTypes.CLUSTER);
            command.setString(readString());
            return command;
        } else if (readIf("DATABASE_EVENT_LISTENER")) {
            readIfEqualOrTo();
            Set command = new Set(session, SetTypes.DATABASE_EVENT_LISTENER);
            command.setString(readString());
            return command;
        } else if (readIf("ALLOW_LITERALS")) {
            readIfEqualOrTo();
            Set command = new Set(session, SetTypes.ALLOW_LITERALS);
            if (readIf("NONE")) {
                command.setInt(Constants.ALLOW_LITERALS_NONE);
            } else if (readIf("ALL")) {
                command.setInt(Constants.ALLOW_LITERALS_ALL);
            } else if (readIf("NUMBERS")) {
                command.setInt(Constants.ALLOW_LITERALS_NUMBERS);
            } else {
                command.setInt(getPositiveInt());
            }
            return command;
        } else if (readIf("DEFAULT_TABLE_TYPE")) {
            readIfEqualOrTo();
            Set command = new Set(session, SetTypes.DEFAULT_TABLE_TYPE);
            if (readIf("MEMORY")) {
                command.setInt(Table.TYPE_MEMORY);
            } else if (readIf("CACHED")) {
                command.setInt(Table.TYPE_CACHED);
            } else {
                command.setInt(getPositiveInt());
            }
            return command;
        } else if (readIf("CREATE")) {
            readIfEqualOrTo();
            // Derby compatibility (CREATE=TRUE in the database URL)
            read();
            return new NoOperation(session);
        } else if (readIf("HSQLDB.DEFAULT_TABLE_TYPE")) {
            readIfEqualOrTo();
            read();
            return new NoOperation(session);
        } else if (readIf("CACHE_TYPE")) {
            readIfEqualOrTo();
            read();
            return new NoOperation(session);
        } else if (readIf("DB_CLOSE_ON_EXIT")) {
            readIfEqualOrTo();
            read();
            return new NoOperation(session);
        } else if (readIf("ASSERT")) {
            readIfEqualOrTo();
            read();
            return new NoOperation(session);
        } else if (readIf("ACCESS_MODE_DATA")) {
            readIfEqualOrTo();
            read();
            return new NoOperation(session);
        } else if (readIf("OPEN_NEW")) {
            readIfEqualOrTo();
            read();
            return new NoOperation(session);
        } else if (readIf("PAGE_SIZE")) {
            readIfEqualOrTo();
            read();
            return new NoOperation(session);
        } else if (readIf("RECOVER")) {
            readIfEqualOrTo();
            read();
            return new NoOperation(session);
        } else if (readIf("NAMES")) {
            // Quercus PHP MySQL driver compatibility
            readIfEqualOrTo();
            read();
            return new NoOperation(session);
        } else if (readIf("SCHEMA")) {
            readIfEqualOrTo();
            Set command = new Set(session, SetTypes.SCHEMA);
            command.setString(readAliasIdentifier());
            return command;
        } else if (readIf("DATESTYLE")) {
            // PostgreSQL compatibility
            readIfEqualOrTo();
            if (!readIf("ISO")) {
                String s = readString();
                if (!equalsToken(s, "ISO")) {
                    throw getSyntaxError();
                }
            }
            return new NoOperation(session);
        } else if (readIf("SEARCH_PATH") || readIf(SetTypes.getTypeName(SetTypes.SCHEMA_SEARCH_PATH))) {
            readIfEqualOrTo();
            Set command = new Set(session, SetTypes.SCHEMA_SEARCH_PATH);
            ArrayList<String> list = New.arrayList();
            list.add(readAliasIdentifier());
            while (readIf(",")) {
                list.add(readAliasIdentifier());
            }
            String[] schemaNames = new String[list.size()];
            list.toArray(schemaNames);
            command.setStringArray(schemaNames);
            return command;
        } else {
            if (isToken("LOGSIZE")) {
                // HSQLDB compatibility
                currentToken = SetTypes.getTypeName(SetTypes.MAX_LOG_SIZE);
            }
            int type = SetTypes.getType(currentToken);
            if (type < 0) {
                throw getSyntaxError();
            }
            read();
            readIfEqualOrTo();
            Set command = new Set(session, type);
            command.setExpression(readExpression());
            return command;
        }
    }

    private Set parseSetCollation() {
        Set command = new Set(session, SetTypes.COLLATION);
        String name = readAliasIdentifier();
        command.setString(name);
        if (equalsToken(name, CompareMode.OFF)) {
            return command;
        }
        Collator coll = CompareMode.getCollator(name);
        if (coll == null) {
            throw DbException.getInvalidValueException("collation", name);
        }
        if (readIf("STRENGTH")) {
            if (readIf("PRIMARY")) {
                command.setInt(Collator.PRIMARY);
            } else if (readIf("SECONDARY")) {
                command.setInt(Collator.SECONDARY);
            } else if (readIf("TERTIARY")) {
                command.setInt(Collator.TERTIARY);
            } else if (readIf("IDENTICAL")) {
                command.setInt(Collator.IDENTICAL);
            }
        } else {
            command.setInt(coll.getStrength());
        }
        return command;
    }

    private Set parseSetBinaryCollation() {
        Set command = new Set(session, SetTypes.BINARY_COLLATION);
        String name = readAliasIdentifier();
        command.setString(name);
        if (equalsToken(name, CompareMode.UNSIGNED) || equalsToken(name, CompareMode.SIGNED)) {
            return command;
        }
        throw DbException.getInvalidValueException("BINARY_COLLATION", name);
    }

    private RunScriptCommand parseRunScript() {
        RunScriptCommand command = new RunScriptCommand(session);
        read("FROM");
        command.setFileNameExpr(readExpression());
        if (readIf("COMPRESSION")) {
            command.setCompressionAlgorithm(readUniqueIdentifier());
        }
        if (readIf("CIPHER")) {
            command.setCipher(readUniqueIdentifier());
            if (readIf("PASSWORD")) {
                command.setPassword(readExpression());
            }
        }
        if (readIf("CHARSET")) {
            command.setCharset(Charset.forName(readString()));
        }
        return command;
    }

    private ScriptCommand parseScript() {
        ScriptCommand command = new ScriptCommand(session);
        boolean data = true, passwords = true, settings = true, dropTables = false, simple = false;
        if (readIf("SIMPLE")) {
            simple = true;
        }
        if (readIf("NODATA")) {
            data = false;
        }
        if (readIf("NOPASSWORDS")) {
            passwords = false;
        }
        if (readIf("NOSETTINGS")) {
            settings = false;
        }
        if (readIf("DROP")) {
            dropTables = true;
        }
        if (readIf("BLOCKSIZE")) {
            long blockSize = readLong();
            command.setLobBlockSize(blockSize);
        }
        command.setData(data);
        command.setPasswords(passwords);
        command.setSettings(settings);
        command.setDrop(dropTables);
        command.setSimple(simple);
        if (readIf("TO")) {
            command.setFileNameExpr(readExpression());
            if (readIf("COMPRESSION")) {
                command.setCompressionAlgorithm(readUniqueIdentifier());
            }
            if (readIf("CIPHER")) {
                command.setCipher(readUniqueIdentifier());
                if (readIf("PASSWORD")) {
                    command.setPassword(readExpression());
                }
            }
            if (readIf("CHARSET")) {
                command.setCharset(Charset.forName(readString()));
            }
        }
        if (readIf("SCHEMA")) {
            HashSet<String> schemaNames = New.hashSet();
            do {
                schemaNames.add(readUniqueIdentifier());
            } while (readIf(","));
            command.setSchemaNames(schemaNames);
        } else if (readIf("TABLE")) {
            ArrayList<Table> tables = New.arrayList();
            do {
                tables.add(readTableOrView());
            } while (readIf(","));
            command.setTables(tables);
        }
        return command;
    }

    private Table readTableOrView() {
        return readTableOrView(readIdentifierWithSchema(null));
    }

    protected Table readTableOrView(String tableName) {
        // same algorithm than readSequence
        if (schemaName != null) {
            return getSchema().getTableOrView(session, tableName);
        }
        Table table = database.getSchema(session.getCurrentSchemaName()).findTableOrView(session, tableName);
        if (table != null) {
            return table;
        }
        String[] schemaNames = session.getSchemaSearchPath();
        if (schemaNames != null) {
            for (String name : schemaNames) {
                Schema s = database.getSchema(name);
                table = s.findTableOrView(session, tableName);
                if (table != null) {
                    return table;
                }
            }
        }
        //TODO 在分布式环境下，如果先在一个JVM上执行create table，再执行insert这样的dml，
        //或者执行create table和insert的是不同JVM，这时由于表的元数据未及时更新到执行insert的JVM，
        //所以有可能出现此异常，因为不同JVM上的表元数据通过zookeeper异步更新，
        //有可能执行create table的线程很快结束了，但是zookeeper还未通知，这时insert时就找不到表。
        //对于这种情况，client重视即可解决，不过还有没有更好的办法呢?
        //client在使用h2作为内存数据库对SQL预解析时也会碰到这样的情况。
        throw DbException.get(ErrorCode.TABLE_OR_VIEW_NOT_FOUND_1, tableName);
    }

    private FunctionAlias findFunctionAlias(String schema, String aliasName) {
        FunctionAlias functionAlias = database.getSchema(schema).findFunction(aliasName);
        if (functionAlias != null) {
            return functionAlias;
        }
        String[] schemaNames = session.getSchemaSearchPath();
        if (schemaNames != null) {
            for (String n : schemaNames) {
                functionAlias = database.getSchema(n).findFunction(aliasName);
                if (functionAlias != null) {
                    return functionAlias;
                }
            }
        }
        return null;
    }

    private Sequence findSequence(String schema, String sequenceName) {
        Sequence sequence = database.getSchema(schema).findSequence(sequenceName);
        if (sequence != null) {
            return sequence;
        }
        String[] schemaNames = session.getSchemaSearchPath();
        if (schemaNames != null) {
            for (String n : schemaNames) {
                sequence = database.getSchema(n).findSequence(sequenceName);
                if (sequence != null) {
                    return sequence;
                }
            }
        }
        return null;
    }

    private Sequence readSequence() {
        // same algorithm as readTableOrView
        String sequenceName = readIdentifierWithSchema(null);
        if (schemaName != null) {
            return getSchema().getSequence(sequenceName);
        }
        Sequence sequence = findSequence(session.getCurrentSchemaName(), sequenceName);
        if (sequence != null) {
            return sequence;
        }
        throw DbException.get(ErrorCode.SEQUENCE_NOT_FOUND_1, sequenceName);
    }

    private Prepared parseAlterTable() {
        Table table = readTableOrView();
        if (readIf("ADD")) {
            Prepared command = parseAlterTableAddConstraintIf(table.getName(), table.getSchema());
            if (command != null) {
                return command;
            }
            return parseAlterTableAddColumn(table);
        } else if (readIf("SET")) {
            read("REFERENTIAL_INTEGRITY");
            int type = CommandInterface.ALTER_TABLE_SET_REFERENTIAL_INTEGRITY;
            boolean value = readBooleanSetting();
            AlterTableSet command = new AlterTableSet(session, table.getSchema(), type, value);
            command.setTableName(table.getName());
            if (readIf("CHECK")) {
                command.setCheckExisting(true);
            } else if (readIf("NOCHECK")) {
                command.setCheckExisting(false);
            }
            return command;
        } else if (readIf("RENAME")) {
            read("TO");
            String newName = readIdentifierWithSchema(table.getSchema().getName());
            checkSchema(table.getSchema());
            AlterTableRename command = new AlterTableRename(session, getSchema());
            command.setOldTable(table);
            command.setNewTableName(newName);
            command.setHidden(readIf("HIDDEN"));
            return command;
        } else if (readIf("DROP")) {
            if (readIf("CONSTRAINT")) {
                boolean ifExists = readIfExists(false);
                String constraintName = readIdentifierWithSchema(table.getSchema().getName());
                ifExists = readIfExists(ifExists);
                checkSchema(table.getSchema());
                AlterTableDropConstraint command = new AlterTableDropConstraint(session, getSchema(), ifExists);
                command.setConstraintName(constraintName);
                return command;
            } else if (readIf("PRIMARY")) {
                read("KEY");
                Index idx = table.getPrimaryKey();
                DropIndex command = new DropIndex(session, table.getSchema());
                command.setIndexName(idx.getName());
                return command;
            } else {
                readIf("COLUMN");
                boolean ifExists = readIfExists(false);
                AlterTableAlterColumn command = new AlterTableAlterColumn(session, table.getSchema());
                command.setType(CommandInterface.ALTER_TABLE_DROP_COLUMN);
                String columnName = readColumnIdentifier();
                command.setTable(table);
                if (ifExists && !table.doesColumnExist(columnName)) {
                    return new NoOperation(session);
                }
                command.setOldColumn(table.getColumn(columnName));
                return command;
            }
        } else if (readIf("MODIFY")) {
            // MySQL compatibility
            readIf("COLUMN");
            String columnName = readColumnIdentifier();
            Column column = table.getColumn(columnName);
            return parseAlterTableAlterColumnType(table, columnName, column);
        } else if (readIf("ALTER")) {
            readIf("COLUMN");
            String columnName = readColumnIdentifier();
            Column column = table.getColumn(columnName);
            if (readIf("RENAME")) {
                read("TO");
                AlterTableRenameColumn command = new AlterTableRenameColumn(session);
                command.setTable(table);
                command.setColumn(column);
                String newName = readColumnIdentifier();
                command.setNewColumnName(newName);
                return command;
            } else if (readIf("DROP")) {
                // PostgreSQL compatibility
                if (readIf("DEFAULT")) {
                    AlterTableAlterColumn command = new AlterTableAlterColumn(session, table.getSchema());
                    command.setTable(table);
                    command.setOldColumn(column);
                    command.setType(CommandInterface.ALTER_TABLE_ALTER_COLUMN_DEFAULT);
                    command.setDefaultExpression(null);
                    return command;
                }
                read("NOT");
                read("NULL");
                AlterTableAlterColumn command = new AlterTableAlterColumn(session, table.getSchema());
                command.setTable(table);
                command.setOldColumn(column);
                command.setType(CommandInterface.ALTER_TABLE_ALTER_COLUMN_NULL);
                return command;
            } else if (readIf("TYPE")) {
                // PostgreSQL compatibility
                return parseAlterTableAlterColumnType(table, columnName, column);
            } else if (readIf("SET")) {
                if (readIf("DATA")) {
                    // Derby compatibility
                    read("TYPE");
                    return parseAlterTableAlterColumnType(table, columnName, column);
                }
                AlterTableAlterColumn command = new AlterTableAlterColumn(session, table.getSchema());
                command.setTable(table);
                command.setOldColumn(column);
                if (readIf("NULL")) {
                    command.setType(CommandInterface.ALTER_TABLE_ALTER_COLUMN_NULL);
                    return command;
                } else if (readIf("NOT")) {
                    read("NULL");
                    command.setType(CommandInterface.ALTER_TABLE_ALTER_COLUMN_NOT_NULL);
                    return command;
                } else if (readIf("DEFAULT")) {
                    Expression defaultExpression = readExpression();
                    command.setType(CommandInterface.ALTER_TABLE_ALTER_COLUMN_DEFAULT);
                    command.setDefaultExpression(defaultExpression);
                    return command;
                }
            } else if (readIf("RESTART")) {
                readIf("WITH");
                Expression start = readExpression();
                AlterSequence command = new AlterSequence(session, table.getSchema());
                command.setColumn(column);
                command.setStartWith(start);
                return command;
            } else if (readIf("SELECTIVITY")) {
                AlterTableAlterColumn command = new AlterTableAlterColumn(session, table.getSchema());
                command.setTable(table);
                command.setType(CommandInterface.ALTER_TABLE_ALTER_COLUMN_SELECTIVITY);
                command.setOldColumn(column);
                command.setSelectivity(readExpression());
                return command;
            } else {
                return parseAlterTableAlterColumnType(table, columnName, column);
            }
        }
        throw getSyntaxError();
    }

    private AlterTableAlterColumn parseAlterTableAlterColumnType(Table table, String columnName, Column column) {
        Column newColumn = parseColumnForTable(columnName, column.isNullable());
        AlterTableAlterColumn command = new AlterTableAlterColumn(session, table.getSchema());
        command.setTable(table);
        command.setType(CommandInterface.ALTER_TABLE_ALTER_COLUMN_CHANGE_TYPE);
        command.setOldColumn(column);
        command.setNewColumn(newColumn);
        return command;
    }

    private AlterTableAlterColumn parseAlterTableAddColumn(Table table) {
        readIf("COLUMN");
        Schema schema = table.getSchema();
        AlterTableAlterColumn command = new AlterTableAlterColumn(session, schema);
        command.setType(CommandInterface.ALTER_TABLE_ADD_COLUMN);
        command.setTable(table);
        ArrayList<Column> columnsToAdd = New.arrayList();
        if (readIf("(")) {
            command.setIfNotExists(false);
            do {
                String columnName = readColumnIdentifier(table);
                Column column = parseColumnForTable(columnName, true);
                columnsToAdd.add(column);
            } while (readIf(","));
            read(")");
            command.setNewColumns(columnsToAdd);
        } else {
            boolean ifNotExists = readIfNoExists();
            command.setIfNotExists(ifNotExists);
            String columnName = readColumnIdentifier(table);
            Column column = parseColumnForTable(columnName, true);
            columnsToAdd.add(column);
            if (readIf("BEFORE")) {
                command.setAddBefore(readColumnIdentifier(table));
            } else if (readIf("AFTER")) {
                command.setAddAfter(readColumnIdentifier());
            }
        }
        command.setNewColumns(columnsToAdd);
        return command;
    }

    private int parseAction() {
        Integer result = parseCascadeOrRestrict();
        if (result != null) {
            return result;
        }
        if (readIf("NO")) {
            read("ACTION");
            return ConstraintReferential.RESTRICT;
        }
        read("SET");
        if (readIf("NULL")) {
            return ConstraintReferential.SET_NULL;
        }
        read("DEFAULT");
        return ConstraintReferential.SET_DEFAULT;
    }

    private Integer parseCascadeOrRestrict() {
        if (readIf("CASCADE")) {
            return ConstraintReferential.CASCADE;
        } else if (readIf("RESTRICT")) {
            return ConstraintReferential.RESTRICT;
        } else {
            return null;
        }
    }

    protected DefineCommand parseAlterTableAddConstraintIf(String tableName, Schema schema) {
        String constraintName = null, comment = null;
        boolean ifNotExists = false;
        boolean allowIndexDefinition = database.getMode().indexDefinitionInCreateTable;
        if (readIf("CONSTRAINT")) {
            ifNotExists = readIfNoExists();
            constraintName = readIdentifierWithSchema(schema.getName());
            checkSchema(schema);
            comment = readCommentIf();
            allowIndexDefinition = true;
        }
        if (readIf("PRIMARY")) {
            read("KEY");
            AlterTableAddConstraint command = new AlterTableAddConstraint(session, schema, ifNotExists);
            command.setType(CommandInterface.ALTER_TABLE_ADD_CONSTRAINT_PRIMARY_KEY);
            command.setComment(comment);
            command.setConstraintName(constraintName);
            command.setTableName(tableName);
            if (readIf("HASH")) {
                command.setPrimaryKeyHash(true);
            }
            read("(");
            command.setIndexColumns(parseIndexColumnList());
            if (readIf("INDEX")) {
                String indexName = readIdentifierWithSchema();
                command.setIndex(getSchema().findIndex(session, indexName));
            }
            return command;
        } else if (allowIndexDefinition && (isToken("INDEX") || isToken("KEY"))) {
            // MySQL
            // need to read ahead, as it could be a column name
            int start = lastParseIndex;
            read();
            if (DataType.getTypeByName(currentToken) != null) {
                // known data type
                parseIndex = start;
                read();
                return null;
            }
            CreateIndex command = new CreateIndex(session, schema);
            command.setComment(comment);
            command.setTableName(tableName);
            if (!readIf("(")) {
                command.setIndexName(readUniqueIdentifier());
                read("(");
            }
            command.setIndexColumns(parseIndexColumnList());
            return command;
        }
        AlterTableAddConstraint command;
        if (readIf("CHECK")) {
            command = new AlterTableAddConstraint(session, schema, ifNotExists);
            command.setType(CommandInterface.ALTER_TABLE_ADD_CONSTRAINT_CHECK);
            command.setCheckExpression(readExpression());
        } else if (readIf("UNIQUE")) {
            readIf("KEY");
            readIf("INDEX");
            command = new AlterTableAddConstraint(session, schema, ifNotExists);
            command.setType(CommandInterface.ALTER_TABLE_ADD_CONSTRAINT_UNIQUE);
            if (!readIf("(")) {
                constraintName = readUniqueIdentifier();
                read("(");
            }
            command.setIndexColumns(parseIndexColumnList());
            if (readIf("INDEX")) {
                String indexName = readIdentifierWithSchema();
                command.setIndex(getSchema().findIndex(session, indexName));
            }
        } else if (readIf("FOREIGN")) {
            command = new AlterTableAddConstraint(session, schema, ifNotExists);
            command.setType(CommandInterface.ALTER_TABLE_ADD_CONSTRAINT_REFERENTIAL);
            read("KEY");
            read("(");
            command.setIndexColumns(parseIndexColumnList());
            if (readIf("INDEX")) {
                String indexName = readIdentifierWithSchema();
                command.setIndex(schema.findIndex(session, indexName));
            }
            read("REFERENCES");
            parseReferences(command, schema, tableName);
        } else {
            if (constraintName != null) {
                throw getSyntaxError();
            }
            return null;
        }
        if (readIf("NOCHECK")) {
            command.setCheckExisting(false);
        } else {
            readIf("CHECK");
            command.setCheckExisting(true);
        }
        command.setTableName(tableName);
        command.setConstraintName(constraintName);
        command.setComment(comment);
        return command;
    }

    private void parseReferences(AlterTableAddConstraint command, Schema schema, String tableName) {
        if (readIf("(")) {
            command.setRefTableName(schema, tableName);
            command.setRefIndexColumns(parseIndexColumnList());
        } else {
            String refTableName = readIdentifierWithSchema(schema.getName());
            command.setRefTableName(getSchema(), refTableName);
            if (readIf("(")) {
                command.setRefIndexColumns(parseIndexColumnList());
            }
        }
        if (readIf("INDEX")) {
            String indexName = readIdentifierWithSchema();
            command.setRefIndex(getSchema().findIndex(session, indexName));
        }
        while (readIf("ON")) {
            if (readIf("DELETE")) {
                command.setDeleteAction(parseAction());
            } else {
                read("UPDATE");
                command.setUpdateAction(parseAction());
            }
        }
        if (readIf("NOT")) {
            read("DEFERRABLE");
        } else {
            readIf("DEFERRABLE");
        }
    }

    protected Column parseColumn(Schema schema, CreateTable command, String tableName, String cfName) {
        String columnName = readColumnIdentifier();
        Column column = parseColumnForTable(columnName, true);
        if (cfName != null)
            column.setColumnFamilyName(cfName);
        if (column.isAutoIncrement() && column.isPrimaryKey()) {
            column.setPrimaryKey(false);
            column.setRowKeyColumn(true);
            IndexColumn[] cols = { new IndexColumn() };
            cols[0].columnName = column.getName();
            cols[0].column = column;
            AlterTableAddConstraint pk = new AlterTableAddConstraint(session, schema, false);
            pk.setType(CommandInterface.ALTER_TABLE_ADD_CONSTRAINT_PRIMARY_KEY);
            pk.setTableName(tableName);
            pk.setIndexColumns(cols);
            command.addConstraintCommand(pk);
        }
        command.addColumn(column);
        String constraintName = null;
        if (readIf("CONSTRAINT")) {
            constraintName = readColumnIdentifier();
        }
        if (readIf("ROW")) {
            read("KEY");
            column.setRowKeyColumn(true);
        } else if (readIf("PRIMARY")) {
            read("KEY");
            column.setRowKeyColumn(true);
            boolean hash = readIf("HASH");
            IndexColumn[] cols = { new IndexColumn() };
            cols[0].columnName = column.getName();
            cols[0].column = column;
            AlterTableAddConstraint pk = new AlterTableAddConstraint(session, schema, false);
            pk.setPrimaryKeyHash(hash);
            pk.setType(CommandInterface.ALTER_TABLE_ADD_CONSTRAINT_PRIMARY_KEY);
            pk.setTableName(tableName);
            pk.setIndexColumns(cols);
            command.addConstraintCommand(pk);
            if (readIf("AUTO_INCREMENT")) {
                parseAutoIncrement(column);
            }
        } else if (readIf("UNIQUE")) {
            AlterTableAddConstraint unique = new AlterTableAddConstraint(session, schema, false);
            unique.setConstraintName(constraintName);
            unique.setType(CommandInterface.ALTER_TABLE_ADD_CONSTRAINT_UNIQUE);
            IndexColumn[] cols = { new IndexColumn() };
            cols[0].columnName = columnName;
            cols[0].column = column;
            unique.setIndexColumns(cols);
            unique.setTableName(tableName);
            command.addConstraintCommand(unique);
        }
        if (readIf("NOT")) {
            read("NULL");
            column.setNullable(false);
        } else {
            readIf("NULL");
        }
        if (readIf("CHECK")) {
            Expression expr = readExpression();
            column.addCheckConstraint(session, expr);
        }
        if (readIf("REFERENCES")) {
            AlterTableAddConstraint ref = new AlterTableAddConstraint(session, schema, false);
            ref.setConstraintName(constraintName);
            ref.setType(CommandInterface.ALTER_TABLE_ADD_CONSTRAINT_REFERENTIAL);
            IndexColumn[] cols = { new IndexColumn() };
            cols[0].columnName = columnName;
            cols[0].column = column;
            ref.setIndexColumns(cols);
            ref.setTableName(tableName);
            parseReferences(ref, schema, tableName);
            command.addConstraintCommand(ref);
        }
        return column;
    }

    protected void parseTableDefinition(Schema schema, CreateTable command, String tableName) {
        do {
            DefineCommand c = parseAlterTableAddConstraintIf(tableName, schema);
            if (c != null) {
                command.addConstraintCommand(c);
            } else {
                parseColumn(schema, command, tableName, null);
            }
        } while (readIfMore());
    }

    protected CreateTable parseCreateTable(boolean temp, boolean globalTemp, boolean persistIndexes,
            boolean dynamicTable) {
        boolean ifNotExists = readIfNoExists();
        String tableName = readIdentifierWithSchema();
        if (temp && globalTemp && equalsToken("SESSION", schemaName)) {
            // support weird syntax: declare global temporary table session.xy
            // (...) not logged
            schemaName = session.getCurrentSchemaName();
            globalTemp = false;
        }
        Schema schema = getSchema();
        CreateTable command = createTable(session, schema);
        command.setPersistIndexes(persistIndexes);
        command.setTemporary(temp);
        command.setGlobalTemporary(globalTemp);
        command.setIfNotExists(ifNotExists);
        command.setTableName(tableName);
        command.setComment(readCommentIf());
        command.setDynamicTable(dynamicTable);
        if (readIf("(")) {
            if (!readIf(")")) {
                parseTableDefinition(schema, command, tableName);
            }
        }
        if (readIf("ENGINE")) {
            if (readIf("=")) {
                // map MySQL engine types onto H2 behavior
                String storageEngine = readUniqueIdentifier();
                if ("InnoDb".equalsIgnoreCase(storageEngine)) {
                    // ok
                } else if (!"MyISAM".equalsIgnoreCase(storageEngine)) {
                    throw DbException.get(ErrorCode.FEATURE_NOT_SUPPORTED_1, storageEngine);
                }
            } else {
                command.setStorageEngine(readUniqueIdentifier());
            }
        } else if (database.getSettings().defaultStorageEngine != null) {
            command.setStorageEngine(database.getSettings().defaultStorageEngine);
        }
        if (readIf("CHARSET")) {
            read("=");
            read("UTF8");
        }
        if (temp) {
            if (readIf("ON")) {
                read("COMMIT");
                if (readIf("DROP")) {
                    command.setOnCommitDrop();
                } else if (readIf("DELETE")) {
                    read("ROWS");
                    command.setOnCommitTruncate();
                }
            } else if (readIf("NOT")) {
                if (readIf("PERSISTENT")) {
                    command.setPersistData(false);
                } else {
                    read("LOGGED");
                }
            }
            if (readIf("TRANSACTIONAL")) {
                command.setTransactional(true);
            }
        } else if (!persistIndexes && readIf("NOT")) {
            read("PERSISTENT");
            command.setPersistData(false);
        }
        if (readIf("HIDDEN")) {
            command.setHidden(true);
        }
        if (readIf("AS")) {
            if (readIf("SORTED")) {
                command.setSortedInsertMode(true);
            }
            command.setQuery(parseSelect());
        }
        return command;
    }

    private static int getCompareType(int tokenType) {
        switch (tokenType) {
        case EQUAL:
            return Comparison.EQUAL;
        case BIGGER_EQUAL:
            return Comparison.BIGGER_EQUAL;
        case BIGGER:
            return Comparison.BIGGER;
        case SMALLER:
            return Comparison.SMALLER;
        case SMALLER_EQUAL:
            return Comparison.SMALLER_EQUAL;
        case NOT_EQUAL:
            return Comparison.NOT_EQUAL;
        default:
            return -1;
        }
    }

    /**
     * Add double quotes around an identifier if required.
     *
     * @param s the identifier
     * @return the quoted identifier
     */
    public static String quoteIdentifier(String s) {
        if (s == null || s.length() == 0) {
            return "\"\"";
        }
        char c = s.charAt(0);
        // lowercase a-z is quoted as well
        if ((!Character.isLetter(c) && c != '_') || Character.isLowerCase(c)) {
            return StringUtils.quoteIdentifier(s);
        }
        for (int i = 1, length = s.length(); i < length; i++) {
            c = s.charAt(i);
            if ((!Character.isLetterOrDigit(c) && c != '_') || Character.isLowerCase(c)) {
                return StringUtils.quoteIdentifier(s);
            }
        }
        if (Parser.isKeyword(s, true)) {
            return StringUtils.quoteIdentifier(s);
        }
        return s;
    }

    public void setRightsChecked(boolean rightsChecked) {
        this.rightsChecked = rightsChecked;
    }

    /**
     * Parse a SQL code snippet that represents an expression.
     *
     * @param sql the code snippet
     * @return the expression object
     */
    public Expression parseExpression(String sql) {
        parameters = New.arrayList();
        initialize(sql);
        read();
        return readExpression();
    }

    /**
     * Parse a SQL code snippet that represents a table name.
     *
     * @param sql the code snippet
     * @return the table object
     */
    public Table parseTableName(String sql) {
        parameters = New.arrayList();
        initialize(sql);
        read();
        return readTableOrView();
    }

    public Insert createInsert(Session session) {
        return new Insert(session);
    }

    public Update createUpdate(Session session) {
        return new Update(session);
    }

    public Delete createDelete(Session session) {
        return new Delete(session);
    }

    public Select createSelect(Session session) {
        return new Select(session);
    }

    public Merge createMerge(Session session) {
        return new Merge(session);
    }

    public Command createCommand(Prepared p, String sql) {
        return new CommandContainer(this, sql, p);
    }

    public CreateTable createTable(Session session, Schema schema) {
        return new CreateTable(session, schema);
    }

    public DefineCommandWrapper createDefineCommandWrapper(Session session, DefineCommand defineCommand) {
        return new DefineCommandWrapper(session, defineCommand);
    }
}
