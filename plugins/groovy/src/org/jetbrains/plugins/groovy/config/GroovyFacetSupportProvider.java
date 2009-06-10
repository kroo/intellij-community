/*
 * Copyright 2000-2007 JetBrains s.r.o.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.plugins.groovy.config;

import com.intellij.facet.impl.ui.FacetTypeFrameworkSupportProvider;
import com.intellij.facet.impl.ui.VersionConfigurable;
import com.intellij.facet.ui.ValidationResult;
import com.intellij.facet.ui.libraries.LibraryInfo;
import com.intellij.ide.util.newProjectWizard.FrameworkSupportModel;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.grails.config.GrailsConfigUtils;
import org.jetbrains.plugins.groovy.GroovyBundle;
import org.jetbrains.plugins.groovy.config.ui.CreateLibraryDialog;
import org.jetbrains.plugins.groovy.config.ui.GroovyFacetEditor;
import org.jetbrains.plugins.groovy.settings.GroovyApplicationSettings;
import org.jetbrains.plugins.groovy.util.LibrariesUtil;

import javax.swing.*;
import java.util.Arrays;
import java.util.Comparator;

/**
 * @author ven
 */
public class GroovyFacetSupportProvider extends FacetTypeFrameworkSupportProvider<GroovyFacet> {

  protected GroovyFacetSupportProvider() {
    super(GroovyFacetType.getInstance());
  }

  @NotNull
  public GroovyVersionConfigurable createConfigurable(final @NotNull FrameworkSupportModel model) {
    return new GroovyVersionConfigurable(model.getProject(), getDefaultVersion());
  }

  @Nullable
  public String getDefaultVersion() {
    GroovyApplicationSettings settings = GroovyApplicationSettings.getInstance();
    return settings.DEFAULT_GROOVY_LIB_NAME;
  }

  @NotNull
  public String[] getVersions() {
    String[] versions =
      ContainerUtil.map2Array(GroovyConfigUtils.getInstance().getGlobalSDKLibraries(), String.class, new Function<Library, String>() {
        public String fun(Library library) {
          return library.getName();
        }
      });
    Arrays.sort(versions, new Comparator<String>() {
      public int compare(String o1, String o2) {
        return -o1.compareToIgnoreCase(o2);
      }
    });
    return versions;
  }

  @NotNull
  @NonNls
  public String getLibraryName(final String name) {
    return name != null ? name : super.getLibraryName(name);
  }

  @Override
  public String getTitle() {
    return "Groovy/Grails";
  }

  @NotNull
  public LibraryInfo[] getLibraries(String selectedVersion) {
    return super.getLibraries(selectedVersion);
  }

  protected void setupConfiguration(GroovyFacet facet, ModifiableRootModel rootModel, String v) {
    /**
     * Everyting is already done in {@link GroovyVersionConfigurable#addSupport}
     * */
  }

  public class GroovyVersionConfigurable extends VersionConfigurable {
    public final GroovyFacetEditor myFacetEditor;

    public JComponent getComponent() {
      return myFacetEditor.createComponent();
    }

    @NotNull
    public LibraryInfo[] getLibraries() {
      final Library lib = myFacetEditor.getSelectedLibrary();
      return GroovyFacetSupportProvider.this.getLibraries(lib != null ? lib.getName() : null);
    }

    @Nullable
    public String getNewSdkPath() {
      return myFacetEditor.getNewSdkPath();
    }

    public boolean addNewSdk() {
      return myFacetEditor.addNewSdk();
    }

    @NonNls
    @NotNull
    public String getLibraryName() {
      final Library lib = myFacetEditor.getSelectedLibrary();
      return GroovyFacetSupportProvider.this.getLibraryName(lib != null ? lib.getName() : null);
    }

    @Nullable
    private AbstractConfigUtils getFrameworkCreatorByPath(String path) {
      if (StringUtil.isEmpty(path)) {
        return null;
      }

      if (GrailsConfigUtils.getInstance().isSDKHome(path) == ValidationResult.OK) {
        return GrailsConfigUtils.getInstance();
      }
      if (GroovyConfigUtils.getInstance().isSDKHome(path) == ValidationResult.OK) {
        return GroovyConfigUtils.getInstance();
      }

      return null;
    }

    public void addSupport(final Module module, final ModifiableRootModel rootModel, @Nullable Library library) {
      final Library selectedLibrary = myFacetEditor.getSelectedLibrary();
      if (selectedLibrary != null && !myFacetEditor.addNewSdk()) {
        final String selectedName = selectedLibrary.getName();
        LibrariesUtil.placeEntryToCorrectPlace(rootModel, rootModel.addLibraryEntry(selectedLibrary));
        GroovyFacetSupportProvider.this.addSupport(module, rootModel, selectedName, selectedLibrary);
      } else if (myFacetEditor.getNewSdkPath() != null) {
        final String path = myFacetEditor.getNewSdkPath();
        final AbstractConfigUtils configUtils = getFrameworkCreatorByPath(path);
        if (configUtils != null && path != null) {
          final Project project = module.getProject();
          final String selectedName = configUtils.generateNewSDKLibName(configUtils.getSDKVersion(path), project);
          //Delayed modal dialog to add Groovy library
          final CreateLibraryDialog dialog = new CreateLibraryDialog(project, GroovyBundle.message("facet.create.lib.title"),
                                                                     GroovyBundle.message("facet.create.project.lib", selectedName),
                                                                     GroovyBundle.message("facet.create.application.lib", selectedName)) {
            @Override
            protected void doOKAction() {
              ApplicationManager.getApplication().runWriteAction(new Runnable() {
                public void run() {
                  ModuleRootManager manager = ModuleRootManager.getInstance(module);
                  ModifiableRootModel rootModel = manager.getModifiableModel();
                  final Library lib = configUtils.createSDKLibrary(path, selectedName, project, false, isInProject());
                  LibrariesUtil.placeEntryToCorrectPlace(rootModel, rootModel.addLibraryEntry(lib));
                  if (lib != null) {
                    configUtils.saveSDKDefaultLibName(selectedName);
                  }
                  GroovyFacetSupportProvider.this.addSupport(module, rootModel, selectedName, lib);
                  rootModel.commit();
                }
              });
              super.doOKAction();
            }

            @Override
            public void doCancelAction() {
              ApplicationManager.getApplication().runWriteAction(new Runnable() {
                public void run() {
                  GroovyFacetSupportProvider.this.addSupport(module, rootModel, "unknown", null);
                }
              });
              super.doCancelAction();
            }
          };

          ApplicationManager.getApplication().invokeLater(new Runnable() {
            public void run() {
              dialog.show();
            }
          });
        } else {
          GroovyFacetSupportProvider.this.addSupport(module, rootModel, "unknown", null);
        }
      }
    }

    public GroovyVersionConfigurable(Project project, String defaultVersion) {
      super(GroovyFacetSupportProvider.this, getVersions(), defaultVersion);
      myFacetEditor = new GroovyFacetEditor(project, getDefaultVersion());
    }
  }
}
