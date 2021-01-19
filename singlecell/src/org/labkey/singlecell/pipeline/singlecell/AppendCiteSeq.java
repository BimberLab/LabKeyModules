package org.labkey.singlecell.pipeline.singlecell;

import org.json.JSONObject;
import org.labkey.api.sequenceanalysis.pipeline.AbstractPipelineStepProvider;
import org.labkey.api.sequenceanalysis.pipeline.PipelineContext;
import org.labkey.api.sequenceanalysis.pipeline.ToolParameterDescriptor;
import org.labkey.api.singlecell.pipeline.SingleCellStep;

import java.io.File;
import java.io.PrintWriter;
import java.util.Arrays;
import java.util.List;

public class AppendCiteSeq extends AbstractOosapStep
{
    public AppendCiteSeq(PipelineContext ctx, Provider provider)
    {
        super(provider, ctx);
    }

    public static class Provider extends AbstractPipelineStepProvider<SingleCellStep>
    {
        public Provider()
        {
            super("AppendCiteSeq", "Append CITE-seq Data", "OOSAP", "If available, this will download and append CITE-seq data to the Seurat object(s).", Arrays.asList(
                ToolParameterDescriptor.create("normalizationMethod", "Normalization Method", "", "ldk-simplecombo", new JSONObject(){{
                    put("storeValues", "CLR");
                    put("initialValue", "CLR");
                }}, null)
            ), null, null);
        }


        @Override
        public AppendCiteSeq create(PipelineContext ctx)
        {
            return new AppendCiteSeq(ctx, this);
        }
    }

    @Override
    public Output execute(List<File> inputObjects, SeuratContext ctx)
    {
        return null;
    }
}