package org.labkey.blast.pipeline;

import org.labkey.api.data.Container;
import org.labkey.api.files.FileUrls;
import org.labkey.api.pipeline.PipeRoot;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineJobService;
import org.labkey.api.pipeline.TaskId;
import org.labkey.api.pipeline.TaskPipeline;
import org.labkey.api.security.User;
import org.labkey.api.util.GUID;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.ViewBackgroundInfo;
import org.labkey.blast.BLASTManager;
import org.labkey.blast.model.BlastJob;

import java.io.File;

/**
 * User: bimber
 * Date: 7/21/2014
 * Time: 10:33 AM
 */
public class BlastDatabasePipelineJob extends PipelineJob
{
    private Integer _libraryId;
    private String _databaseGuid;

    public BlastDatabasePipelineJob(Container c, User user, ActionURL url, PipeRoot pipeRoot, Integer libraryId)
    {
        super(null, new ViewBackgroundInfo(c, user, url), pipeRoot);
        _libraryId = libraryId;
        _databaseGuid = new GUID().toString().toUpperCase();
        setLogFile(new File(BLASTManager.get().getDatabaseDir(), "blast-" + _databaseGuid + ".log"));
    }

    @Override
    public String getDescription()
    {
        return "Create BLAST Database";
    }

    @Override
    public ActionURL getStatusHref()
    {
        return PageFlowUtil.urlProvider(FileUrls.class).urlBegin(getContainer());
    }

    @Override
    public TaskPipeline getTaskPipeline()
    {
        return PipelineJobService.get().getTaskPipeline(new TaskId(BlastDatabasePipelineJob.class));
    }

    public Integer getLibraryId()
    {
        return _libraryId;
    }

    public String getDatabaseGuid()
    {
        return _databaseGuid;
    }
}