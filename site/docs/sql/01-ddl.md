---
title: DDL syntax
description: DDL commands for Hazelcast Jet SQL
---

## CREATE, DROP EXTERNAL MAPPING

### Introduction

The SQL language works with _tables_ that have a fixed list of columns
with data types. To use a remote object as a table, an _external
mapping_ must be created first (except for local IMaps, which can be
queried without creating a mapping first).

The mapping specifies the table name, an optional column list with types
and connection and other parameters.

Be default, the mappings are created in the `public` schema. Jet doesn't
support user-created schemas currently. The implicit mappings for IMaps
are created in the `partitioned` schema. You can't drop mappings in this
schema, nor are they listed in the [information
schema](#information-schema).

We currently do not support `ALTER MAPPING` command, use the `CREATE OR
REPLACE MAPPING` command to replace the mapping definition or DROP the
mapping first. Changes to mappings do not affect any jobs that are
already running, only new jobs are affected.

### CREATE MAPPING Syntax

**CreateMapping ::=**
![CREATE MAPPING syntax](/docs/assets/ddl-CreateMapping.svg)

**ColumnList ::=**
![ColumnList syntax](/docs/assets/ddl-ColumnList.svg)

**TypeSpec ::=**
![TypeSpec syntax](/docs/assets/ddl-TypeSpec.svg)

**Options ::=**
![Options syntax](/docs/assets/ddl-Options.svg)

- `EXTERNAL`: an optional keyword, has no semantic meaning

- `OR REPLACE`: overwrite the mapping if it already exists.

- `IF NOT EXISTS`: do nothing if the external mapping already exists.

- `MappingName`: an SQL identifier that identifies the mapping in SQL
  queries.

- `ColumnName`, `ColumnType`: the name type of the column.

- `ExternalName`: the optional external name. If omitted, a
  connector-specific rules are used to derive it from the column name.
  For example for key-value connectors such as IMap or Kafka, unless the
  column name is `__key` or `this`, it is assumed to be a field of the
  value. See the connector specification for details.

- `TypeIdentifier`: the identifier of the connector type.

- `OptionName`, `OptionValue`: a connector-specific option. For a list
  of possible options check out the connector javadoc. The `OptionName`
  is a SQL identifier: it must be enclosed in double quotes if it
  contains special characters like the period (`.`), dash (`-`) etc. The
  `OptionValue` is a regular SQL string enclosed in apostrophes.

#### Auto-resolving of columns

The `ColumnList` is optional. If it is omitted, Jet will connect to the
remote system and try to resolve the columns in a connector-specific
way. For example, Jet can read a random message from a Kafka topic and
determine the column list from it. If Jet fails to resolve the columns
(most commonly if the remote object is empty), the DDL statement will
fail.

#### Example Without a Column List and Options

```sql
CREATE MAPPING my_table
TYPE IMap
```

As specified for the IMap connector, in this case the column list and
serialization options are automatically discovered from existing data in
the map.

#### A Full Example

In this example the field list and options are explicit.

```sql
CREATE MAPPING my_table(
    keyField1 INT EXTERNAL NAME "__key.field1",
    keyField2 VARCHAR EXTERNAL NAME "__key.field2",
    valueField BIGINT EXTERNAL NAME this
)
TYPE IMap
OPTIONS (
    "serialization.key.format" 'java',
    "serialization.key.java.class" 'com.example.KeyClass'
    "serialization.value.format" 'java',
    "serialization.value.java.class" 'com.example.ValueClass'
)
```

### DROP MAPPING Syntax

**DropMapping ::=**
![CREATE MAPPING syntax](/docs/assets/ddl-DropMapping.svg)

- `EXTERNAL`: an optional keyword, has no semantic meaning

- `IF EXISTS`: if the external mapping doesn't exist, do nothing; fail
  otherwise.

- `MappingName`: the name of the mapping

## Information Schema

The information about existing mappings is available through
`information_schema` tables.

Currently, two tables are exposed:

- `mappings`: contains information about existing mappings

- `columns`: contains information about mapping columns

To query the information schema, use:

```sql
SELECT * FROM information_schema.mappings
```

## Custom connectors

Adding SQL mappings is currently not a public API, we plan to define an
API in the future.