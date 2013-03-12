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

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;
import java.util.logging.Logger;

import javax.annotation.Nonnull;

import net.sf.mzmine.data.DataPoint;
import net.sf.mzmine.data.RawDataFile;
import net.sf.mzmine.data.Scan;
import net.sf.mzmine.modules.rawdatamethods.peakpicking.massdetection.MassDetector;
import net.sf.mzmine.parameters.ParameterSet;
import FileChecksum.FileChecksum;

import com.google.common.base.Charsets;
import com.google.common.io.Files;

public class Veritomyx implements MassDetector
{
	private Logger logger = Logger.getLogger(this.getClass().getName());

	/**
	 * 
	 */
	public DataPoint[] getMassValues(Scan scan, ParameterSet parameters)
	{
		int     first_scan = parameters.getParameter(VeritomyxParameters.first_scan).getValue();
		int     last_scan  = parameters.getParameter(VeritomyxParameters.last_scan).getValue();
		boolean dump_scans = parameters.getParameter(VeritomyxParameters.dump_scans).getValue();
		boolean read_peaks = parameters.getParameter(VeritomyxParameters.read_peaks).getValue();
		
		String datafilename = scan.getDataFile().getName();

		RawDataFile rawdata = scan.getDataFile();
		ArrayList<VeritomyxMzDataPoint> mzPeaks = new ArrayList<VeritomyxMzDataPoint>();

		if (dump_scans)
		{
			for (int s = first_scan; s <= last_scan; s++)
			{
				scan = rawdata.getScan(s);
				if (scan == null)
					continue;

				String scanfilename = datafilename + ".MS" + scan.getMSLevel() +"_S" + s + ".txt";
				logger.info("Saving scan to " + scanfilename);
				DataPoint points[] = scan.getDataPoints();	// get data sorted in m/z
				try
				{
					File         scanfile = new File(scanfilename);
					PrintWriter  fd       = new PrintWriter(scanfile);
					FileChecksum fchksum  = new FileChecksum(scanfile);
					fd.print("# Raw File: " + scan.getDataFile().getName() + "\n");
					fd.print("# Scan: "     + scan.getScanNumber() + "\n");
					fd.print("# MS Level: " + scan.getMSLevel() + "\n");
					for (int i = 0; i < scan.getNumberOfDataPoints(); i++)
					{
						String line = points[i].getMZ() + "\t" + points[i].getIntensity();
						fd.print(line + "\n");
						fchksum.hash_line(line);
					}
					fd.close();
					fchksum.append_txt(false);
					fd = null;
				}
				catch (Exception e)
				{
					logger.info(e.getMessage());
					e.printStackTrace();
					return mzPeaks.toArray(new DataPoint[0]);
				}
			}
		}

		if (read_peaks)
		{
			int s = scan.getScanNumber();
			if ((s < first_scan) || (s < last_scan))
			{
				return mzPeaks.toArray(new DataPoint[0]);
			}

			String centfilename = datafilename + ".MS" + scan.getMSLevel() +"_S" + s + "_cent.txt";
			logger.info("Reading centroided data from " + centfilename);
			try
			{
				File centfile = new File(centfilename);
				FileChecksum fchksum = new FileChecksum(centfile);
				if (fchksum.verify(false))
				{
					throw new IOException("Invalid checksum in centroided file " + centfilename);
				}
				List<String> lines = Files.readLines(centfile , Charsets.UTF_8);
				for (String line:lines)
				{
					if (line.startsWith("#"))	// skip comment lines
						continue;

					Scanner sc = new Scanner(line);
					double mz  = sc.nextDouble();
					double y   = sc.nextDouble();
					mzPeaks.add(new VeritomyxMzDataPoint(mz, y));
					sc.close();
				}
			}
			catch (Exception e)
			{
				logger.info(e.getMessage());
				e.printStackTrace();
				return mzPeaks.toArray(new DataPoint[0]);
			}
		}

		// Return an array of detected peaks sorted by MZ
		return mzPeaks.toArray(new DataPoint[0]);
    }

    public @Nonnull String getName()
    {
    	return "Veritomyx Centroid";
    }

    @Override
    public @Nonnull Class<? extends ParameterSet> getParameterSetClass()
    {
    	return VeritomyxParameters.class;
    }
}