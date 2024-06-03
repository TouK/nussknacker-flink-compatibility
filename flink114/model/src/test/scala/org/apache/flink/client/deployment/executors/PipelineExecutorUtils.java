/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.flink.client.deployment.executors;

import org.apache.flink.api.common.JobID;
import org.apache.flink.api.dag.Pipeline;
import org.apache.flink.client.FlinkPipelineTranslationUtil;
import org.apache.flink.client.cli.ExecutionConfigAccessor;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.configuration.PipelineOptionsInternal;
import org.apache.flink.runtime.jobgraph.JobGraph;

import javax.annotation.Nonnull;

import java.net.MalformedURLException;

import static org.apache.flink.util.Preconditions.checkNotNull;

// the class is copied from flink release-1.14 and pathed because we bind in MiniClusterExecutionEnvironment to flink16+ version with getJobGraph(Pipeline, Configuration, ClassLoader)
// but the method in flink-1.14 doesn't have ClassLoader argument which leads to binary incompatibility
public class PipelineExecutorUtils {

    public static JobGraph getJobGraph(
            @Nonnull final Pipeline pipeline,
            @Nonnull final Configuration configuration,
            @Nonnull ClassLoader userClassloader // dummy parameter <- the only one line changed
    ) throws MalformedURLException {
        checkNotNull(pipeline);
        checkNotNull(configuration);

        final ExecutionConfigAccessor executionConfigAccessor =
                ExecutionConfigAccessor.fromConfiguration(configuration);
        final JobGraph jobGraph =
                FlinkPipelineTranslationUtil.getJobGraph(
                        pipeline, configuration, executionConfigAccessor.getParallelism());

        configuration
                .getOptional(PipelineOptionsInternal.PIPELINE_FIXED_JOB_ID)
                .ifPresent(strJobID -> jobGraph.setJobID(JobID.fromHexString(strJobID)));

        jobGraph.addJars(executionConfigAccessor.getJars());
        jobGraph.setClasspaths(executionConfigAccessor.getClasspaths());
        jobGraph.setSavepointRestoreSettings(executionConfigAccessor.getSavepointRestoreSettings());

        return jobGraph;
    }
}
