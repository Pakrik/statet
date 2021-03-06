/*=============================================================================#
 # Copyright (c) 2007-2016 Stephan Wahlbrink (WalWare.de) and others.
 # All rights reserved. This program and the accompanying materials
 # are made available under the terms of the Eclipse Public License v1.0
 # which accompanies this distribution, and is available at
 # http://www.eclipse.org/legal/epl-v10.html
 # 
 # Contributors:
 #     Stephan Wahlbrink - initial API and implementation
 #=============================================================================*/

package de.walware.statet.r.core;

import de.walware.ecommons.preferences.core.IPreferenceAccess;

import de.walware.statet.r.core.renv.IREnv;


/**
 * Interface to access R Core services, respecting the scope.
 */
public interface IRCoreAccess {
	
	
	IPreferenceAccess getPrefs();
	
	
	IREnv getREnv();
	
	RCodeStyleSettings getRCodeStyle();
	
}
