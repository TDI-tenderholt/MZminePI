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

package net.sf.mzmine.parameters.parametertypes;

import java.util.Arrays;
import java.util.Collection;

import javax.swing.JComboBox;

import net.sf.mzmine.data.RawDataFile;
import net.sf.mzmine.data.impl.RemoteJobInfo;
import net.sf.mzmine.main.MZmineCore;
import net.sf.mzmine.parameters.UserParameter;

import org.w3c.dom.Element;

/**
 * Jobs Combo Parameter implementation.
 * This retrieves the existing jobs for the given raw data file
 * and presents them in a pulldown selector.
 * 
 */
@SuppressWarnings("rawtypes")
public class JobsComboParameter implements UserParameter<String, JComboBox>
{
	public static final String NEWJOB = "Launch new job on given scan range";
	private String name, description;
	private String choices[], value;

	public JobsComboParameter(String name, String description)
	{
		this.name        = name;
		this.description = description;
		this.choices     = null;
		this.value       = NEWJOB;
	}

	@Override
	public String getDescription()
	{
		return description;
	}

	@SuppressWarnings("unchecked")
	@Override
	public JComboBox createEditingComponent()
	{
		RawDataFile[] dataFiles = MZmineCore.getCurrentProject().getDataFiles();
		//RawDataFile selectedFiles[] = MZmineCore.getDesktop().getSelectedDataFiles();

		// count the number of jobs
		int job_count = 1;
		for (RawDataFile raw : dataFiles)
			job_count += raw.getJobs().size();

		// build the list of choices
		choices = new String[job_count];
		choices[job_count = 0] = NEWJOB;
		for (RawDataFile raw : dataFiles)
		{
			for (RemoteJobInfo job : raw.getJobs())
			{
				choices[++job_count] = job.getName()
							+ "    " + job.getStatus()
							+ "    " + raw.getName();
			}
		}
		return new JComboBox(choices);
	}

	@Override
	public String getValue()
	{
		return value;
	}

	public String[] getChoices()
	{
		return choices;
	}

	public void setChoices(String newChoices[])
	{
		choices = newChoices;
	}

	@Override
	public void setValue(String value)
	{
		this.value = value;
	}

	@Override
	public JobsComboParameter cloneParameter()
	{
		JobsComboParameter copy = new JobsComboParameter(name, description);
		copy.choices = this.choices;
		copy.value   = this.value;
		return copy;
	}

	@Override
	public void setValueFromComponent(JComboBox component)
	{
		Object selectedItem = component.getSelectedItem();
		if (selectedItem == null)
			{
			value = null;
			return;
		}
		if (!Arrays.asList(choices).contains(selectedItem))
		{
			throw new IllegalArgumentException("Invalid value for parameter " + name + ": " + selectedItem);
		}
		int index = component.getSelectedIndex();
		if (index < 0)
			return;

		value = choices[index];
	}

	@Override
	public void setValueToComponent(JComboBox component, String newValue)
	{
		component.setSelectedItem(newValue);
	}

	@Override
	public void loadValueFromXML(Element xmlElement)
	{
	}

	@Override
	public void saveValueToXML(Element xmlElement)
	{
	}

	@Override
	public String getName()
	{
		return name;
	}

	@Override
	public String toString()
	{
	    return name;
	}
	
	@Override
	public boolean checkValue(Collection<String> errorMessages)
	{
		if (value == null)
		{
			errorMessages.add(name + " is not set properly");
			return false;
		}
		return true;
	}

}