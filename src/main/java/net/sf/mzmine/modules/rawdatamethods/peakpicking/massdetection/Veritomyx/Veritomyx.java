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
import FileChecksum.FileChecksum;

import com.google.common.base.Charsets;
import com.google.common.io.Files;

public class Veritomyx implements MassDetector
{
	private Logger logger = Logger.getLogger(this.getClass().getName());
	private boolean scans_dumped = false;

	private void transferFile(String fname)
	{
		SftpUtil util = SftpUtilFactory.getSftpUtil();
		SftpSession session = null;
		SftpResult  result;
		String host     = "boman";
		String user     = "dschmidt";
		String password = "vtmx.eldersdi";
		String dir      = "batches";

		logger.info("Copy " + fname + " to " + user + "@" + host + ":" + dir);

		try {
			session = util.connectByPasswdAuth(host, 22, user, password, SftpUtil.STRICT_HOST_KEY_CHECKING_OPTION_NO, 10000);
		} catch (SftpException e) {
			logger.info("Error: Cannot connect to SFTP server " + user + "@" + host);
			e.printStackTrace();
			return;
		}

		result = util.cd(session, dir);
		if (!result.getSuccessFlag())
		{
			result = util.mkdir(session, dir);
			if (!result.getSuccessFlag())
			{
				logger.info("Error: Cannot find or create " + dir + " directory");
				return;
			}
		}
		result = util.rm(session, fname);
		result = util.put(session, fname, fname + ".filepart");
		if (!result.getSuccessFlag())
		{
			logger.info("Error: Cannot write file: " + fname);
			return;
		}
		result = util.rename(session, fname + ".filepart", fname); //rename a remote file
		if (!result.getSuccessFlag())
		{
			logger.info("Error: Cannot rename file: " + fname);
			return;
		}
		util.disconnect(session); //quit
	}

	/**
	 * 
	 */
	public DataPoint[] getMassValues(Scan scan, ParameterSet parameters)
	{
		int     first_scan = parameters.getParameter(VeritomyxParameters.first_scan).getValue();
		int     last_scan  = parameters.getParameter(VeritomyxParameters.last_scan).getValue();
		boolean dump_scans = parameters.getParameter(VeritomyxParameters.dump_scans).getValue();
		boolean read_peaks = parameters.getParameter(VeritomyxParameters.read_peaks).getValue();
		
		RawDataFile rawdata = scan.getDataFile();
		ArrayList<DataPoint> mzPeaks = new ArrayList<DataPoint>();

		if (dump_scans && !scans_dumped)
		{
			for (int s = first_scan; s <= last_scan; s++)
			{
				Scan tmpscan = rawdata.getScan(s);
				if (tmpscan != null)
				{
					String fileName = scan.exportFilename("");
					tmpscan.exportToFile("", fileName, true);
					transferFile(fileName);
				}
			}
			scans_dumped = true;

			try {
				// build the tar file
				String exsistingFileName = scan.exportFilename("");

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

		if (read_peaks)
		{
			int s = scan.getScanNumber();
			if ((s < first_scan) || (s > last_scan))
				return null;

			String centfilename = scan.exportFilename("").replace(".txt", ".vcent.txt");
			logger.info("Reading centroided data from " + centfilename);
			try
			{
				File centfile = new File(centfilename);
				FileChecksum fchksum = new FileChecksum(centfile);
				if (!fchksum.verify(false))
					throw new IOException("Invalid checksum in centroided file " + centfilename);

				List<String> lines = Files.readLines(centfile , Charsets.UTF_8);
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
				return null;
			}
		}

		// Return an array of detected peaks sorted by MZ
		return mzPeaks.toArray(new DataPoint[0]);
    }

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