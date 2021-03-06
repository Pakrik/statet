/*=============================================================================#
 # Copyright (c) 2010-2016 Stephan Wahlbrink (WalWare.de) and others.
 # All rights reserved. This program and the accompanying materials
 # are made available under the terms of the Eclipse Public License v1.0
 # which accompanies this distribution, and is available at
 # http://www.eclipse.org/legal/epl-v10.html
 # 
 # Contributors:
 #     Stephan Wahlbrink - initial API and implementation
 #=============================================================================*/

package de.walware.statet.r.internal.debug.ui.breakpoints;

import java.util.IdentityHashMap;
import java.util.Map;

import org.eclipse.core.resources.IMarker;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.Status;
import org.eclipse.debug.core.DebugPlugin;
import org.eclipse.debug.core.model.IBreakpoint;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.Position;
import org.eclipse.osgi.util.NLS;
import org.eclipse.ui.statushandlers.StatusManager;
import org.eclipse.ui.texteditor.IMarkerUpdater;
import org.eclipse.ui.texteditor.MarkerUtilities;

import de.walware.ecommons.ltk.ISourceUnitManager;
import de.walware.ecommons.ltk.LTK;
import de.walware.ecommons.ltk.core.model.ISourceUnit;

import de.walware.statet.r.core.model.IRWorkspaceSourceUnit;
import de.walware.statet.r.debug.core.breakpoints.IRLineBreakpoint;
import de.walware.statet.r.debug.core.breakpoints.RLineBreakpointValidator;
import de.walware.statet.r.ui.RUI;


public class BreakpointMarkerUpdater implements IMarkerUpdater {
	
	
	private final static String[] ATTRIBUTES = new String[] {
		IMarker.LINE_NUMBER,
		IMarker.CHAR_START,
		IMarker.CHAR_END,
	};
	
	
	public BreakpointMarkerUpdater() {
	}
	
	
	@Override
	public String getMarkerType() {
		return IBreakpoint.BREAKPOINT_MARKER;
	}
	
	@Override
	public String[] getAttribute() {
		return ATTRIBUTES;
	}
	
	@Override
	public boolean updateMarker(final IMarker marker, final IDocument document, final Position position) {
		if (position == null) {
			return true;
		}
		if (position.isDeleted()) {
			return false;
		}
		
		final IBreakpoint breakpoint = DebugPlugin.getDefault().getBreakpointManager()
				.getBreakpoint(marker);
		if (breakpoint == null) {
			return false;
		}
		if (breakpoint instanceof IRLineBreakpoint) {
			return update((IRLineBreakpoint) breakpoint, marker, document, position);
		}
		return updateBasic(marker, document, position);
	}
	
	private boolean update(final IRLineBreakpoint breakpoint, final IMarker marker,
			final IDocument document, final Position position) {
		final IProgressMonitor monitor = new NullProgressMonitor();
		final ISourceUnitManager suManager = LTK.getSourceUnitManager();
		ISourceUnit su= suManager.getSourceUnit(LTK.PERSISTENCE_CONTEXT, marker.getResource(),
				null, true, monitor );
		if (su != null) {
			try {
				su = suManager.getSourceUnit(LTK.EDITOR_CONTEXT, su, null, true, monitor);
				assert (su.getDocument(null) == document);
				
				if (su instanceof IRWorkspaceSourceUnit) {
					final IRWorkspaceSourceUnit rSourceUnit= (IRWorkspaceSourceUnit) su;
					final RLineBreakpointValidator validator = new RLineBreakpointValidator(rSourceUnit,
							breakpoint.getBreakpointType(), position.getOffset(), monitor );
					if (validator.getType() != null) {
						validator.updateBreakpoint(breakpoint);
						return true;
					}
	//				// TODO search method ?
	//				if (breakpoint.getElementType() != IRLineBreakpoint.R_TOPLEVEL_COMMAND_ELEMENT_TYPE) {
	//				}
				}
				return false;
			}
			catch (final CoreException e) {
				StatusManager.getManager().handle(new Status(IStatus.ERROR, RUI.PLUGIN_ID, 0,
						NLS.bind("An error occurred when updating an R line breakpoint in ''{0}''.",
								su.getElementName().getDisplayName() ), e ));
				return false;
			}
			finally {
				su.disconnect(monitor);
			}
		}
		return false;
	}
	
	private boolean updateBasic(final IMarker marker,
			final IDocument document, final Position position) {
		boolean offsetsInitialized = false;
		final int markerStart= MarkerUtilities.getCharStart(marker);
		final int markerEnd= MarkerUtilities.getCharEnd(marker);
		
		final Map<String, Object> attributes= new IdentityHashMap<>(4);
		if (markerStart != -1 && markerEnd != -1) {
			offsetsInitialized = true;
			
			int offset= position.getOffset();
			if (markerStart != offset) {
				attributes.put(IMarker.CHAR_START, Integer.valueOf(offset));
			}
			
			offset += position.getLength();
			if (markerEnd != offset) {
				attributes.put(IMarker.CHAR_END, Integer.valueOf(offset));
			}
		}
		
		if (!offsetsInitialized || (!attributes.isEmpty() && MarkerUtilities.getLineNumber(marker) != -1)) {
			try {
				// marker line numbers are 1-based
				attributes.put(IMarker.LINE_NUMBER, Integer.valueOf(
						document.getLineOfOffset(position.getOffset()) + 1 ));
			} catch (final BadLocationException x) {}
		}
		
		if (!attributes.isEmpty()) {
			try {
				marker.setAttributes(attributes);
			}
			catch (final CoreException e) {
				StatusManager.getManager().handle(e.getStatus());
			}
		}
		
		return true;
	}
	
}
