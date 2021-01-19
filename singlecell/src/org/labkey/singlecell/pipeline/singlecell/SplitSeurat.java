package org.labkey.singlecell.pipeline.singlecell;

import org.json.JSONObject;
import org.labkey.api.sequenceanalysis.pipeline.AbstractPipelineStepProvider;
import org.labkey.api.sequenceanalysis.pipeline.PipelineContext;
import org.labkey.api.sequenceanalysis.pipeline.ToolParameterDescriptor;
import org.labkey.api.singlecell.pipeline.SingleCellStep;

import java.io.File;
import java.util.Arrays;
import java.util.List;

public class SplitSeurat extends AbstractOosapStep
{
    public SplitSeurat(PipelineContext ctx, SplitSeurat.Provider provider)
    {
        super(provider, ctx);
    }

    public static class Provider extends AbstractPipelineStepProvider<SingleCellStep>
    {
        public Provider()
        {
            super("SplitSeurat", "Split Seurat Objects", "OOSAP", "This will split each input seurat object into multiple objects.", Arrays.asList(
                    ToolParameterDescriptor.create("splitField", "Field Name", "", "textbox", new JSONObject(){{
                        put("allowBlank", false);
                    }}, null)
            ), null, null);
        }

        @Override
        public SplitSeurat create(PipelineContext ctx)
        {
            return new SplitSeurat(ctx, this);
        }
    }

    @Override
    public Output execute(List<File> inputObjects, SeuratContext ctx)
    {
        return null;
    }
}