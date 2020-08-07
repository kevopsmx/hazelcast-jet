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

package com.hazelcast.jet.sql.impl.connector.file;

import com.hazelcast.function.FunctionEx;
import com.hazelcast.function.SupplierEx;
import com.hazelcast.jet.core.ProcessorMetaSupplier;
import com.hazelcast.jet.impl.connector.ReadFilesP;
import com.hazelcast.jet.sql.impl.connector.RowProjector;
import com.hazelcast.jet.sql.impl.extract.AvroQueryTarget;
import com.hazelcast.jet.sql.impl.schema.ExternalField;
import com.hazelcast.sql.impl.expression.Expression;
import com.hazelcast.sql.impl.schema.TableField;
import com.hazelcast.sql.impl.type.QueryDataType;
import org.apache.avro.Schema;
import org.apache.avro.file.DataFileReader;
import org.apache.avro.generic.GenericDatumReader;
import org.apache.avro.generic.GenericRecord;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import static com.hazelcast.jet.impl.util.Util.uncheckRun;
import static com.hazelcast.jet.sql.impl.connector.file.AvroMetadataResolver.fields;
import static com.hazelcast.jet.sql.impl.connector.file.AvroMetadataResolver.paths;
import static com.hazelcast.jet.sql.impl.connector.file.AvroMetadataResolver.types;

final class LocalAvroMetadataResolver {

    private LocalAvroMetadataResolver() {
    }

    static Metadata resolve(List<ExternalField> externalFields, FileOptions options) throws IOException {
        return !externalFields.isEmpty()
                ? resolveFromFields(externalFields, options)
                : resolveFromSample(options);
    }

    private static Metadata resolveFromFields(List<ExternalField> externalFields, FileOptions options) {
        List<TableField> fields = fields(externalFields);

        return new Metadata(
                new AvroTargetDescriptor(options.path(), options.glob(), options.sharedFileSystem()),
                fields
        );
    }

    private static Metadata resolveFromSample(FileOptions options) throws IOException {
        String path = options.path();
        String glob = options.glob();

        Schema schema = schema(path, glob);
        List<TableField> fields = fields(schema);

        return new Metadata(
                new AvroTargetDescriptor(path, glob, options.sharedFileSystem()),
                fields
        );
    }

    private static Schema schema(String directory, String glob) throws IOException {
        for (Path path : Files.newDirectoryStream(Paths.get(directory), glob)) { // TODO: directory check
            File file = path.toFile();

            try (DataFileReader<GenericRecord> reader = new DataFileReader<>(file, new GenericDatumReader<>())) {
                return reader.getSchema();
            }
        }
        throw new IllegalArgumentException("No data found in '" + directory + "/" + glob + "'");
    }

    private static class AvroTargetDescriptor implements TargetDescriptor {

        private final String path;
        private final String glob;
        private final boolean sharedFileSystem;

        private AvroTargetDescriptor(
                String path,
                String glob,
                boolean sharedFileSystem
        ) {
            this.path = path;
            this.glob = glob;
            this.sharedFileSystem = sharedFileSystem;
        }

        @Override
        public ProcessorMetaSupplier processor(
                List<TableField> fields,
                Expression<Boolean> predicate,
                List<Expression<?>> projection
        ) {
            String[] paths = paths(fields);
            QueryDataType[] types = types(fields);

            SupplierEx<RowProjector> projectorSupplier =
                    () -> new RowProjector(new AvroQueryTarget(), paths, types, predicate, projection);

            FunctionEx<? super Path, ? extends Stream<Object[]>> readFileFn = path -> {
                RowProjector projector = projectorSupplier.get();

                DataFileReader<GenericRecord> reader = new DataFileReader<>(path.toFile(), new GenericDatumReader<>());
                return StreamSupport.stream(reader.spliterator(), false)
                                    .map(projector::project)
                                    .filter(Objects::nonNull)
                                    .onClose(() -> uncheckRun(reader::close));
            };

            return ReadFilesP.metaSupplier(path, glob, sharedFileSystem, readFileFn);
        }
    }
}