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
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;
import java.util.logging.Logger;

import javax.annotation.Nonnull;

import net.sf.mzmine.data.DataPoint;
import net.sf.mzmine.data.Scan;
import net.sf.mzmine.data.impl.SimpleDataPoint;
import net.sf.mzmine.modules.rawdatamethods.peakpicking.massdetection.MassDetector;
import net.sf.mzmine.parameters.ParameterSet;
import net.sf.opensftp.SftpException;
import net.sf.opensftp.SftpResult;
import net.sf.opensftp.SftpSession;
import net.sf.opensftp.SftpUtil;
import net.sf.opensftp.SftpUtilFactory;
import FileChecksum.FileChecksum;

public class Veritomyx implements MassDetector
{
	private Logger logger       = Logger.getLogger(this.getClass().getName());
	private int last_scan_num   = -1;
	private SftpUtil sftp       = SftpUtilFactory.getSftpUtil();
	private SftpSession session = null;
	private String host         = "boman";
	private String user         = "dschmidt";
	private String password     = "vtmx.eldersdi";
	private String dir          = "batches";

	/**
	 * Open the SFTP session
	 */
	private boolean _open_sftp_session()
	{
		if (session == null)
		{
			try {
				session = sftp.connectByPasswdAuth(host, 22, user, password, SftpUtil.STRICT_HOST_KEY_CHECKING_OPTION_NO, 10000);
			} catch (SftpException e) {
				session = null;
				logger.info("Error: Cannot connect to SFTP server " + user + "@" + host);
				e.printStackTrace();
				return false;
			}
			SftpResult result = sftp.cd(session, dir);
			if (!result.getSuccessFlag())
			{
				result = sftp.mkdir(session, dir);
				if (!result.getSuccessFlag())
				{
					logger.info("Error: Cannot create remote directory, " + dir);
					return false;
				}
				result = sftp.cd(session, dir);
				if (!result.getSuccessFlag())
				{
					logger.info("Error: Cannot find remote directory, " + dir);
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

		logger.info("Put " + user + "@" + host + ":" + dir + "/" + fname);
		if (!_open_sftp_session())
			return false;

		result = sftp.rm(session, fname);
		result = sftp.put(session, fname, fname + ".filepart");
		if (!result.getSuccessFlag())
		{
			logger.info("Error: Cannot write file: " + fname);
			return false;
		}
		result = sftp.rename(session, fname + ".filepart", fname); //rename a remote file
		if (!result.getSuccessFlag())
		{
			logger.info("Error: Cannot rename file: " + fname);
			return false;
		}

		return true;
	}

	/**
	 * Transfer the given file from SFTP drop
	 * 
	 * @param fname
	 */
	private boolean _sftp_get_file(String fname)
	{
		SftpResult  result;

		logger.info("Get " + user + "@" + host + ":" + dir + "/" + fname);
		if (!_open_sftp_session())
			return false;

		result = sftp.get(session, fname);
		if (!result.getSuccessFlag())
		{
			logger.info("Error: Cannot read file: " + fname);
			return false;
		}

		return true;
	}

	private void _web_page()
	{
		String exsistingFileName = "";
		try {
			// build the URL parameters
			String args = "?Version=" + "1.2.6";
			args += "&p=" + URLEncoder.encode("1000", "UTF-8");
			args += "&User=" + URLEncoder.encode("regression@veritomyx.com", "UTF-8");
			args += "&Code=" + URLEncoder.encode("joe3test", "UTF-8");
			args += "&Action=" + "upload";
			args += "&Size=" + "0";
			args += "&Fcnt=" + "0";
			args += "&Files=" + "0";
			args += "&Pcmd=" + "ctr";	// Centroid
			args += "&Tarf=" + URLEncoder.encode(exsistingFileName, "UTF-8");
			args += "&Over=" + "0";
			System.out.println(args + "\n");

			String boundary = "***232404jkg4220957934FW**";

			URL url = new URL("http://test.veritomyx.com/vtmx/interface/vtmx_batch_internal_t.php" + args);
			HttpURLConnection uc = (HttpURLConnection)url.openConnection();

			uc.setDoOutput(true);
			uc.setDoInput(true);
			uc.setAllowUserInteraction(false);
			uc.setUseCaches(false);
			uc.setRequestMethod("POST");
			uc.setRequestProperty("Connection", "Keep-Alive");
			uc.setRequestProperty("Content-Type", "multipart/form-data;boundary=" + boundary);

			DataOutputStream dos = new DataOutputStream(uc.getOutputStream());
			dos.writeBytes("--" + boundary + "\r\n");
			dos.writeBytes("Content-Disposition: form-data; name=\"userfile\";" + " filename=\"" + exsistingFileName + "\"" + "\r\n");
			dos.writeBytes("\r\n");

			// create a buffer of maximum size
			FileInputStream fileInputStream = new FileInputStream(new File(exsistingFileName));
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

			// Read the response from the HTTP server
			BufferedReader in = new BufferedReader(new InputStreamReader(uc.getInputStream()));
			String decodedString;
			while ((decodedString = in.readLine()) != null)
			{
				System.out.println(decodedString);
			}
			in.close();

		} catch (MalformedURLException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		} catch (UnsupportedEncodingException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	/**
	 * Compute the peaks list for the given scan
	 * 
	 * @param scan
	 * @param parameters
	 * @return
	 */
	public DataPoint[] getMassValues(Scan scan, ParameterSet parameters)
	{
		boolean dump_scans = parameters.getParameter(VeritomyxParameters.dump_scans).getValue();
		boolean read_peaks = parameters.getParameter(VeritomyxParameters.read_peaks).getValue();
		int     first_scan = parameters.getParameter(VeritomyxParameters.first_scan).getValue();
		int     last_scan  = parameters.getParameter(VeritomyxParameters.last_scan).getValue();

		List<DataPoint> mzPeaks = null;
		int scan_num = scan.getScanNumber();
		if ((scan_num >= first_scan) && (scan_num <= last_scan))	// only process scans within requested range
		{
			String filename = scan.exportFilename("") + ".gz";
	
			if (dump_scans)
			{
				scan.exportToFile("", filename);
				_sftp_put_file(filename);
			}
	
			if (read_peaks)
			{
				// convert filename to expected peak file name
				String pfilename = filename.replace(".txt", ".vcent.txt");
				logger.info("Reading centroided data from " + pfilename);
				_sftp_get_file(pfilename);
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
		}

		if (last_scan_num == -1)
		{
			int[] i = scan.getDataFile().getScanNumbers(scan.getMSLevel());
			last_scan_num = i[i.length - 1];
		}
		if (scan_num == last_scan_num)	// last scan in set
		{
			_close_sftp_session();
		}

		if (mzPeaks != null)
			return mzPeaks.toArray(new DataPoint[0]);	// Return an array of detected peaks sorted by MZ
		return null;
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
}
