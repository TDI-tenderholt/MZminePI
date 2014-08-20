/*
 * Copyright 2013-2014 Veritomyx
 */

package veritomyxSaaS;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;

import net.sf.opensftp.SftpException;
import net.sf.opensftp.SftpResult;
import net.sf.opensftp.SftpSession;
import net.sf.opensftp.SftpUtil;
import net.sf.opensftp.SftpUtilFactory;

import org.apache.log4j.Level;
import org.apache.log4j.Logger;

/**
 * This class is used to access the Veritomyx SaaS servers
 * 
 * @author dschmidt
 */
public class VeritomyxSaaS
{
	// Required CLI version (see https://secure.veritomyx.com/interface/API.php)
	public static final String reqVeritomyxCLIVersion = "2.00";

	// return codes from web pages
	public  static final int W_UNDEFINED =  0;
	public  static final int W_INFO      =  1;
	public  static final int W_RUNNING   =  2;
	public  static final int W_DONE      =  3;
	public  static final int W_DELETED   =  4;
	public  static final int W_EXCEPTION = -99;
	public  static final int W_ERROR             = -1;	// these are pulled from API.php
	public  static final int W_ERROR_API         = -3;
	public  static final int W_ERROR_LOGIN       = -3;
	public  static final int W_ERROR_PID         = -4;
	public  static final int W_ERROR_SFTP        = -5;
	public  static final int W_ERROR_INPUT       = -6;
	public  static final int W_ERROR_FILE_WRITE  = -7;
	public  static final int W_ERROR_JOB_WRITE   = -8;
	public  static final int W_ERROR_JOB_LAUNCH  = -9;
	public  static final int W_ERROR_JOB_TYPE    = -10;
	public  static final int W_ERROR_JOB_RESULTS = -11;
	public  static final int W_ERROR_ACTION      = -12;

	// page actions
	private static final String JOB_INIT   = "INIT";
	private static final String JOB_RUN    = "RUN";
	private static final String JOB_STATUS = "STATUS";
	private static final String JOB_DELETE = "DELETE";

	private Logger log;
	private String username;
	private String password;
	private int    aid;
	private String jobID;				// name of the job and the scans tar file
	private String dir;
	private String quality;
	private String pi_version;
	private int    scans_in;
	private int    scans_done;

	private String    host;
	private String    sftp_user;
	private String    sftp_pw;
	private SftpUtil  sftp;

	private int    web_result;
	private String web_str;

	/**
	 * Constructor
	 * 
	 * @param reqVersion
	 * @param debug
	 */
	public VeritomyxSaaS(boolean debug, String server)
	{
		log        = Logger.getLogger(this.getClass().getName());
		log.setLevel(debug ? Level.INFO : Level.DEBUG);
		log.info(this.getClass().getName());
		jobID      = null;
		dir        = null;
		host       = server;
		sftp_user  = null;
		sftp_pw    = null;
		sftp       = null;
		web_result = W_UNDEFINED;
		web_str    = null;
	}

	/**
	 * Define the JobID
	 *  
	 * @param email
	 * @param passwd
	 * @param account
	 * @param existingJobName
	 * @param scanCount
	 * @return
	 */
	public int init(String email, String passwd, int account, String existingJobName, int scanCount)
	{
		jobID    = null;	// if jobID is set, this is a valid job
		username = email;
		password = passwd;
		aid      = account;
		boolean pickup = ((existingJobName != null) && (existingJobName.startsWith("vpi-") == true));

		// make sure we have access to the Veritomyx Server
		// this also gets the job_id and SFTP credentials
		if (!pickup)
		{
			if (getPage(JOB_INIT, scanCount, 0.0, 1.0) != W_INFO)
				return web_result;

			// got a valid result from JOB_INIT, parse it
			String sa[] = web_str.split("\\|");
			jobID       = sa[1];
			aid         = Integer.parseInt(sa[2]);
			sftp_user   = sa[3];
			sftp_pw     = sa[4];
			String qos  = sa[6];
			String ver  = sa[7];

			if (jobID == null)	// no valid job
				return web_result;

			String qos_array[] = qos.split(",");
			String ver_array[] = ver.split(",");
			quality    = qos_array[0].substring(0, qos_array[0].indexOf("="));		// just accept the first choice QoS
			pi_version = ver_array[0];												// just accept the first choice PI version
		}
		else
		{
			// check this job ID
			jobID = existingJobName;
			if (getPageStatus() != W_DONE)
				return web_result;

			// got a valid result from JOB_INIT, parse it
			String sa[]        = web_str.split("\\|");
			jobID              = sa[1];
			aid                = Integer.parseInt(sa[2]);
			sftp_user          = sa[3];
			sftp_pw            = sa[4];
			scans_in           = Integer.parseInt(sa[5]);
			scans_done         = Integer.parseInt(sa[6]);
			float cost         = Float.parseFloat(sa[7]);
			String result_file = sa[8];
		}

		sftp = SftpUtilFactory.getSftpUtil();
		SftpSession session = openSession();	// open to verify we can
		if (session == null)
		{
			jobID      = null;
			web_str    = "SFTP access not available";
			web_result = W_ERROR_SFTP;
			return web_result;
		}
		closeSession(session);
		return web_result;
	}

	/**
	 * Provide access to some private data
	 * 
	 * @return
	 */
	public String getJobID()            { return jobID; }
	public int    getPageRun(int count) { return getPage(JOB_RUN,    count, 0.0, 0.0); }
	public int    getPageStatus()       { return getPage(JOB_STATUS,     0, 0.0, 0.0); }
	public int    getPageDone()         { return getPage(JOB_DELETE,     0, 0.0, 0.0); }
	public String getPageStr()          { return web_str; }
	public int    get_scans_in()        { return scans_in; }
	public int    get_scans_done()      { return scans_done; }

	/**
	 * Get the first line of a web page from the Veritomyx server
	 * Puts first line of results into web_results String
	 * 
	 * @param action
	 * @param count
	 * @param minm
	 * @param maxm
	 * @return int
	 */
	private int getPage(String action, int count, double minm, double maxm)
	{
		web_result = W_UNDEFINED;
		web_str    = "";
		if ((action != JOB_INIT) && (action != JOB_RUN) && (action != JOB_STATUS) && (action != JOB_DELETE))
		{
			web_result = W_ERROR_ACTION;
			web_str    = "Invalid action";
			return web_result;
		}

		BufferedReader    in = null;
		HttpURLConnection uc = null;
		try {
			// build the URL with parameters
			String page = "https://" + host + "/api" 
					+ "?Version=" + reqVeritomyxCLIVersion	// online CLI version that matches this interface
					+ "&User="    + URLEncoder.encode(username, "UTF-8")
					+ "&Code="    + URLEncoder.encode(password, "UTF-8")
					+ "&Action="  + action;
			if ((action == JOB_INIT) && (jobID == null))	// new job request
			{
				page += "&Account=" + aid
					  + "&Command=" + "ckm"		// Centroid Set
					  + "&Count="   + count
					  + "&MinMass=" + minm
					  + "&MaxMass=" + maxm;
			}
			else if ((action == JOB_RUN) && (jobID != null))	// run job request
			{
				page += "&Job=" + jobID
					  + "&ArchiveType=tar"
					  + "&CalibrationScans=0"
					  + "&QoS=" + quality
					  + "&PIVersion=" + pi_version;
			}
			else if (jobID != null)	// all the rest require a jobID
			{
				page += "&Job=" + jobID;
			}
			else
			{
				web_result = W_ERROR_JOB_TYPE;
				web_str    = "Job ID, " + jobID + ", not defined";
				return web_result;
			}
			log.debug(page);

			URL url = new URL(page);
			uc = (HttpURLConnection)url.openConnection();
			uc.setUseCaches(false);
			uc.setRequestMethod("POST");
			uc.setReadTimeout(240 * 1000);	// give it 4 minutes to respond
			System.setProperty("java.net.preferIPv4Stack", "true");	// without this we get exception in getInputStream
			uc.connect();

			// Read the response from the HTTP server
			in = new BufferedReader(new InputStreamReader(uc.getInputStream()));
			String decodedString;
			while ((decodedString = in.readLine()) != null)
			{
				log.debug(decodedString);
				if (web_result == W_UNDEFINED)
				{
					web_str = decodedString;
					if      (web_str.startsWith("Info:"))    web_result = W_INFO;
					else if (web_str.startsWith("Running:")) web_result = W_RUNNING;
					else if (web_str.startsWith("Done:"))    web_result = W_DONE;
					else if (web_str.startsWith("Deleted:")) web_result = W_DELETED;
					else if (web_str.startsWith("Error-"))   web_result = - Integer.parseInt(web_str.substring(6, web_str.indexOf(":"))); // "ERROR-#"
					else                                     web_result = W_EXCEPTION;
				}
			}
		}
		catch (Exception e)
		{
			log.error(e.getMessage());
			web_result = W_EXCEPTION;
			web_str    = "Web exception - Unabled to connect to server";
		}
		try { in.close();      } catch (Exception e) { }
		try { uc.disconnect(); } catch (Exception e) { }
		log.debug("Web results: [" + web_result + "] '" + web_str + "'");
		return web_result;
	}

	/**
	 * Open the SFTP session and cd into the account/aid directory
	 * 
	 * @return SftpSession
	 */
	private SftpSession openSession()
	{
		SftpSession session;
		try {
			session = sftp.connectByPasswdAuth(host, 22, sftp_user, sftp_pw, SftpUtil.STRICT_HOST_KEY_CHECKING_OPTION_NO, 3000);
		} catch (SftpException e) {
			session = null;
			web_result = W_ERROR_SFTP;
			web_str    = "Cannot connect to SFTP server " + sftp_user + "@" + host;
			return null;
		}
		dir = "accounts/" + aid;
		SftpResult result = sftp.cd(session, dir);	// cd into the account directory
		if (!result.getSuccessFlag())
		{
			web_result = W_ERROR_SFTP;
			web_str    = "Cannot find remote directory, " + dir;
			return null;
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
	 * @param path
	 * @return boolean
	 */
	public boolean putFile(String path)
	{
		SftpResult result;
		File f = new File(path);
		String fname = f.getName();
		log.info("Transmit " + sftp_user + "@" + host + ":" + dir + "/" + fname);
		SftpSession session = openSession();
		if (session == null)
			return false;

		try { sftp.rm(session, fname); } catch (Exception e) {}
		try { sftp.rm(session, fname + ".filepart"); } catch (Exception e) {}
		result = sftp.put(session, path, fname + ".filepart");
		if (!result.getSuccessFlag())
		{
			closeSession(session);
			web_result = W_ERROR_SFTP;
			web_str    = "Cannot write file: " + fname;
			return false;
		}
		else
		{
			result = sftp.rename(session, fname + ".filepart", fname); //rename a remote file
			if (!result.getSuccessFlag())
			{
				closeSession(session);
				web_result = W_ERROR_SFTP;
				web_str    = "Cannot rename file: " + fname;
				return false;
			}
		}
		closeSession(session);
		return true;
	}

	/**
	 * Transfer the given file from SFTP drop
	 * 
	 * @param path
	 * @return boolean
	 */
	public boolean getFile(String path)
	{
		SftpResult result;
		File f = new File(path);
		String fname = f.getName();
		log.info("Retrieve " + sftp_user + "@" + host + ":" + dir + "/" + fname);
		SftpSession session = openSession();
		if (session == null)
			return false;

		result = sftp.get(session, fname, path);
		if (!result.getSuccessFlag())
		{
			closeSession(session);
			web_result = W_ERROR_SFTP;
			web_str    = "Cannot read file: " + fname;
			return false;
		}
		sftp.rm(session, fname);
		closeSession(session);
		return true;
	}
}
