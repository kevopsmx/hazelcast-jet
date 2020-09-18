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

import com.hazelcast.jet.JetInstance;
import com.hazelcast.jet.Job;
import com.hazelcast.jet.JobStateSnapshot;
import com.hazelcast.jet.sql.impl.JetPlan.AlterJobPlan;
import com.hazelcast.jet.sql.impl.JetPlan.CreateJobPlan;
import com.hazelcast.jet.sql.impl.JetPlan.CreateMappingPlan;
import com.hazelcast.jet.sql.impl.JetPlan.CreateSnapshotPlan;
import com.hazelcast.jet.sql.impl.JetPlan.DropJobPlan;
import com.hazelcast.jet.sql.impl.JetPlan.DropMappingPlan;
import com.hazelcast.jet.sql.impl.JetPlan.DropSnapshotPlan;
import com.hazelcast.jet.sql.impl.JetPlan.ExecutionPlan;
import com.hazelcast.jet.sql.impl.schema.MappingCatalog;
import com.hazelcast.sql.SqlResult;
import com.hazelcast.sql.impl.QueryException;
import com.hazelcast.sql.impl.QueryId;
import com.hazelcast.sql.impl.QueryResultProducer;
import com.hazelcast.sql.impl.SqlResultImpl;

import java.util.Map;

class JetPlanExecutor {

    private final MappingCatalog catalog;
    private final JetInstance jetInstance;
    private final Map<QueryId, QueryResultProducer> resultConsumerRegistry;

    JetPlanExecutor(
            MappingCatalog catalog,
            JetInstance jetInstance,
            Map<QueryId, QueryResultProducer> resultConsumerRegistry
    ) {
        this.catalog = catalog;
        this.jetInstance = jetInstance;
        this.resultConsumerRegistry = resultConsumerRegistry;
    }

    SqlResult execute(CreateMappingPlan plan) {
        catalog.createMapping(plan.mapping(), plan.replace(), plan.ifNotExists());
        return SqlResultImpl.createUpdateCountResult(-1);
    }

    SqlResult execute(DropMappingPlan plan) {
        catalog.removeMapping(plan.name(), plan.ifExists());
        return SqlResultImpl.createUpdateCountResult(-1);
    }

    SqlResult execute(CreateJobPlan plan) {
        if (plan.isIfNotExists()) {
            jetInstance.newJobIfAbsent(plan.getExecutionPlan().getDag(), plan.getJobConfig());
        } else {
            jetInstance.newJob(plan.getExecutionPlan().getDag(), plan.getJobConfig());
        }
        return SqlResultImpl.createUpdateCountResult(-1);
    }

    SqlResult execute(AlterJobPlan plan) {
        Job job = jetInstance.getJob(plan.getJobName());
        if (job == null) {
            throw QueryException.error("The job '" + plan.getJobName() + "' doesn't exist");
        }
        switch (plan.getOperation()) {
            case SUSPEND:
                job.suspend();
                break;

            case RESUME:
                job.resume();
                break;

            case RESTART:
                job.restart();
                break;

            default:
        }
        return SqlResultImpl.createUpdateCountResult(0);
    }

    SqlResult execute(DropJobPlan plan) {
        Job job = jetInstance.getJob(plan.getJobName());
        if (job == null || job.getStatus().isTerminal()) {
            if (plan.isIfExists()) {
                return SqlResultImpl.createUpdateCountResult(-1);
            }
            throw QueryException.error("Job doesn't exist or already terminated: " + plan.getJobName());
        }
        if (plan.getWithSnapshotName() != null) {
            job.cancelAndExportSnapshot(plan.getWithSnapshotName());
        } else {
            job.cancel();
        }
        return SqlResultImpl.createUpdateCountResult(-1);
    }

    SqlResult execute(CreateSnapshotPlan plan) {
        Job job = jetInstance.getJob(plan.getJobName());
        if (job == null) {
            throw QueryException.error("The job '" + plan.getJobName() + "' doesn't exist");
        }
        job.exportSnapshot(plan.getSnapshotName());
        return SqlResultImpl.createUpdateCountResult(0);
    }

    SqlResult execute(DropSnapshotPlan plan) {
        JobStateSnapshot snapshot = jetInstance.getJobStateSnapshot(plan.getSnapshotName());
        if (snapshot == null) {
            if (plan.isIfExists()) {
                return SqlResultImpl.createUpdateCountResult(0);
            }
            throw QueryException.error("The snapshot doesnt exist: " + plan.getSnapshotName());
        }
        snapshot.destroy();
        return SqlResultImpl.createUpdateCountResult(0);
    }

    SqlResult execute(ExecutionPlan plan) {
        if (plan.isInsert()) {
            Job job = jetInstance.newJob(plan.getDag());

            if (!plan.isStreaming()) {
                job.join();
            }

            return SqlResultImpl.createUpdateCountResult(-1);
        } else {
            QueryResultProducer queryResultProducer = new JetQueryResultProducer();
            Object oldValue = resultConsumerRegistry.put(plan.getQueryId(), queryResultProducer);
            assert oldValue == null : oldValue;

            jetInstance.newJob(plan.getDag());

            return new JetSqlResultImpl(plan.getQueryId(), queryResultProducer, plan.getRowMetadata());
        }
    }
}