/** 
 * Copyright (c) 2009-2011, The HATS Consortium. All rights reserved. 
 * This file is licensed under the terms of the Modified BSD License.
 */
package eu.hatsproject.absplugin.wizards;

import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.ui.INewWizard;
import org.eclipse.ui.IWorkbench;

import eu.hatsproject.absplugin.editor.ABSEditor;
import eu.hatsproject.absplugin.wizards.WizardUtil.InsertType;
import eu.hatsproject.absplugin.wizards.pages.IABSClassInterfaceWizardPage;
import eu.hatsproject.absplugin.wizards.pages.NewInterfaceInFileWizardPage;

/**
 * Wizard for creating a new interface in an existing module
 * @author cseise
 *
 */
public class NewInterfaceWizard extends NewClassInterfaceWizard implements INewWizard {
	private NewInterfaceInFileWizardPage interfaceInFilePage;

	private final String WIZARD_TITLE = "New ABS interface"; 
	private final String WIZARD_DESCRIPTION = "Create a new ABS Interface within an ABS module";
	
	private final String ERROR_TITLE = "Error";
	private final String ERROR_TEXT  = "Fatal Error in wizard: The module declaration could not be found!";
	
	public NewInterfaceWizard() {
		insertType = InsertType.INSERT_INTERFACE;		
	}

	@Override
	public void init(IWorkbench workbench, IStructuredSelection selection) {
		super.init(workbench, selection);
		setWindowTitle(WIZARD_TITLE);
	}
	
	@Override
	public void addPages(){
		interfaceInFilePage = new NewInterfaceInFileWizardPage(WIZARD_TITLE);
		page = (IABSClassInterfaceWizardPage) interfaceInFilePage;
		page.setInitialModule(mDecl);
		page.setDescription(WIZARD_DESCRIPTION);
		page.setTitle(WIZARD_TITLE);
		addPage(page);		
	}
	
	@Override
	public boolean performFinish() {
		this.findModuleDecl();

		ABSEditor editor = WizardUtil.getEditorForModuleDecl(mDecl);
		if (editor == null)
			return false;
		IDocument document = WizardUtil.getDocumentForModuleDecl(editor);
		try {
			int off = WizardUtil.getInsertionPosition(document, mDecl);

			document.replace(off, 0, insertType.getInsertionString(page.getNewName()));

			off += insertType.getInsertOffset(page.getNewName());
			
			WizardUtil.saveEditorAndGotoOffset(editor, off);

			return true;
		} catch (BadLocationException e) {
			e.printStackTrace();
			MessageDialog.openError(getShell(), ERROR_TITLE, ERROR_TEXT);
			return false;
		}
	}

}
