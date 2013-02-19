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
import net.sf.mzmine.data.impl.SimpleMassList;
import net.sf.mzmine.parameters.ParameterSet;
import net.sf.mzmine.taskcontrol.AbstractTask;
import net.sf.mzmine.taskcontrol.TaskStatus;

/**
 *
 */
public class MassListExportTask extends AbstractTask {

	private Logger logger = Logger.getLogger(this.getClass().getName());
	private RawDataFile dataFile;

	// scan counter
	private int processedScans = 0, totalScans;
	private int[] scanNumbers;

	// User parameters
	private String massListName, suffix;
	private boolean autoRemove;
	private ParameterSet parameters;

	/**
	 * @param dataFile
	 * @param parameters
	 */
	public MassListExportTask(RawDataFile dataFile, ParameterSet parameters) {

		this.dataFile = dataFile;
		this.parameters = parameters;

		this.massListName = parameters.getParameter(
				MassListExportParameters.massList).getValue();

//		this.suffix = parameters.getParameter(
//				MassListExportParameters.suffix).getValue();
//		this.autoRemove = parameters.getParameter(
//				MassListExportParameters.autoRemove).getValue();

	}

	/**
	 * @see net.sf.mzmine.taskcontrol.Task#getTaskDescription()
	 */
	public String getTaskDescription() {
		return "Mass list export of " + dataFile;
	}

	/**
	 * @see net.sf.mzmine.taskcontrol.Task#getFinishedPercentage()
	 */
	public double getFinishedPercentage() {
		if (totalScans == 0)
			return 0;
		else
			return (double) processedScans / totalScans;
	}

	public RawDataFile getDataFile() {
		return dataFile;
	}

	/**
	 * @see Runnable#run()
	 */
	public void run() {

		setStatus(TaskStatus.PROCESSING);

		logger.info("Mass list export on " + dataFile);

		scanNumbers = dataFile.getScanNumbers();
		totalScans = scanNumbers.length;

		// Check if we have at least one scan with a mass list of given name
		boolean haveMassList = false;
		for (int i = 0; i < totalScans; i++) {
			Scan scan = dataFile.getScan(scanNumbers[i]);
			MassList massList = scan.getMassList(massListName);
			if (massList != null) {
				haveMassList = true;
				break;
			}
		}
		if (!haveMassList) {
			setStatus(TaskStatus.ERROR);
			this.errorMessage = dataFile.getName()
					+ " has no mass list called '" + massListName + "'";
			return;
		}

		// Process all scans
		for (int i = 0; i < totalScans; i++) {

			if (isCanceled())
				return;

			Scan scan = dataFile.getScan(scanNumbers[i]);

			MassList massList = scan.getMassList(massListName);

			// Skip those scans which do not have a mass list of given name
			if (massList == null) {
				processedScans++;
				continue;
			}
                        String fName;
                        if ( i < 10) {
                         fName = dataFile.getName().replaceAll("\\..*$", "") + "_" + massListName + "_scan0" + i + ".txt";
                        } else {
                         fName = dataFile.getName().replaceAll("\\..*$", "") + "_" + massListName + "_scan" + i + ".txt";
                        }
		logger.info("Exporting scan " + i + " to file " + fName);
                File f = new File(fName);
                FileWriter fWriter = null;
            try {
                fWriter = new FileWriter(f);

			DataPoint mzPeaks[] = massList.getDataPoints();
                        int num = mzPeaks.length;
                        for ( int thisPeak = 0; thisPeak < num; thisPeak++) {
                            fWriter.write(mzPeaks[thisPeak].getMZ() + "\t" + mzPeaks[thisPeak].getIntensity() + "\n");
                        }
                        fWriter.close();
            } catch (IOException ex) {
                Logger.getLogger(MassListExportTask.class.getName()).log(Level.SEVERE, null, ex);
            }

//			DataPoint newMzPeaks[] = MassListExport.filterMassValues(
//					mzPeaks, parameters);
//
//			SimpleMassList newMassList = new SimpleMassList(massListName + " "
//					+ suffix, scan, newMzPeaks);
//
//			scan.addMassList(newMassList);
//
//			// Remove old mass list
//			if (autoRemove)
//				scan.removeMassList(massList);

			processedScans++;
		}

		setStatus(TaskStatus.FINISHED);

		logger.info("Finished mass list export on " + dataFile);

	}

	public Object[] getCreatedObjects() {
		return null;
	}

}
