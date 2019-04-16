package de.mpg.mpi_inf.bioinf.netanalyzer.ui;

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

import java.awt.Component;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Constructor;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTabbedPane;

import org.cytoscape.application.swing.CySwingApplication;
import org.cytoscape.application.swing.CytoPanel;
import org.cytoscape.application.swing.CytoPanelName;
import org.cytoscape.application.swing.CytoPanelState;
import org.cytoscape.application.swing.events.CytoPanelStateChangedEvent;
import org.cytoscape.application.swing.events.CytoPanelStateChangedListener;
import org.cytoscape.model.CyNetwork;
import org.cytoscape.util.swing.LookAndFeelUtil;
import org.cytoscape.view.model.CyNetworkViewManager;
import org.cytoscape.view.vizmap.VisualMappingManager;

import de.mpg.mpi_inf.bioinf.netanalyzer.CyNetworkUtils;
import de.mpg.mpi_inf.bioinf.netanalyzer.InnerException;
import de.mpg.mpi_inf.bioinf.netanalyzer.NetworkAnalyzer;
import de.mpg.mpi_inf.bioinf.netanalyzer.Plugin;
import de.mpg.mpi_inf.bioinf.netanalyzer.data.ComplexParam;
import de.mpg.mpi_inf.bioinf.netanalyzer.data.Decorators;
import de.mpg.mpi_inf.bioinf.netanalyzer.data.Messages;
import de.mpg.mpi_inf.bioinf.netanalyzer.data.NetworkStats;
import de.mpg.mpi_inf.bioinf.netanalyzer.data.io.SettingsSerializer;
import de.mpg.mpi_inf.bioinf.netanalyzer.data.io.StatsSerializer;
import de.mpg.mpi_inf.bioinf.netanalyzer.data.settings.PluginSettings;
import de.mpg.mpi_inf.bioinf.netanalyzer.dec.Decorator;

/**
 * Dialog presenting results of network analysis.
 * 
 * @author Yassen Assenov
 */
public class AnalysisResultPanel extends JPanel implements ActionListener, CytoPanelStateChangedListener {

	private ResultPanel resultPanel;
	private final ResultPanelFactory resultPanelFactory;
	
	private final CyNetworkViewManager viewManager;
	private final VisualMappingManager vmm;
	private final VisualStyleBuilder vsBuilder;
		
	/**
	 * Dialog window for choosing a filename when saving and loading .netstats files.
	 */
	public static JFileChooser netstatsDialog = new JFileChooser();

	static {
		netstatsDialog.addChoosableFileFilter(SupportedExtensions.netStatsFilter);
	}

	private final Window owner;

	private CySwingApplication swingApplication;
	
	/**
	 * Initializes a new instance of <code>AnalysisDialog</code>.
	 * <p>
	 * The dialog created is non-modal and has a title &quot;Network Analysis - [name]&quot;, where [name] is
	 * the name of the network, as saved in the <code>aStats</code> parameter. The constructor creates and
	 * lays out all the controls of the dialog. It also positions the window according to its parent, so no
	 * subsequent calls to <code>pack</code> or <code>setLocation(...)</code> are necessary.
	 * </p>
	 * 
	 * @param owner
	 *            The <code>Frame</code> from which this dialog is displayed.
	 * @param aStats
	 *            Network statistics to be visualized.
	 * @param aAnalyzer
	 *            Analyzer class that performed the topological analysis. Set this to <code>null</code> if the
	 *            results were loaded from a file rather than just computed.
	 */
	public AnalysisResultPanel(
			final CySwingApplication swingApplication,
			final Window owner,
			final ResultPanelFactory panelFactory,
			final NetworkStats aStats,
			final NetworkAnalyzer aAnalyzer,
			final CyNetworkViewManager viewManager,
			final VisualStyleBuilder vsBuilder,
			final VisualMappingManager vmm) {
		this.owner = owner;
		this.viewManager = viewManager;
		this.vmm = vmm;
		this.vsBuilder = vsBuilder;
		this.swingApplication = swingApplication;
		this.resultPanelFactory =panelFactory;

		this.stats = aStats;
		boolean paramMapping = false;
		if (aAnalyzer != null) {
			final PluginSettings s = SettingsSerializer.getPluginSettings();
			paramMapping = aAnalyzer.isGlobal() && (s.getUseNodeAttributes() || s.getUseEdgeAttributes());
			saved = false;
		} else {
			saved = true;
		}
		
		initControls(paramMapping);
		resultPanel = panelFactory.registerPanel(this, "Network Statistics of " + stats.getTitle());

		final CytoPanel cytoPanel = swingApplication.getCytoPanel(CytoPanelName.EAST);
		oldState = cytoPanel.getState();
		
		int panelCount = cytoPanel.getCytoPanelComponentCount();
		for(int i=0; i<panelCount; i++) {
			Component panel = cytoPanel.getComponentAt(i);
			if(panel.equals(resultPanel)) {
				cytoPanel.setSelectedIndex(i);
				break;
			}
		}

		final CytoPanelState currentState = cytoPanel.getState();
		if( currentState == CytoPanelState.HIDE )
		{
			cytoPanel.setState(CytoPanelState.FLOAT);
			JPanel thisPanel = (JPanel)cytoPanel.getThisComponent();
			Window dialog = (Window) thisPanel.getTopLevelAncestor();
			dialog.setSize(DEFAULT_WIDTH,DEFAULT_HEIGHT);
		}
	}

	@Override
	public void actionPerformed(ActionEvent e) {
		final Object src = e.getSource();
		if (src == saveButton) {
			saveNetstats();
		} else if (src == visualizeButton) {
			visualizeParameter();
		}
	}

	public void panelClosing() {
		if (!saved) {
			int choice = JOptionPane.showConfirmDialog(this, Messages.SM_CLOSEWARN, Messages.DT_CLOSEWARN,
					JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
			if (choice == JOptionPane.NO_OPTION)
				return;
		}
		
		// Close this panel
		
		final CytoPanel cytoPanel = swingApplication.getCytoPanel(CytoPanelName.EAST);
		int panelCount = cytoPanel.getCytoPanelComponentCount();
		for(int i=0; i<panelCount; i++) {
			Component panel = cytoPanel.getComponentAt(i);
			if(panel.equals(resultPanel)) {
				resultPanelFactory.removePanel(resultPanel);
				resultPanel = null;
				break;
			}
		}
//		dispose();
	}

	/**
	 * Creates and lays out the controls inside this dialog.
	 * <p>
	 * This method is called upon initialization only.
	 * </p>
	 */
	private void initControls(boolean enableParameterMapping) {
		// TODO: Enable when fixed
		// final boolean useExpandable = SettingsSerializer.getPluginSettings().getExpandable();
		final boolean useExpandable = false;
		this.setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
		final JTabbedPane tabs = useExpandable ? null : new JTabbedPane();

		final JComponent simpleStatsPanel = new SimpleStatsPanel(stats);
		if (useExpandable) {
			simpleStatsPanel.setBorder(LookAndFeelUtil.createTitledBorder(Messages.DI_SIMPLEPARAMS));
			this.add(simpleStatsPanel);
		} else {
			this.add(tabs);
			tabs.addTab(Messages.DI_SIMPLEPARAMS, simpleStatsPanel);
		}

		final String[] complexNames = stats.getComputedComplex();
		for (int i = 0; i < complexNames.length; ++i) {
			final String id = complexNames[i];
			final ComplexParam cp = stats.getComplex(id);
			final String typeName = cp.getClass().getSimpleName();
			try {
				final Class<?> visClass = Plugin.getVisualizerClass(typeName);
				final Constructor<?> con = visClass.getConstructors()[0];
				final Object[] conParams = new Object[] { cp, SettingsSerializer.getDefault(id) };
				ComplexParamVisualizer v = (ComplexParamVisualizer) con.newInstance(conParams);
				final Decorator[] decs = Decorators.get(id);
				if (useExpandable) {
					this.add(new ChartExpandablePanel(owner, id, v, (i == 0), decs));
				} else {
					tabs.addTab(v.getTitle(), new ChartDisplayPanel(owner, id, v, decs));
				}
			} catch (Exception ex) {
				throw new InnerException(ex);
			}
		}

		this.add(Box.createVerticalStrut(Utils.BORDER_SIZE));
		
		saveButton = new JButton(Messages.DI_SAVESTATISTICS);
		saveButton.addActionListener(this);
		visualizeButton = new JButton(Messages.DI_VISUALIZEPARAMETER);
		visualizeButton.setEnabled(false);
		visualizeButton.addActionListener(this);
		visualizeButton.setEnabled(enableParameterMapping);
		
		closeButton = new JButton("Close Tab");
		closeButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent arg0) {
				panelClosing();
			}
		});
		
		// Button panel
		final JPanel buttonPane = LookAndFeelUtil.createOkCancelPanel(visualizeButton, closeButton, saveButton);
		
		if (LookAndFeelUtil.isAquaLAF())
			buttonPane.setOpaque(false);
		
		this.add(buttonPane);
		this.add(Box.createVerticalStrut(Utils.BORDER_SIZE));
	}

	/**
	 * Displays a dialog prompting to save the network analysis in a <code>.netstats</code> file.
	 */
	private void saveNetstats() {
		int saveIt = netstatsDialog.showSaveDialog(this);
		if (saveIt == JFileChooser.APPROVE_OPTION) {
			try {
				String fileName = netstatsDialog.getSelectedFile().getAbsolutePath();
				Utils.removeSelectedFile(netstatsDialog);
				if (!SupportedExtensions.netStatsFilter.hasExtension(fileName)) {
					fileName = SupportedExtensions.netStatsFilter.appendExtension(fileName);
				}
				if (Utils.canSave(new File(fileName), this)) {
					StatsSerializer.save(stats, fileName);
					saved = true;
				}
			} catch (IOException ex) {
				// Could not save file
				Utils.showErrorBox(this, Messages.DT_IOERROR, Messages.SM_OERROR);
			} catch (SecurityException ex) {
				// Could not save file - security manager has denied access
				Utils.showErrorBox(this, Messages.DT_SECERROR, Messages.SM_SECERROR2);
			}
		} else if (saveIt == JFileChooser.ERROR_OPTION) {
			Utils.showErrorBox(this, Messages.DT_GUIERROR, Messages.SM_GUIERROR);
		}
	}

	/**
	 * Opens the &quot;Map Parameters to Visual Styles&quot; dialog when the &quot;Map Parameters to Visual
	 * Styles&quot; button is activated.
	 */
	private void visualizeParameter() {
		CyNetwork network = stats.getNetwork();
		if (network != null) {
			final String[][] nodeAttr = CyNetworkUtils.getComputedNodeAttributes(network);
			final String[][] edgeAttr = CyNetworkUtils.getComputedEdgeAttributes(network);
			if ((nodeAttr[0].length > 0) || (nodeAttr[1].length > 0) || (edgeAttr[0].length > 0)
					|| (edgeAttr[1].length > 0)) {
				final MapParameterDialog d = new MapParameterDialog(owner, network, viewManager, vsBuilder, vmm,
						nodeAttr, edgeAttr);
				d.setVisible(true);
				return;
			}
		}
		// Could not locate network - display an error message to the user
		Utils.showErrorBox(this, Messages.DT_WRONGDATA, Messages.SM_VISUALIZEERROR);
		visualizeButton.setEnabled(false);
	}

	/**
	 * Unique ID for this version of this class. It is used in serialization.
	 */
	private static final long serialVersionUID = 5759174742857183439L;

	/**
	 * Flag indicating if the network analysis results were successfully saved to a file.
	 */
	private boolean saved;

	/**
	 * &quot;Save Statistics&quot; button.
	 */
	private JButton saveButton;

	/**
	 * &quot;Visualize Parameters&quot; button.
	 */
	private JButton visualizeButton;

	/**
	 * Network parameters instance displayed in this dialog.
	 */
	private NetworkStats stats;
	
	private JButton closeButton;

	private CytoPanelState oldState = null;
	private static final int DEFAULT_WIDTH = 680;
	private static final int DEFAULT_HEIGHT = 540;
	/**
	 * Handles specified event by resizing the state if the EAST CytoPanel is either undocked or shown for the first
	 * time.
	 *
	 * @param e The event to be handled.
	 */
	@Override
	public void handleEvent(CytoPanelStateChangedEvent e) {
		if( oldState != CytoPanelState.DOCK && e.getNewState() == CytoPanelState.DOCK )
		{
			final CytoPanel cytoPanel = swingApplication.getCytoPanel(CytoPanelName.EAST);
			JPanel thisPanel = (JPanel)cytoPanel.getThisComponent();
			int x = 5;
		}

		//If the oldState was already FLOAT, or the new state is not FLOAT, no need to do anything.
		if( oldState == CytoPanelState.FLOAT || e.getNewState() != CytoPanelState.FLOAT )
		{
			//Don't do anything, but before we leave, record the new state for future reference.
			oldState = e.getNewState();
			return;
		}
		//Record the new state for future reference.
	    oldState = e.getNewState();

		//Get the EAST cytoPanel, which we are going to resize below.
		final CytoPanel cytoPanel = swingApplication.getCytoPanel(CytoPanelName.EAST);

		//Check that the resultPanel is currently selected. If it is not, ignore the event.
		if(!cytoPanel.getSelectedComponent().equals(resultPanel))
			return;

		JPanel thisPanel = (JPanel)cytoPanel.getThisComponent();
		Window dialog = (Window) thisPanel.getTopLevelAncestor();
		dialog.setSize(DEFAULT_WIDTH,DEFAULT_HEIGHT);
	}
}
