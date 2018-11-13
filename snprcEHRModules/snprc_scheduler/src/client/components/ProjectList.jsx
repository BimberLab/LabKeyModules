/* 
    ==================================================================================
    author:             David P. Smith
    email:              dsmith@txbiomed.org
    name:               snprc_scheduler
    description:        Animal procedure scheduling system     
    copyright:          Texas Biomedical Research Institute
    created:            October 1 2018      
    ==================================================================================
*/
import React from 'react';
import ReactDataGrid from 'react-data-grid';
import Glyphicon from 'react-bootstrap/lib/Glyphicon'
import { selectProject, filterProjects, sortProjects } from '../actions/dataActions';

const verboseOutput = true;

class EmptyProjectRowsView extends React.Component { render() {return (<div> Loading active projects...</div>);} }

class ProjectList extends React.Component {
    
    constructor(props, context) {
        super(props, context);
        this.state = {
            projectCols: [
                { key: 'Iacuc', name: 'IACUC', width: 75, sortable: true },
                { key: 'description', name: 'Description', width: 255, sortable: true },
                { key: 'revisionNum', name: 'Rev', width: 42, sortable: true }
            ],
            selectedProjects: [],
            sortColumn: null, 
            sortDirection: null,
            filters: {}
        };
        // wire into redux store updates
        this.disconnect = this.props.store.subscribe(this.handleStoreUpdate); 
    }

    componentWillUnmount = () => this.disconnect();
    
    onProjectRowsSelected = (rows) => {
        let selectedProject = null;
        if (rows.length == 1) selectedProject = rows[0].row;
        else rows = [];
        this.setState({ 
            selectedProjects: rows.map(r => r.rowIdx),
            selectedProject: selectedProject
        });
        if (selectedProject != null) {
            if (rows.length > 0) this.props.store.dispatch(selectProject(selectedProject.projectId, selectedProject.revisionNum));
            if (verboseOutput) {
                console.log("ProjectID " + selectedProject.projectId + " selected.");
                console.log(selectedProject);
            }             
        }
    }

    onProjectRowsDeselected = (rows) => {
        let rowIndexes = rows.map(r => r.rowIdx);
        this.setState({ selectedProjects: this.state.selectedProjects.filter(i => rowIndexes.indexOf(i) === -1) });       
    }

    projectRowGetter = (index) => this.state.projects[index];   
    
    handleProjectSearchChange = (event) => this.props.store.dispatch(filterProjects(event.target.value));

    handleStoreUpdate = () => {
        // get all projects from redux
        let projects = this.props.store.getState().project.projects || [];
        // manage project list in local state for rendering
        this.setState({ projects: projects, projectCount: projects.length });
    }

    handleGridSort = (sortColumn, sortDirection) => {
        // de-select any currently selected projects
        this.setState({ selectedProjects: [], selectedProject: null });
        // dispatch our filter request
        this.props.store.dispatch(sortProjects(sortColumn, sortDirection));
    };

    render = () => { 
        return (<div>
            <div className="input-group bottom-padding-8">
            <span className="input-group-addon input-group-addon-buffer"><Glyphicon glyph="search"/></span>
            <input 
                id="projectSearch" 
                type="text" 
                onChange={this.handleProjectSearchChange}
                className="form-control search-input" 
                name="projectSearch" 
                placeholder="Search projects" />
            </div>
            <div className="bottom-padding-8">
                <ReactDataGrid
                    rowKey="ProjectId"
                    columns={this.state.projectCols}
                    rowGetter={this.projectRowGetter}
                    rowsCount={this.state.projectCount}
                    onGridSort={this.handleGridSort}
                    enableCellSelect={true}
                    minHeight={284}
                    rowSelection={{
                        showCheckbox: true,
                        enableShiftSelect: false,
                        onRowsSelected: this.onProjectRowsSelected,
                        onRowsDeselected: this.onProjectRowsDeselected,
                        selectBy: { indexes: this.state.selectedProjects}
                    }}
                    emptyRowsView={EmptyProjectRowsView}  
                />               
            </div>
        </div>)             
    };

}

export default ProjectList;