/*
 * Copyright 2013-2014 The Veritomyx
 * 
 * This file is part of MZmine 2.
 * 
 * MZmine 2 is free software; you can redistribute it and/or modify it under the
 * terms of the GNU General Public License as published by the Free Software
 * Foundation; either version 2 of the License, or (at your option) any later
 * version.
 * 
 * MZmine 2 is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR
 * A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License along with
 * MZmine 2; if not, write to the Free Software Foundation, Inc., 51 Franklin St,
 * Fifth Floor, Boston, MA 02110-1301 USA
 */

package net.sf.mzmine.modules.rawdatamethods.peakpicking.massdetection.Veritomyx;

import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.logging.Logger;

import net.sf.mzmine.data.DataPoint;
import net.sf.mzmine.data.RawDataFile;
import net.sf.mzmine.data.Scan;
import net.sf.mzmine.modules.rawdatamethods.peakpicking.massdetection.MassDetector;
import net.sf.mzmine.parameters.ParameterSet;

public class Veritomyx implements MassDetector
{
	private Logger logger;	
	private ArrayList<PeakInvestigatorTask> jobs;

	public Veritomyx()
	{
		logger = Logger.getLogger(this.getClass().getName());
		logger.finest("Initializing Veritomyx " + this.getName());
		jobs = new ArrayList<PeakInvestigatorTask>();
	}

	/**
	 * Return the name of this module
	 * 
	 * @return
	 */
	public String getName() { return "PeakInvestigator™"; }

	@Override
	public Class<? extends ParameterSet> getParameterSetClass() { return VeritomyxParameters.class; }

	/**
	 * Return target name and filter out possible job name from "|job[target]"
	 * 
	 * @param compoundName
	 * @return
	 */
	public String filterTargetName(String compoundName)
	{
		if (compoundName.startsWith("|job-"))
			return compoundName.substring(compoundName.indexOf('[') + 1, compoundName.indexOf(']'));
		return compoundName;
	}

	/**
	 * Return job name and filter out target name from "|job[target]"
	 * 
	 * @param compoundName
	 * @return
	 */
	private String filterJobName(String compoundName)
	{
		if (compoundName.startsWith("|job-"))
			return compoundName.substring(1, compoundName.indexOf('['));
		return null;
	}

	/**
	 * Create a new job task from the given parameters
	 * 
	 * @param raw
	 * @param targetName
	 * @param parameters
	 * @return
	 */
	public String startMassValuesJob(RawDataFile raw, String name, ParameterSet parameters)
	{
		PeakInvestigatorTask job = new PeakInvestigatorTask(raw, filterJobName(name), filterTargetName(name), parameters);
		String job_name = job.getName();
		if (job_name != null)
			jobs.add(job);
		return job_name;
	}

	/**
	 * Compute the peaks list for the given scan
	 * 
	 * @param scan
	 * @param selected
	 * @param jobName
	 * @param parameters
	 * @return
	 * @throws FileNotFoundException 
	 */
	public DataPoint[] getMassValues(Scan scan, boolean selected, String jobName, ParameterSet parameters)
	{
		// get the thread-safe job from jobs list using the jobName
		PeakInvestigatorTask job = getJobFromName(jobName);
		return (job == null) ? null : job.processScan(scan, selected);
	}

	/**
	 * Mark the job done
	 * 
	 * @param parameters
	 * @return
	 */
	public void finishMassValuesJob(String job_name)
	{
		PeakInvestigatorTask job = getJobFromName(job_name);
		if (job != null)
		{
			job.finish();
			jobs.remove(job);
		}
	}

	/**
	 * Retrieve the job task from a job name
	 * 
	 * @param jobName
	 * @return
	 */
	private PeakInvestigatorTask getJobFromName(String jobName)
	{
		// get the job from jobs list using the jobName
		for (PeakInvestigatorTask job:jobs)
		{
			if (job.getName().equals(jobName))
				return job;
		}
		return null;
	}

}