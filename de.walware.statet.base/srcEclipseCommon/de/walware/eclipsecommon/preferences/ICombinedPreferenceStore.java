/*******************************************************************************
 * Copyright (c) 2006 StatET-Project (www.walware.de/goto/statet).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Stephan Wahlbrink - initial API and implementation
 *******************************************************************************/

package de.walware.eclipsecommon.preferences;

import org.eclipse.jface.preference.IPreferenceStore;


/**
 * Prefence Store with core support.
 * <p>
 * So you can access core preference which use Preference/IPreferenceAccess
 * in UI environment (where usually PreferenceStores are used).
 * 
 */
public interface ICombinedPreferenceStore extends IPreferenceStore {
	
	
	public IPreferenceAccess getCorePreferences();
	
}
