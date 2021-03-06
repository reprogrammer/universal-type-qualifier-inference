package checker.framework.separated.view.views.tree;

import java.util.Set;

import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jface.action.Action;
import org.eclipse.jface.action.IMenuListener;
import org.eclipse.jface.action.IMenuManager;
import org.eclipse.jface.action.MenuManager;
import org.eclipse.jface.action.Separator;
import org.eclipse.jface.viewers.DoubleClickEvent;
import org.eclipse.jface.viewers.IDoubleClickListener;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.ISelectionChangedListener;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.SelectionChangedEvent;
import org.eclipse.jface.viewers.StructuredSelection;
import org.eclipse.jface.viewers.TreeViewer;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Menu;
import org.eclipse.swt.widgets.Tree;
import org.eclipse.swt.widgets.TreeItem;
import org.eclipse.ui.ISelectionListener;
import org.eclipse.ui.IViewSite;
import org.eclipse.ui.IWorkbenchActionConstants;
import org.eclipse.ui.IWorkbenchPart;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.part.DrillDownAdapter;
import org.eclipse.ui.part.ViewPart;

import checker.framework.change.propagator.ActionableMarkerResolution;
import checker.framework.change.propagator.ComparableMarker;
import checker.framework.quickfixes.descriptors.Fixer;
import checker.framework.separated.propagator.commands.InferNullnessCommandHandler;
import checker.framework.separated.view.views.CodeSnippetRevealer;
import checker.framework.separated.view.views.Colors;
import checker.framework.separated.view.views.Views;
import checker.framework.separated.view.views.list.SeparatedErrorsView;

import com.google.common.base.Optional;

/**
 * This sample class demonstrates how to plug-in a new workbench view. The view
 * shows data obtained from the model. The sample creates a dummy model on the
 * fly, but a real implementation would connect to the model available either in
 * this or another plug-in (e.g. the workspace). The view is connected to the
 * model using a content provider.
 * <p>
 * The view uses a label provider to define how model objects should be
 * presented in the view. Each view can present the same model objects using
 * different labels and icons, if needed. Alternatively, a single label provider
 * can be shared between views in order to ensure that objects of the same type
 * are presented in the same way everywhere.
 * <p>
 */

public class SeparatedChangesView extends ViewPart implements
        ISelectionListener {

    /**
     * The ID of the view as specified by the extension.
     */
    public static final String ID = "checker.framework.separated.view.views.tree.SeparatedChangesView";

    private TreeViewer viewer;
    private DrillDownAdapter drillDownAdapter;
    private Action computeFixesAction;
    private Action action2;
    private Action doubleClickAction;

    /**
     * String representation of the previous in the errors view;
     */
    private String prevSelection = "";

    /**
     * The constructor.
     */
    public SeparatedChangesView() {
    }

    /**
     * This is a callback during the initialization phase.
     */
    @Override
    public void init(IViewSite site) throws PartInitException {
        super.init(site);
        Views.setChangesView(this);
    }

    /**
     * This is a callback that will allow us to create the viewer and initialize
     * it.
     */
    public void createPartControl(Composite parent) {
        viewer = new TreeViewer(parent, SWT.MULTI | SWT.H_SCROLL | SWT.V_SCROLL);
        drillDownAdapter = new DrillDownAdapter(viewer);
        viewer.setContentProvider(new TreeContentProvider());
        viewer.setLabelProvider(new TreeLabelProvider());
        viewer.setSorter(new NameSorter());
        viewer.setInput(getViewSite());
        makeActions();
        hookContextMenu();
        hookDoubleClickAction();
        hookSelectionAction();

        getSite().setSelectionProvider(viewer);
        getSite().getPage().addSelectionListener(SeparatedErrorsView.ID, this);

    }

    private void hookContextMenu() {
        MenuManager menuMgr = new MenuManager("#PopupMenu");
        menuMgr.setRemoveAllWhenShown(true);
        menuMgr.addMenuListener(new IMenuListener() {
            public void menuAboutToShow(IMenuManager manager) {
                SeparatedChangesView.this.fillContextMenu(manager);
            }
        });
        Menu menu = menuMgr.createContextMenu(viewer.getControl());
        viewer.getControl().setMenu(menu);
        getSite().registerContextMenu(menuMgr, viewer);
    }

    private void fillContextMenu(IMenuManager manager) {
        manager.add(computeFixesAction);
        manager.add(action2);
        manager.add(new Separator());
        drillDownAdapter.addNavigationActions(manager);
        // Other plug-ins can contribute there actions here
        manager.add(new Separator(IWorkbenchActionConstants.MB_ADDITIONS));
    }

    private void makeActions() {
        doubleClickAction = new Action() {
            public void run() {
                ISelection selection = viewer.getSelection();
                Optional<TreeObject> selectedTreeObject = getSelectedTreeObject(selection);
                Optional<MarkerResolutionTreeNode> resolutionTreeNode = getSelectedMarkResolution(selectedTreeObject);
                if (resolutionTreeNode.isPresent()) {
                    MarkerResolutionTreeNode markerResolutionTreeNode = resolutionTreeNode
                            .get();
                    ActionableMarkerResolution resolution = markerResolutionTreeNode
                            .getResolution();
                    resolution.run();
                    Optional<SeparatedErrorsView> errorsView = Views
                            .getErrorsView();
                    if (errorsView.isPresent()) {
                        errorsView.get().markAsFixed(
                                markerResolutionTreeNode
                                        .getAllMarkersOnlyBeforeResolution());
                        errorsView.get().rebuildErrorsList(selection);
                    }
                }
            }
        };
    }

    private Optional<MarkerResolutionTreeNode> getSelectedMarkResolution(
            Optional<TreeObject> optionalTreeObject) {
        Optional<MarkerResolutionTreeNode> optionalMarkerTreeNode = Optional
                .absent();
        if (optionalTreeObject.isPresent()) {
            if (optionalTreeObject.isPresent()) {
                TreeObject treeObject = optionalTreeObject.get();
                if (treeObject instanceof MarkerResolutionTreeNode) {
                    optionalMarkerTreeNode = Optional
                            .of((MarkerResolutionTreeNode) treeObject);
                }
            }
        }
        return optionalMarkerTreeNode;
    }

    private Optional<TreeObject> getSelectedTreeObject(ISelection selection) {
        Object selectedObject = ((IStructuredSelection) selection)
                .getFirstElement();
        if (selectedObject instanceof TreeObject) {
            return Optional.of((TreeObject) selectedObject);
        }
        return Optional.absent();
    }

    private void hookDoubleClickAction() {
        viewer.addDoubleClickListener(new IDoubleClickListener() {
            public void doubleClick(DoubleClickEvent event) {
                doubleClickAction.run();
            }
        });
    }

    private void hookSelectionAction() {
        viewer.addPostSelectionChangedListener(new ISelectionChangedListener() {
            @Override
            public void selectionChanged(SelectionChangedEvent event) {
                Optional<MarkerResolutionTreeNode> optionalResolution = getSelectedMarkResolution(getSelectedTreeObject(event
                        .getSelection()));
                if (optionalResolution.isPresent()) {
                    MarkerResolutionTreeNode resolution = optionalResolution
                            .get();
                    IJavaProject javaProject = InferNullnessCommandHandler.selectedJavaProject
                            .get();
                    Fixer fixer = resolution.getResolution().createFixer(
                            javaProject);
                    selectAndReveal(fixer);

                }
            }

            private void selectAndReveal(Fixer fixer) {
                new CodeSnippetRevealer().reveal(fixer.getCompilationUnit(),
                        fixer.getOffset(), fixer.getLength());
            }

        });
    }

    /**
     * Passing the focus request to the viewer's control.
     */
    public void setFocus() {
        viewer.getControl().setFocus();
    }

    @Override
    public void selectionChanged(IWorkbenchPart part, ISelection selection) {
        if (!(selection instanceof StructuredSelection)) {
            return;
        }
        if (selection.isEmpty()) {
            return;
        }
        String currentSelection = selection.toString();
        if (prevSelection.equals(currentSelection)) {
            return;
        }
        prevSelection = currentSelection;

        Tree tree = viewer.getTree();
        Color defaultColor = tree.getForeground();
        tree.setRedraw(false);
        for (Object o : ((StructuredSelection) selection).toList()) {
            ComparableMarker selectedMarker = (ComparableMarker) o;
            for (TreeItem item : tree.getItems()) {
                highlight(selectedMarker, item, defaultColor);
            }
        }
        tree.setRedraw(true);
    }

    private void highlight(ComparableMarker selectedMarker, TreeItem item,
            Color defaultColor) {
        Object data = item.getData();
        if (!(data instanceof MarkerResolutionTreeNode)) {
            return;
        }
        MarkerResolutionTreeNode treeNode = (MarkerResolutionTreeNode) data;
        Set<ComparableMarker> markers = treeNode.getResolution()
                .getMarkersToBeResolvedByFixer();
        if (markers.contains(selectedMarker)) {
            highlightTreeItem(item);
        } else {
            item.setForeground(defaultColor);
        }
        for (TreeItem child : item.getItems()) {
            highlight(selectedMarker, child, defaultColor);
        }
    }

    private void highlightTreeItem(TreeItem item) {
        item.setForeground(Colors.RED);
    }

}
