package org.labkey.sequenceanalysis.run.alignment;

import au.com.bytecode.opencsv.CSVReader;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Logger;
import org.jetbrains.annotations.Nullable;
import org.json.JSONObject;
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.data.ConvertHelper;
import org.labkey.api.data.DbSchema;
import org.labkey.api.data.DbSchemaType;
import org.labkey.api.data.Table;
import org.labkey.api.data.TableInfo;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.reader.Readers;
import org.labkey.api.sequenceanalysis.model.AnalysisModel;
import org.labkey.api.sequenceanalysis.model.ReadData;
import org.labkey.api.sequenceanalysis.model.Readset;
import org.labkey.api.sequenceanalysis.pipeline.AbstractAlignmentStepProvider;
import org.labkey.api.sequenceanalysis.pipeline.AlignerIndexUtil;
import org.labkey.api.sequenceanalysis.pipeline.AlignmentOutputImpl;
import org.labkey.api.sequenceanalysis.pipeline.AlignmentStep;
import org.labkey.api.sequenceanalysis.pipeline.CommandLineParam;
import org.labkey.api.sequenceanalysis.pipeline.IndexOutputImpl;
import org.labkey.api.sequenceanalysis.pipeline.PipelineContext;
import org.labkey.api.sequenceanalysis.pipeline.PipelineStepProvider;
import org.labkey.api.sequenceanalysis.pipeline.ReferenceGenome;
import org.labkey.api.sequenceanalysis.pipeline.SequenceAnalysisJobSupport;
import org.labkey.api.sequenceanalysis.pipeline.SequencePipelineService;
import org.labkey.api.sequenceanalysis.pipeline.ToolParameterDescriptor;
import org.labkey.api.sequenceanalysis.run.AbstractCommandPipelineStep;
import org.labkey.api.sequenceanalysis.run.AbstractCommandWrapper;
import org.labkey.api.sequenceanalysis.run.SimpleScriptWrapper;
import org.labkey.api.util.FileUtil;
import org.labkey.api.util.PageFlowUtil;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class CellRangerWrapper extends AbstractCommandWrapper
{
    public CellRangerWrapper(@Nullable Logger logger)
    {
        super(logger);
    }

    public static class Provider extends AbstractAlignmentStepProvider<AlignmentStep>
    {
        public Provider()
        {
            super("CellRanger", "Cell Ranger is an alignment/analysis pipeline specific to 10x genomic data, and this can only be used on fastqs generated by 10x.", Arrays.asList(
                    ToolParameterDescriptor.create("id", "Run ID Suffix", "If provided, this will be appended to the ID of this run (readset name will be first).", "textfield", new JSONObject(){{
                        put("allowBlank", true);
                    }}, null),
                    ToolParameterDescriptor.createCommandLineParam(CommandLineParam.createSwitch("--nosecondary"), "nosecondary", "Skip Secondary Analysis", "Add this flag to skip secondary analysis of the gene-barcode matrix (dimensionality reduction, clustering and visualization). Set this if you plan to use cellranger reanalyze or your own custom analysis.", "checkbox", new JSONObject(){{

                    }}, null),
                    ToolParameterDescriptor.createCommandLineParam(CommandLineParam.create("--r1-length"), "r1-length", "R1 Read Length", "Use this value for the first read length.", "ldk-integerfield", new JSONObject(){{
                        put("minValue", 0);
                    }}, null),
                    ToolParameterDescriptor.createCommandLineParam(CommandLineParam.create("--r2-length"), "r2-length", "R2 Read Length", "Use this value for the second read length.", "ldk-integerfield", new JSONObject(){{
                        put("minValue", 0);
                    }}, null),
                    ToolParameterDescriptor.createCommandLineParam(CommandLineParam.create("--expect-cells"), "expect-cells", "Expect Cells", "Expected number of recovered cells.", "ldk-integerfield", new JSONObject(){{
                        put("minValue", 0);
                    }}, 5000),
                    ToolParameterDescriptor.createCommandLineParam(CommandLineParam.create("--force-cells"), "force-cells", "Force Cells", "Force pipeline to use this number of cells, bypassing the cell detection algorithm. Use this if the number of cells estimated by Cell Ranger is not consistent with the barcode rank plot.", "ldk-integerfield", new JSONObject(){{
                        put("minValue", 0);
                    }}, null),
                    ToolParameterDescriptor.createExpDataParam("gtfFile", "Gene File", "This is the ID of a GTF file containing genes from this genome.", "sequenceanalysis-genomefileselectorfield", new JSONObject()
                    {{
                        put("extensions", Arrays.asList("gtf"));
                        put("width", 400);
                        put("allowBlank", false);
                    }}, null)
            ), PageFlowUtil.set("sequenceanalysis/field/GenomeFileSelectorField.js"), "https://support.10xgenomics.com/single-cell-gene-expression/software/pipelines/latest/what-is-cell-ranger", true, false, ALIGNMENT_MODE.MERGE_THEN_ALIGN);
        }

        public String getName()
        {
            return "CellRanger";
        }

        public String getDescription()
        {
            return null;
        }

        public AlignmentStep create(PipelineContext context)
        {
            return new CellRangerWrapper.CellRangerAlignmentStep(this, context, new CellRangerWrapper(context.getLogger()));
        }
    }

    public static class CellRangerAlignmentStep extends AbstractCommandPipelineStep<CellRangerWrapper> implements AlignmentStep
    {
        public CellRangerAlignmentStep(PipelineStepProvider provider, PipelineContext ctx, CellRangerWrapper wrapper)
        {
            super(provider, ctx, wrapper);
        }

        @Override
        public boolean supportsGzipFastqs()
        {
            return true;
        }

        @Override
        public String getIndexCachedDirName(PipelineJob job)
        {
            Integer gtfId = getProvider().getParameterByName("gtfFile").extractValue(getPipelineCtx().getJob(), getProvider(), getStepIdx(), Integer.class);
            if (gtfId == null)
            {
                throw new IllegalArgumentException("Missing gtfFile parameter");
            }

            return "cellRanger-" + gtfId;
        }

        @Override
        public IndexOutput createIndex(ReferenceGenome referenceGenome, File outputDir) throws PipelineJobException
        {
            //NOTE: GTF filtering typically only necessary for pseudogenes.  Assume this occurs upstream.
            //cellranger mkgtf hg19-ensembl.gtf hg19-filtered-ensembl.gtf --attribute=gene_biotype:protein_coding

            Integer gtfId = getProvider().getParameterByName("gtfFile").extractValue(getPipelineCtx().getJob(), getProvider(), getStepIdx(), Integer.class);
            File gtfFile = getPipelineCtx().getSequenceSupport().getCachedData(gtfId);

            IndexOutputImpl output = new IndexOutputImpl(referenceGenome);

            File indexDir = new File(outputDir, getIndexCachedDirName(getPipelineCtx().getJob()));
            boolean hasCachedIndex = AlignerIndexUtil.hasCachedIndex(this.getPipelineCtx(), getIndexCachedDirName(getPipelineCtx().getJob()), referenceGenome);
            if (!hasCachedIndex)
            {
                getPipelineCtx().getLogger().info("Creating CellRanger Index");

                //remove if directory exists
                if (indexDir.exists())
                {
                    try
                    {
                        FileUtils.deleteDirectory(indexDir);
                    }
                    catch (IOException e)
                    {
                        throw new PipelineJobException(e);
                    }
                }

                output.addInput(gtfFile, "GTF File");

                List<String> args = new ArrayList<>();
                args.add(getWrapper().getExe().getPath());
                args.add("mkref");
                args.add("--fasta=" + referenceGenome.getWorkingFastaFile().getPath());
                args.add("--genes=" + gtfFile.getPath());
                args.add("--genome=" + indexDir.getName());

                Integer maxThreads = SequencePipelineService.get().getMaxThreads(getPipelineCtx().getLogger());
                if (maxThreads != null)
                {
                    args.add("--nthreads=" + maxThreads.toString());
                }

                Integer maxRam = SequencePipelineService.get().getMaxRam();
                if (maxRam != null)
                {
                    args.add("--memgb=" + maxRam.toString());
                }

                getWrapper().setWorkingDir(indexDir.getParentFile());
                getWrapper().execute(args);

                output.appendOutputs(referenceGenome.getWorkingFastaFile(), indexDir);

                //recache if not already
                AlignerIndexUtil.saveCachedIndex(hasCachedIndex, getPipelineCtx(), indexDir, getIndexCachedDirName(getPipelineCtx().getJob()), referenceGenome);
            }

            return output;
        }

        @Override
        public AlignmentOutput performAlignment(Readset rs, File inputFastq1, @Nullable File inputFastq2, File outputDirectory, ReferenceGenome referenceGenome, String basename, String readGroupId, @Nullable String platformUnit) throws PipelineJobException
        {
            AlignmentOutputImpl output = new AlignmentOutputImpl();

            List<String> args = new ArrayList<>();
            args.add(getWrapper().getExe().getPath());
            args.add("count");

            //TODO: consider always adding this?
            //args.add("--nosecondary");

            String idParam = StringUtils.trimToNull(getProvider().getParameterByName("id").extractValue(getPipelineCtx().getJob(), getProvider(), getStepIdx(), String.class));
            String id = FileUtil.makeLegalName(rs.getName()) + (idParam == null ? "" : "-" + idParam);
            id = id.replaceAll("[^a-zA-z0-9_\\-]", "_");
            args.add("--id=" + id);

            args.addAll(getClientCommandArgs("="));

            Integer maxThreads = SequencePipelineService.get().getMaxThreads(getPipelineCtx().getLogger());
            if (maxThreads != null)
            {
                args.add("--localcores=" + maxThreads.toString());
            }

            Integer maxRam = SequencePipelineService.get().getMaxRam();
            if (maxRam != null)
            {
                args.add("--localmem=" + maxRam.toString());
            }

            File localFqDir = new File(outputDirectory, "localFq");
            output.addIntermediateFile(localFqDir);
            Set<String> sampleNames = prepareFastqSymlinks(rs, localFqDir);
            args.add("--fastqs=" + localFqDir.getPath());

            getPipelineCtx().getLogger().debug("Sample names: [" + StringUtils.join(sampleNames, ",") + "]");
            if (sampleNames.size() > 1)
            {
                args.add("--sample=" + StringUtils.join(sampleNames, ","));
            }

            File indexDir = AlignerIndexUtil.getWebserverIndexDir(referenceGenome, getIndexCachedDirName(getPipelineCtx().getJob()));
            args.add("--transcriptome=" + indexDir.getPath());

            getWrapper().setWorkingDir(outputDirectory);
            getWrapper().execute(args);

            File outdir = new File(outputDirectory, id);
            outdir = new File(outdir, "outs");

            File bam = new File(outdir, "possorted_genome_bam.bam");
            if (!bam.exists())
            {
                throw new PipelineJobException("Unable to find file: " + bam.getPath());
            }
            output.setBAM(bam);

            deleteSymlinks(localFqDir);

            try
            {
                String prefix = FileUtil.makeLegalName(rs.getName() + "_");
                File outputHtml = new File(outdir, "web_summary.html");
                if (!outputHtml.exists())
                {
                    throw new PipelineJobException("Unable to find file: " + outputHtml.getPath());
                }

                File outputHtmlRename = new File(outdir, prefix + outputHtml.getName());
                if (outputHtmlRename.exists())
                {
                    outputHtmlRename.delete();
                }
                FileUtils.moveFile(outputHtml, outputHtmlRename);
                output.addSequenceOutput(outputHtmlRename, rs.getName() + " 10x Count Summary", "10x Run Summary", rs.getRowId(), null, referenceGenome.getGenomeId(), null);

                File loupe = new File(outdir, "cloupe.cloupe");
                if (loupe.exists())
                {
                    File loupeRename = new File(outdir, prefix + loupe.getName());
                    if (loupeRename.exists())
                    {
                        loupeRename.delete();
                    }
                    FileUtils.moveFile(loupe, loupeRename);
                    output.addSequenceOutput(loupeRename, rs.getName() + " 10x Loupe File", "10x Loupe File", rs.getRowId(), null, referenceGenome.getGenomeId(), null);
                }
                else
                {
                    getPipelineCtx().getLogger().info("loupe file not found: " + loupe.getPath());
                }
            }
            catch (IOException e)
            {
                throw new PipelineJobException(e);
            }

            //NOTE: this folder has many unnecessary files and symlinks that get corrupted when we rename the main outputs
            File directory = new File(outdir.getParentFile(), "SC_RNA_COUNTER_CS");
            if (directory.exists())
            {
                //NOTE: this will have lots of symlinks, including corrupted ones, which java handles badly
                new SimpleScriptWrapper(getPipelineCtx().getLogger()).execute(Arrays.asList("rm", "-Rf", directory.getPath()));
            }
            else
            {
                getPipelineCtx().getLogger().warn("Unable to find folder: " + directory.getPath());
            }

            return output;
        }

        private String getSymlinkFileName(String fileName)
        {
            //NOTE: cellranger is very picky about file name formatting
            Matcher m = FILE_PATTERN.matcher(fileName);
            if (m.matches())
            {
                if (!StringUtils.isEmpty(m.group(7)))
                {
                    return m.group(1).replaceAll("_", "-") + StringUtils.trimToEmpty(m.group(2)) + "_L" + StringUtils.trimToEmpty(m.group(3)) + "_" + StringUtils.trimToEmpty(m.group(4)) + StringUtils.trimToEmpty(m.group(5)) + StringUtils.trimToEmpty(m.group(6)) + ".fastq.gz";
                }
                else if (m.group(1).contains("_"))
                {
                    getPipelineCtx().getLogger().info("replacing underscores in file/sample name");
                    return m.group(1).replaceAll("_", "-") + StringUtils.trimToEmpty(m.group(2)) + "_L" + StringUtils.trimToEmpty(m.group(3)) + "_" + StringUtils.trimToEmpty(m.group(4)) + StringUtils.trimToEmpty(m.group(5)) + StringUtils.trimToEmpty(m.group(6)) + ".fastq.gz";
                }
                else
                {
                    getPipelineCtx().getLogger().info("no additional characters found");
                }
            }
            else
            {
                getPipelineCtx().getLogger().warn("filename does not match Illumina formatting: " + fileName);
            }

            return fileName;
        }

        public Set<String> prepareFastqSymlinks(Readset rs, File localFqDir) throws PipelineJobException
        {
            Set<String> ret = new HashSet<>();
            if (!localFqDir.exists())
            {
                localFqDir.mkdirs();
            }

            for (ReadData rd : rs.getReadData())
            {
                try
                {
                    File target1 = new File(localFqDir, getSymlinkFileName(rd.getFile1().getName()));
                    getPipelineCtx().getLogger().debug("file: " + rd.getFile1().getPath());
                    getPipelineCtx().getLogger().debug("target: " + target1.getPath());
                    if (target1.exists())
                    {
                        getPipelineCtx().getLogger().debug("deleting existing symlink: " + target1.getName());
                        Files.delete(target1.toPath());
                    }

                    Files.createSymbolicLink(target1.toPath(), rd.getFile1().toPath());
                    ret.add(getSampleName(target1.getName()));

                    if (rd.getFile2() != null)
                    {
                        File target2 = new File(localFqDir, getSymlinkFileName(rd.getFile2().getName()));
                        getPipelineCtx().getLogger().debug("file: " + rd.getFile2().getPath());
                        getPipelineCtx().getLogger().debug("target: " + target2.getPath());
                        if (target2.exists())
                        {
                            getPipelineCtx().getLogger().debug("deleting existing symlink: " + target2.getName());
                            Files.delete(target2.toPath());
                        }
                        Files.createSymbolicLink(target2.toPath(), rd.getFile2().toPath());
                        ret.add(getSampleName(target2.getName()));
                    }
                }
                catch (IOException e)
                {
                    throw new PipelineJobException(e);
                }
            }

            return ret;
        }

        private static Pattern FILE_PATTERN = Pattern.compile("^(.+?)(_S[0-9]+){0,1}_L(.+?)_(R){0,1}([0-9])(_[0-9]+){0,1}(.*?)(\\.f(ast){0,1}q)(\\.gz)?$");
        private static Pattern SAMPLE_PATTERN = Pattern.compile("^(.+)_S[0-9]+(.*)$");

        private String getSampleName(String fn)
        {
            Matcher matcher = FILE_PATTERN.matcher(fn);
            if (matcher.matches())
            {
                String ret = matcher.group(1);
                Matcher matcher2 = SAMPLE_PATTERN.matcher(ret);
                if (matcher2.matches())
                {
                    ret = matcher2.group(1);
                }
                else
                {
                    getPipelineCtx().getLogger().debug("_S not found in sample: [" + ret + "]");
                }

                ret = ret.replaceAll("_", "-");

                return ret;
            }

            throw new IllegalArgumentException("Unable to infer Illumina sample name: " + fn);
        }

        public void deleteSymlinks(File localFqDir) throws PipelineJobException
        {
            for (File fq : localFqDir.listFiles())
            {
                try
                {
                    getPipelineCtx().getLogger().debug("deleting symlink: " + fq.getName());
                    Files.delete(fq.toPath());
                }
                catch (IOException e)
                {
                    throw new PipelineJobException(e);
                }
            }
        }

        private File getRawGeneMatrix(File bam)
        {
            return new File(bam.getParentFile(), "raw_gene_bc_matrices_h5.h5");
        }

        private File getFilteredGeneMatrix(File bam)
        {
            return new File(bam.getParentFile(), "filtered_gene_bc_matrices_h5.h5");
        }

        @Override
        public boolean doAddReadGroups()
        {
            return false;
        }

        @Override
        public boolean doSortIndexBam()
        {
            return true;
        }

        @Override
        public boolean alwaysCopyIndexToWorkingDir()
        {
            return false;
        }

        @Override
        public void complete(SequenceAnalysisJobSupport support, AnalysisModel model) throws PipelineJobException
        {
            File metrics = new File(model.getAlignmentFileObject().getParentFile(), "metrics_summary.csv");
            if (metrics.exists())
            {
                getPipelineCtx().getLogger().debug("adding 10x metrics");
                try (CSVReader reader = new CSVReader(Readers.getReader(metrics)))
                {
                    String[] line;
                    String[] header = null;
                    String[] metricValues = null;

                    int i = 0;
                    while ((line = reader.readNext()) != null)
                    {
                        if (i == 0)
                        {
                            header = line;
                        }
                        else
                        {
                            metricValues = line;
                            break;
                        }

                        i++;
                    }

                    TableInfo ti = DbSchema.get("sequenceanalysis", DbSchemaType.Module).getTable("quality_metrics");
                    for (int j = 0; j < header.length; j++)
                    {
                        Map<String, Object> toInsert = new CaseInsensitiveHashMap<>();
                        toInsert.put("container", getPipelineCtx().getJob().getContainer().getId());
                        toInsert.put("createdby", getPipelineCtx().getJob().getUser().getUserId());
                        toInsert.put("created", new Date());
                        toInsert.put("readset", model.getReadset());
                        toInsert.put("analysis_id", model.getRowId());
                        toInsert.put("dataid", model.getAlignmentFile());

                        toInsert.put("category", "Cell Ranger");
                        toInsert.put("metricname", header[j]);

                        metricValues[j] = metricValues[j].replaceAll(",", "");
                        Object val = metricValues[j];
                        if (metricValues[j].contains("%"))
                        {
                            metricValues[j] = metricValues[j].replaceAll("%", "");
                            Double d = ConvertHelper.convert(metricValues[j], Double.class);
                            d = d / 100.0;
                            val = d;
                        }

                        toInsert.put("metricvalue", val);

                        Table.insert(getPipelineCtx().getJob().getUser(), ti, toInsert);
                    }
                }
                catch (IOException e)
                {
                    throw new PipelineJobException(e);
                }
            }
            else
            {
                getPipelineCtx().getLogger().warn("unable to find metrics file: " + metrics.getPath());
            }
        }
    }

    public File runAggr(String id, File csvFile, List<String> extraArgs) throws PipelineJobException
    {
        List<String> args = new ArrayList<>();
        args.add(getExe().getPath());
        args.add("aggr");

        id = id.replaceAll("[^a-zA-z0-9_\\-]", "_");
        args.add("--id=" + id);
        args.add("--csv=" + csvFile.getPath());

        if (extraArgs != null)
        {
            args.addAll(extraArgs);
        }

        execute(args);

        File expectedOutput = new File(getOutputDir(csvFile), id + "/outs/cloupe.cloupe");
        if (!expectedOutput.exists())
        {
            throw new PipelineJobException("Unable to find output: " + expectedOutput.getPath());
        }

        return expectedOutput;
    }


    public File runReanalyze(File matrix, File outDir, String id, List<String> extraParams) throws PipelineJobException
    {
        List<String> args = new ArrayList<>();
        args.add(getExe().getPath());
        args.add("reanalyze");

        id = id.replaceAll("[^a-zA-z0-9_\\-]", "_");
        args.add("--id=" + id);

        args.add("--matrix=" + matrix.getPath());

        if (!extraParams.isEmpty())
        {
            args.addAll(extraParams);
        }

        setWorkingDir(outDir);
        execute(args);

        File output = new File(outDir, id);
        if (!output.exists())
        {
            throw new PipelineJobException("Unable to find output: " + output.getPath());
        }

        return output;
    }

    protected File getExe()
    {
        return SequencePipelineService.get().getExeForPackage("CELLRANGERPATH", "cellranger");
    }

    public static Set<File> getRawDataDirs(File outputDir, boolean filteredOnly)
    {
        List<String> dirs = new ArrayList<>();
        dirs.add("filtered_feature_bc_matrix");
        dirs.add("filtered_gene_bc_matrices");

        if (!filteredOnly)
        {
            dirs.add("raw_gene_bc_matrices");
            dirs.add("raw_feature_bc_matrix");
        }

        Set<File> toAdd = new HashSet<>();
        for (String dir : dirs)
        {
            File subDir = new File(outputDir, dir);
            if (subDir.exists())
            {
                toAdd.add(subDir);
            }
        }

        return toAdd;
    }
}
