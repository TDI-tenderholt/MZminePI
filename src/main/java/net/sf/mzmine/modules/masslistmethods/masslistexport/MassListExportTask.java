/*
 * Copyright 2006-2012 The MZmine 2 Development Team
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
 * MZmine 2; if not, write to the Free Software Foundation, Inc., 51 Franklin
 * St, Fifth Floor, Boston, MA 02110-1301 USA
 */

package net.sf.mzmine.modules.masslistmethods.masslistexport;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

import net.sf.mzmine.data.DataPoint;
import net.sf.mzmine.data.MassList;
import net.sf.mzmine.data.RawDataFile;
import net.sf.mzmine.data.Scan;
import net.sf.mzmine.parameters.ParameterSet;
import net.sf.mzmine.taskcontrol.AbstractTask;
import net.sf.mzmine.taskcontrol.TaskStatus;

/**
 *
 */
public class MassListExportTask extends AbstractTask
{
	private Logger logger = Logger.getLogger(this.getClass().getName());
	private RawDataFile dataFile;

	// scan counter
	private int processedScans = 0, totalScans;
	private int[] scanNumbers;

	// User parameters
	private String massListName;
	private boolean dumpScans;

	/**
	 * @param dataFile
	 * @param parameters
	 */
	public MassListExportTask(RawDataFile dataFile, ParameterSet parameters) {
		this.dataFile = dataFile;
		massListName  = parameters.getParameter(MassListExportParameters.massList).getValue();
		dumpScans     = parameters.getParameter(MassListExportParameters.dumpScans).getValue();
	}

	/**
	 * @see net.sf.mzmine.taskcontrol.Task#getTaskDescription()
	 */
	public String getTaskDescription() {
		return "Mass list export of " + dataFile.getName();
	}

	/**
	 * @see net.sf.mzmine.taskcontrol.Task#getFinishedPercentage()
	 */
	public double getFinishedPercentage() {
		return (totalScans == 0) ? 0 : (double) processedScans / totalScans;
	}

	public RawDataFile getDataFile() {
		return dataFile;
	}

	/**
	 * @see Runnable#run()
	 */
	public void run()
	{
		setStatus(TaskStatus.PROCESSING);
		logger.info("Mass list export of " + massListName + " from " + dataFile);

		scanNumbers = dataFile.getScanNumbers();
		totalScans  = scanNumbers.length;
		int scansWithMassList = 0;
		String dfileName = dataFile.getName();
		String sName, fName;
		FileWriter fWriter;

		// Process all scans
		for (int s = 0; s < totalScans; s++)
		{
			if (isCanceled())
				return;

			Scan scan = dataFile.getScan(scanNumbers[s]);
			sName = dfileName + ".scan" + String.format("%04d", scan.getScanNumber()) + ".ms" + scan.getMSLevel();
			if (dumpScans)
			{	// dump the scan data
				fName = sName + ".txt";
				logger.info("Exporting scan " + s + " to file " + fName);
				try {
	                fWriter = new FileWriter(new File(fName));
	                DataPoint pts[] = scan.getDataPoints();
		            int num = pts.length;
		            fWriter.write("# Raw Data File: " + dfileName + "\n");
		            fWriter.write("# Scan: " + scan.getScanNumber() + "\n");
		            fWriter.write("# MS Level: " + scan.getMSLevel() + "\n");
		            fWriter.write("# Data Points: " + num + "\n");
		            fWriter.write("# mz Range (min, max): " + scan.getMZRange().getMin() + ", " + scan.getMZRange().getMax() + "\n");
	                for (int p = 0; p < num; p++)
	                {
	                	DataPoint pt = pts[p];
	                	fWriter.write(pt.getMZ() + "\t" + pt.getIntensity() + "\n");
	            	}
	                fWriter.close();
	            } catch (IOException ex) {
	                Logger.getLogger(MassListExportTask.class.getName()).log(Level.SEVERE, null, ex);
	            }
			}

			MassList massList = scan.getMassList(massListName);
			if (massList != null)	// Skip those scans which do not have a mass list of given name
			{
				scansWithMassList++;
				fName = sName + "." + massListName + ".txt";
				logger.info("Exporting scan " + s + " to file " + fName);
				try {
	                fWriter = new FileWriter(new File(fName));
	                DataPoint mzPeaks[] = massList.getDataPoints();
	                int num = mzPeaks.length;
		            fWriter.write("# Raw Data File: " + dfileName + "\n");
		            fWriter.write("# Scan: " + scan.getScanNumber() + "\n");
		            fWriter.write("# MS Level: " + scan.getMSLevel() + "\n");
		            fWriter.write("# Mass List: " + massListName + "\n");
		            fWriter.write("# Data Points: " + num + "\n");
		            for (int p = 0; p < num; p++)
	                {
	                	DataPoint pt = mzPeaks[p];
	                	fWriter.write(pt.getMZ() + "\t" + pt.getIntensity() + "\n");
	            	}
	                fWriter.close();
	            } catch (IOException ex) {
	                Logger.getLogger(MassListExportTask.class.getName()).log(Level.SEVERE, null, ex);
	            }
			}
			processedScans++;
		}

		if (scansWithMassList > 0)
			setStatus(TaskStatus.FINISHED);
		else
		{
			setStatus(TaskStatus.ERROR);
			this.errorMessage = dataFile.getName() + " has no mass list called '" + massListName + "'";
		}
		logger.info("Finished mass list export on " + dataFile);
	}

	public Object[] getCreatedObjects() {
		return null;
	}
}