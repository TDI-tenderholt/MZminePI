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

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.logging.Logger;

import net.sf.mzmine.main.MZmineCore;
import net.sf.opensftp.SftpException;
import net.sf.opensftp.SftpResult;
import net.sf.opensftp.SftpSession;
import net.sf.opensftp.SftpUtil;
import net.sf.opensftp.SftpUtilFactory;

/**
 * This class is used to access the Veritomyx SaaS servers
 * 
 * @author dschmidt
 *
 */
public class VeritomyxSaaS
{
	public  static final int EXCEPTION  = -2;
	public  static final int ERROR      = -1;
	public  static final int UNDEFINED  =  0;
	public  static final int RUNNING    =  1;
	public  static final int DONE       =  2;

	private static final int JOB_INIT   = 123;
	public  static final int JOB_RUN    = 234;
	public  static final int JOB_STATUS = 345;
	public  static final int JOB_DONE   = 456;

	private Logger logger;

	private String username;
	private String password;
	private int    pid;
	private String job_id = null;	// name of the job and the scans tar file
	private int    first_scan;
	private int    last_scan;

	private static final String host = "secure.veritomyx.com";;
	private String      sftp_user = null;
	private String      sftp_pw   = null;
	private SftpUtil    sftp      = null;

	private String dir = null;

	private int    web_result = UNDEFINED;
	private String web_str    = null;

	public VeritomyxSaaS(String username, String password, int pid, String job_str, int firstScan, int lastScan)
	{
		logger = Logger.getLogger(this.getClass().getName());

		this.username = username;
		this.password = password;
		this.pid      = pid;
		
		// make sure we have access to the Veritomyx Server
		// this also gets the job_id and SFTP credentials
		if (getPage(JOB_INIT) != DONE)
		{
			MZmineCore.getDesktop().displayErrorMessage("Error", web_str, logger);
			job_id = null;
			return;
		}
		String sa[] = web_str.split(" ");
		job_id      = sa[1];	// new job ID
		sftp_user   = sa[2];
		sftp_pw     = sa[3];

		// see if we were given a job ID
		if ((job_str != null) && (job_str.startsWith("job-") == true))
		{
			String jobID = job_str.substring(0, job_str.indexOf('['));	// extract job ID from beginning of string
			job_id = jobID;	// check this job ID
			int existing_job_status = getPage(JOB_STATUS);
			if ((existing_job_status != RUNNING) && (existing_job_status != DONE))
			{
				MZmineCore.getDesktop().displayErrorMessage("Error", "Job, " + jobID + ", not found", logger);
				job_id = null;	// not a valid job
				return;
			}
			else
			{
				// extract scan range from job_str
				String s  = job_str.substring(job_str.indexOf('[') + 1, job_str.indexOf(']'));
				firstScan = Integer.parseInt(s.substring(0, s.indexOf('.')));
				lastScan  = Integer.parseInt(s.substring(s.lastIndexOf('.') + 1));
			}
		}
		first_scan = firstScan;
		last_scan  = lastScan;

		if (job_id != null)
		{
			sftp = SftpUtilFactory.getSftpUtil();
			SftpSession session = openSession();	// open to verify we can
			if (session != null)
				closeSession(session);
			else
			{
				MZmineCore.getDesktop().displayErrorMessage("Error", "VTMX SFTP access not available", logger);
				job_id = null;
			}
		}
	}

	public String getJobID()     { return job_id;     }
	public int    getStatus()    { return getPage(JOB_STATUS); }
	public int    getFirstScan() { return first_scan; }
	public int    getLastScan()  { return last_scan;  }
	public String getPageData()  { return web_str;    }

	/**
	 * Get the first line of a web page from the Veritomyx server
	 * Puts first line of results into web_results String
	 * 
	 * @param action
	 * @return int
	 */
	public int getPage(int action)
	{
		web_result = UNDEFINED;
		web_str    = "";
		String act = null;
		if      (action == JOB_INIT)   { act = "JOB"; }
		else if (action == JOB_RUN)    { act = "RUN"; }
		else if (action == JOB_STATUS) { act = "STATUS"; }
		else if (action == JOB_DONE)   { act = "DONE"; }
		else
		{
			web_result = ERROR;
			web_str = "Invalid action";
			MZmineCore.getDesktop().displayErrorMessage("Error", web_str, logger);
			return ERROR;
		}

		BufferedReader    in = null;
		HttpURLConnection uc = null;
		try {
			// build the URL with parameters
			String page = "http://" + host + "/interface/vtmx_sftp_job.php" + 
					"?Version=" + "1.13" +	// minimum online CLI version that matches this interface
					"&User="    + URLEncoder.encode(username, "UTF-8") +
					"&Code="    + URLEncoder.encode(password, "UTF-8") +
					"&Project=" + pid +
					"&Action="  + act;
			if (action != JOB_INIT)	// need job_id for these commands
			{
				if (job_id == null)
				{
					web_result = ERROR;
					web_str = "Job ID not defined";
					MZmineCore.getDesktop().displayErrorMessage("Error", web_str, logger);
					return ERROR;
				}
				page += "&Command=" + "ckm" +	// Centroid Set
						"&Job="     + URLEncoder.encode(job_id, "UTF-8") +
						"&Force="   + "1";
			}
			logger.finest("dgshack: " + page);

			URL url = new URL(page);
			uc = (HttpURLConnection)url.openConnection();
			uc.setUseCaches(false);
			uc.setRequestMethod("POST");
			uc.setReadTimeout(15 * 1000);	// give it 15 seconds to respond
			System.setProperty("java.net.preferIPv4Stack", "true");	// without this we get exception in getInputStream
			uc.connect();

			// Read the response from the HTTP server
			in = new BufferedReader(new InputStreamReader(uc.getInputStream()));
			String decodedString;
			while ((decodedString = in.readLine()) != null)
			{
				logger.finest("dgshack: " + decodedString);
				if (web_result == UNDEFINED)
				{
					web_str = decodedString;
					if      (web_str.startsWith("Done: "))    { web_result = DONE;    }
					else if (web_str.startsWith("Error: "))   { web_result = ERROR;   }
					else if (web_str.startsWith("Running: ")) { web_result = RUNNING; }
				}
			}
		}
		catch (Exception e)
		{
			web_result = EXCEPTION;
			web_str = "Web exception - " + e.getMessage();
			MZmineCore.getDesktop().displayErrorMessage("Error", web_str, logger);
		}
		try { in.close();      } catch (Exception e) { }
		try { uc.disconnect(); } catch (Exception e) { }
		logger.finest("dgshack: Web results: [" + web_result + "] '" + web_str + "'");
		return web_result;
	}

	/**
	 * Open the SFTP session
	 * 
	 * @return boolean
	 */
	private SftpSession openSession()
	{
		SftpSession session;
		try {
			session = sftp.connectByPasswdAuth(host, 22, sftp_user, sftp_pw, SftpUtil.STRICT_HOST_KEY_CHECKING_OPTION_NO, 3000);
		} catch (SftpException e) {
			session = null;
			String msg = "Cannot connect to SFTP server " + sftp_user + "@" + host;
			MZmineCore.getDesktop().displayErrorMessage("Error", msg, logger);
			e.printStackTrace();
			return null;
		}
		dir = "projects/" + pid;
		SftpResult result = sftp.cd(session, dir);	// cd into the v_project directory
		if (!result.getSuccessFlag())
		{
			result = sftp.mkdir(session, "/batches");
			if (!result.getSuccessFlag())
			{
				String msg = "Cannot create remote directory, " + dir + "/batches";
				MZmineCore.getDesktop().displayErrorMessage("Error", msg, logger);
				return null;
			}
			result = sftp.mkdir(session, "/results");
			if (!result.getSuccessFlag())
			{
				String msg = "Cannot create remote directory, " + dir + "/results";
				MZmineCore.getDesktop().displayErrorMessage("Error", msg, logger);
				return null;
			}
		}
		return session;
	}

	/**
	 * Close the SFTP session
	 * Call this when we are closing the instance
	 */
	private void closeSession(SftpSession session)
	{
		if ((sftp != null) && (session != null))
			sftp.disconnect(session);
	}

	/**
	 * Transfer the given file to SFTP drop
	 * 
	 * @param fname
	 */
	public boolean putFile(String fname)
	{
		SftpResult result;
		logger.info("Put " + sftp_user + "@" + host + ":" + dir + "/" + fname);
		SftpSession session = openSession();
		if (session == null)
			return false;

		sftp.cd(session, "batches");
		try { sftp.rm(session, fname); } catch (Exception e) {}
		try { sftp.rm(session, fname + ".filepart"); } catch (Exception e) {}
		result = sftp.put(session, fname, fname + ".filepart");
		sftp.cd(session, "..");
		if (!result.getSuccessFlag())
		{
			String msg = "Cannot write file: " + fname;
			MZmineCore.getDesktop().displayErrorMessage("Error", msg, logger);
			closeSession(session);
			return false;
		}
		else
		{
			sftp.cd(session, "batches");
			result = sftp.rename(session, fname + ".filepart", fname); //rename a remote file
			sftp.cd(session, "..");
			if (!result.getSuccessFlag())
			{
				String msg = "Cannot rename file: " + fname;
				MZmineCore.getDesktop().displayErrorMessage("Error", msg, logger);
				closeSession(session);
				return false;
			}
		}
		closeSession(session);
		return true;
	}

	/**
	 * Transfer the given file from SFTP drop
	 * 
	 * @param fname
	 */
	public boolean getFile(String fname)
	{
		SftpResult result;
		logger.info("Get " + sftp_user + "@" + host + ":" + dir + "/" + fname);
		SftpSession session = openSession();
		if (session == null)
			return false;

		sftp.cd(session, "results");
		result = sftp.get(session, fname);
		if (!result.getSuccessFlag())
		{
			sftp.cd(session, "..");
			String msg = "Cannot read file: " + fname;
			MZmineCore.getDesktop().displayErrorMessage("Error", msg, logger);
			closeSession(session);
			return false;
		}
		sftp.rm(session, fname);
		sftp.cd(session, "..");
		closeSession(session);
		return true;
	}
}
