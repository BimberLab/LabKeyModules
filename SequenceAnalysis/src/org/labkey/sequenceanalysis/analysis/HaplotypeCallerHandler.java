package org.labkey.sequenceanalysis.analysis;

import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Logger;
import org.json.JSONObject;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.pipeline.RecordedAction;
import org.labkey.api.sequenceanalysis.SequenceOutputFile;
import org.labkey.api.sequenceanalysis.pipeline.AbstractParameterizedOutputHandler;
import org.labkey.api.sequenceanalysis.pipeline.ReferenceGenome;
import org.labkey.api.sequenceanalysis.pipeline.SequenceAnalysisJobSupport;
import org.labkey.api.sequenceanalysis.pipeline.ToolParameterDescriptor;
import org.labkey.api.util.FileType;
import org.labkey.api.util.FileUtil;
import org.labkey.sequenceanalysis.SequenceAnalysisModule;
import org.labkey.sequenceanalysis.run.analysis.HaplotypeCallerAnalysis;
import org.labkey.sequenceanalysis.run.util.HaplotypeCallerWrapper;

import java.io.File;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * Created by bimber on 2/3/2016.
 */
public class HaplotypeCallerHandler extends AbstractParameterizedOutputHandler
{
    private FileType _bamFileType = new FileType("bam", false);

    public HaplotypeCallerHandler()
    {
        super(ModuleLoader.getInstance().getModule(SequenceAnalysisModule.class), "Run GATK HaplotypeCaller", "This will run GATK HaplotypeCaller on the selected BAMs to generate gVCF files.", null, HaplotypeCallerAnalysis.getToolDescriptors());
    }

    @Override
    public boolean canProcess(SequenceOutputFile o)
    {
        return o.getFile() != null && _bamFileType.isType(o.getFile());
    }

    @Override
    public List<String> validateParameters(JSONObject params)
    {
        return null;
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
    public OutputProcessor getProcessor()
    {
        return new Processor();
    }

    @Override
    public boolean doSplitJobs()
    {
        return true;
    }

    public class Processor implements OutputProcessor
    {
        @Override
        public void init(PipelineJob job, SequenceAnalysisJobSupport support, List<SequenceOutputFile> inputFiles, JSONObject params, File outputDir, List<RecordedAction> actions, List<SequenceOutputFile> outputsToCreate) throws UnsupportedOperationException, PipelineJobException
        {

        }

        @Override
        public void processFilesRemote(PipelineJob job, SequenceAnalysisJobSupport support, List<SequenceOutputFile> inputFiles, JSONObject params, File outputDir, List<RecordedAction> actions, List<SequenceOutputFile> outputsToCreate) throws UnsupportedOperationException, PipelineJobException
        {
            if (inputFiles.isEmpty())
            {
                job.getLogger().warn("no input files");
            }

            for (SequenceOutputFile so : inputFiles)
            {
                RecordedAction action = new RecordedAction(getName());
                action.setStartTime(new Date());

                action.addInput(so.getFile(), "Input BAM File");

                File outputFile = new File(outputDir, FileUtil.getBaseName(so.getFile()) + ".g.vcf.gz");
                File idxFile = new File(outputDir, FileUtil.getBaseName(so.getFile()) + ".g.vcf.gz.idx");

                if (params.optBoolean("multithreaded", false))
                {
                    job.getLogger().debug("HaplotypeCaller will run multi-threaded");
                    getWrapper(job.getLogger()).setMultiThreaded(true);
                }

                getWrapper(job.getLogger()).setOutputDir(outputDir);

                ReferenceGenome referenceGenome = support.getCachedGenome(so.getLibrary_id());
                if (referenceGenome == null)
                {
                    throw new PipelineJobException("No reference genome found for output: " + so.getRowid());
                }

                if (params.optBoolean("useQueue", false))
                {
                    getWrapper(job.getLogger()).executeWithQueue(so.getFile(), referenceGenome.getWorkingFastaFile(), outputFile, getClientCommandArgs(params));
                }
                else
                {
                    List<String> args = new ArrayList<>();
                    args.addAll(getClientCommandArgs(params));
                    args.add("--emitRefConfidence");
                    args.add("GVCF");

                    getWrapper(job.getLogger()).execute(so.getFile(), referenceGenome.getWorkingFastaFile(), outputFile, args);
                }

                action.addOutput(outputFile, "gVCF File", false);
                if (idxFile.exists())
                {
                    action.addOutput(idxFile, "VCF Index", false);
                }

                SequenceOutputFile o = new SequenceOutputFile();
                o.setName(outputFile.getName());
                o.setFile(outputFile);
                o.setLibrary_id(so.getLibrary_id());
                o.setCategory("gVCF File");
                o.setReadset(so.getReadset());
                outputsToCreate.add(o);

                actions.add(action);
            }
        }

        private List<String> getClientCommandArgs(JSONObject params)
        {
            List<String> ret = new ArrayList<>();

            for (ToolParameterDescriptor desc : getParameters())
            {
                if (desc.getCommandLineParam() != null)
                {
                    String val = params.optString(desc.getName(), null);
                    if (StringUtils.trimToNull(val) != null)
                    {
                        ret.addAll(desc.getCommandLineParam().getArguments(" ", val));
                    }
                }
            }

            return ret;
        }

        private HaplotypeCallerWrapper getWrapper(Logger log)
        {
            return new HaplotypeCallerWrapper(log);
        }

        @Override
        public void processFilesOnWebserver(PipelineJob job, SequenceAnalysisJobSupport support, List<SequenceOutputFile> inputFiles, JSONObject params, File outputDir, List<RecordedAction> actions, List<SequenceOutputFile> outputsToCreate) throws UnsupportedOperationException, PipelineJobException
        {

        }
    }
}