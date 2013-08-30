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

import net.sf.mzmine.modules.rawdatamethods.peakpicking.massdetection.MassDetectorSetupDialog;
import net.sf.mzmine.parameters.UserParameter;
import net.sf.mzmine.parameters.impl.SimpleParameterSet;
import net.sf.mzmine.parameters.parametertypes.BooleanParameter;
import net.sf.mzmine.parameters.parametertypes.IntegerParameter;
import net.sf.mzmine.parameters.parametertypes.StringParameter;
import net.sf.mzmine.parameters.parametertypes.PasswordParameter;
import net.sf.mzmine.util.ExitCode;

public class VeritomyxParameters extends SimpleParameterSet
{
	public static final StringParameter   username = new StringParameter(  "Username", "Login name for Veritomyx SaaS.");
	public static final PasswordParameter password = new PasswordParameter("Password", "Password for Veritomyx SaaS.");
	public static final IntegerParameter project = new IntegerParameter(
			"Project Number",
			"Project number in Veritomyx system.",
			0);
	public static final IntegerParameter first_scan = new IntegerParameter(
			"Min Scan Number",
			"Scan number of first scan to centroid.",
			1);
	public static final IntegerParameter last_scan = new IntegerParameter(
			"Max Scan Number",
			"Scan number of last scan of scan range to centroid.",
			1);
	public static final BooleanParameter dump_scans = new BooleanParameter(
			"Export Scan Files",
			"Write specified scans to text files for processing.");
	public static final BooleanParameter read_peaks = new BooleanParameter(
			"Import Peak Lists",
			"Read peak lists for specified scans from processed text files.");

	public VeritomyxParameters()
	{
		super(new UserParameter[] { username, password, project, first_scan, last_scan, dump_scans, read_peaks });
	}

	public ExitCode showSetupDialog()
	{
		MassDetectorSetupDialog dialog = new MassDetectorSetupDialog(Veritomyx.class, this);
		dialog.setVisible(true);
		return dialog.getExitCode();
	}
}