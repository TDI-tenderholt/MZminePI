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

import net.sf.mzmine.data.JobInfo;
import net.sf.mzmine.modules.rawdatamethods.peakpicking.massdetection.Veritomyx.VeritomyxSaaS;

/**
 * This defines a Veritomyx job
 */
public class RemoteJobInfo implements JobInfo
{
	private String name;
	private int min_scan, max_scan;
	private VeritomyxSaaS vtmx;
	
	public RemoteJobInfo(String name, int min_scan, int max_scan, VeritomyxSaaS vtmx)
	{
		this.name     = name;
		this.min_scan = min_scan;
		this.max_scan = max_scan;
//		if (vtmx != null)
			this.vtmx = vtmx;
//		else
//			this.vtmx = new VeritomyxSaaS(username, password, pid, name, min_scan, max_scan);

	}

    public String getName()    { return name; }
    public int    getMinScan() { return min_scan; }
    public int    getMaxScan() { return max_scan; }
    public int    getStatus()  { return vtmx.getStatus(); }
}