package org.labkey.sequenceanalysis.run;

import org.apache.log4j.Logger;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.pipeline.PipelineJobService;
import org.labkey.api.util.FileUtil;
import org.labkey.sequenceanalysis.util.FastqUtils;

import java.io.File;
import java.util.LinkedList;
import java.util.List;

/**
 * Created with IntelliJ IDEA.
 * User: bimber
 * Date: 10/24/12
 * Time: 9:14 AM
 */
public class FastqToSamRunner extends PicardRunner
{
    private FastqUtils.FASTQ_ENCODING _fastqEncoding = null;

    public FastqToSamRunner(Logger logger)
    {
        _logger = logger;
    }

    public File execute(File file, @Nullable File file2) throws PipelineJobException
    {
        _logger.info("Converting FASTQ to SAM: " + file.getPath());
        _logger.info("\tFastqToSam version: " + getVersion());

        doExecute(getWorkingDir(file), getParams(file, file2));
        File output = new File(getWorkingDir(file), getOutputFilename(file));
        if (!output.exists())
        {
            throw new PipelineJobException("Output file could not be found: " + output.getPath());
        }

        return output;
    }

    protected File getJar()
    {
        return getPicardJar("FastqToSam.jar");
    }

    private List<String> getParams(File file, File file2) throws PipelineJobException
    {
        List<String> params = new LinkedList<>();
        params.add("java");
        params.add("-jar");
        params.add(getJar().getPath());

        params.add("FASTQ=" + file.getPath());
        if (file2 != null)
            params.add("FASTQ2=" + file2.getPath());

        FastqUtils.FASTQ_ENCODING encoding = _fastqEncoding;
        if (encoding == null)
        {
            encoding = FastqUtils.inferFastqEncoding(file);
            if (encoding != null)
                _logger.info("\tInferred FASTQ encoding of file " + file.getName() + " was: " + encoding);
            else
            {
                encoding = FastqUtils.FASTQ_ENCODING.Illumina;
                _logger.warn("\tUnable to infer FASTQ encoding for file: " + file.getPath() + ", defaulting to " + encoding);
            }
        }

        params.add("QUALITY_FORMAT=" + encoding);
        params.add("SAMPLE_NAME=SAMPLE");
        params.add("OUTPUT=" + new File(getWorkingDir(file), getOutputFilename(file)).getPath());

        return params;
    }

    public String getOutputFilename(File file)
    {
        return FileUtil.getBaseName(file) + ".sam";
    }

    public void setFastqEncoding(FastqUtils.FASTQ_ENCODING fastqEncoding)
    {
        _fastqEncoding = fastqEncoding;
    }
}
