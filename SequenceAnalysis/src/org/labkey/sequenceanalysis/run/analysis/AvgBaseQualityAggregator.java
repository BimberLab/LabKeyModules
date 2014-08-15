/*
 * Copyright (c) 2012 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.labkey.sequenceanalysis.run.analysis;

import net.sf.picard.reference.FastaSequenceIndex;
import net.sf.picard.reference.IndexedFastaSequenceFile;
import net.sf.picard.util.Interval;
import net.sf.picard.util.IntervalList;
import net.sf.picard.util.SamLocusIterator;
import net.sf.samtools.SAMFileHeader;
import net.sf.samtools.SAMFileReader;
import net.sf.samtools.SAMSequenceRecord;
import org.apache.log4j.Logger;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * User: bimber
 * Date: 10/19/12
 * Time: 4:18 PM
 */
public class AvgBaseQualityAggregator
{
    private Logger _log;
    private File _bam;
    private File _bai;
    private File _ref;
    private IndexedFastaSequenceFile _refFastq;
    private Map<Integer, Map<String, Double>> _quals = null;

    public AvgBaseQualityAggregator(Logger log, File bam, File refFasta) throws FileNotFoundException
    {
        _log = log;
        _bam = bam;
        _ref = refFasta;

        _bai = new File(_bam.getPath() + ".bai");
        if(!_bai.exists())
            throw new FileNotFoundException("Missing index for BAM, expected: " + _bai.getPath());

        File fai = new File(_ref.getPath() + ".fai");
        if(!fai.exists())
            throw new FileNotFoundException("Missing index for FASTA, expected: " + fai.getPath());

        _refFastq = new IndexedFastaSequenceFile(_ref, new FastaSequenceIndex(fai));
    }

    public void calculateAvgQuals(String refName, int start, int stop)
    {
        SAMFileReader sam = null;

        try
        {
            sam = new SAMFileReader(_bam, _bai);
            sam.setValidationStringency(SAMFileReader.ValidationStringency.SILENT);

            SAMFileHeader header = sam.getFileHeader();
            List<SAMSequenceRecord> sequences = header.getSequenceDictionary().getSequences();

            SAMSequenceRecord sr = null;
            for (SAMSequenceRecord it : sequences)
            {
                if (refName.equalsIgnoreCase(it.getSequenceName()))
                {
                    sr = it;
                    break;
                }
            }

            if (sr == null)
                throw new IllegalArgumentException("Unknown reference: " + refName);

            Map<Integer, Map<String, Double>> quals = new HashMap<Integer, Map<String, Double>>();
            IntervalList il = new IntervalList(header);
            il.add(new Interval(refName, start, stop));
            quals.put(sr.getSequenceIndex(), calculateAvgQualsForInterval(sam, il));

            _quals = quals;
        }
        finally
        {
            if (sam != null)
                sam.close();
        }
    }

    public void calculateAvgQuals()
    {
        SAMFileReader sam = null;

        try
        {
            sam = new SAMFileReader(_bam, _bai);
            sam.setValidationStringency(SAMFileReader.ValidationStringency.SILENT);

            SAMFileHeader header = sam.getFileHeader();
            List<SAMSequenceRecord> sequences = header.getSequenceDictionary().getSequences();

            Map<Integer, Map<String, Double>> quals = new HashMap<>();
            for(SAMSequenceRecord sr : sequences) {
                IntervalList il = new IntervalList(header);
                il.add(new Interval(sr.getSequenceName(), 1, sr.getSequenceLength()));

                quals.put(sr.getSequenceIndex(), calculateAvgQualsForInterval(sam, il));
            }

            _quals = quals;
        }
        finally
        {
            if (sam != null)
                sam.close();
        }
    }

    /**
     * @return 0-based map
     */
    private Map<String, Double> calculateAvgQualsForInterval(SAMFileReader sam, IntervalList il)
    {
        Map<String, Double> quals = new HashMap<>();

        SamLocusIterator sli = new SamLocusIterator(sam, il, true);
        sli.setEmitUncoveredLoci(false);

        Iterator<SamLocusIterator.LocusInfo> it = sli.iterator();
        int idx = 0;
        while (it.hasNext())
        {
            SamLocusIterator.LocusInfo locus = it.next();
            idx++;

            if (idx % 2500 == 0)
            {
                _log.info("processed " + idx + " loci in AvgBaseQualityAggregator");
            }

            Map<String, Integer> snpMap = new HashMap<>();
            Map<String, Integer> baseCountMap = new HashMap<>();

            for (SamLocusIterator.RecordAndOffset r : locus.getRecordAndPositions())
            {
                String base = Character.toString((char)r.getReadBase());
                if (!snpMap.containsKey(base))
                {
                    snpMap.put(base, (int)r.getBaseQuality());
                    baseCountMap.put(base, 1);
                }
                else
                {
                    snpMap.put(base, snpMap.get(base) + r.getBaseQuality());
                    baseCountMap.put(base, baseCountMap.get(base) + 1);
                }
            }

            for (String b : baseCountMap.keySet())
            {
                double avg = snpMap.get(b) / baseCountMap.get(b);
                Integer pos = locus.getPosition() - 1; //convert to 0-based
                String key = getBaseKey(pos, b);
                quals.put(key, avg);
            }
        }

        return quals;
    }

    private String getBaseKey(Integer pos, String base)
    {
        return pos + "||" + base;
    }

    public Map<Integer, Map<String, Double>> getQualsForReference(Integer refId)
    {
        if (_quals == null)
            calculateAvgQuals();

        Map<String, Double> map = _quals.get(refId);
        if (map == null)
            return null;

        Map<Integer, Map<String, Double>> ret = new HashMap<>();
        for (String key : map.keySet())
        {
            String[] tokens = key.split("\\|\\|");
            assert tokens.length == 2;

            Integer pos = Integer.parseInt(tokens[0]);

            Map<String, Double> baseMap = ret.get(pos);
            if (baseMap == null)
                baseMap = new HashMap<>();

            baseMap.put(tokens[1], map.get(key));
            ret.put(pos, baseMap);
        }

        return ret;
    }

}