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

package net.sf.mzmine.modules.rawdatamethods.peakpicking.massdetection;

import java.util.ArrayList;
import java.util.logging.Logger;

import net.sf.mzmine.data.DataPoint;
import net.sf.mzmine.data.RawDataFile;
import net.sf.mzmine.data.Scan;
import net.sf.mzmine.data.impl.SimpleMassList;
import net.sf.mzmine.desktop.impl.MainWindow;
import net.sf.mzmine.main.MZmineCore;
import net.sf.mzmine.modules.MZmineProcessingStep;
import net.sf.mzmine.parameters.ParameterSet;
import net.sf.mzmine.taskcontrol.AbstractTask;
import net.sf.mzmine.taskcontrol.TaskStatus;

public class MassDetectionTask extends AbstractTask {

    private Logger logger = Logger.getLogger(this.getClass().getName());
    private RawDataFile dataFile;

    // scan counter
    private int step = 0, totalSteps = 0;
    private int msLevel;

    // User parameters
    private String name;

    // Mass detector
    private MZmineProcessingStep<MassDetector> massDetector;

    /**
     * @param rawDataFile
     * @param parameters
     */
    @SuppressWarnings("unchecked")
    public MassDetectionTask(RawDataFile rawDataFile, ParameterSet parameters)
    {
		dataFile     = rawDataFile;
		massDetector = parameters.getParameter(MassDetectionParameters.massDetector).getValue();
		msLevel      = parameters.getParameter(MassDetectionParameters.msLevel).getValue();
		name         = parameters.getParameter(MassDetectionParameters.name).getValue();
    }

    /**
     * @see net.sf.mzmine.taskcontrol.Task#getTaskDescription()
     */
    public String getTaskDescription() {
    	return "Detecting masses in " + dataFile;
    }

    /**
     * @see net.sf.mzmine.taskcontrol.Task#getFinishedPercentage()
     */
    public double getFinishedPercentage() {
		if (totalSteps == 0)
		    return 0;
		return (double) step / totalSteps;
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
	    MassDetector detector = massDetector.getModule();
		logger.info("Started " + detector.getName() + " mass detector on " + dataFile);
		
		int scanNumbers[] = dataFile.getScanNumbers();	// all the scans in this file
		totalSteps = scanNumbers.length + 2;			// add one for the job start & finish calls

		// get the selected scans for this data file
		ArrayList<Scan> selectedScans = new ArrayList<Scan>();
		MainWindow mainWindow = (MainWindow) MZmineCore.getDesktop();
		Scan scans[] = mainWindow.getMainPanel().getProjectTree().getSelectedObjects(Scan.class);
		for (Scan scan : scans)	// for all selected scans
		{
			if (scan.getDataFile().equals(dataFile))	// if this scan is in this raw data file
				selectedScans.add(scan);
		}

		// if there are no selected scans for this data file, get all of the msLevel ones
		if (selectedScans.size() == 0)
		{
			int msScanNumbers[] = dataFile.getScanNumbers(msLevel);
			for (int i = 0; i < msScanNumbers.length; i++)
				selectedScans.add(dataFile.getScan(msScanNumbers[i]));
		}

		// start the job
		String job = detector.startMassValuesJob(dataFile, name, massDetector.getParameterSet());
		name       = detector.filterTargetName(name);	// get the target name, the detector may change it
	    step += 1;

		// Process all scans one by one
		for (int i = 0; i < scanNumbers.length; i++)
		{
		    if (isCanceled())
		    	return;

		    Scan scan = dataFile.getScan(scanNumbers[i]);
    		boolean selected = selectedScans.remove(scan);

		    // give the detector all scans but tell it which ones to process
		    // some detectors run a second pass where the selected list is incorrect but they know which scans to process
		    DataPoint mzPeaks[] = detector.getMassValues(scan, selected, job, massDetector.getParameterSet());
		    if (mzPeaks != null)
		    {
		    	SimpleMassList newMassList = new SimpleMassList(name, scan, mzPeaks);
			    scan.addMassList(newMassList);	// Add new mass list to the scan
		    }
		    step += 1;
		}

		// finish the job
		detector.finishMassValuesJob(job);
	    step += 1;

		setStatus(TaskStatus.FINISHED);
		logger.info("Finished " + detector.getName() + " mass detector on " + dataFile);
    }

    public Object[] getCreatedObjects()
    {
    	return null;
    }
}