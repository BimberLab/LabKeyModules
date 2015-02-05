package org.labkey.sequenceanalysis.pipeline;

import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.data.Table;
import org.labkey.api.data.TableInfo;
import org.labkey.api.pipeline.AbstractTaskFactory;
import org.labkey.api.pipeline.AbstractTaskFactorySettings;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.pipeline.RecordedActionSet;
import org.labkey.api.sequenceanalysis.SequenceOutputFile;
import org.labkey.api.util.FileType;
import org.labkey.sequenceanalysis.SequenceAnalysisSchema;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Created by bimber on 1/16/2015.
 */
public class SequenceOutputHandlerFinalTask extends PipelineJob.Task<SequenceOutputHandlerFinalTask.Factory>
{
    private static final String ACTION_NAME = "Processing Files";

    protected SequenceOutputHandlerFinalTask(Factory factory, PipelineJob job)
    {
        super(factory, job);
    }

    public static class Factory extends AbstractTaskFactory<AbstractTaskFactorySettings, Factory>
    {
        public Factory()
        {
            super(SequenceOutputHandlerFinalTask.class);
            setLocation("webserver-high-priority");
        }

        public List<FileType> getInputTypes()
        {
            return Collections.emptyList();
        }

        public String getStatusName()
        {
            return PipelineJob.TaskStatus.running.toString();
        }

        public List<String> getProtocolActionNames()
        {
            return Collections.singletonList(ACTION_NAME);
        }

        public PipelineJob.Task createTask(PipelineJob job)
        {
            return new SequenceOutputHandlerFinalTask(this, job);
        }

        public boolean isJobComplete(PipelineJob job)
        {
            return false;
        }
    }

    private SequenceOutputHandlerJob getPipelineJob()
    {
        return (SequenceOutputHandlerJob)getJob();
    }

    public RecordedActionSet run() throws PipelineJobException
    {
        Integer runId = SequenceTaskHelper.getExpRunIdForJob(getJob());
        getPipelineJob().setExperimentRunRowId(runId);

        if (!getPipelineJob().getOutputsCreated().isEmpty())
        {
            TableInfo ti = SequenceAnalysisSchema.getTable(SequenceAnalysisSchema.TABLE_OUTPUTFILES);
            for (SequenceOutputFile o : getPipelineJob().getOutputsCreated())
            {
                Map<String, Object> toUpdate = new CaseInsensitiveHashMap<>();
                toUpdate.put("rowid", o.getRowid());
                toUpdate.put("runid", runId);

                Table.update(getJob().getUser(), ti, toUpdate, o.getRowid());
            }
        }
        else
        {
            getJob().getLogger().info("no outputs created, nothing to do");
        }

        return new RecordedActionSet();
    }

}