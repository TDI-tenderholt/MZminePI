/*
 * Copyright 2006-2012 The MZmine 2 Development Team
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
 * MZmine 2; if not, write to the Free Software Foundation, Inc., 51 Franklin
 * St, Fifth Floor, Boston, MA 02110-1301 USA
 */

package net.sf.mzmine.desktop.impl.projecttree;

import java.awt.Color;
import java.awt.Component;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Vector;

import javax.swing.JPopupMenu;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreePath;

import net.sf.mzmine.data.MassList;
import net.sf.mzmine.data.PeakList;
import net.sf.mzmine.data.PeakListRow;
import net.sf.mzmine.data.RawDataFile;
import net.sf.mzmine.data.Scan;
import net.sf.mzmine.data.impl.RemoteJobInfo;
import net.sf.mzmine.main.MZmineCore;
import net.sf.mzmine.modules.visualization.infovisualizer.InfoVisualizerModule;
import net.sf.mzmine.modules.visualization.peaklist.PeakListTableModule;
import net.sf.mzmine.modules.visualization.peaksummary.PeakSummaryVisualizerModule;
import net.sf.mzmine.modules.visualization.scatterplot.ScatterPlotVisualizerModule;
import net.sf.mzmine.modules.visualization.spectra.SpectraVisualizerModule;
import net.sf.mzmine.modules.visualization.spectra.SpectraVisualizerParameters;
import net.sf.mzmine.modules.visualization.spectra.SpectraVisualizerWindow;
import net.sf.mzmine.modules.visualization.spectra.datasets.MassListDataSet;
import net.sf.mzmine.modules.visualization.threed.ThreeDVisualizerModule;
import net.sf.mzmine.modules.visualization.tic.TICVisualizerModule;
import net.sf.mzmine.modules.visualization.twod.TwoDVisualizerModule;
import net.sf.mzmine.parameters.ParameterSet;
import net.sf.mzmine.taskcontrol.Task;
import net.sf.mzmine.util.ExitCode;
import net.sf.mzmine.util.GUIUtils;

/**
 * This class handles pop-up menus and double click events in the project tree
 */
public class ProjectTreeMouseHandler extends MouseAdapter implements ActionListener {

    private ProjectTree tree;
    private JPopupMenu dataFilePopupMenu,
    				   peakListPopupMenu,
				            jobPopupMenu,
				           scanPopupMenu,
    				   massListPopupMenu,
    				peakListRowPopupMenu;
    private Object rightClickObj;

    /**
     * Constructor
     */
    public ProjectTreeMouseHandler(ProjectTree tree) {
	
		this.tree = tree;
	
		dataFilePopupMenu = new JPopupMenu();
		GUIUtils.addMenuItem(dataFilePopupMenu, "Show TIC",           this, "SHOW_TIC");
		GUIUtils.addMenuItem(dataFilePopupMenu, "Show mass spectrum", this, "SHOW_SPECTRUM");
		GUIUtils.addMenuItem(dataFilePopupMenu, "Show 2D visualizer", this, "SHOW_2D");
		GUIUtils.addMenuItem(dataFilePopupMenu, "Show 3D visualizer", this, "SHOW_3D");
		GUIUtils.addMenuItem(dataFilePopupMenu, "Remove",             this, "REMOVE_FILE");
	
		jobPopupMenu = new JPopupMenu();
		GUIUtils.addMenuItem(jobPopupMenu,      "Retrieve job", this, "RETRIEVE_JOB");
	
		scanPopupMenu = new JPopupMenu();
		GUIUtils.addMenuItem(scanPopupMenu,     "Show scan", this, "SHOW_SCAN");
	
		massListPopupMenu = new JPopupMenu();
		GUIUtils.addMenuItem(massListPopupMenu, "Show mass list",                       this, "SHOW_MASSLIST");
		GUIUtils.addMenuItem(massListPopupMenu, "Remove mass list",                     this, "REMOVE_MASSLIST");
		GUIUtils.addMenuItem(massListPopupMenu, "Remove all mass lists with this name", this, "REMOVE_ALL_MASSLISTS");
	
		peakListPopupMenu = new JPopupMenu();
		GUIUtils.addMenuItem(peakListPopupMenu, "Show peak list table", this, "SHOW_PEAKLIST_TABLES");
		GUIUtils.addMenuItem(peakListPopupMenu, "Show peak list info",  this, "SHOW_PEAKLIST_INFO");
		GUIUtils.addMenuItem(peakListPopupMenu, "Show scatter plot",    this, "SHOW_SCATTER_PLOT");
		GUIUtils.addMenuItem(peakListPopupMenu, "Remove",               this, "REMOVE_PEAKLIST");
	
		peakListRowPopupMenu = new JPopupMenu();
		GUIUtils.addMenuItem(peakListRowPopupMenu, "Show peak summary", this, "SHOW_PEAK_SUMMARY");
    }

    public void actionPerformed(ActionEvent e) {
    	System.out.println("actionPerformed " + e);
		String command = e.getActionCommand();
	
		// Actions for raw data files
	
		if (command.equals("SHOW_TIC")) {
		    TICVisualizerModule.setupNewTICVisualizer(getObjList(RawDataFile.class));
		}
	
		else if (command.equals("SHOW_SPECTRUM")) {
		    RawDataFile[] selectedFiles = getObjList(RawDataFile.class);
		    SpectraVisualizerModule module = MZmineCore.getModuleInstance(SpectraVisualizerModule.class);
		    ParameterSet parameters = MZmineCore.getConfiguration().getModuleParameters(SpectraVisualizerModule.class);
		    parameters.getParameter(SpectraVisualizerParameters.dataFiles).setValue(selectedFiles);
		    ExitCode exitCode = parameters.showSetupDialog();
		    if (exitCode == ExitCode.OK)
		    	module.runModule(parameters, new ArrayList<Task>());
		}
	
		else if (command.equals("SHOW_2D")) {
		    for (RawDataFile file : getObjList(RawDataFile.class))
		    	TwoDVisualizerModule.show2DVisualizerSetupDialog(file);
		}
	
		else if (command.equals("SHOW_3D")) {
		    for (RawDataFile file : getObjList(RawDataFile.class))
		    	ThreeDVisualizerModule.setupNew3DVisualizer(file);
		}
	
		else if (command.equals("REMOVE_FILE")) {
		    RawDataFile[] selectedFiles = getObjList(RawDataFile.class);
		    PeakList allPeakLists[] = MZmineCore.getCurrentProject().getPeakLists();
		    for (RawDataFile file : selectedFiles) {
				for (PeakList peakList : allPeakLists) {
				    if (peakList.hasRawDataFile(file)) {
						String msg = "Cannot remove file " + file.getName()
										+ ", because it is present in the peak list "
										+ peakList.getName();
						MZmineCore.getDesktop().displayErrorMessage(msg);
						return;
				    }
				}
				MZmineCore.getCurrentProject().removeFile(file);
		    }
		}
		
		// Actions for jobs
	
		else if (command.equals("RETRIEVE_JOB"))
		{
		    for (RemoteJobInfo job : getObjList(RemoteJobInfo.class))
		    {
		    	System.out.println(job.getName());
		    }
		}
	
		// Actions for scans
	
		else if (command.equals("SHOW_SCAN")) {
		    for (Scan scan : getObjList(Scan.class))
		    	SpectraVisualizerModule.showNewSpectrumWindow(scan.getDataFile(), scan.getScanNumber());
		}
	
		else if (command.equals("SHOW_MASSLIST")) {
		    for (MassList massList : getObjList(MassList.class)) {
				Scan scan = massList.getScan();
				SpectraVisualizerWindow window = SpectraVisualizerModule.showNewSpectrumWindow(scan.getDataFile(),scan.getScanNumber());
				MassListDataSet dataset = new MassListDataSet(massList);
				window.addDataSet(dataset, Color.green);
		    }
		}
	
		else if (command.equals("REMOVE_MASSLIST")) {
		    for (MassList massList : getObjList(MassList.class)) {
				Scan scan = massList.getScan();
				scan.removeMassList(massList);
		    }
		}
	
		else if (command.equals("REMOVE_ALL_MASSLISTS")) {
		    for (MassList massList : getObjList(MassList.class)) {
				String massListName = massList.getName();
				RawDataFile dataFiles[] = MZmineCore.getCurrentProject().getDataFiles();
				for (RawDataFile dataFile : dataFiles) {
				    int scanNumbers[] = dataFile.getScanNumbers();
				    for (int scanNum : scanNumbers) {
						Scan scan = dataFile.getScan(scanNum);
						MassList ml = scan.getMassList(massListName);
						if (ml != null)
						    scan.removeMassList(ml);
				    }
				}
		    }
		}
	
		// Actions for peak lists
	
		else if (command.equals("SHOW_PEAKLIST_TABLES")) {
		    for (PeakList peakList : getObjList(PeakList.class))
		    	PeakListTableModule.showNewPeakListVisualizerWindow(peakList);
		}
	
		else if (command.equals("SHOW_PEAKLIST_INFO")) {
		    for (PeakList peakList : getObjList(PeakList.class))
		    	InfoVisualizerModule.showNewPeakListInfo(peakList);
		}
	
		else if (command.equals("SHOW_SCATTER_PLOT")) {
		    for (PeakList peakList : getObjList(PeakList.class))
		    	ScatterPlotVisualizerModule.showNewScatterPlotWindow(peakList);
		}
	
		else if (command.equals("REMOVE_PEAKLIST")) {
		    for (PeakList peakList : getObjList(PeakList.class))
		    	MZmineCore.getCurrentProject().removePeakList(peakList);
		}
	
		// Actions for peak list rows
	
		else if (command.equals("SHOW_PEAK_SUMMARY")) {
		    for (PeakListRow row : getObjList(PeakListRow.class))
		    	PeakSummaryVisualizerModule.showNewPeakSummaryWindow(row);
		}
    }

    public void mousePressed(MouseEvent e) {
	
    	if (e.getButton() == MouseEvent.BUTTON1) System.out.println("button 1");
    	if (e.getButton() == MouseEvent.BUTTON2) System.out.println("button 2");
    	if (e.getButton() == MouseEvent.BUTTON3) System.out.println("button 3");

    	if (e.getButton() == MouseEvent.BUTTON3)
    		rightClickObj = getClickedObject(e);	// save object in case nothing else is selected

		if (e.isPopupTrigger())
		{
			System.out.println("isPopupTrigger");
		    handlePopupTriggerEvent(e);
		}
	
		if ((e.getClickCount() == 2) && (e.getButton() == MouseEvent.BUTTON1))
		{
			System.out.println("getClickCount");
		    handleDoubleClickEvent(e);
		}
    }

    public void mouseReleased(MouseEvent e) {
		if (e.isPopupTrigger())
		{
			System.out.println("mouseReleased isPopupTrigger");
		    handlePopupTriggerEvent(e);
		}
    }

    /**
     * Get a list of all selected elements of a given class.
     * If none are selected, assume the element that was last right clicked is selected.
     * 
     * @param objectClass
     * @return
     */
    @SuppressWarnings("unchecked")
	private <T> T[] getObjList(Class<T> objectClass)
    {
    	T[] list = tree.getSelectedObjects(objectClass);
	    if (list.length == 0)
	    {
	    	list = Arrays.copyOf(list, 1);
	    	list[0] = (T) rightClickObj;
	    }
	    return list;
    }

    /**
     * Get the tree node user object that was clicked on.
     * 
     * @param e
     * @return
     */
    private Object getClickedObject(MouseEvent e)
    {
		TreePath clickedPath = tree.getPathForLocation(e.getX(), e.getY());
		if (clickedPath == null)
		    return null;
		DefaultMutableTreeNode node = (DefaultMutableTreeNode) clickedPath.getLastPathComponent();
		Object clickedObject = node.getUserObject();
		return clickedObject;
    }

    private void handlePopupTriggerEvent(MouseEvent e) {
    	Component c = e.getComponent();
    	int       x = e.getX();
    	int       y = e.getY();
		Object clickedObject = getClickedObject(e);
		System.out.println("clicked obj " + clickedObject.getClass() + " " + clickedObject);

		if      (clickedObject instanceof RawDataFile)    dataFilePopupMenu.show(c, x, y);
		else if (clickedObject instanceof RemoteJobInfo)       jobPopupMenu.show(c, x, y);
		else if (clickedObject instanceof Scan)               scanPopupMenu.show(c, x, y);
		else if (clickedObject instanceof MassList)       massListPopupMenu.show(c, x, y);
		else if (clickedObject instanceof PeakList)       peakListPopupMenu.show(c, x, y);
		else if (clickedObject instanceof PeakListRow) peakListRowPopupMenu.show(c, x, y);
    }

    private void handleDoubleClickEvent(MouseEvent e) {
		TreePath clickedPath = tree.getPathForLocation(e.getX(), e.getY());
		if (clickedPath == null)
		    return;
		DefaultMutableTreeNode node = (DefaultMutableTreeNode) clickedPath.getLastPathComponent();
		Object clickedObject = node.getUserObject();
	
		if (clickedObject instanceof RawDataFile) {
		    RawDataFile clickedFile = (RawDataFile) clickedObject;
		    TICVisualizerModule.setupNewTICVisualizer(clickedFile);
		}
	
		if (clickedObject instanceof PeakList) {
		    PeakList clickedPeakList = (PeakList) clickedObject;
		    PeakListTableModule.showNewPeakListVisualizerWindow(clickedPeakList);
		}
		
		if (clickedObject instanceof RemoteJobInfo) {
			RemoteJobInfo job = (RemoteJobInfo) clickedObject;
		    System.out.println(job.getName());
		}
		
		if (clickedObject instanceof Scan) {
		    Scan clickedScan = (Scan) clickedObject;
		    SpectraVisualizerModule.showNewSpectrumWindow(clickedScan.getDataFile(), clickedScan.getScanNumber());
		}
	
		if (clickedObject instanceof MassList) {
		    MassList clickedMassList = (MassList) clickedObject;
		    Scan clickedScan = clickedMassList.getScan();
		    SpectraVisualizerWindow window = SpectraVisualizerModule.showNewSpectrumWindow(clickedScan.getDataFile(),clickedScan.getScanNumber());
		    MassListDataSet dataset = new MassListDataSet(clickedMassList);
		    window.addDataSet(dataset, Color.green);
		}
	
		if (clickedObject instanceof PeakListRow) {
		    PeakListRow clickedPeak = (PeakListRow) clickedObject;
		    PeakSummaryVisualizerModule.showNewPeakSummaryWindow(clickedPeak);
		}
    }

}