/*
 * Copyright 2013-2013 The Veritomyx
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
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;
import java.util.logging.Logger;
import java.util.zip.GZIPInputStream;

import net.sf.mzmine.data.DataPoint;
import net.sf.mzmine.data.Scan;
import net.sf.mzmine.data.impl.SimpleDataPoint;
import net.sf.mzmine.parameters.ParameterSet;
import net.sf.mzmine.project.impl.RawDataFileImpl;

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
	private Logger logger;
	private String job_id = null;	// name of the job and the scans tar file
	private int    status;
	private VeritomyxSaaS vtmx = null;
	private String username;
	private String password;
	private int    pid;
	private int    first_scan;
	private int    last_scan;
	private String job_pickup;
	private TarOutputStream tarfile = null;

	public PeakInvestigatorTask(ParameterSet parameters)
	{
		logger = Logger.getLogger(this.getClass().getName());
		logger.finest("Initializing PeakInvestigator™ Task");

		// pickup all the parameters
		username   = parameters.getParameter(VeritomyxParameters.username).getValue();
		password   = parameters.getParameter(VeritomyxParameters.password).getValue();
		pid        = parameters.getParameter(VeritomyxParameters.project).getValue();
		job_pickup = parameters.getParameter(VeritomyxParameters.job_list).getValue();
		first_scan = parameters.getParameter(VeritomyxParameters.first_scan).getValue();
		last_scan  = parameters.getParameter(VeritomyxParameters.last_scan).getValue();

		// make sure we have access to the Veritomyx Server
		// this also gets the job_id and SFTP credentials
		vtmx = new VeritomyxSaaS(username, password, pid, job_pickup, first_scan, last_scan);
		job_id = vtmx.getJobID();
		status = vtmx.getStatus();
		if (status > VeritomyxSaaS.UNDEFINED)
		{
			// overwrite the scan range with original job range
			first_scan = vtmx.getFirstScan();
			last_scan  = vtmx.getLastScan();
		}
	}

	/**
	 * Return the name of this task
	 * 
	 * @return
	 */
	public  String getName()        { return job_id; }
	private String intputFilename() { return job_id + ".scans.tar"; }
	private String outputFilename() { return job_id + ".vcent.tar"; }

	/**
	 * Compute the peaks list for the given scan
	 * 
	 * @param scan
	 * @return
	 */
	public DataPoint[] getMassValues(Scan scan)
	{
		List<DataPoint> mzPeaks = null;
		RawDataFileImpl raw = (RawDataFileImpl) scan.getDataFile();
		int scan_num      = scan.getScanNumber();
		int scanNumbers[] = raw.getScanNumbers(scan.getMSLevel());
		if (scanNumbers[0] > first_scan)						// make sure the first scan is in range
			first_scan = scanNumbers[0];
		if (scanNumbers[scanNumbers.length - 1] < last_scan)	// make sure the last scan is in range
			last_scan = scanNumbers[scanNumbers.length - 1];
		File f = null;

		// simply return null if we are not in our scan range
		if ((scan_num < first_scan) || (scan_num > last_scan))
			return null;

		// ########################################################################
		// Export all scans to remote processor
		if (status <= VeritomyxSaaS.UNDEFINED)
		{
			try {
				if (scan_num == first_scan)
				{
					raw.addJob(job_id, first_scan, last_scan);	// record this job start
					tarfile = new TarOutputStream(new BufferedOutputStream(new FileOutputStream(intputFilename())));
				}
	
				// export the scan to a file
				String filename = "scan" + String.format("%04d", scan_num) + ".txt.gz";
				scan.exportToFile("", filename);
				
				// put the exported scan into the tar file
				f = new File(filename);
				tarfile.putNextEntry(new TarEntry(f, filename));
				BufferedInputStream origin = new BufferedInputStream(new FileInputStream(f));
				int count;
				byte data[] = new byte[2048];
				while ((count = origin.read(data)) != -1)
					tarfile.write(data, 0, count);
				origin.close();
				f.delete();			// remove the local copy of the scan file
				tarfile.flush();

				// if done, ship them to the SFTP drop
				if (scan_num == last_scan)
				{
					tarfile.close();
					vtmx.putFile(intputFilename());
		
					//####################################################################
					// start for remote job
					if (vtmx.getPage(VeritomyxSaaS.JOB_RUN) < VeritomyxSaaS.RUNNING)
					{
						logger.info("Error: Failed to launch job for " + job_id);
						return null;
					}

					// job was started - record it
					logger.info(vtmx.getPageData().split(" ",2)[1]);
					raw.updateJob(job_id, "Running");
					f = new File(intputFilename());
					f.delete();			// remove the local copy of the tar file
				}
			} catch (Exception e) {
				logger.info(e.getMessage());
				logger.info("Error: Cannot create tar file");
			}

			return null;	// never return peaks from pass 1
		}

		//####################################################################
		// wait for remote job to complete
		while (status == VeritomyxSaaS.RUNNING)
		{
			try {
				Thread.sleep(60 * 1000);	// sleep for 60 seconds
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
			status = vtmx.getStatus();	// refresh the job status
		}

		if (status == VeritomyxSaaS.DONE)
		{
			if (scan_num == first_scan)
			{
				logger.info(vtmx.getPageData().split(" ",2)[1]);
				raw.updateJob(job_id, "Done");
	
				//####################################################################
				// read the results tar file and extract all the peak list files
				logger.info("Reading centroided data from " + outputFilename());
				vtmx.getFile(outputFilename());
				{
					TarInputStream tis = null;
					FileOutputStream outputStream = null;
					try {
						tis = new TarInputStream(new GZIPInputStream(new FileInputStream(outputFilename())));
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
						f = new File(outputFilename());
						f.delete();			// remove the local copy of the results tar file
					} catch (Exception e1) {
						logger.info(e1.getMessage());
						logger.info("Error: Cannot parse results file");
						e1.printStackTrace();
					} finally {
						try { tis.close(); } catch (Exception e) {}
						try { outputStream.close(); } catch (Exception e) {}
					}
				}
			}

			//####################################################################
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
			catch (Exception e)
			{
				logger.info(e.getMessage());
				logger.info("Error: Cannot parse peaks file.");
				e.printStackTrace();
			}
			
			if (scan_num == last_scan)
			{
				vtmx.getPage(VeritomyxSaaS.JOB_DONE);
				raw.removeJob(job_id);
				vtmx.closeSession();
			}
		}

		if (mzPeaks == null)
			return null;
		return mzPeaks.toArray(new DataPoint[0]);	// Return an array of detected peaks sorted by MZ
	}

}