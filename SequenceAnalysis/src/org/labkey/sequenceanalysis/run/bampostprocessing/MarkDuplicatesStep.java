package org.labkey.sequenceanalysis.run.bampostprocessing;

import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.sequenceanalysis.model.Readset;
import org.labkey.api.util.FileUtil;
import org.labkey.api.sequenceanalysis.pipeline.AbstractPipelineStepProvider;
import org.labkey.api.sequenceanalysis.pipeline.BamProcessingStep;
import org.labkey.api.sequenceanalysis.pipeline.PipelineContext;
import org.labkey.api.sequenceanalysis.pipeline.PipelineStepProvider;
import org.labkey.api.sequenceanalysis.pipeline.ReferenceGenome;
import org.labkey.api.sequenceanalysis.run.AbstractCommandPipelineStep;
import org.labkey.api.sequenceanalysis.pipeline.CommandLineParam;
import org.labkey.api.sequenceanalysis.pipeline.ToolParameterDescriptor;
import org.labkey.sequenceanalysis.run.util.MarkDuplicatesWrapper;

import java.io.File;
import java.util.Arrays;

/**
 * User: bimber
 * Date: 6/15/2014
 * Time: 4:44 PM
 */
public class MarkDuplicatesStep extends AbstractCommandPipelineStep<MarkDuplicatesWrapper> implements BamProcessingStep
{
    public MarkDuplicatesStep(PipelineStepProvider provider, PipelineContext ctx)
    {
        super(provider, ctx, new MarkDuplicatesWrapper(ctx.getLogger()));
    }

    public static class Provider extends AbstractPipelineStepProvider<MarkDuplicatesStep>
    {
        public Provider()
        {
            super("MarkDuplicates", "Mark Duplicates", "Picard", "This runs Picard tools MarkDuplicates command in order to mark and/or remove duplicate reads.  Please note this can have implications for downstream analysis, because reads marked as duplicates are frequently omitted.  This is often desired, but can be a problem for sequencing of PCR products.", Arrays.asList(
                    ToolParameterDescriptor.createCommandLineParam(CommandLineParam.create("REMOVE_DUPLICATES"), "removeDuplicates", "Remove Duplicates", "If selected, duplicate reads will be removed, as opposed to flagged as duplicates.", "checkbox", null, null)
            ), null, "http://picard.sourceforge.net/command-line-overview.shtml");
        }

        @Override
        public MarkDuplicatesStep create(PipelineContext ctx)
        {
            return new MarkDuplicatesStep(this, ctx);
        }
    }

    @Override
    public Output processBam(Readset rs, File inputBam, ReferenceGenome referenceGenome, File outputDirectory) throws PipelineJobException
    {
        BamProcessingOutputImpl output = new BamProcessingOutputImpl();
        getWrapper().setOutputDir(outputDirectory);

        File outputBam = new File(outputDirectory, FileUtil.getBaseName(inputBam) + ".markduplicates.bam");
        output.addIntermediateFile(outputBam);

        File sortedBam = new File(outputDirectory, FileUtil.getBaseName(inputBam) + ".sorted.bam");
        boolean sortedPreexisting = sortedBam.exists();

        output.setBAM(getWrapper().executeCommand(inputBam, outputBam, getClientCommandArgs("=")));


        if (sortedBam.exists() && !sortedPreexisting)
        {
            output.addIntermediateFile(sortedBam);
        }

        //NOTE: depending on whether the BAM is sorted by the wrapper, the metrics file name will differ
        if (getWrapper().getMetricsFile(sortedBam).exists())
        {
            output.addIntermediateFile(getWrapper().getMetricsFile(sortedBam));
        }
        else if (getWrapper().getMetricsFile(inputBam).exists())
        {
            output.addIntermediateFile(getWrapper().getMetricsFile(inputBam));
        }

        return output;
    }
}
