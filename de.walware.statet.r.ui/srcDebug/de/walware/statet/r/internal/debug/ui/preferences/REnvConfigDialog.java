/*******************************************************************************
 * Copyright (c) 2007 WalWare/StatET-Project (www.walware.de/goto/statet).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Stephan Wahlbrink - initial API and implementation
 *******************************************************************************/

package de.walware.statet.r.internal.debug.ui.preferences;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

import com.ibm.icu.text.Collator;

import org.eclipse.core.databinding.AggregateValidationStatus;
import org.eclipse.core.databinding.DataBindingContext;
import org.eclipse.core.databinding.UpdateValueStrategy;
import org.eclipse.core.databinding.beans.BeansObservables;
import org.eclipse.core.databinding.observable.Realm;
import org.eclipse.core.databinding.observable.value.IValueChangeListener;
import org.eclipse.core.databinding.observable.value.ValueChangeEvent;
import org.eclipse.core.databinding.validation.IValidator;
import org.eclipse.core.databinding.validation.ValidationStatus;
import org.eclipse.core.filesystem.EFS;
import org.eclipse.core.filesystem.IFileStore;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Path;
import org.eclipse.core.runtime.Platform;
import org.eclipse.core.variables.IStringVariableManager;
import org.eclipse.core.variables.VariablesPlugin;
import org.eclipse.jface.databinding.swt.SWTObservables;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.dialogs.StatusDialog;
import org.eclipse.osgi.util.NLS;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Menu;
import org.eclipse.swt.widgets.MenuItem;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Text;

import de.walware.eclipsecommons.ui.databinding.DirtyTracker;
import de.walware.eclipsecommons.ui.dialogs.ChooseResourceComposite;
import de.walware.eclipsecommons.ui.util.MessageUtil;

import de.walware.statet.r.core.renv.REnvConfiguration;
import de.walware.statet.r.internal.ui.RUIPlugin;


/**
 *
 */
public class REnvConfigDialog extends StatusDialog {
	

	private class RHomeComposite extends ChooseResourceComposite {

		public RHomeComposite(Composite parent) {
			super (parent, 
					ChooseResourceComposite.STYLE_TEXT,
					ChooseResourceComposite.MODE_DIRECTORY | ChooseResourceComposite.MODE_OPEN, 
					Messages.REnv_Detail_Location_label);
			showInsertVariable(true);
		}
		
		@Override
		protected boolean excludeVariable(String variableName) {
			return (super.excludeVariable(variableName)
				|| excludeBuildVariable(variableName)
				|| excludeInteractiveVariable(variableName));
		}
		
		@Override
		protected void fillMenu(Menu menu) {
			super.fillMenu(menu);
			
			MenuItem item = new MenuItem(menu, SWT.PUSH);
			item.setText(Messages.REnv_Detail_FindAuto_label);
			item.addSelectionListener(new SelectionAdapter() {
				@Override
				public void widgetSelected(SelectionEvent e) {
					String[] rhome = searchRHOME();
					if (rhome != null) {
						setText(rhome[0], true);
						String current = fNameControl.getText().trim();
						if ((current.length() == 0 || current.equals("R")) && rhome[1] != null) { //$NON-NLS-1$
							fNameControl.setText(rhome[1]);
						}
					}
					else {
						String name = Messages.REnv_Detail_Location_label;
						MessageDialog.openInformation(getShell(), 
								MessageUtil.removeMnemonics(name), 
								NLS.bind(Messages.REnv_Detail_FindAuto_Failed_message, name));
					}
					getTextControl().setFocus();
				}
			});
			
		}
	}
	
	private REnvPreferencePage.REnvConfig fConfigModel;
	private boolean fIsNewConfig;
	private DataBindingContext fDbc;
	private Set<String> fExistingNames;
	private AggregateValidationStatus fAggregateStatus;

	private Text fNameControl;
	private ChooseResourceComposite fRHomeControl;
	
	
	public REnvConfigDialog(Shell parent, 
			REnvPreferencePage.REnvConfig config, boolean isNewConfig, 
			Collection<REnvConfiguration> existingConfigs) {
		super(parent);
		setShellStyle(getShellStyle() | SWT.RESIZE);
		
		fConfigModel = config;
		fIsNewConfig = isNewConfig;
		fExistingNames = new HashSet<String>();
		for (REnvConfiguration ec : existingConfigs) {
			fExistingNames.add(ec.getName());
		}
		setTitle(fIsNewConfig ? Messages.REnv_Detail_AddDialog_title : Messages.REnv_Detail_Edit_Dialog_title);
	}
	
	
	@Override
	protected Control createDialogArea(Composite parent) {
		Composite content = (Composite) super.createDialogArea(parent);
		((GridData) content.getLayoutData()).widthHint = convertWidthInCharsToPixels(100);
		((GridLayout) content.getLayout()).numColumns = 2;
		
		Label label;
		
		label = new Label(content, SWT.LEFT);
		label.setText(Messages.REnv_Detail_Name_label+':');
		label.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, false, false));
		fNameControl = new Text(content, SWT.SINGLE | SWT.BORDER);
		fNameControl.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, true));

		label = new Label(content, SWT.LEFT);
		label.setText(Messages.REnv_Detail_Location_label+':');
		label.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, false, false));
		fRHomeControl = new RHomeComposite(content);
		fRHomeControl.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

		applyDialogFont(content);
		initBindings();
		return content;
	}
	
	private void initBindings() {
		Realm realm = Realm.getDefault();
		fDbc = new DataBindingContext(realm);

		fDbc.bindValue(SWTObservables.observeText(fNameControl, SWT.Modify), 
				BeansObservables.observeValue(realm, fConfigModel, REnvConfiguration.PROP_NAME), 
				new UpdateValueStrategy().setAfterGetValidator(new IValidator() {
					public IStatus validate(Object value) {
						String s = (String) value;
						s = s.trim();
						if (s.length() == 0) {
							return ValidationStatus.error(Messages.REnv_Detail_Name_error_Missing_message);
						}
						if (fExistingNames.contains(s)) {
							return ValidationStatus.error(Messages.REnv_Detail_Name_error_Duplicate_message);
						}
						if (s.contains("/")) { //$NON-NLS-1$
							return ValidationStatus.error(Messages.REnv_Detail_Name_error_InvalidChar_message);
						}
						return ValidationStatus.OK_STATUS;
					}
				}), null);
		fDbc.bindValue(fRHomeControl.createObservable(), 
				BeansObservables.observeValue(realm, fConfigModel, REnvConfiguration.PROP_RHOME), 
				new UpdateValueStrategy().setAfterGetValidator(new IValidator() {
					public IStatus validate(Object value) {
						IStatus status = fRHomeControl.getValidator().validate(value);
						if (!status.isOK()) {
							return status;
						}
						if (!REnvConfiguration.isValidRHomeLocation(fRHomeControl.getResourceAsFileStore())) {
							return ValidationStatus.error(Messages.REnv_Detail_Location_error_NoRHome_message);
						}
						return ValidationStatus.ok();
					}
				}), null);
		
		
		fAggregateStatus = new AggregateValidationStatus(fDbc.getBindings(), AggregateValidationStatus.MAX_SEVERITY);
		fAggregateStatus.addValueChangeListener(new IValueChangeListener() {
			public void handleValueChange(ValueChangeEvent event) {
				IStatus currentStatus = (IStatus) event.diff.getNewValue();
				updateStatus(currentStatus);
			}
		});
		updateStatus((IStatus) fAggregateStatus.getValue());
		new DirtyTracker(fDbc) { // sets initial status on first change again, because initial errors are suppressed
			@Override
			public void handleChange() {
				if (!isDirty()) {
					updateStatus((IStatus) fAggregateStatus.getValue());
					super.handleChange();
				}
			}
		};
	}
	
	private String[] searchRHOME() {
		try {
			IStringVariableManager variables = VariablesPlugin.getDefault().getStringVariableManager();
			boolean isWin = Platform.getOS().startsWith("win"); //$NON-NLS-1$

			String loc = variables.performStringSubstitution("${env_var:R_HOME}", false); //$NON-NLS-1$
			if (loc != null && loc.length() > 0) {
				if (EFS.getLocalFileSystem().getStore(
						new Path(loc)).fetchInfo().exists()) {
					return new String[] { loc, "System Default" };
				}
			}
			if (isWin) {
				loc = "${env_var:PROGRAMFILES}\\R"; //$NON-NLS-1$
				IFileStore res = EFS.getLocalFileSystem().getStore(
						new Path(variables.performStringSubstitution(loc)));
				if (!res.fetchInfo().exists()) {
					return null;
				}
				String[] childNames = res.childNames(EFS.NONE, null);
				Arrays.sort(childNames, 0, childNames.length, Collator.getInstance());
				for (int i = childNames.length-1; i >= 0; i--) {
					if (REnvConfiguration.isValidRHomeLocation(res.getChild(childNames[i]))) {
						return new String[] { loc + '\\' + childNames[i], childNames[i] };
					}
				}
			}
			else {
				String[] defLocations = new String[] {
						"/usr/lib/R", //$NON-NLS-1$
						"/usr/local/bin/R", //$NON-NLS-1$
				};
				for (int i = 0; i < defLocations.length; i++) {
					loc = defLocations[i];
					if (REnvConfiguration.isValidRHomeLocation(EFS.getLocalFileSystem().getStore(new Path(loc)))) {
						return new String[] { loc, null };
					}
				}
			}
		} catch (Exception e) {
			RUIPlugin.logError(-1, "Error when searching R_HOME location", e); //$NON-NLS-1$
		}
		return null;
	}
	
}