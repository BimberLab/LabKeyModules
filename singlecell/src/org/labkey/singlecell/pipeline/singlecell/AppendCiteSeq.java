package org.labkey.singlecell.pipeline.singlecell;

import org.json.JSONObject;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.sequenceanalysis.SequenceOutputFile;
import org.labkey.api.sequenceanalysis.model.Readset;
import org.labkey.api.sequenceanalysis.pipeline.AbstractPipelineStepProvider;
import org.labkey.api.sequenceanalysis.pipeline.PipelineContext;
import org.labkey.api.sequenceanalysis.pipeline.SequenceOutputHandler;
import org.labkey.api.singlecell.CellHashingService;
import org.labkey.api.singlecell.pipeline.SeuratToolParameter;
import org.labkey.api.singlecell.pipeline.SingleCellOutput;
import org.labkey.api.singlecell.pipeline.SingleCellStep;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.singlecell.analysis.CellRangerSeuratHandler;
import org.labkey.singlecell.analysis.SeuratCellHashingHandler;
import org.labkey.singlecell.analysis.SeuratCiteSeqHandler;

import java.io.File;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class AppendCiteSeq extends AbstractCellHashingCiteseqStep
{
    public AppendCiteSeq(PipelineContext ctx, Provider provider)
    {
        super(provider, ctx);
    }

    public static class Provider extends AbstractPipelineStepProvider<SingleCellStep>
    {
        public Provider()
        {
            super("AppendCiteSeq", "Possibly Append CITE-seq Data", "OOSAP", "If available, this will process and append CITE-seq data to the Seurat object(s).", Arrays.asList(
                SeuratToolParameter.create("normalizationMethod", "Normalization Method", "", "ldk-simplecombo", new JSONObject(){{
                    put("storeValues", "CLR");
                    put("initialValues", "CLR");
                }}, "CLR"),
                SeuratToolParameter.create("processingMethod", "Processing Method", "", "ldk-simplecombo", new JSONObject(){{
                    put("storeValues", "PCA-tSNE;Distance-tSNE");
                    put("initialValues", "PCA-tSNE");
                }}, "PCA-tSNE")
            ), null, null);
        }

        @Override
        public AppendCiteSeq create(PipelineContext ctx)
        {
            return new AppendCiteSeq(ctx, this);
        }
    }

    @Override
    public Collection<String> getRLibraries()
    {
        return PageFlowUtil.set("CellMembrane");
    }

    @Override
    public String getDockerContainerName()
    {
        return AbstractCellMembraneStep.CONTINAER_NAME;
    }

    @Override
    protected Map<Integer, File> prepareCountData(SingleCellOutput output, SequenceOutputHandler.JobContext ctx, List<SeuratObjectWrapper> inputObjects, String outputPrefix) throws PipelineJobException
    {
        Map<Integer, File> dataIdToCalls = new HashMap<>();

        for (SeuratObjectWrapper wrapper : inputObjects)
        {
            File finalOutput = null;
            if (wrapper.getSequenceOutputFileId() == null)
            {
                throw new PipelineJobException("Append CITE-seq is only support using seurat objects will a single input dataset. Consider moving this step easier in your pipeline, before merging or subsetting");
            }

            File allCellBarcodes = SeuratCellHashingHandler.getCellBarcodesFromSeurat(wrapper.getFile());

            //NOTE: by leaving null, it will simply drop the barcode prefix. Upstream checks should ensure this is a single-readset object
            File cellBarcodesParsed = CellRangerSeuratHandler.subsetBarcodes(allCellBarcodes, null);
            ctx.getFileManager().addIntermediateFile(cellBarcodesParsed);

            Readset parentReadset = ctx.getSequenceSupport().getCachedReadset(wrapper.getSequenceOutputFile().getReadset());
            if (parentReadset == null)
            {
                throw new PipelineJobException("Unable to find readset for outputfile: " + wrapper.getSequenceOutputFileId());
            }

            if (CellHashingService.get().usesCiteSeq(ctx.getSequenceSupport(), Collections.singletonList(wrapper.getSequenceOutputFile())))
            {
                CellHashingService.CellHashingParameters params = CellHashingService.CellHashingParameters.createFromStep(ctx, this, CellHashingService.BARCODE_TYPE.citeseq, null, parentReadset, cellBarcodesParsed);
                params.outputCategory = SeuratCiteSeqHandler.CATEGORY;
                params.createOutputFiles = true;
                params.genomeId = wrapper.getSequenceOutputFile().getLibrary_id();
                params.cellBarcodeWhitelistFile = cellBarcodesParsed;

                finalOutput = CellHashingService.get().processCellHashingOrCiteSeqForParent(parentReadset, output, ctx, params);
            }
            else
            {
                ctx.getLogger().info("CITE-seq not used, skipping: " + parentReadset.getName());
            }

            dataIdToCalls.put(wrapper.getSequenceOutputFileId(), finalOutput);
        }

        return dataIdToCalls;
    }

    @Override
    public boolean isIncluded(SequenceOutputHandler.JobContext ctx, List<SequenceOutputFile> inputs) throws PipelineJobException
    {
        return CellHashingService.get().usesCiteSeq(ctx.getSequenceSupport(), inputs);
    }
}
