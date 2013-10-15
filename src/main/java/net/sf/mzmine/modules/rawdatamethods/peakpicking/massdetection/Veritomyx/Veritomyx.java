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
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;
import java.util.logging.Logger;
import java.util.zip.GZIPInputStream;

import javax.annotation.Nonnull;

import net.sf.mzmine.data.DataPoint;
import net.sf.mzmine.data.RawDataFile;
import net.sf.mzmine.data.Scan;
import net.sf.mzmine.data.impl.SimpleDataPoint;
import net.sf.mzmine.modules.rawdatamethods.peakpicking.massdetection.MassDetector;
import net.sf.mzmine.parameters.ParameterSet;
import net.sf.opensftp.SftpException;
import net.sf.opensftp.SftpResult;
import net.sf.opensftp.SftpSession;
import net.sf.opensftp.SftpUtil;
import net.sf.opensftp.SftpUtilFactory;

import org.xeustechnologies.jtar.TarEntry;
import org.xeustechnologies.jtar.TarInputStream;
import org.xeustechnologies.jtar.TarOutputStream;

import FileChecksum.FileChecksum;

public class Veritomyx implements MassDetector
{
	private static final int WEB_UNDEFINED = -3;
	private static final int WEB_EXCEPTION = -2;
	private static final int WEB_ERROR     = -1;
	private static final int WEB_RUNNING   =  0;
	private static final int WEB_DONE      =  1;
	private int    web_result = WEB_UNDEFINED;
	private String web_str    = null;

	private Logger      logger      = null;
	private SftpUtil    sftp        = null;
	private SftpSession session     = null;
	private String      host        = null;
	private int         project     = 0;
	private String      username    = null;
	private String      password    = null;
	private String      sftp_user   = null;
	private String      sftp_pw     = null;
	private String      dir         = null;
	private String      tarfilename = null;

	public Veritomyx()
	{
		logger = Logger.getLogger(this.getClass().getName());
		sftp   = SftpUtilFactory.getSftpUtil();
		host   = "secure.veritomyx.com";
	}

	/**
	 * Return the name of this module
	 * 
	 * @return
	 */
	public @Nonnull String getName()
	{
		return "PeakInvestigator™";
	}

	@Override
	public @Nonnull Class<? extends ParameterSet> getParameterSetClass()
	{
		return VeritomyxParameters.class;
	}

	/**
	 * Compute the peaks list for the given scan
	 * 
	 * @param scan
	 * @param parameters
	 * @return
	 * @throws FileNotFoundException 
	 */
	public DataPoint[] getMassValues(Scan scan, ParameterSet parameters)
	{
		username           = parameters.getParameter(VeritomyxParameters.username).getValue();
		password           = parameters.getParameter(VeritomyxParameters.password).getValue();
		project            = parameters.getParameter(VeritomyxParameters.project).getValue();
		boolean start_job  = parameters.getParameter(VeritomyxParameters.start_job).getValue();
		int     first_scan = parameters.getParameter(VeritomyxParameters.first_scan).getValue();
		int     last_scan  = parameters.getParameter(VeritomyxParameters.last_scan).getValue();
		List<DataPoint> mzPeaks = null;
		RawDataFile raw   = scan.getDataFile();
		int scan_num      = scan.getScanNumber();
		int scanNumbers[] = raw.getScanNumbers(scan.getMSLevel());
		if (scanNumbers[0] > first_scan)						// make sure the first scan is in range
			first_scan = scanNumbers[0];
		if (scanNumbers[scanNumbers.length - 1] < last_scan)	// make sure the last scan is in range
			last_scan = scanNumbers[scanNumbers.length - 1];
		tarfilename = raw.getName() + ".scans.tar";

		// simply return null if we are not in our scan range
		if ((scan_num < first_scan) || (scan_num > last_scan))
			return null;

		if (start_job)	// launch the job on remote server
		{
			String filename = scan.exportFilename("") + ".gz";
			scan.exportToFile("", filename);

			if (scan_num == last_scan)
			{
				TarOutputStream     tarfile = null;
				BufferedInputStream origin  = null;
				
				_open_sftp_session();		// make sure we have a connection before taking time to build tar file
				
				try {
					tarfile = new TarOutputStream(new BufferedOutputStream(new FileOutputStream(tarfilename)));
	
					// archive all the scan files
					for (scan_num = first_scan; scan_num <= last_scan; scan_num++)
					{
						// get the scan and export it to a file
						scan = raw.getScan(scan_num);
						if (scan != null)
						{
							filename = scan.exportFilename("") + ".gz";
	
							// put the exported scan into the tar file
							File f = new File(filename);
							tarfile.putNextEntry(new TarEntry(f, filename));
							origin = new BufferedInputStream(new FileInputStream(f));
							int count;
							byte data[] = new byte[2048];
							while ((count = origin.read(data)) != -1)
								tarfile.write(data, 0, count);
							origin.close();
							f.delete();			// remove the local copy of the scan file
							tarfile.flush();
						}
					}
					tarfile.close();
	
					_sftp_put_file(tarfilename);
					_close_sftp_session();
	
					if (_web_page("run", tarfilename) < WEB_RUNNING)
					{
						logger.info("Error: Failed to launch job for " + tarfilename);
					}
					else
						logger.info(web_str.split(" ",2)[1]);
	
				} catch (IOException e) {
					logger.info(e.getMessage());
					e.printStackTrace();
				} finally {
					try { tarfile.close(); } catch (Exception e) {}
					try { origin.close(); } catch (Exception e) {}
				}
				File f = new File(tarfilename);
				f.delete();			// remove the local copy of the tar file
			}
		}
		else	// read the resulting peaks file
		{
			if (scan_num == first_scan)
			{
				_web_page("status", tarfilename);
				while (web_result != WEB_DONE)
				{
					try {
						Thread.sleep(60 * 1000);	// sleep for 60 seconds
					} catch (InterruptedException e) {
						e.printStackTrace();
					}
					_web_page("status", tarfilename);
				}
				logger.info(web_str.split(" ",2)[1]);
	
				// read the results tar file and extract all the peaks lists
				String rtarfilename = raw.getName() + ".vcent.tar";	// results tar file name
				_sftp_get_file(rtarfilename);
				logger.info("Reading centroided data from " + rtarfilename);
				{
					TarInputStream tis = null;
					FileOutputStream outputStream = null;
					try {
						tis = new TarInputStream(new GZIPInputStream(new FileInputStream(rtarfilename)));
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
					} catch (Exception e1) {
						e1.printStackTrace();
					} finally {
						try { tis.close(); } catch (Exception e) {}
						try { outputStream.close(); } catch (Exception e) {}
					}
				}
			}

			// convert filename to expected peak file name
			String pfilename = scan.exportFilename("").replace(".txt", ".vcent.txt");
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
			}
			catch (Exception e)
			{
				logger.info(e.getMessage());
				e.printStackTrace();
			}
		}

		if (mzPeaks == null)
			return null;
		return mzPeaks.toArray(new DataPoint[0]);	// Return an array of detected peaks sorted by MZ
	}

	/**
	 * Get the first line of a web page from the Veritomyx server
	 * Puts first line of results into web_results String
	 * 
	 * @param action
	 * @param tname
	 * @return int
	 */
	private int _web_page(String action, String tname)
	{
		web_result = WEB_UNDEFINED;
		web_str    = null;
		try {
			// build the URL with parameters
			String page = "http://" + host + "/vtmx/interface/vtmx_sftp_job.php" + 
					"?Version=" + "1.2.6" +
					"&User="    + URLEncoder.encode(username, "UTF-8") +
					"&Code="    + URLEncoder.encode(password, "UTF-8") +
					"&Project=" + project +
					"&Action="  + action;
			if ((action == "run") || (action == "status"))	// need more parameters for these command
			{
				page += "&Command=" + "ckm" +	// Centroid Set
						"&File="    + URLEncoder.encode(tname, "UTF-8") +
						"&Force="   + "0";
			}
			System.out.println(page + "\n");

			URL url = new URL(page);
			HttpURLConnection uc = (HttpURLConnection)url.openConnection();

			uc.setDoOutput(true);
			uc.setAllowUserInteraction(false);
			uc.setUseCaches(false);
			uc.setRequestMethod("POST");
			uc.setRequestProperty("Connection", "Keep-Alive");

/* extra code to write a file to the connection
			uc.setDoInput(true);
			String boundary = "***232404jkg4220957934FW**";
			uc.setRequestProperty("Content-Type", "multipart/form-data;boundary=" + boundary);
			DataOutputStream dos = new DataOutputStream(uc.getOutputStream());
			dos.writeBytes("--" + boundary + "\r\n");
			dos.writeBytes("Content-Disposition: form-data; name=\"userfile\";" + " filename=\"" + fname + "\"" + "\r\n");
			dos.writeBytes("\r\n");

			// create a buffer of maximum size
			FileInputStream fileInputStream = new FileInputStream(new File(fname));
			int maxBufferSize = 1 * 1024 * 1024;;
			int bytesAvailable = fileInputStream.available();
			int bufferSize = Math.min(bytesAvailable, maxBufferSize);
			byte[] buffer = new byte[bufferSize];

			// read file and write it into form...
			int bytesRead = fileInputStream.read(buffer, 0, bufferSize);
			while (bytesRead > 0)
			{
				dos.write(buffer, 0, bytesRead);
				bytesAvailable = fileInputStream.available();
				bufferSize = Math.min(bytesAvailable, maxBufferSize);
				bytesRead = fileInputStream.read(buffer, 0, bufferSize);
			}

			// send multi-part form data necessary after file data...
			dos.writeBytes("\r\n--" + boundary + "--\r\n");

			// close streams
			fileInputStream.close();
			dos.flush();
			dos.close();
*/

			// Read the response from the HTTP server
			BufferedReader in = new BufferedReader(new InputStreamReader(uc.getInputStream()));
			String decodedString;
			while ((decodedString = in.readLine()) != null)
			{
				System.out.println(decodedString);
				if (web_str == null) web_str = decodedString;
			}
			in.close();

		} catch (IOException e) {
			web_str = e.getMessage();
			web_result = WEB_EXCEPTION;
		}
		if (web_result == WEB_UNDEFINED)
		{
			if      (web_str.startsWith("Done: "))    { web_result = WEB_DONE;    }
			else if (web_str.startsWith("Error: "))   { web_result = WEB_ERROR;   }
			else if (web_str.startsWith("Running: ")) { web_result = WEB_RUNNING; }
		}
		return web_result;
	}

	/**
	 * Open the SFTP session
	 * 
	 * @return boolean
	 */
	private boolean _open_sftp_session()
	{
		if (sftp_user == null)
		{
			if (_web_page("sftp", "") != WEB_DONE)
			{
				logger.info("Error: SFTP access not available");
				return false;
			}
			String sa[] = web_str.split(" ");
			sftp_user  = sa[1];
			sftp_pw    = sa[2];
		}
		if (session == null)
		{
			try {
				session = sftp.connectByPasswdAuth(host, 22, sftp_user, sftp_pw, SftpUtil.STRICT_HOST_KEY_CHECKING_OPTION_NO, 3000);
			} catch (SftpException e) {
				session = null;
				logger.info("Error: Cannot connect to SFTP server " + sftp_user + "@" + host);
				e.printStackTrace();
				return false;
			}
			dir = "projects/" + project;
			SftpResult result = sftp.cd(session, dir);	// cd into the project directory
			if (!result.getSuccessFlag())
			{
				result = sftp.mkdir(session, dir + "/batches");
				if (!result.getSuccessFlag())
				{
					logger.info("Error: Cannot create remote directory, " + dir + "/batches");
					return false;
				}
				result = sftp.mkdir(session, dir + "/results");
				if (!result.getSuccessFlag())
				{
					logger.info("Error: Cannot create remote directory, " + dir + "/results");
					return false;
				}
			}
		}
		return true;
	}

	/**
	 * Close the SFTP session
	 */
	private void _close_sftp_session()
	{
		sftp.disconnect(session);
		session = null;
	}

	/**
	 * Transfer the given file to SFTP drop
	 * 
	 * @param fname
	 */
	private boolean _sftp_put_file(String fname)
	{
		SftpResult result;

		logger.info("Put " + sftp_user + "@" + host + ":" + dir + "/" + fname);
		if (!_open_sftp_session())
			return false;

		sftp.cd(session, "batches");
		result = sftp.rm(session, fname);
		result = sftp.put(session, fname, fname + ".filepart");
		if (!result.getSuccessFlag())
		{
			sftp.cd(session, "..");
			logger.info("Error: Cannot write file: " + fname);
			return false;
		}
		else
		{
			result = sftp.rename(session, fname + ".filepart", fname); //rename a remote file
			if (!result.getSuccessFlag())
			{
				sftp.cd(session, "..");
				logger.info("Error: Cannot rename file: " + fname);
				return false;
			}
		}
		sftp.cd(session, "..");

		return true;
	}

	/**
	 * Transfer the given file from SFTP drop
	 * 
	 * @param fname
	 */
	private boolean _sftp_get_file(String fname)
	{
		SftpResult result;

		logger.info("Get " + sftp_user + "@" + host + ":" + dir + "/" + fname);
		if (!_open_sftp_session())
			return false;

		sftp.cd(session, "results");
		result = sftp.get(session, fname);
		sftp.cd(session, "..");
		if (!result.getSuccessFlag())
		{
			logger.info("Error: Cannot read file: " + fname);
			return false;
		}

		return true;
	}

}
