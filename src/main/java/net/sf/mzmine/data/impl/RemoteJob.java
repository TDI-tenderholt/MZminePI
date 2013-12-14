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

package net.sf.mzmine.data.impl;

import net.sf.mzmine.data.RemoteJobInfo;
import net.sf.mzmine.data.RawDataFile;
import net.sf.mzmine.modules.rawdatamethods.peakpicking.massdetection.Veritomyx.VeritomyxSaaS;

/**
 * This defines a Veritomyx job
 */
public class RemoteJob implements RemoteJobInfo
{
	private String        jobID;
	private RawDataFile   rawDataFile;
	private String        targetName;
	private VeritomyxSaaS vtmx;
	
	public RemoteJob(String name, RawDataFile raw, String target, VeritomyxSaaS vtmxConn)
	{
		jobID       = name;
		rawDataFile = raw;
		targetName  = target;
//		if (vtmx != null)
			vtmx = vtmxConn;
//		else
//			this.vtmx = new VeritomyxSaaS(username, password, pid, name);
	}

    public String      getName()        { return jobID; }
    public String      toString()       { return jobID; }
    public RawDataFile getRawDataFile() { return rawDataFile; }
    public String      getTargetName()  { return targetName; }
    public int         getStatus()      { return (vtmx != null) ? vtmx.getPageStatus() : VeritomyxSaaS.UNDEFINED; }
}