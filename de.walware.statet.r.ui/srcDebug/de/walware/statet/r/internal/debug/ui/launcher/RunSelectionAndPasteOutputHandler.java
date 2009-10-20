/*******************************************************************************
 * Copyright (c) 2008-2009 WalWare/StatET-Project (www.walware.de/goto/statet).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     Stephan Wahlbrink - initial API and implementation
 *******************************************************************************/

package de.walware.statet.r.internal.debug.ui.launcher;

import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.debug.core.IStreamListener;
import org.eclipse.debug.core.model.IStreamMonitor;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.IRegion;
import org.eclipse.jface.text.ITextSelection;
import org.eclipse.jface.text.Position;
import org.eclipse.jface.text.TextUtilities;
import org.eclipse.jface.text.source.SourceViewer;
import org.eclipse.ui.IWorkbenchPart;
import org.eclipse.ui.handlers.HandlerUtil;
import org.eclipse.ui.progress.IWorkbenchSiteProgressService;
import org.eclipse.ui.services.IServiceLocator;
import org.eclipse.ui.statushandlers.StatusManager;

import de.walware.ecommons.text.TextUtil;
import de.walware.ecommons.ui.text.sourceediting.ISourceEditor;
import de.walware.ecommons.ui.util.UIAccess;
import de.walware.ecommons.ui.util.WorkbenchUIUtil;

import de.walware.statet.nico.core.runtime.IRequireSynch;
import de.walware.statet.nico.core.runtime.IToolRunnable;
import de.walware.statet.nico.core.runtime.IToolRunnableControllerAdapter;
import de.walware.statet.nico.core.runtime.Queue;
import de.walware.statet.nico.core.runtime.SubmitType;
import de.walware.statet.nico.core.runtime.ToolController;
import de.walware.statet.nico.core.runtime.ToolProcess;
import de.walware.statet.nico.ui.NicoUI;
import de.walware.statet.nico.ui.NicoUITools;

import de.walware.statet.r.core.IRCoreAccess;
import de.walware.statet.r.core.RUtil;
import de.walware.statet.r.core.rsource.RIndentUtil;
import de.walware.statet.r.internal.debug.ui.RLaunchingMessages;
import de.walware.statet.r.nico.IRBasicAdapter;
import de.walware.statet.r.nico.RTool;
import de.walware.statet.r.nico.RWorkspace;
import de.walware.statet.r.ui.RUI;


/**
 * 
 */
public class RunSelectionAndPasteOutputHandler extends AbstractHandler {
	
	
	private static class R implements IToolRunnable, Runnable {
		
		private ISourceEditor fEditor;
		private IDocument fDocument;
		private String[] fLines;
		private Position fPosition;
		private StringBuilder fOutput;
		
		
		public R(final ISourceEditor editor) {
			fEditor = editor;
		}
		
		private boolean setupSource(final ITextSelection selection) {
			final SourceViewer viewer = fEditor.getViewer();
			fDocument = viewer.getDocument();
			try {
				if (selection.getLength() > 0) {
					final ArrayList<String> lines = new ArrayList<String>(0);
					TextUtil.addLines(fDocument, selection.getOffset(), selection.getLength(), lines);
					fLines = lines.toArray(new String[lines.size()]);
					
					final int start = selection.getOffset();
					int end = start + selection.getLength();
					final char c = fDocument.getChar(end-1);
					if (c == '\n') {
						end--;
						if (end > 0 && fDocument.getChar(end-1) == '\r') {
							end--;
						}
					}
					else if (c == '\r') {
						end--;
					}
					fPosition = new Position(start, end - start);
				}
				else {
					final IRegion line = fDocument.getLineInformationOfOffset(selection.getOffset());
					fLines = new String[] { fDocument.get(line.getOffset(), line.getLength()) };
					fPosition = new Position(line.getOffset(), line.getLength());
				}
				fDocument.addPosition(fPosition);
				return true;
			}
			catch (final BadLocationException e) {
				StatusManager.getManager().handle(new Status(IStatus.ERROR, RUI.PLUGIN_ID, -1, "An error occurred preparing Run and Paste Output", e)); //$NON-NLS-1$
				return false;
			}
		}
		
		public void dispose() {
			if (fDocument != null && fPosition != null) {
				fDocument.removePosition(fPosition);
			}
			fEditor = null;
			fDocument = null;
			fPosition = null;
			fOutput = null;
			fLines = null;
		}
		
		public String getTypeId() {
			return "editor/run-and-paste"; //$NON-NLS-1$
		}
		
		public String getLabel() {
			return RLaunchingMessages.RunCodeAndPasteOutput_RTask_label;
		}
		
		public SubmitType getSubmitType() {
			return SubmitType.TOOLS;
		}
		
		public void changed(final int event, final ToolProcess process) {
			if (event == Queue.ENTRIES_DELETE || event == Queue.ENTRIES_ABANDONED) {
				UIAccess.getDisplay().asyncExec(new Runnable() {
					public void run() {
						dispose();
					}
				});
			}
		}
		
		public void run(final IToolRunnableControllerAdapter adapter, final IProgressMonitor monitor)
				throws InterruptedException, CoreException {
			final IRBasicAdapter r = (IRBasicAdapter) adapter;
			fOutput = new StringBuilder(200);
			final IStreamListener listener = new IStreamListener() {
				public void streamAppended(final String text, final IStreamMonitor monitor) {
					fOutput.append(text);
				}
			};
			final ToolController<? extends RWorkspace> controller = r.getController();
			try {
				controller.getStreams().getOutputStreamMonitor().addListener(listener);
				controller.getStreams().getErrorStreamMonitor().addListener(listener);
				for (int i = 0; i < fLines.length; i++) {
					if (monitor.isCanceled()) {
						return;
					}
					monitor.subTask(fLines[i]);
					r.submitToConsole(fLines[i], monitor);
					if (r instanceof IRequireSynch) {
						final Pattern pattern = ((IRequireSynch) r).synch(monitor);
						if (pattern != null) {
							final Matcher matcher = pattern.matcher(fOutput);
							int idx = -1;
							while (matcher.find()) {
								idx = matcher.start();
							}
							if (idx >= 0) {
								fOutput.delete(idx, fOutput.length());
							}
						}
					}
					r.briefAboutChange(RWorkspace.REFRESH_AUTO);
				}
			}
			finally {
				controller.getStreams().getOutputStreamMonitor().removeListener(listener);
				controller.getStreams().getErrorStreamMonitor().removeListener(listener);
				UIAccess.getDisplay().asyncExec(this);
			}
		}
		
		public void run() {
			// After R in display
			final SourceViewer viewer = fEditor.getViewer();
			if (!UIAccess.isOkToUse(viewer)
					|| (viewer.getDocument() != fDocument)
					|| fPosition.isDeleted()) {
				return;
			}
			
			IWorkbenchSiteProgressService progressService = null;
			final ISourceEditor editor = (ISourceEditor) fEditor.getAdapter(ISourceEditor.class);
			if (editor != null) {
				final IServiceLocator serviceLocator = editor.getServiceLocator();
				if (serviceLocator != null) {
					progressService = (IWorkbenchSiteProgressService) serviceLocator.getService(IWorkbenchSiteProgressService.class);
				}
			}
			if (progressService != null) {
				progressService.incrementBusy();
			}
			
			try {
				final IRCoreAccess rCore = (IRCoreAccess) fEditor.getAdapter(IRCoreAccess.class);
				final RIndentUtil util = new RIndentUtil(fDocument, rCore.getRCodeStyle());
				
				final int indent = util.getMultilineIndentColumn(fDocument.getLineOfOffset(fPosition.getOffset()),
						fDocument.getLineOfOffset(fPosition.getOffset() + fPosition.getLength()));
				final String delimiter = TextUtilities.getDefaultLineDelimiter(fDocument);
				final String prefix = delimiter + util.createIndentString(indent) + "# "; //$NON-NLS-1$
				
				final String[] lines = RUtil.LINE_SEPARATOR_PATTERN.split(fOutput);
				final int size = fOutput.length() + lines.length*(prefix.length()-1) + 2;
				fOutput.setLength(0);
				fOutput.ensureCapacity(size);
				for (int i = 0; i < lines.length; i++) {
					fOutput.append(prefix);
					fOutput.append(lines[i]);
				}
				fOutput.append(delimiter);
				final int pos = fPosition.getOffset()+fPosition.getLength();
				fDocument.replace(pos, 0, fOutput.toString());
				viewer.revealRange(pos, fOutput.length());
				if (progressService != null) {
					progressService.warnOfContentChange();
				}
			} catch (final BadLocationException e) {
				StatusManager.getManager().handle(new Status(IStatus.ERROR, RUI.PLUGIN_ID, -1,
						RLaunchingMessages.RunCodeAndPasteOutput_error_WhenPasting_message, e),
						StatusManager.LOG | StatusManager.SHOW);
			}
			finally {
				dispose();
				if (progressService != null) {
					progressService.decrementBusy();
				}
			}
			
		}
	}
	
	
	public RunSelectionAndPasteOutputHandler() {
	}
	
	
	public Object execute(final ExecutionEvent event) throws ExecutionException {
		final IWorkbenchPart workbenchPart = HandlerUtil.getActivePart(event);
		final ISourceEditor editor = (ISourceEditor) workbenchPart.getAdapter(ISourceEditor.class);
		if (editor != null) {
			if (!editor.isEditable(true)) {
				cancel(null, new Status(IStatus.ERROR, RUI.PLUGIN_ID,
						RLaunchingMessages.RunCodeAndPasteOutput_info_WriteProtected_status), event);
				return null;
			}
			final SourceViewer viewer = editor.getViewer();
			final ITextSelection selection = (ITextSelection) viewer.getSelection();
			final R r = new R(editor);
			if (!r.setupSource(selection)) {
				cancel(r, new Status(IStatus.ERROR, RUI.PLUGIN_ID,
						RLaunchingMessages.RunCodeAndPasteOutput_error_Unspecific_status), null);
				return null;
			}
			final ToolProcess process = NicoUI.getToolRegistry().getActiveToolSession(UIAccess.getActiveWorkbenchPage(true)).getProcess();
			final ToolController controller; 
			try {
				controller = NicoUITools.accessController(RTool.TYPE, process);
			}
			catch (final CoreException e) {
				cancel(r, e.getStatus(), event);
				return null;
			}
			final IStatus submitStatus = controller.submit(r);
			if (submitStatus.getSeverity() > IStatus.OK) {
				cancel(r, submitStatus, event);
			}
			return null;
		}
		
		LaunchShortcutUtil.handleUnsupportedExecution(event);
		return null;
	}
	
	private void cancel(final R r, final IStatus status, final ExecutionEvent executionEvent) {
		if (r != null) {
			r.dispose();
		}
		WorkbenchUIUtil.indicateStatus(status, executionEvent);
	}
	
}
