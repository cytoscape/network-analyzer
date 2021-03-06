package de.mpg.mpi_inf.bioinf.netanalyzer;

/*
 * #%L
 * Cytoscape NetworkAnalyzer Impl (network-analyzer-impl)
 * $Id:$
 * $HeadURL:$
 * %%
 * Copyright (C) 2006 - 2013
 *   Max Planck Institute for Informatics, Saarbruecken, Germany
 *   The Cytoscape Consortium
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as 
 * published by the Free Software Foundation, either version 2.1 of the 
 * License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Lesser Public License for more details.
 * 
 * You should have received a copy of the GNU General Lesser Public 
 * License along with this program.  If not, see
 * <http://www.gnu.org/licenses/lgpl-2.1.html>.
 * #L%
 */

import java.awt.Frame;
import java.awt.event.ActionEvent;
import java.io.File;
import java.io.FileFilter;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.cytoscape.application.CyApplicationManager;
import org.cytoscape.application.swing.CySwingApplication;
import org.cytoscape.io.read.CyNetworkReader;
import org.cytoscape.io.read.CyNetworkReaderManager;
import org.cytoscape.model.CyNetworkManager;
import org.cytoscape.view.model.CyNetworkViewManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.mpg.mpi_inf.bioinf.netanalyzer.data.Interpretations;
import de.mpg.mpi_inf.bioinf.netanalyzer.data.Messages;
import de.mpg.mpi_inf.bioinf.netanalyzer.ui.BatchAnalysisDialog;
import de.mpg.mpi_inf.bioinf.netanalyzer.ui.BatchResultsDialog;
import de.mpg.mpi_inf.bioinf.netanalyzer.ui.BatchSettingsDialog;
import de.mpg.mpi_inf.bioinf.netanalyzer.ui.Utils;

/**
 * Action handler for the menu item &quot;Batch Analysis&quot;.
 * 
 * @author Yassen Assenov
 * @author Nadezhda Doncheva
 */
public class BatchAnalysisAction extends NetAnalyzerAction {

	private static final long serialVersionUID = -1228030064334629585L;
	
	private static final Logger logger = LoggerFactory.getLogger(BatchAnalysisAction.class);
	
	private final CyNetworkReaderManager cyNetworkViewReaderMgr;
	private final CyNetworkManager netMgr;
	private final CyNetworkViewManager netViewMgr;
	private final LoadNetstatsAction action;

	/**
	 * Constructs a new batch analysis action.
	 */
	protected BatchAnalysisAction(CyApplicationManager appMgr,CySwingApplication swingApp, CyNetworkManager netMgr, CyNetworkReaderManager cyNetworkViewReaderMgr, CyNetworkViewManager netViewMgr, final LoadNetstatsAction action) {
		super(Messages.AC_BATCH_ANALYSIS,appMgr,swingApp);
		this.action = action;
		this.netMgr = netMgr;
		this.cyNetworkViewReaderMgr = cyNetworkViewReaderMgr;
		this.netViewMgr = netViewMgr;
		setPreferredMenu(NetworkAnalyzer.PARENT_MENU + Messages.AC_MENU_ANALYSIS);
	}

	@Override
	public void actionPerformed(ActionEvent e) {
		try {
			final Frame desktop = swingApp.getJFrame();
			// Step 1 - Adjust settings
			BatchSettingsDialog d1 = new BatchSettingsDialog(desktop);
			d1.setVisible(true);
			final File[] inOutDirs = d1.getInOutDirs();

			// Step 2 - Run the analysis
			if (inOutDirs != null) {
				final List<File> files = getInputFiles(inOutDirs[0]);
				
				if (files.size() > 0) {
					final Interpretations ins = d1.getInterpretations();
					final BatchNetworkAnalyzer analyzer =
							new BatchNetworkAnalyzer(inOutDirs[1], files, ins,netMgr, cyNetworkViewReaderMgr );
					final BatchAnalysisDialog d2 = new BatchAnalysisDialog(desktop, analyzer);
					d2.setVisible(true);
					
					if (d2.resultsPressed()) {
						// Step 3 - Show results
						BatchResultsDialog d3 = new BatchResultsDialog(swingApp.getJFrame(), analyzer.getReports(),
								cyNetworkViewReaderMgr, netMgr, netViewMgr, action);
						d3.setVisible(true);
					}
				} else {
					Utils.showInfoBox(swingApp.getJFrame(),Messages.DT_INFO, Messages.SM_NOINPUTFILES);
				}
			}
		} catch (InnerException ex) {
			// NetworkAnalyzer internal error
			logger.error(Messages.SM_LOGERROR, ex);
		}
	}

	/**
	 * Get all readable Network files from the input directory. These are all SIF, GML and XGMML files.
	 * <p>
	 * This method is called upon initialization only.
	 * </p>
	 * 
	 * @param inputDir
	 *            Input directory as selected by the user.
	 * @return All readable Network files in the input directory, as a list of <code>File</code> instances.
	 */
	private List<File> getInputFiles(File inputDir) {
		final FileFilter inputFileFilter = new FileFilter() {
			@Override
			public boolean accept(File aPathname) {
				CyNetworkReader reader = null;
				
				if (aPathname.isFile() && aPathname.canRead() && !aPathname.isHidden()) {
					final String name = aPathname.getAbsolutePath();
					
					try {
						reader = cyNetworkViewReaderMgr.getReader(aPathname.toURI(), name);
					} catch (Exception e) {
						// No need to log it...
					}
				}
				
				return reader != null;
			}
		};

		final List<File> inputFiles = Arrays.asList(inputDir.listFiles(inputFileFilter));
		Collections.sort(inputFiles);
		
		return inputFiles;
	}
}
