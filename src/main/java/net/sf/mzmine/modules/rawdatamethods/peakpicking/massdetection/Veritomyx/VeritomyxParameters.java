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
import net.sf.mzmine.parameters.parametertypes.IntegerParameter;
import net.sf.mzmine.parameters.parametertypes.JobsComboParameter;
import net.sf.mzmine.parameters.parametertypes.PasswordParameter;
import net.sf.mzmine.parameters.parametertypes.StringParameter;
import net.sf.mzmine.util.ExitCode;

public class VeritomyxParameters extends SimpleParameterSet
{
	public static final StringParameter username = new StringParameter(
			"Veritomyx Username",
			"Login name (email address) for Veritomyx SaaS.");
	public static final PasswordParameter password = new PasswordParameter(
			"Password",
			"Password for Veritomyx SaaS.");
	public static final IntegerParameter project = new IntegerParameter(
			"Project Number",
			"Project to which jobs will be assigned within the Veritomyx system.\n" +
				"Your login must have access to this project.\n" +
				"Setting this to zero defaults to your primary project.",
			0);
	public static final IntegerParameter first_scan = new IntegerParameter(
			"Minimum Scan Number",
			"Lower limit of scan range to centroid for new job.",
			0);
	public static final IntegerParameter last_scan = new IntegerParameter(
			"Maximum Scan Number",
			"Upper limit of scan range to centroid for new job.",
			1);
	public static final JobsComboParameter job_list = new JobsComboParameter(
			"New or Pending Jobs (if any)",
			"Launch a new job with given scan range or\n" +
			"Await completion and retrieve results of a previously launched job.");

	public VeritomyxParameters()
	{
		super(new UserParameter[] { username, password, project, first_scan, last_scan, job_list });
	}

	public ExitCode showSetupDialog()
	{
		MassDetectorSetupDialog dialog = new MassDetectorSetupDialog(Veritomyx.class, this);
		dialog.setVisible(true);
		return dialog.getExitCode();
	}
}