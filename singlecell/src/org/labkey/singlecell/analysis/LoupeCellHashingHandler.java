package org.labkey.singlecell.analysis;

import org.json.JSONObject;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.pipeline.RecordedAction;
import org.labkey.api.sequenceanalysis.SequenceOutputFile;
import org.labkey.api.sequenceanalysis.model.Readset;
import org.labkey.api.sequenceanalysis.pipeline.AbstractParameterizedOutputHandler;
import org.labkey.api.sequenceanalysis.pipeline.SequenceAnalysisJobSupport;
import org.labkey.api.sequenceanalysis.pipeline.SequenceOutputHandler;
import org.labkey.api.sequenceanalysis.pipeline.ToolParameterDescriptor;
import org.labkey.api.singlecell.CellHashingService;
import org.labkey.api.util.FileType;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.singlecell.CellHashingServiceImpl;
import org.labkey.singlecell.SingleCellModule;

import java.io.File;
import java.io.FileFilter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

public class LoupeCellHashingHandler extends AbstractParameterizedOutputHandler<SequenceOutputHandler.SequenceOutputProcessor>
{
    private FileType _fileType = new FileType("cloupe", false);
    public static String CATEGORY = "10x GEX Cell Hashing Calls";

    public LoupeCellHashingHandler()
    {
        super(ModuleLoader.getInstance().getModule(SingleCellModule.class), "CellRanger GEX/Cell Hashing", "This will run CiteSeqCount/cellhashR to generate a sample-to-cellbarcode TSV based on the filtered barcodes from CellRanger.", new LinkedHashSet<>(PageFlowUtil.set("sequenceanalysis/field/CellRangerAggrTextarea.js")), getDefaultParams());
    }

    private static List<ToolParameterDescriptor> getDefaultParams()
    {
        List<ToolParameterDescriptor> ret = new ArrayList<>(CellHashingService.get().getDefaultHashingParams(true));
        ret.add(
                ToolParameterDescriptor.create("useOutputFileContainer", "Submit to Source File Workbook", "If checked, each job will be submitted to the same workbook as the input file, as opposed to submitting all jobs to the same workbook.  This is primarily useful if submitting a large batch of files to process separately. This only applies if 'Run Separately' is selected.", "checkbox", new JSONObject(){{
                    put("checked", true);
                }}, true)
        );

        return ret;
    }

    @Override
    public boolean canProcess(SequenceOutputFile o)
    {
        return o.getFile() != null && _fileType.isType(o.getFile());
    }

    @Override
    public boolean doRunRemote()
    {
        return true;
    }

    @Override
    public boolean doRunLocal()
    {
        return false;
    }

    @Override
    public SequenceOutputProcessor getProcessor()
    {
        return new LoupeCellHashingHandler.Processor();
    }

    @Override
    public boolean doSplitJobs()
    {
        return true;
    }

    @Override
    public boolean requiresSingleGenome()
    {
        return false;
    }

    public class Processor implements SequenceOutputHandler.SequenceOutputProcessor
    {
        @Override
        public void init(JobContext ctx, List<SequenceOutputFile> inputFiles, List<RecordedAction> actions, List<SequenceOutputFile> outputsToCreate) throws UnsupportedOperationException, PipelineJobException
        {
            CellHashingService.get().prepareHashingAndCiteSeqFilesIfNeeded(ctx.getOutputDir(), ctx.getJob(), ctx.getSequenceSupport(), "readsetId", ctx.getParams().optBoolean("excludeFailedcDNA", true), true, false);
        }

        @Override
        public void processFilesOnWebserver(PipelineJob job, SequenceAnalysisJobSupport support, List<SequenceOutputFile> inputFiles, JSONObject params, File outputDir, List<RecordedAction> actions, List<SequenceOutputFile> outputsToCreate) throws UnsupportedOperationException, PipelineJobException
        {

        }

        @Override
        public void processFilesRemote(List<SequenceOutputFile> inputFiles, SequenceOutputHandler.JobContext ctx) throws UnsupportedOperationException, PipelineJobException
        {
            RecordedAction action = new RecordedAction(getName());
            Map<Integer, Integer> readsetToHashing = CellHashingServiceImpl.get().getCachedHashingReadsetMap(ctx.getSequenceSupport());
            ctx.getLogger().debug("total cached readset to hashing pairs: " + readsetToHashing.size());

            for (SequenceOutputFile so : inputFiles)
            {
                ctx.getLogger().info("processing file: " + so.getName());

                //find TSV:
                File rawCellBarcodeWhitelistFile;
                File barcodeDir = null;
                for (String dirName : Arrays.asList("filtered_gene_bc_matrices", "filtered_feature_bc_matrix"))
                {
                    File f = new File(so.getFile().getParentFile(), dirName);
                    if (f.exists())
                    {
                        barcodeDir = f;
                        break;
                    }
                }

                if (barcodeDir == null)
                {
                    //this might be a re-analysis loupe directory.  in this case, use the tsne projection.csv as the whitelist:
                    File dir = new File(so.getFile().getParentFile(), "analysis");
                    dir = new File(dir, "tsne");
                    dir = new File(dir, "2_components");
                    if (!dir.exists())
                    {
                        throw new PipelineJobException("Unable to find barcode or analysis directory: " + dir.getPath());
                    }

                    rawCellBarcodeWhitelistFile = new File(dir, "projection.csv");
                }
                //cellranger 2 format
                else if ("filtered_gene_bc_matrices".equals(barcodeDir.getName()))
                {
                    File[] children = barcodeDir.listFiles(new FileFilter()
                    {
                        @Override
                        public boolean accept(File pathname)
                        {
                            return pathname.isDirectory();
                        }
                    });

                    if (children == null || children.length != 1)
                    {
                        throw new PipelineJobException("Expected to find a single subfolder under: " + barcodeDir.getPath());
                    }

                    rawCellBarcodeWhitelistFile = new File(children[0], "barcodes.tsv");
                }
                else
                {
                    rawCellBarcodeWhitelistFile = new File(barcodeDir, "barcodes.tsv.gz");
                }

                if (!rawCellBarcodeWhitelistFile.exists())
                {
                    throw new PipelineJobException("Unable to find file: " + rawCellBarcodeWhitelistFile.getPath());
                }

                Readset rs = ctx.getSequenceSupport().getCachedReadset(so.getReadset());
                if (rs == null)
                {
                    throw new PipelineJobException("Unable to find readset for outputfile: " + so.getRowid());
                }
                else if (rs.getReadsetId() == null)
                {
                    throw new PipelineJobException("Readset lacks a rowId for outputfile: " + so.getRowid());
                }

                Readset htoReadset = ctx.getSequenceSupport().getCachedReadset(readsetToHashing.get(rs.getReadsetId()));
                if (htoReadset == null)
                {
                    throw new PipelineJobException("Unable to find Hashing/Cite-seq readset for GEX readset: " + rs.getReadsetId());
                }

                List<String> htosPerReadset = CellHashingServiceImpl.get().getHtosForReadset(htoReadset.getReadsetId(), ctx.getSourceDirectory());
                if (htosPerReadset.size() > 1)
                {
                    ctx.getLogger().info("Total HTOs for readset: " + htosPerReadset.size());

                    CellHashingService.CellHashingParameters parameters = CellHashingService.CellHashingParameters.createFromJson(CellHashingService.BARCODE_TYPE.hashing, ctx.getSourceDirectory(), ctx.getParams(), htoReadset, rs, null);
                    parameters.cellBarcodeWhitelistFile = CellHashingServiceImpl.get().createCellBarcodeWhitelistFromLoupe(ctx, rawCellBarcodeWhitelistFile);
                    parameters.genomeId = so.getLibrary_id();
                    parameters.outputCategory = CATEGORY;
                    parameters.allowableHtoOrCiteseqBarcodes = htosPerReadset;

                    CellHashingService.get().processCellHashingOrCiteSeq(ctx.getFileManager(), ctx.getOutputDir(), ctx.getSourceDirectory(), ctx.getLogger(), parameters);
                }
                else if (htosPerReadset.size() == 1)
                {
                    ctx.getLogger().info("Only single HTO used for lane, skipping cell hashing calling");
                }
                else
                {
                    ctx.getLogger().info("No HTOs found for readset");
                }
            }

            ctx.addActions(action);
        }

        @Override
        public void complete(PipelineJob job, List<SequenceOutputFile> inputs, List<SequenceOutputFile> outputsCreated, SequenceAnalysisJobSupport support) throws PipelineJobException
        {
            for (SequenceOutputFile so : outputsCreated)
            {
                if (so.getCategory().equals(CATEGORY))
                {
                    CellHashingService.get().processMetrics(so, job, true);
                }
            }
        }
    }
}