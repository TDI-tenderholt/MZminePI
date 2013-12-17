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

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;
import java.util.logging.Logger;
import java.util.zip.GZIPInputStream;

import net.sf.mzmine.data.DataPoint;
import net.sf.mzmine.data.RawDataFile;
import net.sf.mzmine.data.Scan;
import net.sf.mzmine.data.impl.SimpleDataPoint;
import net.sf.mzmine.desktop.preferences.MZminePreferences;
import net.sf.mzmine.main.MZmineCore;
import net.sf.mzmine.parameters.ParameterSet;
import net.sf.mzmine.util.ExitCode;

import org.xeustechnologies.jtar.TarEntry;
import org.xeustechnologies.jtar.TarInputStream;
import org.xeustechnologies.jtar.TarOutputStream;

import FileChecksum.FileChecksum;

/**
 * This class is used to run a set of scans through the Veritomyx SaaS servers
 * 
 * @author dschmidt
 *
 */
public class PeakInvestigatorTask
{
	private Logger          logger;
	private boolean         launch;			// launch or retrieve
	private String          jobID;			// name of the job and the scans tar file
	private int             scanCnt;
	private String          targetName;
	private String          intputFilename;
	private String          outputFilename;
	private VeritomyxSaaS   vtmx;
	private String          username;
	private String          password;
	private int             pid;
	private TarOutputStream tarfile;
	private RawDataFile     rawDataFile;

	public PeakInvestigatorTask(RawDataFile raw, String pickup_job, String target, ParameterSet parameters)
	{
		logger  = Logger.getLogger(this.getClass().getName());
		logger.info("Initializing PeakInvestigator™ Task");
		jobID   = null;
		tarfile = null;

		// pickup all the parameters
		MZminePreferences preferences = MZmineCore.getConfiguration().getPreferences();
		username = preferences.getParameter(MZminePreferences.vtmxUsername).getValue();
		password = preferences.getParameter(MZminePreferences.vtmxPassword).getValue();
		pid      = preferences.getParameter(MZminePreferences.vtmxProject).getValue();
		
		if ((username == null) || username.isEmpty() || (password == null) || password.isEmpty())
		{
			if (preferences.showSetupDialog() != ExitCode.OK)
				return;
			username = preferences.getParameter(MZminePreferences.vtmxUsername).getValue();
			password = preferences.getParameter(MZminePreferences.vtmxPassword).getValue();
			pid      = preferences.getParameter(MZminePreferences.vtmxProject).getValue();
		}

		// save the raw data file
		rawDataFile = raw;

		// figure out if this a new job (launch) or not (retrieval)
		launch     = (pickup_job == null);
		targetName = target;

		// make sure we have access to the Veritomyx Server
		// this also gets the job_id and SFTP credentials
		vtmx = new VeritomyxSaaS(MZmineCore.ReqVtmxVersion, MZmineCore.VtmxTest);
		while (true)
		{
			int status = vtmx.init(username, password, pid, pickup_job);
			if (status == VeritomyxSaaS.W_DONE)
				break;

			MZmineCore.getDesktop().displayErrorMessage("Error", vtmx.getPageStr(), logger);
			if (status != VeritomyxSaaS.W_ERROR_LOGIN)
				return;

			if (preferences.showSetupDialog() != ExitCode.OK)
				return;
			username = preferences.getParameter(MZminePreferences.vtmxUsername).getValue();
			password = preferences.getParameter(MZminePreferences.vtmxPassword).getValue();
			pid      = preferences.getParameter(MZminePreferences.vtmxProject).getValue();
		}

		if (vtmx.getPageStatus() != VeritomyxSaaS.W_DONE)
		{
			MZmineCore.getDesktop().displayErrorMessage("Error", vtmx.getPageStr(), logger);
			return;
		}

		jobID         = vtmx.getJobID();		
		intputFilename = jobID + ".scans.tar";
		outputFilename = jobID + ".vcent.tar";

		if (launch) startLaunch();
		else        startRetrieve();
	}

	/**
	 * Return the name of this task
	 * 
	 * @return
	 */
	public String getName() { return jobID; }

	/**
	 * Compute the peaks list for the given scan
	 * 
	 * @param scan
	 * @param selected
	 * @return
	 */
	public DataPoint[] processScan(Scan scan, boolean selected)
	{
		int scan_num = scan.getScanNumber();
		DataPoint[] peaks;
		if (launch) peaks = selected ? processScanLaunch(scan_num, scan) : null;
		else        peaks = processScanRetrieve(scan_num);		// ignore selected flag on retrieval
		return peaks;
	}

	/**
	 * Send the bundle of scans to the VTMX cloud processor via the SFTP drop
	 */
	public void finish()
	{
		if (launch) finishLaunch();
		else        finishRetrieve();
	}

	private void startLaunch()
	{
		logger.info("Preparing to launch new job, " + jobID);
		scanCnt = 0;
		try {
			tarfile = new TarOutputStream(new BufferedOutputStream(new FileOutputStream(intputFilename)));
		} catch (FileNotFoundException e) {
			logger.info(e.getMessage());
			MZmineCore.getDesktop().displayErrorMessage("Error", "Cannot create scans bundle file", logger);
			jobID = null;
		}
	}

	/**
	 * Add scan to compressed tar file
	 * 
	 * @param scan_num
	 * @param scan
	 * @return
	 */
	private DataPoint[] processScanLaunch(int scan_num, Scan scan)
	{
		// ########################################################################
		// Export all scans to remote processor
		try {
			// export the scan to a file
			String filename = "scan" + String.format("%04d", scan_num) + ".txt.gz";
			scan.exportToFile("", filename);

			// put the exported scan into the tar file
			File f = new File(filename);
			tarfile.putNextEntry(new TarEntry(f, filename));
			BufferedInputStream origin = new BufferedInputStream(new FileInputStream(f));
			int count;
			byte data[] = new byte[2048];
			while ((count = origin.read(data)) != -1)
				tarfile.write(data, 0, count);
			origin.close();
			f.delete();			// remove the local copy of the scan file
			tarfile.flush();
			scanCnt += 1;		// count this scan
		} catch (IOException e) {
			logger.info(e.getMessage());
			MZmineCore.getDesktop().displayErrorMessage("Error", "Cannot write to scans bundle file", logger);
		}
		return null;	// never return peaks from pass 1
	}

	/**
	 * Finish the job launch process and send job to VTMX SaaS server
	 */
	private void finishLaunch()
	{
		try {
			tarfile.close();
		} catch (IOException e) {
			logger.info(e.getMessage());
			MZmineCore.getDesktop().displayErrorMessage("Error", "Cannot close scans bundle file.", logger);
		}
		vtmx.putFile(intputFilename);

		//####################################################################
		// start for remote job
		if (vtmx.getPageRun(scanCnt) < VeritomyxSaaS.W_RUNNING)
		{
			MZmineCore.getDesktop().displayErrorMessage("Error", "Failed to launch " + jobID, logger);
			return;
		}

		// job was started - record it
		rawDataFile.addJob(jobID, rawDataFile, targetName, vtmx);	// record this job start
		logger.info(vtmx.getPageStr().split(" ",2)[1]);
		File f = new File(intputFilename);
		f.delete();			// remove the local copy of the tar file
	}

	/**
	 * Start the retrieval of the job
	 * Wait for job to finish in cloud and pickup the results
	 */
	private void startRetrieve()
	{
		int status;
		logger.info("Waiting for previously launched job, " + jobID + ", to finish");

		// wait for remote job to complete
		while ((status = vtmx.getPageStatus()) == VeritomyxSaaS.W_RUNNING)
		{
			try {
				Thread.sleep(60 * 1000);	// sleep for 60 seconds
			} catch (InterruptedException e) {
				MZmineCore.getDesktop().displayMessage("Warning", "Wait for remote job interrupted.", logger);
				return;
			}
		}
		if (status != VeritomyxSaaS.W_DONE)
		{
			MZmineCore.getDesktop().displayErrorMessage("Error", vtmx.getPageStr(), logger);
			return;
		}
		logger.info(vtmx.getPageStr().split(" ",2)[1]);

		// read the results tar file and extract all the peak list files
		logger.info("Reading centroided data from " + outputFilename);
		vtmx.getFile(outputFilename);
		{
			TarInputStream tis = null;
			FileOutputStream outputStream = null;
			try {
				tis = new TarInputStream(new GZIPInputStream(new FileInputStream(outputFilename)));
				TarEntry tf;
				int bytesRead;
				byte buf[] = new byte[1024];
				while ((tf = tis.getNextEntry()) != null)
				{
					if (tf.isDirectory()) continue;
					System.out.println(tf.getName() + " - " + tf.getSize() + " bytes");
					outputStream = new FileOutputStream(tf.getName());
					while ((bytesRead = tis.read(buf, 0, 1024)) > -1)
						outputStream.write(buf, 0, bytesRead);
					outputStream.close();
				}
				tis.close();
				File f = new File(outputFilename);
				f.delete();			// remove the local copy of the results tar file
			} catch (Exception e1) {
				logger.info(e1.getMessage());
				MZmineCore.getDesktop().displayErrorMessage("Error", "Cannot parse results file", logger);
				e1.printStackTrace();
			} finally {
				try { tis.close(); } catch (Exception e) {}
				try { outputStream.close(); } catch (Exception e) {}
			}
		}
	}

	/**
	 * Parse the completed scans
	 * 
	 * @param scan_num
	 * @return
	 */
	private DataPoint[] processScanRetrieve(int scan_num)
	{
		List<DataPoint> mzPeaks = null;

		// read in the peaks for this scan
		// convert filename to expected peak file name
		String pfilename = "scan" + String.format("%04d", scan_num) + ".vcent.txt";
		try
		{
			File centfile = new File(pfilename);
			FileChecksum fchksum = new FileChecksum(centfile);
			if (!fchksum.verify(false))
				throw new IOException("Invalid checksum in centroided file " + pfilename);
	
			List<String> lines = fchksum.getFileStrings();
			mzPeaks = new ArrayList<DataPoint>();
			for (String line:lines)
			{
				if (line.startsWith("#") || line.isEmpty())	// skip comment lines
					continue;
	
				Scanner sc = new Scanner(line);
				double mz  = sc.nextDouble();
				double y   = sc.nextDouble();
				mzPeaks.add(new SimpleDataPoint(mz, y));
				sc.close();
			}
			centfile.delete();	// delete the temporary results peaks list file
		}
		catch (FileNotFoundException e) { /* expect some scans might not be included in original processing */ }
		catch (Exception e)
		{
			logger.info(e.getMessage());
			MZmineCore.getDesktop().displayErrorMessage("Error", "Cannot parse peaks file, " + pfilename, logger);
		}

		return (mzPeaks == null) ? null : mzPeaks.toArray(new DataPoint[0]);
	}

	/**
	 * Finish the retrieval process
	 */
	private void finishRetrieve()
	{
		vtmx.getPageDone();
		rawDataFile.removeJob(jobID);
	}
}