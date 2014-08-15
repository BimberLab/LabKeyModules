package org.labkey.sequenceanalysis.api.pipeline;

import org.labkey.api.pipeline.PipelineJobException;

import java.io.File;

/**
 * User: bimber
 * Date: 6/13/2014
 * Time: 1:11 PM
 */
public interface ReferenceLibraryStep extends PipelineStep
{
    public File getExpectedFastaFile(File outputDirectory) throws PipelineJobException;

    public Output createReferenceFasta(File outputDirectory) throws PipelineJobException;

    public static interface Output extends PipelineStepOutput
    {
        public File getReferenceFasta() throws PipelineJobException;
    }
}