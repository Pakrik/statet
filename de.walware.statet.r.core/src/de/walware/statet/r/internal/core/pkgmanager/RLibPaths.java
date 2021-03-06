/*=============================================================================#
 # Copyright (c) 2012-2016 Stephan Wahlbrink (WalWare.de) and others.
 # All rights reserved. This program and the accompanying materials
 # are made available under the terms of the Eclipse Public License v1.0
 # which accompanies this distribution, and is available at
 # http://www.eclipse.org/legal/epl-v10.html
 # 
 # Contributors:
 #     Stephan Wahlbrink - initial API and implementation
 #=============================================================================*/

package de.walware.statet.r.internal.core.pkgmanager;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.core.filesystem.EFS;
import org.eclipse.core.filesystem.URIUtil;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;

import de.walware.rj.data.RDataUtil;
import de.walware.rj.data.RIntegerStore;
import de.walware.rj.data.RNumericStore;
import de.walware.rj.data.RVector;
import de.walware.rj.data.UnexpectedRDataException;
import de.walware.rj.services.FunctionCall;
import de.walware.rj.services.RService;

import de.walware.statet.r.core.RCore;
import de.walware.statet.r.core.pkgmanager.IRLibPaths;
import de.walware.statet.r.core.renv.IRLibraryGroup;
import de.walware.statet.r.core.renv.IRLibraryLocation;
import de.walware.statet.r.internal.core.RCorePlugin;


public class RLibPaths implements IRLibPaths {
	
	
	static class EntryImpl implements Entry {
		
		private final IRLibraryLocation fLocation;
		
		private final String fRPath;
		private final int fRIndex;
		
		private final int fAccess;
		
		private final double fStamp;
		
		
		public EntryImpl(final IRLibraryLocation location, final String rPath, final int rIndex,
				final int access, final double stamp) {
			this.fLocation= location;
			this.fRPath= rPath;
			this.fRIndex= rIndex;
			this.fAccess= access;
			this.fStamp= stamp;
		}
		
		
		@Override
		public IRLibraryLocation getLocation() {
			return this.fLocation;
		}
		
		@Override
		public String getRPath() {
			return this.fRPath;
		}
		
		public int getRIndex() {
			return this.fRIndex;
		}
		
		@Override
		public int getAccess() {
			return this.fAccess;
		}
		
		@Override
		public double getStamp() {
			return this.fStamp;
		}
		
	}
	
	
	/**
	 * Only existing, no writable check
	 */
	static RLibPaths createLight(final REnvLibGroups rLibGroups,
			final RVector<RNumericStore> rLibsStamps) throws CoreException {
		final int l= (int) rLibsStamps.getLength();
		final List<EntryImpl> entries= new ArrayList<>(l + 1);
		final RLibPaths libPaths= new RLibPaths(rLibGroups.getGroups(), entries);
		
		for (int i= 0; i < l; i++) {
			final String rPath= rLibsStamps.getNames().getChar(i);
			final IRLibraryLocation location= rLibGroups.getLibLocation(rPath);
			if (location != null) {
				final EntryImpl entry= new EntryImpl(location, rPath, i, EXISTS,
						rLibsStamps.getData().getNum(i) );
				entries.add(entry);
			}
		}
		
		return libPaths;
	}
	
	static RLibPaths create(final REnvLibGroups rLibGroups,
			final RVector<RNumericStore> rLibsStamps,
			final RService r, final IProgressMonitor monitor) throws CoreException {
		final int l= (int) rLibsStamps.getLength();
		final List<EntryImpl> entries= new ArrayList<>(l + 1);
		final RLibPaths libPaths= new RLibPaths(rLibGroups.getGroups(), entries);
		
		try {
			final RVector<RIntegerStore> rLibsAccess= RDataUtil.checkRIntVector(
				r.evalData("file.access(.libPaths(), 3L)", monitor) ); //$NON-NLS-1$
			for (int i= 0; i < l; i++) {
				final String rPath= rLibsStamps.getNames().getChar(i);
				final IRLibraryLocation location= rLibGroups.getLibLocation(rPath);
				if (location != null) {
					final EntryImpl entry= new EntryImpl(location, rPath, i,
							(location.getSource() != IRLibraryLocation.EPLUGIN
									&& rLibsAccess.getData().getInt(i) == 0) ?
											(EXISTS | WRITABLE) : (EXISTS),
							rLibsStamps.getData().getNum(i) );
					entries.add(entry);
				}
			}
		}
		catch (final CoreException | UnexpectedRDataException e) {
			throw new CoreException(new Status(IStatus.ERROR, RCore.PLUGIN_ID,
					"An error occurred when checking R library locations for access.",
					e ));
		}
		
		try {
			final IRLibraryLocation location= rLibGroups.getFirstUserLibLocation();
			if (location != null && libPaths.getEntryByLocation(location) == null
					&& location.getDirectoryStore() != null
					&& location.getDirectoryStore().getFileSystem().equals(EFS.getLocalFileSystem()) ) {
				final FunctionCall call= r.createFunctionCall("rj:::.renv.isValidLibLoc"); //$NON-NLS-1$
				final IPath path= URIUtil.toPath(location.getDirectoryStore().toURI());
				if (path != null) {
					call.addChar(path.toString());
					final RVector<RIntegerStore> data= RDataUtil.checkRIntVector(call.evalData(monitor));
					final int state= RDataUtil.checkSingleIntValue(data);
					if (state == 0) {
						final EntryImpl entry= new EntryImpl(location, data.getNames().getChar(0),
								-1, (WRITABLE), 0);
						entries.add(entry);
					}
				}
			}
		}
		catch (final CoreException | UnexpectedRDataException e) {
			RCorePlugin.log(new Status(IStatus.ERROR, RCore.PLUGIN_ID,
					"An error occurred when checking missing R user library location.",
					e ));
		}
		
		return libPaths;
	}
	
	
	private final List<? extends IRLibraryGroup> envGroups;
	
	private final List<EntryImpl> entries;
	
	
	private RLibPaths(final List<? extends IRLibraryGroup> envLibs, final List<EntryImpl> entries) {
		this.envGroups= envLibs;
		this.entries= entries;
	}
	
	
	@Override
	public List<? extends IRLibraryGroup> getRLibraryGroups() {
		return this.envGroups;
	}
	
	@Override
	public IRLibraryGroup getRLibraryGroup(final String id) {
		for (final IRLibraryGroup group : this.envGroups) {
			if (group.getId() == id) {
				return group;
			}
		}
		return null;
	}
	
	@Override
	public List<EntryImpl> getEntries() {
		return this.entries;
	}
	
	@Override
	public Entry getEntryByRPath(final String rPath) {
		for (final EntryImpl entry : this.entries) {
			if (entry.fRPath.equals(rPath)) {
				return entry;
			}
		}
		return null;
	}
	
	@Override
	public Entry getEntryByLocation(final IRLibraryLocation location) {
		for (final EntryImpl entry : this.entries) {
			if (entry.fLocation.equals(location)) {
				return entry;
			}
		}
		return null;
	}
	
}
