/*
 * Copyright (c) 2012 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.labkey.laboratory;

import org.jetbrains.annotations.NotNull;
import org.json.JSONObject;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.TableInfo;
import org.labkey.api.laboratory.AbstractDataProvider;
import org.labkey.api.laboratory.DetailsUrlNavItem;
import org.labkey.api.laboratory.JSTabbedReportItem;
import org.labkey.api.laboratory.LaboratoryService;
import org.labkey.api.laboratory.QueryCountNavItem;
import org.labkey.api.laboratory.QueryTabbedReportItem;
import org.labkey.api.laboratory.ReportItem;
import org.labkey.api.laboratory.SimpleQueryNavItem;
import org.labkey.api.laboratory.SimpleSettingsItem;
import org.labkey.api.laboratory.TabbedReportItem;
import org.labkey.api.ldk.NavItem;
import org.labkey.api.module.Module;
import org.labkey.api.query.DetailsURL;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.UserSchema;
import org.labkey.api.security.User;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.ViewContext;
import org.labkey.api.view.template.ClientDependency;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * User: bimber
 * Date: 10/7/12
 * Time: 10:01 AM
 */
public class LaboratoryDataProvider extends AbstractDataProvider
{
    public static final String NAME = "Laboratory";
    private Module _module;

    public LaboratoryDataProvider(Module m)
    {
        _module = m;
    }

    public String getName()
    {
        return NAME;
    }

    //TODO
    public ActionURL getInstructionsUrl(Container c, User u)
    {
        return null;
    }

    public List<NavItem> getDataNavItems(Container c, User u)
    {
        return Collections.emptyList();
    }

    public List<NavItem> getSampleNavItems(Container c, User u)
    {
        List<NavItem> items = new ArrayList<NavItem>();
        if (c.getActiveModules().contains(getOwningModule()))
        {
            items.add(new SimpleQueryNavItem(this, LaboratoryModule.SCHEMA_NAME, "Samples", LaboratoryService.NavItemCategory.samples.name()));
            items.add(new SimpleQueryNavItem(this, LaboratoryModule.SCHEMA_NAME, "DNA_Oligos", LaboratoryService.NavItemCategory.samples.name()));
            items.add(new SimpleQueryNavItem(this, LaboratoryModule.SCHEMA_NAME, "Peptides", LaboratoryService.NavItemCategory.samples.name()));
            items.add(new SimpleQueryNavItem(this, LaboratoryModule.SCHEMA_NAME, "Antibodies", LaboratoryService.NavItemCategory.samples.name()));
        }
        return Collections.unmodifiableList(items);
    }

    public List<NavItem> getReportItems(Container c, User u)
    {
        List<NavItem> items = new ArrayList<NavItem>();
        if (c.getActiveModules().contains(getOwningModule()))
        {
            String category = "Samples"; //note, this is how they appear in the reports panel
            ReportItem owner1 = new ReportItem(this, LaboratoryModule.SCHEMA_NAME, "Samples", LaboratoryService.NavItemCategory.samples.name());
            SimpleQueryNavItem item1 = new SimpleQueryNavItem(this, LaboratoryModule.SCHEMA_NAME, "Samples", category, "View All Samples");
            item1.setOwnerKey(owner1.getPropertyManagerKey());
            items.add(item1);

            DetailsUrlNavItem item2 = new DetailsUrlNavItem(this, DetailsURL.fromString("/laboratory/freezerSummary.view", c), "Freezer Summary", category);
            item2.setOwnerKey(owner1.getPropertyManagerKey());
            items.add(item2);

            items.add(new ReportItem(this, LaboratoryModule.SCHEMA_NAME, "samples_by_subjectid_and_type", category, "Samples By Subject Id And Type"));
            items.add(new ReportItem(this, LaboratoryModule.SCHEMA_NAME, "samples_by_subjectid_date_and_type", category, "Samples By Subject Id, Sample Date And Type"));

            SimpleQueryNavItem owner2 = new SimpleQueryNavItem(this, LaboratoryModule.SCHEMA_NAME, "DNA_Oligos", LaboratoryService.NavItemCategory.samples.name());
            ReportItem item3 = new ReportItem(this, LaboratoryModule.SCHEMA_NAME, "DNA_Oligos", category, "View All DNA Oligos");
            item3.setOwnerKey(owner2.getPropertyManagerKey());
            items.add(item3);

            SimpleQueryNavItem owner3 = new SimpleQueryNavItem(this, LaboratoryModule.SCHEMA_NAME, "Peptides", LaboratoryService.NavItemCategory.samples.name());
            ReportItem item4 = new ReportItem(this, LaboratoryModule.SCHEMA_NAME, "Peptides", category, "View All Peptides");
            item4.setOwnerKey(owner3.getPropertyManagerKey());
            items.add(item4);

//            for (Study study : StudyService.get().getAllStudies(c, u))
//            {
//                items.add(new DetailsUrlNavItem(this, DetailsURL.fromString("/study/begin.view", study.getContainer()), study.getLabel(), "Studies"));
//            }
//
//            FileContentService service = ServiceRegistry.get().getService(FileContentService.class);
//            for (AttachmentDirectory dir : service.getRegisteredDirectories(c))
//            {
//                items.add(new DetailsUrlNavItem(this, DetailsURL.fromString("/filecontent/begin.view?fileSetName=" + dir.getName(), c), dir.getLabel(), "Filesets"));
//            }

            DetailsUrlNavItem item5 = new DetailsUrlNavItem(this, DetailsURL.fromString("/laboratory/dataBrowser.view", c), "Data Browser", "General");
            items.add(item5);

            SimpleQueryNavItem owner4 = new SimpleQueryNavItem(this, LaboratoryModule.SCHEMA_NAME, "Antibodies", LaboratoryService.NavItemCategory.samples.name());
            ReportItem item6 = new ReportItem(this, LaboratoryModule.SCHEMA_NAME, "Antibodies", category, "View All Antibodies");
            item6.setOwnerKey(owner4.getPropertyManagerKey());
            items.add(item6);
        }

        return Collections.unmodifiableList(items);
    }

    public List<NavItem> getSettingsItems(Container c, User u)
    {
        List<NavItem> items = new ArrayList<NavItem>();
        String categoryName = "Samples";
        String general = "General Settings";

        if (ContainerManager.getSharedContainer().equals(c))
        {
            items.add(new SimpleSettingsItem(this, LaboratoryModule.SCHEMA_NAME, "Cell_Type", categoryName, "Allowable Cell Types"));
            items.add(new SimpleSettingsItem(this, LaboratoryModule.SCHEMA_NAME, "DNA_Mol_Type", categoryName, "Allowable DNA Molecule Types"));
            items.add(new SimpleSettingsItem(this, LaboratoryModule.SCHEMA_NAME, "Reference_Peptides", categoryName, "Reference Peptides"));
            items.add(new SimpleSettingsItem(this, LaboratoryModule.SCHEMA_NAME, "Genders", categoryName, "Allowable Genders"));
            items.add(new SimpleSettingsItem(this, LaboratoryModule.SCHEMA_NAME, "Geographic_Origins", categoryName, "Allowable Geographic Origins"));
            items.add(new SimpleSettingsItem(this, LaboratoryModule.SCHEMA_NAME, "Sample_Additive", categoryName, "Allowable Sample Additives"));
            items.add(new SimpleSettingsItem(this, LaboratoryModule.SCHEMA_NAME, "Species", categoryName, "Allowable Species"));

            items.add(new DetailsUrlNavItem(this, DetailsURL.fromString("/laboratory/synchronizeAssayFields.view", ContainerManager.getRoot()), "Synchronize Assay Fields", general));

            if (u.isSiteAdmin())
            {
                items.add(new DetailsUrlNavItem(this, DetailsURL.fromString("/laboratory/resetLaboratoryFolders.view", ContainerManager.getRoot()), "Reset Tabs and Webparts", general));
                items.add(new DetailsUrlNavItem(this, DetailsURL.fromString("/laboratory/initWorkbooks.view", ContainerManager.getRoot()), "Initialize Workbooks", general));
                items.add(new DetailsUrlNavItem(this, DetailsURL.fromString("/laboratory/initContainerIncrementingTable.view", ContainerManager.getRoot()), "Initialize Autoincrementing Tables", general));
            }
        }
        else
        {
            if (c.getActiveModules().contains(getOwningModule()))
            {
                items.add(new SimpleSettingsItem(this, LaboratoryModule.SCHEMA_NAME, "Freezers", categoryName, "Manage Freezers"));

                items.add(new DetailsUrlNavItem(this, DetailsURL.fromString("/laboratory/itemVisibility.view", c), "Control Item Visibility", general));
                items.add(new DetailsUrlNavItem(this, DetailsURL.fromString("/laboratory/itemDefaultViews.view", c), "Control Item Default Views", general));
                items.add(new DetailsUrlNavItem(this, DetailsURL.fromString("/laboratory/customizeDataBrowser.view", c), "Customize Data Browser", general));
                items.add(new DetailsUrlNavItem(this, DetailsURL.fromString("/ldk/notificationAdmin.view", c), "Manage Notifications", general));
                items.add(new DetailsUrlNavItem(this, DetailsURL.fromString("/laboratory/assayDefaults.view", c), "Set Assay Defaults", general));
                items.add(new DetailsUrlNavItem(this, DetailsURL.fromString("/laboratory/populateInitialValues.view", c), "Populate Default Values", general));

                items.add(new SimpleSettingsItem(this, LaboratoryModule.SCHEMA_NAME, "Sample_Type", "Samples", "Allowable Sample Types"));

                if (u.isSiteAdmin())
                {
                    items.add(new DetailsUrlNavItem(this, DetailsURL.fromString("/laboratory/synchronizeAssayFields.view", c), "Synchronize Assay Fields", general));
                    items.add(new DetailsUrlNavItem(this, DetailsURL.fromString("/laboratory/resetLaboratoryFolders.view", c), "Reset Tabs and Webparts", general));
                    items.add(new DetailsUrlNavItem(this, DetailsURL.fromString("/laboratory/initWorkbooks.view", c), "Initialize Workbooks", general));
                }
            }
        }

        //these are always available
        if (c.getActiveModules().contains(getOwningModule()))
        {
            String name = "Manage " + (ContainerManager.getSharedContainer().equals(c) ? "Default " : "") + "Data and Demographics Sources";
            items.add(new DetailsUrlNavItem(this, DetailsURL.fromString("/laboratory/manageDataSources.view", c), name, general));
        }

        return Collections.unmodifiableList(items);
    }

    public List<NavItem> getMiscItems(Container c, User u)
    {
        List<NavItem> items = new ArrayList<NavItem>();
        if (c.getActiveModules().contains(getOwningModule()))
        {
            items.add(new DetailsUrlNavItem(this, DetailsURL.fromString("/laboratory/manageSubjects.view", c), "Manage Subjects and Groups", "Subjects and Projects"));
        }

        return Collections.unmodifiableList(items);
    }

    public JSONObject getTemplateMetadata(ViewContext ctx)
    {
        return new JSONObject();
    }

    @NotNull
    public Set<ClientDependency> getClientDependencies()
    {
        return Collections.emptySet();
    }

    public Module getOwningModule()
    {
        return _module;
    }

    public List<NavItem> getSummary(Container c, User u)
    {
        List<NavItem> items = new ArrayList<NavItem>();

        for (NavItem nav : getSampleNavItems(c, u))
        {
            if (nav.isVisible(c, u))
            {
                SimpleQueryNavItem item = ((SimpleQueryNavItem)nav);
                items.add(new QueryCountNavItem(this, item.getSchema(), item.getQuery(), item.getCategory(), item.getLabel()));
            }
        }

        return Collections.unmodifiableList(items);
    }

    public List<NavItem> getSubjectIdSummary(Container c, User u, String subjectId)
    {
        List<NavItem> items = new ArrayList<NavItem>();

        for (NavItem nav : getSampleNavItems(c, u))
        {
            if (nav.isVisible(c, u) && nav instanceof SimpleQueryNavItem)
            {
                SimpleQueryNavItem item = ((SimpleQueryNavItem)nav);
                UserSchema us = QueryService.get().getUserSchema(u, c, item.getSchema());
                if (us != null)
                {
                    TableInfo ti = us.getTable(item.getQuery());
                    if (ti != null)
                    {
                        ColumnInfo ci = getSubjectColumn(ti);
                        if (ci != null)
                        {
                            QueryCountNavItem qc = new QueryCountNavItem(this, item.getSchema(), item.getQuery(), item.getCategory(), item.getLabel());
                            qc.setFilter(new SimpleFilter(ci.getFieldKey(), subjectId));
                            items.add(qc);
                        }
                    }
                }
            }
        }

        return Collections.unmodifiableList(items);
    }

    @Override
    public List<TabbedReportItem> getTabbedReportItems(Container c, User u)
    {
        List<TabbedReportItem> items = new ArrayList<TabbedReportItem>();

        NavItem nav = new SimpleQueryNavItem(this, LaboratoryModule.SCHEMA_NAME, "Samples", LaboratoryService.NavItemCategory.samples.name());
        TabbedReportItem item = new QueryTabbedReportItem(this, LaboratoryModule.SCHEMA_NAME, LaboratorySchema.TABLE_SAMPLES, "Samples", "Samples");
        item.setVisible(nav.isVisible(c, u));
        item.setOwnerKey(nav.getPropertyManagerKey());
        items.add(item);


        TabbedReportItem subSummary = new JSTabbedReportItem(this, "subjectSummary", "Subject Summary", "General", "subjectSummary");
        items.add(subSummary);

        return Collections.unmodifiableList(items);
    }
}
